package edu.umro.DicomTest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.CodeStringAttribute;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.DicomOutputStream;
import com.pixelmed.dicom.LongStringAttribute;
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

/**
 * Performs a DICOM C-MOVE
 * 
 * @author irrer
 *
 */
public class CMove {

    /***/
    protected int debugLevel;
    /***/
    protected int status;           // set by CMoveResponseHandler

    private static int failureCount = 0;

    public int getStatus() {
        return status;
    }

    private static File logFile = null;
    private static FileOutputStream logFileOutputStream = null;

    class IdentifierMessage {

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

    /***/
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
            status = Attribute.getSingleIntegerValueOrDefault(list,TagFromName.Status,0xffff);
            int numRemaining = Attribute.getSingleIntegerValueOrDefault(list, TagFromName.NumberOfRemainingSuboperations, -1);
            logAbbrev(" " + numRemaining);

            String statusText = "0x" + Integer.toHexString(status);
            if ((status != 0xFF00) && (status != 0)) {
                log("\nBad status: " + statusText);
                failureCount++;

                switch (status) {
                case 0xA701: 
                    log(statusText + " : Refused - Out of Resources - Unable to calculate number of matches");
                case 0xA702: 
                    log(statusText + " : Refused - Out of Resources - Unable to perform sub-operations");
                    break;
                case 0xA801: 
                    log(statusText + " : Refused - Move Destination unknown");
                    break;
                case 0xA900: 
                    log(statusText + " : Failed - Identifier does not match SOP Class");
                    break;
                case 0xFE00: 
                    log(statusText + " : Cancel - Sub-operations terminated due to Cancel Indication");
                    break;
                case 0xB000: 
                    log(statusText + " : Warning Sub-operations Complete - One or more Failures");
                    break;
                case 0x0000: 
                    log(statusText + " : Success - Sub-operations Complete - No Failures");
                    break;
                case 0xFF00: 
                    log(statusText + " : Pending - Matches are continuing");
                    break;
                default:
                    if ((status & 0xF000) == (0xC000)) {
                        log(statusText + " : Failed - Unable to process");
                    }
                    else {
                        log(statusText + " : Unknown error: ");
                    }
                    break;
                }
            }
            //System.out.println(list.toString().replaceAll("\0", " ") + "\n");  // TODO clean up

            int failedSuboperations = Attribute.getSingleIntegerValueOrDefault(list,TagFromName.NumberOfFailedSuboperations,0);

            if (failedSuboperations != 0) {
                log("Number of failed sub operations: " + failedSuboperations);
                failureCount++;
            }

            success = status == 0x0000;		// success

            if (status != 0xFF00 && failedSuboperations == 0) {
                setDone(true);	// stop if not "pending"
            }
        }

        /**
         * @param	list
         */
        protected void makeUseOfDataSet(AttributeList list) {
            if (debugLevel > 0) System.err.println("MoveSOPClassSCU.CMoveResponseHandler.makeUseOfDataSet:");
            if (debugLevel > 0) System.err.print(list);
            // we only get here if there are failed sub-operations, in which case we get a list
            // in Failed SOP Instance UID List (0008,0058)
            setDone(true);
        }
    }



    /**
     * Perform a C-MOVE
     * 
     * @param hostname
     * @param port
     * @param calledAETitle
     * @param callingAETitle
     * @param moveDestination
     * @param affectedSOPClass
     * @param identifier
     * @param debugLevel
     * @throws DicomNetworkException
     * @throws DicomException
     * @throws IOException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public CMove(String hostname,int port,String calledAETitle,String callingAETitle,String moveDestination,
            String affectedSOPClass,AttributeList identifier,int debugLevel)
    throws DicomNetworkException, DicomException, IOException {

        this.debugLevel=debugLevel;

        LinkedList presentationContexts = new LinkedList();
        {
            LinkedList tslist = new LinkedList();
            tslist.add(TransferSyntax.Default);
            tslist.add(TransferSyntax.ExplicitVRLittleEndian);
            presentationContexts.add(new PresentationContext((byte)0x01,affectedSOPClass,tslist));
        }
    //  presentationContexts.add(new PresentationContext((byte)0x03,affectedSOPClass,TransferSyntax.ImplicitVRLittleEndian));
        presentationContexts.add(new PresentationContext((byte)0x03,affectedSOPClass,TransferSyntax.ExplicitVRLittleEndian));
        presentationContexts.add(new PresentationContext((byte)0x05,affectedSOPClass,TransferSyntax.ExplicitVRLittleEndian));

        Association association = AssociationFactory.createNewAssociation(hostname,port,calledAETitle,callingAETitle,presentationContexts,null,false,debugLevel);
        if (debugLevel > 0) System.err.println(association);
        // Decide which presentation context we are going to use ...
        byte usePresentationContextID = association.getSuitablePresentationContextID(affectedSOPClass);
        if (debugLevel > 0) System.err.println("MoveSOPClassSCU: Using context ID "+usePresentationContextID);
        byte cMoveRequestCommandMessage[] = new CMoveRequestCommandMessage(affectedSOPClass,moveDestination).getBytes();
        byte cMoveIdentifier[] = new IdentifierMessage(identifier,association.getTransferSyntaxForPresentationContextID(usePresentationContextID)).getBytes();
        if (debugLevel > 0) System.err.println("MoveSOPClassSCU: Identifier:\n"+identifier);
        association.setReceivedDataHandler(new CMoveResponseHandler(debugLevel));
        // for some reason association.send(usePresentationContextID,cMoveRequestCommandMessage,cMoveIdentifier) fails with Oldenburg imagectn
        // so send the command and the identifier separately ...
        association.send(usePresentationContextID,cMoveRequestCommandMessage,null);
        association.send(usePresentationContextID,null,cMoveIdentifier);
        if (debugLevel > 0) System.err.println("MoveSOPClassSCU: waiting for PDUs");
        try {
            association.waitForPDataPDUsUntilHandlerReportsDone();
            if (debugLevel > 0) System.err.println("MoveSOPClassSCU: got PDU, now releasing association");
            // State 6
            association.release();
        }
        catch (AReleaseException e) {
            // State 1
            // the other end released and didn't wait for us to do it
        }
    }





    final static String MOVE_LEVEL = "IMAGE";

    private static AttributeList constructRequest(String patientId) {
        try {
            AttributeList identifier = new AttributeList();
            {
                AttributeTag t = TagFromName.QueryRetrieveLevel;
                Attribute a = new CodeStringAttribute(t);
                a.addValue(MOVE_LEVEL);
                identifier.put(t,a);
            }
            {
                AttributeTag t = TagFromName.PatientID;
                Attribute a = new LongStringAttribute(t);
                a.addValue(patientId);
                identifier.put(t,a);
            }  
            return identifier;
        }
        catch (DicomException ex) {
            System.err.println("Unexpected internal error for patient ID " + patientId + "  DicomException: " + ex);
            return null;
        }
    }


    private static final void usageFailure(String msg) {
        System.err.println(msg);
        System.err.println("Usage: Set the indicated environment variables and pass a single\nparameter, the file containing a list of patient IDs");
        System.err.println("Failure - exiting.");
        System.exit(1);
    }


    private static void log(String msg) {
        logAbbrev(msg + "\n");
    }


    private static void logAbbrev(String msg) {
        System.out.print(msg);
        if (logFileOutputStream != null) {
            try {
                logFileOutputStream.write(msg.getBytes());
            }
            catch (IOException ex) {
                System.err.println("Error while logging message to file " + logFile.getAbsolutePath());
                ex.printStackTrace();
                System.exit(2);
            }
        }
    }


    private static final String getEnv(String token) throws IOException {
        String value = System.getenv(token);
        if (value == null) {
            usageFailure("No " + token + " environment vaiable set.");
        }
        log("    " + token + " : " + value);
        return value;
    }

    /**
     * @param args 
     */
    public static void main(String[] args) {
        try {
            System.out.println("Starting...");

            /*
            String daroeHost = "141.214.125.40";
            int daroePort = 15678;
            String daroeAETitle = "DAROE";

            String cmoveHost = "141.214.125.70";
            int cmovePort = 15677;
            String cmoveAETitle = "CMOVE";

            String irrerHost = "141.214.125.70";
            int irrerPort = 15678;
            String irrerAETitle = "IRRER";

            String irrer2Host = "141.214.125.70";
            int irrer2Port = 5679;
            String irrer2AETitle = "IRRER2";

            String conq1Host = "172.20.125.27";
            int conq1Port = 5678;
            String conq1AETitle = "CONQUESTSRVUM1";

            String conq2Host = "172.20.125.37";
            int conq2Port = 5678;
            String conq2AETitle = "CONQUESTSRVUM2";



            String sourceHost = irrerHost;
            int sourcePort = irrerPort;
            String sourceAETitle = irrerAETitle;

            String destAETitle = irrer2AETitle;

            // This could actually be any syntactically valid AE title, even
            // if the PACS did not exist or was configured in either the
            // source or destination PACS.

            String callingAETitle = cmoveAETitle;
             */

            if (args.length != 1) {
                usageFailure("You must run this program with exactly one parameter,\n  the file containing a list of patient ids.");
            }
            File patientListFile = new File(args[0]);
            if (!patientListFile.canRead()) {
                usageFailure("Can not read patient id file: " + patientListFile.getAbsolutePath());
            }
            log("Using patient id list file: " + patientListFile.getAbsolutePath());
            FileInputStream patientListFileStream = new FileInputStream(patientListFile);
            // Get the object of DataInputStream
            DataInputStream dataInputStream = new DataInputStream(patientListFileStream);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dataInputStream));

            logFile = new File(patientListFile.getAbsoluteFile() + ".log");
            logFile.delete();
            log("Creating log file: " + logFile.getAbsolutePath());     
            logFile.createNewFile();

            logFileOutputStream = new FileOutputStream(logFile);


            String sourceHost = getEnv("SOURCE_HOST");
            int sourcePort = Integer.parseInt(getEnv("SOURCE_PORT"));
            int debugLevel = 0;
            if (getEnv("DEBUG_LEVEL") != null) {
                debugLevel = Integer.parseInt(getEnv("DEBUG_LEVEL"));
            }
            log("Using log level of " + debugLevel + " (0 is least amount of detail)");
            String sourceAETitle = getEnv("SOURCE_AETITLE");

            String destAETitle = getEnv("DEST_AETITLE");

            String callingAETitle = getEnv("CALLING_AETITLE");


            /*
            String sourceHost = "roariadev";
            int sourcePort = 57348;
            String sourceAETitle = "ARIADEVDAEMON2";

            String destAETitle = "IRRER2";
            destAETitle = "VMSFSD";

            String callingAETitle = "CMOVE_JI";
            callingAETitle = "VMSFSD";
            callingAETitle = "CMOVE_JI";

            String queryType = SOPClass.RTBeamsDeliveryInstructionStorage;
            SOPClass.PatientRootQueryRetrieveInformationModelMove,    // type of query
            SOPClass.StudyRootQueryRetrieveInformationModelMove,      // type of query
            SOPClass.RTBeamsDeliveryInstructionStorage,               // type of query
             */

            String queryType = SOPClass.StudyRootQueryRetrieveInformationModelMove;    // type of query

            String patientId;
            //Read File Line By Line
            int patientCount = 0;
            while ((patientId = bufferedReader.readLine()) != null)   {

                patientId = patientId.trim();
                if (patientId.length() > 0) {

                    patientCount++;
                    log("\nprocessing patient " + patientCount + "   ID: " + patientId);

                    //MoveSOPClassSCU moveSOPClassSCU = new MoveSOPClassSCU(
                    CMove cMove = new CMove(
                            sourceHost,
                            sourcePort,
                            sourceAETitle,
                            callingAETitle,
                            destAETitle,
                            queryType,
                            constructRequest(patientId),
                            debugLevel);        // debug level
                    String status = (cMove.getStatus() == 0) ? "Good" : "Failure";
                    log("\nDone with patientId: " + patientId + "  Status: " + status);
                }
            }
            dataInputStream.close();
            log("\nAll CMOVEs done.");
            log("\nFailure count: " + failureCount);
            log("\nCheck log file " + logFile.getAbsolutePath() + " for details.\n\n");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            usageFailure("Got unexpected exception: " + ex);
        }
    }

}
