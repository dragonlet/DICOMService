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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.representation.OutputRepresentation;

import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.DicomOutputStream;
import com.pixelmed.dicom.FileMetaInformation;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.TransferSyntax;

import edu.umro.util.Log;

/**
 * Streams DICOM objects directly from a PACS to a client via a zip stream.
 * 
 * @author irrer
 * 
 */
public class RepresentationCMove extends OutputRepresentation implements
ReceiveDicom {

    /** Buffer size for transferring DICOM files to zip. */
    private static final int DEFAULT_DICOM_BUFFER_SIZE = 1024; // 10 * 1024 *
    // 1024;

    /** Name of this program. */
    private static final String PROGRAM_NAME = "dicomsvc";

    /** PACS to query. */
    private PACS pacs = null;

    /** DICOM attributes specifying what to get. */
    private AttributeList specification = null;


    /**
     * Make private class that keeps track of whether stream is open.
     * 
     * @author irrer
     * 
     */
    private class ZipOut extends ZipOutputStream {
        boolean open = true;

        public ZipOut(OutputStream outputStream) {
            super(outputStream);
        }

        @Override
        public void close() {
            if (open) {
                try {
                    super.flush();
                    super.close();
                }
                catch (IOException e) {
                    Log.get().warning("Error while closing DICOM zip output stream: " + e);
                }
                finally {
                    open = false;
                }
            }
        }

        @Override
        public void flush() {
            close();
        }

    }

    /** Place to write DICOM objects. */
    private ZipOut zipOut = null;

    /** Number of DICOM objects sent. */
    private int dicomObjectCount = 0;

    /** Response to client. */
    private Response response = null;

    /** Maximum number of allowed DICOM files before initiating abort. */
    private int limit = 0;

    /**
     * Construct a ReSTLet Representation that streams directly from a PACS to a
     * client via a zip stream.
     * 
     * @param response
     * @param pacs
     * @param specification
     */
    public RepresentationCMove(Response response, PACS pacs,
            AttributeList specification, int limit) {
        super(MediaType.APPLICATION_ZIP, -1);
        setTransient(true);
        this.response = response;
        this.pacs = pacs;
        this.specification = specification;
        this.limit = limit;
    }

    @Override
    public ReadableByteChannel getChannel() throws IOException {
        String msg = "ReadableByteChannel RepresentationCMove.getChannel() not implemented.";
        Log.get().severe(msg);
        throw new RuntimeException(msg);
    }

    @Override
    public Reader getReader() throws IOException {
        String msg = "Reader RepresentationCMove.getReader() not implemented.";
        Log.get().severe(msg);
        throw new RuntimeException(msg);
    }

    @Override
    public InputStream getStream() throws IOException {
        String msg = "InputStream RepresentationCMove.getStream() not implemented.";
        Log.get().severe(msg);
        throw new RuntimeException(msg);
    }

    @Override
    public void write(Writer writer) throws IOException {
        String msg = "void RepresentationCMove.write(Writer writer) not implemented.";
        Log.get().severe(msg);
        throw new RuntimeException(msg);
    }

    /**
     * This is very strange that ReSTLet does not handle this directly, but
     * instead converts from a WritableByteChannel to an OutputStream. Figured
     * this out by looking at the StreamRepresentation.
     */
    @Override
    public void write(WritableByteChannel writableChannel) throws IOException {
        write(org.restlet.engine.io.NioUtils.getStream(writableChannel));
    }

    /**
     * Get the matching DICOM objects and put them into a zip stream. Stream the
     * file to the client as they are received from the PACS.
     * 
     * A special check is done to determine if zero matching DICOM objects were
     * found, and if so, a zero length is returned. There is a limitation of
     * Restlet that does not allow a more explicit status to be set, so this is
     * all we can do.
     * 
     * For more info:
     * https://github.com/restlet/restlet-framework-java/issues/670
     * 
     * If there is an actual error, then the client will receive an error
     * status, though it is vague.
     */
    @Override
    public void write(OutputStream outputStream) throws IOException {

        this.zipOut = new ZipOut(outputStream);

        try {
            DicomGet.get(this.pacs, this.specification, null, limit, this);
        } finally {
            if (dicomObjectCount == 0) {
                Log.get().info(
                        "No matching DICOM objects for "
                        + response.getRequest());
                outputStream.flush();
                outputStream.close();
            } else {
                zipOut.close();
                Log.get().info(
                        "Found and sent " + dicomObjectCount
                        + " matching DICOM objects for "
                        + response.getRequest());
            }
        }
    }

    @Override
    public void receive(AttributeList attributeList, String transferSyntax,
            String sourceAETitle) {
        if ((limit > 0) && (dicomObjectCount >= limit)) {
            zipOut.close();
            Log.get().info(
                    "Closed DICOM stream because limit of " + limit
                    + " was reached.");
        } else {
            try {
                FileMetaInformation.addFileMetaInformation(attributeList,
                        TransferSyntax.ExplicitVRLittleEndian, sourceAETitle
                        + " via " + PROGRAM_NAME);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(
                        DEFAULT_DICOM_BUFFER_SIZE);
                DicomOutputStream dicomOutputStream = new DicomOutputStream(
                        byteArrayOutputStream,
                        TransferSyntax.ExplicitVRLittleEndian,
                        TransferSyntax.ExplicitVRLittleEndian);
                attributeList.write(dicomOutputStream);

                String fileName = attributeList.get(TagFromName.SOPInstanceUID).getSingleStringValueOrNull() + ".dcm";

                try {
                    zipOut.putNextEntry(new ZipEntry(fileName));
                    zipOut.write(byteArrayOutputStream.toByteArray());
                } catch (Exception e) {
                    String msg = "Unexpected IOException while writing zip stream entry "
                        + fileName + " : " + e;
                    Log.get().severe(msg);
                } finally {
                    try {
                        zipOut.closeEntry();
                        dicomObjectCount++;
                    } catch (IOException e) {
                        String msg = "Unexpected IOException while closing zip stream entry "
                            + fileName + " : " + e;
                        Log.get().severe(msg);
                    }
                }

                Log.get().fine(
                        "Transferred DICOM object " + dicomObjectCount + " : "
                        + Utilities.dicomSummary(attributeList));
            } catch (IOException e) {
                Log.get().severe(
                        "Unable to transfer DICOM file to client because of IOException: "
                        + e);
            } catch (DicomException e) {
                Log.get().severe(
                        "Unable to transfer DICOM file to client because of DicomException: "
                        + e);
            }
        }
    }

}
