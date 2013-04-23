package edu.umro.dicom.service;

/*
 * Copyright 2013 Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeFactory;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.DicomOutputStream;
import com.pixelmed.dicom.SOPClass;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.TransferSyntax;
import com.pixelmed.network.AReleaseException;
import com.pixelmed.network.Association;
import com.pixelmed.network.AssociationFactory;
import com.pixelmed.network.CMoveRequestCommandMessage;
import com.pixelmed.network.CompositeResponseHandler;
import com.pixelmed.network.DicomNetworkException;
import com.pixelmed.network.PresentationContext;

import edu.umro.util.Log;

/**
 * Performs a DICOM C-MOVE
 * 
 * @author irrer, adapted from David Clunie's Pixelmed library
 *
 */
public class CMove {

    /** Pixelmed debug level. */
    private static final int DEBUG_LEVEL = 0;

    /** Command that indicates that a C-MOVE is to be performed. */
    private static final String CMOVE_COMMAND = SOPClass.PatientRootQueryRetrieveInformationModelMove;

    /** set by CMoveResponseHandler.  Initialized to 'pending' */
    private int responseStatus = 0xFF00;

    /** Number of failures. */
    private int failureCount = 0;

    /** DICOM Network association for transfer. */
    private Association association = null;

    /** Caller defined processor of incoming responses. */
    private ProgressProcessor progressProcessor = null;

    /** Abort if more than this number of DICOM objects are to
     * be transferred.  0 or less means ignore this value and
     * allow unlimited number of objects to be transferred. */
    private int limit = 0;


    public class Status {
        /** Number of objects completed. */
        public int numCompleted =  0;

        /** Number of objects remaining. */
        public int numRemaining = -1;

        /** Number of objects failed. */
        public int numFailed    =  0;

        /** Number of objects completed with a warning. */
        public int numWarning   =  0;

        /** Total number of objects to process. */
        public int total        = -1;

        /** Status of transfer of latest object. */
        public int latestResponseStatus = 0xFF00;

        @Override
        public String toString() {
            String text =
                "Completed: "     + numCompleted +
                "    Remaining: " + numRemaining + 
                "    Failed: "    + numFailed +
                "    Warning: "   + numWarning +
                "    Total: "     + total +
                "    Latest transfer status: " + responseStatusToString(latestResponseStatus);
            return text;
        }

        /**
         * Show a response status as a string.
         * 
         * @param sts Status to interpret.
         * 
         * @return Human readable description.
         */
        private String responseStatusToString(int sts) {
            String statusText = "0x" + Integer.toHexString(sts);

            switch (sts) {
                case 0xA701: 
                    statusText += " : Refused - Out of Resources - Unable to calculate number of matches";
                case 0xA702: 
                    statusText += " : Refused - Out of Resources - Unable to perform sub-operations";
                    break;
                case 0xA801: 
                    statusText += " : Refused - Move Destination unknown";
                    break;
                case 0xA900: 
                    statusText += " : Failed - Identifier does not match SOP Class";
                    break;
                case 0xFE00: 
                    statusText += " : Cancel - Sub-operations terminated due to Cancel Indication";
                    break;
                case 0xB000: 
                    statusText += " : Warning Sub-operations Complete - One or more Failures";
                    break;
                case 0x0000: 
                    statusText += " : Success - Sub-operations Complete - No Failures";
                    break;
                case 0xFF00: 
                    statusText += " : Pending - Matches are continuing";
                    break;
                default:
                    if ((sts & 0xF000) == (0xC000)) {
                        statusText += " : Failed - Unable to process";
                    }
                    else {
                        statusText += " : Unknown responseStatus";
                    }
                    break;
            }

            return statusText;
        }
    }

    private Status status = new Status();

    public interface ProgressProcessor {
        void process(AttributeList attributeList);
    }

    public Status getStatus() {
        return status;
    }


    private class IdentifierMessage {

        private byte bytes[];

        /**
         * @param   list
         * @param   transferSyntaxUID
         * @exception   IOException
         * @exception   DicomException
         */
        public IdentifierMessage(AttributeList list,String transferSyntaxUID) throws DicomException, IOException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DicomOutputStream dout = new DicomOutputStream(bout,null/* no meta-header */,transferSyntaxUID);
            list.write(dout);
            if (bout.size() % 2 != 0) {
                // How could this happen ? The use of deflate or bzip2 transfer syntaxes can result in an odd number of bytes written by AttributeList.write() (000525)
                // which should pad with null but doesn't, so do it here just in case (000524) :(
                bout.write(0);
            }
            bytes = bout.toByteArray();
            //System.err.println("IdentifierMessage: bytes="+HexDump.dump(bytes));
        }

        /***/
        public byte[] getBytes() {
            return bytes;
        }
    }


    /**
     * Handle the notification of an incoming DICOM.  This does not process the
     * actual DICOM object, but the status associated with the transfer of a
     * single DICOM object.  This is useful for situations where the caller
     * wants to monitor the progress, such as showing the user a progress bar.
     * 
     * The information in the notification includes the number of objects transferred
     * thus far and the number of objects yet to be transferred, so upon receipt of
     * the first object the caller can determine the total size of the transfer.
     * 
     * @author irrer
     *
     */
    protected class CMoveResponseHandler extends CompositeResponseHandler {


        CMoveResponseHandler(int debugLevel) {
            super(debugLevel);
            allowData=true;
        }


        /**
         * @param	list
         */
        protected void evaluateStatusAndSetSuccess(AttributeList list) {
            // could check all sorts of things, like:
            // - AffectedSOPClassUID is what we sent
            // - CommandField is 0x8021 C-MOVE-RSP
            // - MessageIDBeingRespondedTo is what we sent
            // - DataSetType is 0101 for success (no data set) or other for pending
            // - Status is success and consider associated elements
            //
            // for now just treat success or warning as success (and absence as failure)
            if (progressProcessor != null) {
                progressProcessor.process(list);
            }
            responseStatus = Attribute.getSingleIntegerValueOrDefault(list,TagFromName.Status,0xffff);
            int numRemaining = Attribute.getSingleIntegerValueOrDefault(list, TagFromName.NumberOfRemainingSuboperations, -1);
            Log.get().fine("numRemaining: " + numRemaining + " list:\n" + list.toString().replace('\0', ' '));

            synchronized(status) {
                status.numCompleted = Attribute.getSingleIntegerValueOrDefault(list, TagFromName.NumberOfCompletedSuboperations, status.numCompleted);
                status.numRemaining = Attribute.getSingleIntegerValueOrDefault(list, TagFromName.NumberOfRemainingSuboperations, status.numRemaining);
                status.numFailed    = Attribute.getSingleIntegerValueOrDefault(list, TagFromName.NumberOfFailedSuboperations   , status.numFailed   );
                status.numWarning   = Attribute.getSingleIntegerValueOrDefault(list, TagFromName.NumberOfWarningSuboperations  , status.numWarning  );
                status.total = status.numCompleted + status.numFailed + status.numWarning;
                if ((limit > 0) && (status.total > limit)) {
                    try {
                        Log.get().info("Attempting to abort DICOM C-MOVE because limit of " + limit + " was exceeded by value " + status.total);
                        association.abort();
                        Log.get().info("Sent abort of DICOM C-MOVE because limit of " + limit + " was exceeded by value " + status.total);
                    }
                    catch (DicomNetworkException e) {
                        Log.get().info("Attempt to abort DICOM C-MOVE because limit of " + limit +
                                " was exceeded by value " + status.total + " may not have properly aborted because it got the exception: " + e);
                    }
                }
            }

            int failedSuboperations = Attribute.getSingleIntegerValueOrDefault(list,TagFromName.NumberOfFailedSuboperations,0);

            if (failedSuboperations != 0) {
                Log.get().severe("Number of failed sub operations: " + failedSuboperations);
                failureCount++;
            }

            success = responseStatus == 0x0000;		// success

            if (responseStatus != 0xFF00 && failedSuboperations == 0) {
                setDone(true);	// stop if not "pending"
            }
        }

        /**
         * @param list
         */
        protected void makeUseOfDataSet(AttributeList list) {
            Log.get().finer("makeUseOfDataSet: list:\n" + list.toString().replace('\0', ' '));
            // we only get here if there are failed sub-operations, in which case we get a list
            // in Failed SOP Instance UID List (0008,0058)
            setDone(true);
        }
    }


    private LinkedList<PresentationContext> getPresentationContext() {
        LinkedList<PresentationContext> presentationContextList = new LinkedList<PresentationContext>();
        {
            LinkedList<String> tslist = new LinkedList<String>();
            tslist.add(TransferSyntax.ExplicitVRLittleEndian);
            tslist.add(TransferSyntax.ImplicitVRLittleEndian);
            presentationContextList.add(new PresentationContext((byte)0x01,SOPClass.PatientRootQueryRetrieveInformationModelFind,tslist));
            presentationContextList.add(new PresentationContext((byte)0x03,SOPClass.PatientRootQueryRetrieveInformationModelMove,tslist));
        }
        return presentationContextList;
    }

    /**
     * Abort the transfer.
     * 
     * @throws DicomNetworkException
     */
    public void abort() throws DicomNetworkException {
        association.abort();
    }


    /**
     * Perform a DICOM C-MOVE, which is actually a copy because the
     * source objects remain unchanged.  Caution should be used in
     * the <code>specification</code> parameter because it is possible
     * to give such a general specification that many more (thousands,
     * millions) DICOM objects are requested than intended.
     * 
     * @param sourcePacs Source of DICOM objects.
     * 
     * @param destAETitle Destination of copy.
     * 
     * @param callingAETitle The AE title of the party orchestrating
     * the move (this entity).
     * 
     * @param specification The DICOM attributes and values that indicate
     * which objects should be moved.  Usually this is a patient ID, study
     * UID, or series UID.
     * 
     * @param limit If the transfer is going to involve more than this many
     * object, then abort the transfer ASAP.  Note that some objects may have
     * already been transferred by the time the abort is honored.  Use a value
     * of zero or less to indicate no limit.
     * 
     */
    public void begin(PACS sourcePacs, String destAETitle, String callingAETitle, AttributeList specification, int limit, ProgressProcessor progressProcessor) {
        try {
            this.limit = limit;
            Log.get().info("initiating DICOM C-MOVE from  " + sourcePacs +
                    " to " + destAETitle + " called from " + callingAETitle +
                    " limit number of transfers to " + ((limit > 0) ? this.limit : "infinite") +
                    " specifying DICOM objects that meet the following criteria: \n" +
                    specification.toString().replace('\0', ' '));
            this.progressProcessor = progressProcessor;

            association = AssociationFactory.createNewAssociation(sourcePacs.host,sourcePacs.port,sourcePacs.aeTitle,callingAETitle,getPresentationContext(),null,false,DEBUG_LEVEL);
            Log.get().finer(association.toString());
            // Decide which presentation context we are going to use ...
            byte presentationContextID = association.getSuitablePresentationContextID(CMOVE_COMMAND);
            Log.get().finer("MoveSOPClassSCU: Using context ID "+presentationContextID);
            byte cMoveRequestCommandMessage[] = new CMoveRequestCommandMessage(CMOVE_COMMAND,destAETitle).getBytes();
            byte cMoveIdentifier[] = new IdentifierMessage(specification,association.getTransferSyntaxForPresentationContextID(presentationContextID)).getBytes();
            Log.get().finer("MoveSOPClassSCU: Identifier:\n"+specification.toString().replace('\0', ' '));
            association.setReceivedDataHandler(new CMoveResponseHandler(DEBUG_LEVEL));
            // for some reason association.send(usePresentationContextID,cMoveRequestCommandMessage,cMoveIdentifier) fails with Oldenburg imagectn
            // so send the command and the identifier separately ...
            association.send(presentationContextID,cMoveRequestCommandMessage,null);
            association.send(presentationContextID,null,cMoveIdentifier);
            Log.get().finer("MoveSOPClassSCU: waiting for PDUs");
            try {
                association.waitForPDataPDUsUntilHandlerReportsDone();
                Log.get().finer("CMove: got PDU, now releasing association");
                // State 6
                association.release();
            }
            catch (AReleaseException e) {
                // State 1
                Log.get().info("DICOM transfer was stopped, probably either because of limit of number of objects exceeded or remote PACS failed: " + e);
            }
            catch (Exception e) {
                Log.get().info("DICOM transfer stopped, probably either because of limit of number of objects exceeded or remote PACS failed: " + e);
            }

        }
        catch (com.pixelmed.network.DicomNetworkException e) {
            String msg = "C-MOVE failed.";
            if (e.toString().indexOf("java.net.ConnectException") != -1) {
                msg += "  This is most likely caused by the source PACS " + sourcePacs + " being down or being unreachable on the network.";
            }
            if (e.toString().indexOf("java.net.SocketException") != -1) {
                msg += "  This is most likely caused by the source PACS " + sourcePacs + " going down during the transfer.";
            }
            System.out.println(msg + e);
        }

        catch (Exception e) {
            Log.get().severe("C-MOVE unexpectedly failed.  Technical details: " + e);
            status.numFailed++;
        }

    }


    /** For testing only */
    public static void main(String[] args) throws Exception {

        class TestCMove implements Runnable {
            CMove cMove = null;

            class ProgProc implements ProgressProcessor {
                public CMove cMove = null;

                public ProgProc(CMove cMove) {
                    this.cMove = cMove;
                }
                @Override
                public void process(AttributeList attributeList) {
                    int completed = attributeList.get(TagFromName.NumberOfCompletedSuboperations).getSingleIntegerValueOrDefault(-1);
                    System.out.print(" " + completed);
                    System.out.println("  " + cMove.getStatus());
                    if (completed == -60) {  // arbitrarily abort at 60
                        try {
                            System.out.println("Testing abort at 60 objects");
                            cMove.abort();
                        }
                        catch (DicomNetworkException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }


            public TestCMove() throws Exception {
                try {
                    new Thread(this).start();
                    while (cMove == null) {
                        Thread.sleep(50);
                    }
                    System.out.println("C-MOVE has started");
                    System.out.println("Status: " + cMove.getStatus());
                    long timeout = System.currentTimeMillis() + (10 * 1000);
                    while ((cMove.getStatus().numCompleted == 0) && (System.currentTimeMillis() < timeout)) {
                        Thread.sleep(50);
                    }
                    System.out.println("C-MOVE moved a file or timed out");

                    /*
                    try {
                        System.out.println("Testing abort after at least one object has been transferred");
                        cMove.abort();
                    }
                    catch (DicomNetworkException e) {
                        e.printStackTrace();
                    }
                     */

                    System.out.println("Status: " + cMove.getStatus());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void run() {
                try {
                    PACS sourcePacs = new PACS("IRRER", "141.214.125.70", 15678);
                    AttributeList specification = new AttributeList();
                    {
                        Attribute a = AttributeFactory.newAttribute(TagFromName.SeriesInstanceUID);
                        //a.addValue("1.2.826.0.1.3680043.2.135.733423.48611003.7.1325114859.378.59");  // 99999999 RTIMAGE
                        a.addValue("1.2.826.0.1.3680043.2.135.733423.48611003.7.1325114853.44.84");   // 99999999 CT
                        //a.addValue("1.2.826.0.1.3680043.2.135.733423.48611003.7.1111111111.44.99");  // non-existent UID

                        //Attribute a = AttributeFactory.newAttribute(TagFromName.PatientID);
                        //a.addValue("99999999");

                        specification.put(a);
                    }

                    cMove = new CMove();
                    cMove.begin(sourcePacs, "IRRER2", "IRRER2", specification, 1000, new ProgProc(cMove));
                    //cMove.begin(sourcePacs, "UMRADONC-STAGING", "IRRER", specification, 0, new ProgProc(cMove));
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

        new TestCMove();

        //CMove cMove = new CMove(sourcePacs, "IRRER2", "IRRER2", specification, 1000);
        //System.out.println("Status: " + cMove.getStatus());

    }
}
