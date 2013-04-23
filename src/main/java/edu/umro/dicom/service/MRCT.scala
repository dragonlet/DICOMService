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

import com.pixelmed.dicom.AttributeList
import scala.xml.PrettyPrinter
import scala.xml.Elem
import java.text.SimpleDateFormat
import scala.xml.PCData
import scala.collection.JavaConversions._
import edu.umro.util.OpSys
import edu.umro.util.Log
import java.io.ByteArrayOutputStream
import java.util.Date
import java.rmi.server.UID
import com.pixelmed.dicom.DicomOutputStream
import com.pixelmed.dicom.TagFromName
import com.pixelmed.dicom.DicomDictionary
import com.pixelmed.dicom.AttributeTag
import com.pixelmed.dicom.Attribute
import com.pixelmed.dicom.TransferSyntax
import com.pixelmed.dicom.FileMetaInformation
import com.pixelmed.dicom.SOPClass
import com.pixelmed.dicom.DicomOutputStream
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.QueueingConsumer
import com.rabbitmq.client.Channel
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream

class MRCT {

    private val config = ServiceConfig.getInstance.getMRCT
    private val MRCTDictionary = new MRCTDictionary

    private def getChannel:Option[Channel] = {
            try {
                val factory = new ConnectionFactory
                factory.setHost(ServiceConfig.getInstance.getAmqpBrokerHost)
                factory.setPort(ServiceConfig.getInstance.getAmqpBrokerPort)
                val connection = factory.newConnection
                connection.createChannel match {
                    case channel:Channel => {
                        Log.get.info("Using AMQP broker at " + ServiceConfig.getInstance.getAmqpBrokerHost + ":" + ServiceConfig.getInstance.getAmqpBrokerPort)
                        Some(channel)
                    }
                    case _ => {
                        Log.get.severe(
                                "Unable to get AMQP channel, channel already in use. to AMQP broker at " +
                                ServiceConfig.getInstance.getAmqpBrokerHost + ":" + ServiceConfig.getInstance.getAmqpBrokerPort)
                                None
                    }
                }
            }
            catch {
                case e:Exception => {
                    Log.get.severe(
                            "Unable to get AMQP channel.  Is the broker running?  AMQP broker at " +
                            ServiceConfig.getInstance.getAmqpBrokerHost + ":" + ServiceConfig.getInstance.getAmqpBrokerPort +
                            " : " + e)
                            None
                }
            }
    }

    // val PROGRAM_NAME = "MRCT";
    private class RecvDicom extends Object with ReceiveDicom {
        private val prettyPrinter = new PrettyPrinter(1000, 2)
        private val timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        private val ipAddress = OpSys.getHostIPAddress()
        private val agentId = config.agentName + "-" + ipAddress + "-" + (new Date).toString.replace(' ', '-')

        val channel = getChannel

        try channel.get.exchangeDeclare(config.dataExchange, "topic", false)
        catch {case e:Exception => Log.get.warning("Unable to declare data exchange: " + config.dataExchange)}

        try channel.get.exchangeDeclare(config.dataExchange, "topic", false)
        catch {case e:Exception => Log.get.warning("Unable to declare data exchange: " + config.dataExchange)}
        
        private def generateEventId:String =
            (new UID()).toString.replaceAll("^[^0-9a-zA-Z]*", "").replaceAll("[^0-9a-zA-Z][^0-9a-zA-Z]*", "_");


        private var destPacs:Option[PACS] = {
                PACS.findPacs(ServiceConfig.getInstance.getMRCT.destinationPacs) match {
                    case pacs:PACS => Some(pacs)
                    case _ => None
                }
        }
        private def buildEvent(attributeList:AttributeList, sourceAETitle:String):String = {
                def getValue(tag:AttributeTag):String =
                    attributeList.get(tag) match {
                        case a:Attribute => a.getSingleStringValueOrEmptyString
                        case _ => ""
                }

                val special = "<&>\"'$".toList
                def gotSpecial(text:String) = text.toList.intersect(special).length > 0

                def get(tag:AttributeTag):Elem = {
                        val text = getValue(tag)
                        <Label>{if (gotSpecial(text)) PCData(text) else text}</Label>.copy(label = MRCTDictionary.getNameFromTag(tag))
                }

                val dicomList = List(
                        TagFromName.PatientName,
                        TagFromName.PatientID,
                        TagFromName.PatientBirthDate,
                        TagFromName.PatientSex,
                        TagFromName.SOPInstanceUID,
                        TagFromName.SeriesInstanceUID,
                        TagFromName.StudyInstanceUID,
                        TagFromName.StudyDate,
                        TagFromName.StudyTime,
                        TagFromName.SeriesTime,
                        TagFromName.StudyDescription,
                        TagFromName.AcquisitionTime,
                        TagFromName.ContentTime,
                        TagFromName.SeriesDescription,
                        TagFromName.OperatorsName,
                        TagFromName.ImageType,
                        TagFromName.Modality,
                        TagFromName.MediaStorageSOPClassUID,
                        TagFromName.PatientComments,
                        TagFromName.PatientPosition,
                        TagFromName.SeriesNumber,
                        TagFromName.StudyID,
                        TagFromName.RequestedProcedureDescription,
                        TagFromName.PerformedProcedureStepStartDate,
                        TagFromName.PerformedProcedureStepStartTime,
                        TagFromName.PerformedProcedureStepID,
                        TagFromName.PerformedProcedureStepDescription,
                        TagFromName.CommentsOnThePerformedProcedureStep,
                        TagFromName.Manufacturer,
                        TagFromName.ManufacturerModelName,
                        TagFromName.SoftwareVersions,
                        TagFromName.EchoTime,
                        TagFromName.EchoNumbers,
                        TagFromName.LargestImagePixelValue,
                        TagFromName.WindowCenter,
                        TagFromName.WindowWidth
                )


                val xml = <EventDicomObject xmlns='urn:EventDicomObject'>
                <Fid>{destPacs match { case pacs:PACS => pacs.aeTitle ; case _ => ""}}</Fid>

                {dicomList.map(t => get(t))}

                <Header>
                <AgentId>{config.agentName + "-" + ipAddress + "-" + (new Date)}</AgentId>
                <AgentName>{config.agentName}</AgentName>
                <EventId>{generateEventId}</EventId>
                <EventDateTime>{timeFormat.format(System.currentTimeMillis)}</EventDateTime>
                <EventSourceName>{sourceAETitle}</EventSourceName>
                <InResponseTo></InResponseTo>
                <IpAddress>{ipAddress}</IpAddress>
                </Header>

                </EventDicomObject>
                ;

                val text = "<?xml version='1.0'?>\n" + prettyPrinter.format(xml)
                text
        }

        // /* This code sends an attribute list to the PFI.  It was considered once but is now obsolete.  It is here because
        //    it may be resurrected.
        // */
        //
        //import edu.umro.pfi_java_client.PFIClient
        //
        //private def sendToPFI(attributeList:AttributeList):String = { 
        //        val url =
        //            pfiBaseUrl + "fid/" +
        //            "?FileSystem=" + config.pfiFileSystem +
        //            "&user_id=" + config.user +
        //            "&media_type=application/dicom" +
        //            "&PatientName=" +attributeList.get(TagFromName.PatientName).getSingleStringValueOrEmptyString
        //
        //            FileMetaInformation.addFileMetaInformation(attributeList, TransferSyntax.ExplicitVRLittleEndian, PROGRAM_NAME)
        //            val outStream = new ByteArrayOutputStream
        //            attributeList.write(new DicomOutputStream(outStream, TransferSyntax.ExplicitVRLittleEndian, TransferSyntax.ExplicitVRLittleEndian))
        //            client.putByteArray(outStream.toByteArray, url, false)
        //}


        /**
         * If a MRCT/AETitle has been specified in the configuration, then
         * send the DICOM to the given PACS.
         */
        private def sendToPACS(attributeList:AttributeList):Unit = {
                destPacs match {
                    case pacs:Some[PACS] => {
                        val dicomPush:DicomPush = new DicomPush(pacs.get, Array(attributeList))
                    val message = dicomPush.push
                    if (message == null) Log.get.info("Sent MR CT file to PACS " + destPacs)
                    else Log.get.warning("Unable to send file to PACS: " + message)
                    }
                    case _ => ;
                }
        }


        private def sendToDicomToCoordinator(eventText:Array[Byte], attributeList:AttributeList):Unit = {
                try {
                    val bout = new ByteArrayOutputStream(16 * 1024 * 1024);
                    var length = eventText.length.toString + " "
                    bout.write(length.getBytes)
                    bout.write(eventText)

                    val dicomOutputStream = new DicomOutputStream(bout,
                            TransferSyntax.ExplicitVRLittleEndian, TransferSyntax.ExplicitVRLittleEndian);
                    attributeList.write(dicomOutputStream);
                    dicomOutputStream.flush;
                    Log.get.info("Sending AMQP message via broker to MRCT exchange: " + config.dataExchange + "   routing key: " +
                            config.dataRoutingKey + "    header: " + new String(eventText))
                    channel.get.basicPublish(config.dataExchange, config.dataRoutingKey, null, bout.toByteArray)
                }
                catch {
                    case e:Exception => Log.get.warning("Unable to send DICOM file to MR CT Coordinator: " + e + "\nEvent: " + new String(eventText))
                }
        }


        override def receive(attributeList:AttributeList, transferSyntax:String, sourceAETitle:String) {
            try {
                val modality = attributeList.get(TagFromName.SOPClassUID).getSingleStringValueOrEmptyString
                if (modality == SOPClass.KeyObjectSelectionDocumentStorage) {
                    Log.get.info("MR CT ignoring Key Object (KO) file...");
                }
                else {
                    sendToPACS(attributeList)
                    val eventText:Array[Byte] = buildEvent(attributeList, sourceAETitle).getBytes                    
                    sendToDicomToCoordinator(eventText, attributeList)
                }
            }
            catch {
                case e:Exception => {
                    Log.get.severe("Failed to relay MR CT image.  Exception: " + e + "\nDICOM: " + buildEvent(attributeList, sourceAETitle))
                }
            }
        }
    }


    // Only run if AMQP broker properly configured.
    if ((ServiceConfig.getInstance.getAmqpBrokerHost != null) && (ServiceConfig.getInstance.getAmqpBrokerPort > 0)) {
        val receiver = new DicomReceiver(config.aeTitle, config.port)

        receiver.receive = new RecvDicom
    }
    else Log.get.info("AMQP is not configured, so MRCT functionality is disabled")

}
