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


import java.io.File
import java.io.IOException

import com.pixelmed.network.StorageSOPClassSCPDispatcher
import com.pixelmed.dicom.StoredFilePathStrategy
import com.pixelmed.dicom.StoredFilePathStrategySingleFolder
import com.pixelmed.network.ReceivedObjectHandler
import com.pixelmed.network.DicomNetworkException
import com.pixelmed.dicom.DicomException
import com.pixelmed.dicom.DicomDictionary
import com.pixelmed.dicom.AttributeTag
import com.pixelmed.dicom.Attribute
import com.pixelmed.dicom.AttributeList
import com.pixelmed.dicom.TagFromName

import edu.umro.util.Log

class DicomReceiver(aeTtl:String, port:Int) extends ReceivedObjectHandler {

    /**
     * Handle incoming DICOM objects.  If a receive class has been specified, then use that, otherwise ignore it.  Ignore
     * all read or processing errors but log them.  After file contents have been passed to the caller's processing
     * function, the files are deleted.
     */
    override def sendReceivedObjectIndication(fileName:String, transferSyntax:String, sourceAETitle:String):Unit = {
            val file = new File(fileName)
            if (receive == null) {
                Log.get.finer("Ignoring DICOM object from " + sourceAETitle + "   file: " + file.getAbsolutePath)
            }
            else {
                val attributeList = new AttributeList
                try {
                    attributeList.read(file)
                    Log.get.info("Processing DICOM object from " + sourceAETitle + "   file: " + file.getAbsolutePath + Utilities.dicomSummary(attributeList))
                    receive.receive(attributeList, transferSyntax, sourceAETitle) // , transferSyntax)
                }
                catch {
                    case e: Exception => {
                        Log.get.warning("Exception while processing incoming DICOM file " + file.getAbsolutePath + " : " + e)
                    }
                }
            }
            file.delete
    }

    private val dictionary = new DicomDictionary
    private val debugLevel = 0
    val aeTitle = aeTtl
    private val strategy:StoredFilePathStrategy = new StoredFilePathStrategySingleFolder
    private val temporaryDirectory:File = ServiceConfig.getInstance.getTemporaryDir
    var receive:ReceiveDicom = null



    val dispatcher = new StorageSOPClassSCPDispatcher(
            port,               // port that we are listening on
            aeTitle,            // our AETitle
            temporaryDirectory, // directory for temporary and fetched files
            strategy,           // strategy for naming incoming DICOM files
            this,               // ReceivedObjectHandler receivedObjectHandler,
            debugLevel);        // debug level
    val dispatcherThread = new Thread(dispatcher);
    dispatcherThread.start();
    Log.get.info("Started DICOM receiver.  AE title: " + aeTitle + "  port: " + port)
}
