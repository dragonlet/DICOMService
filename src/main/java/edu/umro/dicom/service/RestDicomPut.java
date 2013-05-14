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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.data.Method;
import org.w3c.dom.DOMException;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.DicomInputStream;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.TransferSyntax;

import edu.umro.dicom.common.Util;
import edu.umro.util.Log;

public class RestDicomPut extends Restlet {

    //private static final int DEBUG_LEVEL = 0;
    //private static final int MOVE_ORIGINATOR_ID = -1;

    private static LinkedList<String> transferSyntaxList = null;

    static {
        transferSyntaxList = new LinkedList<String>();
        String[] transferArray =
        {
                TransferSyntax.ExplicitVRLittleEndian,
                TransferSyntax.ImplicitVRLittleEndian,
                TransferSyntax.ExplicitVRBigEndian,
                TransferSyntax.DeflatedExplicitVRLittleEndian,
                TransferSyntax.JPEGBaseline,
                TransferSyntax.JPEGExtended,
                TransferSyntax.JPEGLossless,
                TransferSyntax.JPEGLosslessSV1,
                TransferSyntax.JPEGLS,
                TransferSyntax.JPEGNLS,
                TransferSyntax.JPEG2000Lossless,
                TransferSyntax.JPEG2000,
                TransferSyntax.MPEG2MPML,
                TransferSyntax.MPEG2MPHL,
                TransferSyntax.MPEG4HP41,
                TransferSyntax.MPEG4HP41BD,
                TransferSyntax.PixelMedBzip2ExplicitVRLittleEndian,
                TransferSyntax.PixelMedEncapsulatedRawLittleEndian
        };
        for (String xs : transferArray) {
            transferSyntaxList.add(xs);
        }
    }
    
    /** Buffer for reading stream from client. */
    private byte[] buffer = new byte[8*1024*1024];

    /**
     * Set the return status, message, and the return content.
     * 
     * @param response Response to client.
     * 
     * @param status HTTP return status to use.
     * 
     * @param msg Error message.
     */
    private void setError(Response response, Status status, String msg) {
        response.setStatus(status, msg);
        response.setEntity(msg, MediaType.TEXT_PLAIN);
    }

    /**
     * Construct a name for an attribute list.
     * 
     * @param attributeList For this list
     * 
     * @return Human readable name.
     */
    private String constructName(AttributeList attributeList) {
        StringBuffer text = new StringBuffer();

        AttributeTag[] tagList = {
                TagFromName.PatientID,
                TagFromName.PatientName,
                TagFromName.Modality,
                TagFromName.InstanceNumber
        };

        for (AttributeTag tag : tagList) {
            Attribute at = attributeList.get(tag);
            if (at != null) {
                if (text.length() > 0) {
                    text.append(" ");
                }
                text.append(at.getSingleStringValueOrEmptyString());
            }
        }

        return text.toString();
    }
    
    private int readStream(InputStream httpInputStream) throws IOException {
        int length;
        int totalLength = 0;
        Log.get().info("Starting read of input stream ...");
        while ((length = httpInputStream.read(buffer, totalLength, buffer.length - totalLength)) != -1) {
            totalLength += length;
            if ((totalLength == buffer.length) && (length != -1)) {
                Log.get().info("Allocating larger buffer of size " + (buffer.length*2));
                byte[] biggerBuffer = new byte[buffer.length * 2];
                System.arraycopy(buffer, 0, biggerBuffer, 0, buffer.length);
                buffer = biggerBuffer;
            }
        }
        Log.get().info("Finished read of input stream.  Length: " + totalLength);
        return totalLength;
    }


    /**
     * Note that this method is <code>synchronized</code> because it reuses the
     * <code>buffer</code> field.  If the synchronization becomes a bottleneck,
     * then it could be corrected by making a thread safe pool of buffers.
     */
    @Override
    public synchronized void handle(Request request, Response response) {
        Log.get().info("RestDicomPut.handle Request: " + request);
        // assume failure until an operation succeeds.
        setError(response, Status.SERVER_ERROR_INTERNAL, this.getClass() + " Internal server error.");
        try {
            if (!request.getMethod().equals(Method.PUT)) {
                setError(response, Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Only PUT is supported.");
                return;
            }

            HashMap<String, String> parameterList = Util.getParameterList(request);

            String pacsAETitle = parameterList.get("pacs");

            if (pacsAETitle == null) {
                pacsAETitle = parameterList.get("aetitle");
            }
            
            if (pacsAETitle == null) {
                setError(response, Status.CLIENT_ERROR_BAD_REQUEST, "No data PACS specified as destination.  Use the 'pacs=MY_AETITLE' parameter in the URL.");
                return;
            }
            
            pacsAETitle = pacsAETitle.trim();
            
            Log.get().info("Put to PACS AE Title: " + pacsAETitle);

            PACS pacs = PACS.findPacs(pacsAETitle);
            if (pacs == null) {
                setError(response, Status.CLIENT_ERROR_BAD_REQUEST, "Unknown PACS AE title given: " + pacsAETitle);
                return;
            }
            Log.get().info("Found PACS: " + pacs);

            // Most likely all PACS will operate in uncompressed mode, but it should be checked for just in case,
            // and if there is a non-compressed PACS out there it should be reported in a way that makes sense
            // rather than failing in a mysterious way.
            if ((pacs.compression != null) && (!pacs.compression.toString().equalsIgnoreCase(PACS.Compression.UN.toString()))) {
                String msg =
                    "The specified PACS: " + pacs +
                    " does not support uncompressed transfers, and this server only supports uncompressed transfers";
                setError(response, Status.CLIENT_ERROR_BAD_REQUEST, msg);
                return;
            }
            //int compressionLevel = 0;

            Log.get().info("Getting httpInputStream");
            InputStream httpInputStream = request.getEntity().getStream();
            if (httpInputStream == null) {
                setError(response, Status.CLIENT_ERROR_BAD_REQUEST, "No data given to upload for HTTP PUT.");
                return;
            }
            Log.get().info("Got httpInputStream");

            // convert byte stream into DICOM file
            AttributeList attributeList = new AttributeList();
            Log.get().info("Constructed empty AttributeList");
            try {
                int length = readStream(httpInputStream);

                boolean converted = false;
                for (String xferSyntax : transferSyntaxList) {
                    try {
                        InputStream byteInputStream = new ByteArrayInputStream(buffer, 0, length);
                        DicomInputStream dicomInputStream = new DicomInputStream(byteInputStream, xferSyntax, true);
                        attributeList.read(dicomInputStream);
                        converted = attributeList.get(TagFromName.SOPInstanceUID) != null;
                        if (converted) {
                            Log.get().info("Read DICOM image from client of size " + length + " bytes.");
                            if (!xferSyntax.equals(transferSyntaxList.getFirst())) {
                                transferSyntaxList.remove(xferSyntax);
                                transferSyntaxList.push(xferSyntax);
                            }
                            break;
                        }
                    }
                    catch (DicomException e) {
                        Log.get().info("Error reading DICOM stream with transfer syntax " + xferSyntax);
                    }
                }
                if (!converted) {
                    setError(response, Status.CLIENT_ERROR_BAD_REQUEST, "Unable to interpret data as valid DICOM");
                    return;
                }
            }
            catch (IOException ex) {
                setError(response, Status.CLIENT_ERROR_BAD_REQUEST, "IO error uploadinging DICOM data: " + ex.getMessage());
                return;
            }
            finally {
                httpInputStream.close();
            }

            String aeTitle = ServiceConfig.getInstance().getHostedPACS()[0].aeTitle;
            String attrName = constructName(attributeList);
            Log.get().info("Starting DICOM transfer from " + aeTitle + " to " + pacs.aeTitle + "@" + pacs.host + ":" + pacs.port + "  " + attrName);
            // transfer the data to the PACS
            DicomPush dicomPush = new DicomPush(pacs, new AttributeList[]{ attributeList });
            String msg = dicomPush.push();
            if (msg != null) {
                setError(response, Status.SERVER_ERROR_SERVICE_UNAVAILABLE, msg);
                return;
            }

            Log.get().info("Completed dicom put to PACS: " + pacs);
            setError(response, Status.SUCCESS_OK, "put one DICOM file to " + pacs);
        }
        catch (DOMException e) {
            setError(response, Status.SERVER_ERROR_INTERNAL, "XML error.  Unable to process request: " + e);
        }
        catch (IOException e) {
            setError(response, Status.SERVER_ERROR_INTERNAL, "I/O error.  Unable to process request: " + e);
        } catch (Exception e) {
            setError(response, Status.SERVER_ERROR_INTERNAL, "Unexpected error.  Unable to process request: " + e);
        }
    }
}
