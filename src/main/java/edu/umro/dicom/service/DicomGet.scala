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

import java.util.concurrent.ArrayBlockingQueue
import java.io.File
import com.pixelmed.network.ReceivedObjectHandler
import com.pixelmed.dicom.TagFromName
import com.pixelmed.dicom.Attribute
import com.pixelmed.dicom.AttributeList
import com.pixelmed.dicom.AttributeFactory
import com.pixelmed.dicom.CodeStringAttribute
import edu.umro.util.Log;

/**
 * Get DICOM files from a given PACS
 */
object DicomGet {

    /** List of available receivers. */
    private val available = new ArrayBlockingQueue[DicomReceiver](ServiceConfig.getInstance.getHostedPACS.length)

    for (pacs <- ServiceConfig.getInstance.getHostedPACS) available.add(new DicomReceiver(pacs.aeTitle, pacs.port))


    /**
     * Get DICOM files.  Do not return until the transfer is done.  If a query retrieve
     * level is not specified, use the level appropriate for the parameters specified.
     * Some PACS (like Conquest) ignore the query retrieve level and will allow any
     * combination of parameters for searching and returning results.  Other PACS
     * (like XStor) absolutely require the query retrieve level, and also require
     * that one of SOPInstanceUID, SeriesInstanceUID, StudyInstanceUID, or PatientID
     * be specified, and if so, that the proper query retrieve level be given with
     * it as well, as in:
     * 
     *     <li> IMAGE: SOPInstanceUID </li><br></br>
     *     <li> SERIES: SeriesInstanceUID </li><br></br>
     *     <li> STUDY: StudyInstanceUID </li><br></br>
     *     <li> PATIENT: PatientID </li><br></br>
     * 
     * @param pacs Where the files are to be retrieved from.
     * 
     * @param specification Qualifications of files (patient ID, series UID, etc.).
     * 
     * @param progressProcessor Optional method for monitoring transfer (may be null).
     * 
     * @param limit Maximum number of DICOM files to receive.  If more than limit are
     * coming, then abort immediately.  This is useful for avoiding the problem of
     * a specification that requests many more files than intended.  If limit is 0 or
     * less, then no limit is enforced.  Note that one or more files might actually
     * be transferred even if the limit is exceeded. 
     * 
     * @param receive Method for receiving DICOM content.
     */
    def get(pacs:PACS, specification:AttributeList, progressProcessor:CMove.ProgressProcessor, limit:Int, receive:ReceiveDicom) = {
            def qrtVal:String = {
            val attr = specification.get(TagFromName.QueryRetrieveLevel)
            if (attr == null) "" 
                else {
                    val value = attr.getSingleStringValueOrEmptyString.trim
                    if (value.length > 0) value
                    else ""
                }
    }

    def getQueryRetrieveLevel:String = {
            specification match {
                case s if qrtVal.length > 0 => qrtVal
                case s if s.get(TagFromName.SOPInstanceUID)     != null => "IMAGE"
                case s if s.get(TagFromName.SeriesInstanceUID)  != null => "SERIES"
                case s if s.get(TagFromName.StudyInstanceUID)   != null => "STUDY"
                case s if s.get(TagFromName.PatientID)          != null => "PATIENT"
                case _ => "PATIENT"
            }

    }

    def ensureQueryRetrieveLevel = {
            specification.get(TagFromName.QueryRetrieveLevel) match {
                case null => {
                    val a = new CodeStringAttribute(TagFromName.QueryRetrieveLevel);
                    a.addValue(getQueryRetrieveLevel);
                    Log.get.warning("Query retrieve level not specified, assuming " + a.getSingleStringValueOrEmptyString() + " for request: " + specification.toString.replace('\0', ' '))
                    specification.put(a.getTag,a);
                }
                case _ =>
            }

    }

    Log.get.fine("Waiting to acquire DICOM connection")
    val receiver = available.take
    Log.get.fine("Acquired DICOM connection")
    try {
        ensureQueryRetrieveLevel
        receiver.receive = receive
        val cmove = new CMove
        cmove.begin(pacs, receiver.aeTitle, receiver.aeTitle, specification, limit, progressProcessor)
    }
    catch {
        case e: Exception => {
            Log.get.warning("Unexpected exception in DicomGet for PACS " + pacs + " : " + e)
        }
    }
    finally {
        receiver.receive = null
        available.put(receiver)
    }
    }


    /**
     * The invocation of this function forces this class to
     * be loaded and the DICOM servers to be started.  Doing
     * this increases the reliability of the service by ensuring
     * that the DICOM receivers can reserve the ports that
     * they need.  This way, any failure will happen right
     * away on server startup, rather than the first time
     * a client uses the DICOM receivers.
     */
    def init = {
            Log.get.info("Initializing DicomGet...");
    }


    /**
     * Test the transferring of a set of DICOM object from an external PACS to this server.
     * 
     * @param args Ignored
     */
    def main(args: Array[String]): Unit = {

            val sourcePacs = new PACS("IRRER", "141.214.125.70", 15678)

            val patientId = "99999999"
                val patientAttribute = AttributeFactory.newAttribute(TagFromName.PatientID)
                patientAttribute.setValue(patientId)
                val list = new AttributeList
                list.put(patientAttribute)

                class RecvDicom extends Object with ReceiveDicom {
                override def receive(attributeList:AttributeList, transferSyntax:String, sourceAETitle:String) {
                    System.out.println("Got DICOM object from " + sourceAETitle)
                }
            }

            get (sourcePacs, list, null, 0, new RecvDicom)
            System.exit(0)
    }

}
