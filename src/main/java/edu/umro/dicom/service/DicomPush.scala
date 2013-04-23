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

import com.pixelmed.network.MultipleInstanceTransferStatusHandler
import com.pixelmed.dicom.AttributeList
import com.pixelmed.dicom.Attribute
import com.pixelmed.dicom.TagFromName
import com.pixelmed.network.StorageSOPClassSCU
import java.util.HashSet
import java.util.Set
import edu.umro.util.Log

/**
 * Push an array of attributes to the given PACS and wait for it to complete.
 * 
 * Caller may access statistics (remaining, completed, failed, warning) in a
 * different thread to get progress during transfer.
 * 
 */
class DicomPush(pacs:PACS, attrListList:Array[AttributeList]) extends MultipleInstanceTransferStatusHandler {

    var remaining:Int = 0
    var completed:Int = 0
    var failed:Int = 0
    var warning = 0

    def updateStatus(nRemaining: Int, nCompleted: Int, nFailed: Int, nWarning: Int, sopInstanceUID: String): Unit = {
    remaining = nRemaining
    completed = nCompleted
    failed = nFailed
    warning = nWarning;
}

private val originatorPacsAETitle = ServiceConfig.getInstance().getHostedPACS()(0).aeTitle

private val MOVE_ORIGINATOR_ID = -1
private val DEBUG_LEVEL = 0
private val compressionLevel = 0

private val sopClassUID:Attribute  = attrListList(0).get(TagFromName.SOPClassUID);
if (sopClassUID == null) {
    throw new RuntimeException("DICOM file does not have a SOPClassUID.")
}
private val setOfSOPClassUIDs:HashSet[String] = new HashSet[String]();

    /**
     * Initiate transfer and wait for results.
     */
    def push:String = {
        setOfSOPClassUIDs.add(sopClassUID.getSingleStringValueOrEmptyString());

        Log.get.info("Starting DICOM transfer of " + attrListList.length + " files from " + originatorPacsAETitle + " to " + pacs.toString);

        val sscs = new StorageSOPClassSCU(pacs.host, pacs.port, pacs.aeTitle, originatorPacsAETitle,
                setOfSOPClassUIDs, attrListList,
                compressionLevel, this,
                originatorPacsAETitle, MOVE_ORIGINATOR_ID, DEBUG_LEVEL);

        val error:String = if ((completed != attrListList.length) || (failed != 0) || (remaining != 0) || (warning != 0)) {
            "Transfer of files to PACS completely or partially failed." +
            "\nDetails: PACS: " + pacs +
            "\n   remaining: " + remaining + "    completed: " + completed + "    failed: " + failed + "    warning: " + warning
        }
        else null

        if (error == null) { Log.get.info("Succeeded in DICOM transfer of " + attrListList.length + " files from " + originatorPacsAETitle + " to " + pacs.toString) }
        else Log.get.warning(error)
        error
    }
}
