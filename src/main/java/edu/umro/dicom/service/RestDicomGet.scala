package edu.umro.dicom.service

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

import java.util.ArrayList
import java.io.IOException
import java.util.zip.ZipOutputStream
import java.util.zip.ZipException
import java.util.zip.GZIPOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import org.restlet.Restlet
import org.restlet.Application
import org.restlet.Request
import org.restlet.Response
import org.restlet.data.Status
import org.restlet.data.Method
import org.restlet.data.MediaType
import org.restlet.data.Encoding
import org.restlet.engine.application.StatusFilter
import com.pixelmed.dicom.TagFromName
import com.pixelmed.dicom.DicomDictionary
import com.pixelmed.dicom.DicomOutputStream
import com.pixelmed.dicom.TransferSyntax
import com.pixelmed.dicom.AttributeList
import edu.umro.util.Log
import org.restlet.data.Parameter
import java.io.PipedInputStream
import org.restlet.representation.Representation
import org.restlet.resource.ServerResource
import org.restlet.resource.Get


class RestDicomGet() extends Restlet { // StatusFilter {

    override def handle(request:Request, response:Response) = {

        def usage = {
                "\nUsage:\n\n" +
                "One of the following DICOM attributes should be given:\n" +
                "\n" +
                "    SOPInstanceUID=[uid]     Gets a single DICOM file\n" +
                "    SeriesInstanceUID=[uid]  Gets all DICOM files for a given series\n" +
                "    StudyInstanceUID=[uid]   Gets all DICOM files for the given study\n" +
                "    PatientID=[patient id]   Gets all DICOM files for the given patient\n" +
                "\n" +
                "Some PACS allow you to get DICOM files by PatientName or other\n" +
                "parameters, but the four attributes listed above work on all PACS.\n" +
                "\n" +
                "The following media types are supported:\n" +
                "    application/zip\n" +
                "\n" +
                "The following PACS can be accessed:\n" +
                Utilities.pacsList.map(p => "    " + p.aeTitle + "\n").fold("") {(total, s) => total + s} + "\n" +
                "\n"
        }
        // assume failure
        response.setStatus(Status.SERVER_ERROR_INTERNAL)

        val paramList = Utilities.getParamList(request)

        val isGet = if (request.getMethod().equals(Method.GET))
            true
            else {
                response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED)
                response.setEntity("Only HTTP GET is allowed", MediaType.TEXT_PLAIN)
                false
            }

        MediaType.APPLICATION_ZIP
        val mediaType = Utilities.getMediaType(request, response, List(MediaType.APPLICATION_ZIP), paramList)

        val specification:Option[AttributeList] = Utilities.getDicomAttributes(request, response, paramList) match {
            case Some(s) if (s.size > 1) || ((s.size > 0) && s.get(TagFromName.QueryRetrieveLevel) == null) => Some(s)
            case None => None  // this happens if the name that the user specified for the dictionary was invalidS
            case _ => {
                val msg = "There were no DICOM attributes specified."
                    response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED)
                    response.setEntity(msg, MediaType.TEXT_PLAIN)
                    None
            }
        }
        val pacs = Utilities.getPacs(response, paramList)

        val limit = Utilities.getLimit(response, paramList)

        val allRecognized = Utilities.checkForUnrecognizedParameters(response, paramList)

        def sendDicom:Unit = {
            response.setStatus(Status.SUCCESS_OK)  // have to do this before starting the transfer or Restlet freaks.
            response.setEntity(new RepresentationCMove(response, pacs.get, specification.get, limit.get))
        }

        def logError = {
            val text = (if (response.getEntity != null && response.getEntity.getText != null) response.getEntity.getText else "") + usage;
            response.setEntity(text, MediaType.TEXT_PLAIN)
            Log.get.info("User DICOM/get request failed: " + request + "\nStatus: " + response.getStatus.toString + "\nMessage:\n" + text)
        }


        try {

            // must be HTTP GET
            // if anonymize specified, then anonymize
            //     - could specify invalid patient id
            // extraneous parameters cause failure with explanation
            // media type must be specified as html, xml, jpeg, or dicom.  If not specified, default to html
            // jpeg is only valid for imaging modalities
            // error explanations must include a usage message
            // PACS must be specified
            //     - could specify invalid PACS
            // if dictionary specified, then use that dictionary
            //     - could specify invalid dictionary

            (isGet, mediaType, specification, pacs, limit, allRecognized) match {
                case (true, mediaType:Some[MediaType], specification:Some[AttributeList], pacs:Some[PACS], lim:Some[Int], true) => mediaType.get match {
                    case MediaType.APPLICATION_ZIP => sendDicom
                    case MediaType.TEXT_HTML =>   // TODO 
                    case MediaType.IMAGE_JPEG =>  // TODO 
                    case MediaType.TEXT_XML =>    // TODO 
                    case MediaType.TEXT_PLAIN =>  // TODO
                    case _ => logError
                }
                case _ => logError 

            }
        }
        catch {
            case e:Exception => {
                val msg = "Unexpected server error\n" + Status.SERVER_ERROR_INTERNAL + "\n" + e
                Log.get.warning(msg)
                response.setStatus(Status.SERVER_ERROR_INTERNAL, msg)
                response.setEntity(msg, MediaType.TEXT_PLAIN);
                e.printStackTrace
                logError
            }
        }
    }
}
