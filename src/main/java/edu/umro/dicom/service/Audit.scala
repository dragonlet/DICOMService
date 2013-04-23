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
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.Date
import edu.umro.util.XML
import edu.umro.util.Log

import org.restlet.Request
import org.restlet.Response
import org.restlet.data.ClientInfo
import org.restlet.data.ChallengeResponse
import org.restlet.data.ChallengeRequest
import org.restlet.data.ChallengeScheme
import org.restlet.data.Method
import org.restlet.data.Protocol
import scala.collection.mutable.HashMap

/**
 * Keep an audit log of users' actions to support HIPAA.  Each entry is formatted
 * in XML to facilitate searching and reporting.  To interpret the file by an XML
 * parser, the consumer must add an opening tag at the beginning of the file and
 * and append a closing tag at the end.
 * 
 * A file is created each month, named for that month.
 * 
 * The following information is recorded:<p>
 * 
 * <ul>
 *     <li>time at which action occurred</li> 
 *     <li>ID of user</li> 
 *     <li>patient ID</li> 
 *     <li>patient name</li> 
 *     <li>user host machine</li> 
 *     <li>URL specified by user</li> 
 *     <li>description of action</li> 
 * </ul>
 */
class Audit(requestName:String, request:Request, requestDescription:String) {

    object AuditTag extends Enumeration {
        type AuditTag = Value
        val
        PatientID,
        StartTime,
        EndTime,
        User,
        PatientName,
        UserHost,
        Request,
        RequestName,
        Status,
        RequestDescription,
        ResultDescription,
        ElapsedTime = Value
    }
    import AuditTag._

    private val valueMap = new HashMap[AuditTag,String];

    def setValue(tag:AuditTag, value:String):Unit = {
            if (valueMap.contains(tag)) throw new RuntimeException("Audit: Value " + tag + " is already defined")
            valueMap.put(tag, value)
    }

    val timeStampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    def setTimeStampValue(tag:AuditTag, value:Long) = {
        setValue(tag, timeStampFormat.format(value))
    }

    val elapsedTimeFormat = new SimpleDateFormat("ddddd HH:mm:ss.SSS");

    def elapsedTimeValue(tag:AuditTag, value:Long) = {
        setValue(tag, elapsedTimeFormat.format(value))
    }

    val startTime = System.currentTimeMillis


    setValue(AuditTag.RequestName, request.toString)
    setValue(AuditTag.Request, request.toString)
    setValue(AuditTag.RequestDescription, requestDescription)
    setTimeStampValue(AuditTag.StartTime, startTime)

    val chall = request.getChallengeResponse
    if (request.getChallengeResponse != null) setValue(AuditTag.User, request.getChallengeResponse.getIdentifier)

    setValue(AuditTag.UserHost, request.getClientInfo.getFrom)


    def commit(response:Response) = {
            val endTime = System.currentTimeMillis
            // setValue(AuditTag.Request, request.toString)  // TODO set response
            setTimeStampValue(AuditTag.EndTime, endTime)
            elapsedTimeValue(AuditTag.ElapsedTime, endTime - startTime)

            val xml = new StringBuffer("<Audit>\n")
            for (key <- valueMap) xml.append("<" + key._1 + ">" + XML.escapeSpecialChars(key._2) + "</" + key._1 + ">\n")
            xml.append("</Audit>\n")
            Log.get.info("Audit record: " + xml.toString)

            this.synchronized {
                val fileNameFormat = new SimpleDateFormat("yyyy-MM");
                val fileName = "audit_" + fileNameFormat.format(endTime) + ".xml"
                val file = new File(ServiceConfig.getInstance().getAuditDirectory(), fileName)
                val writer = new BufferedWriter(new FileWriter(file, true))
                writer.write(xml.toString)
                writer.close
            }
    }

}

object TestAudit {
    def main(args:Array[String]) {
        val clientInfo = new ClientInfo
        val request = new Request
        request.setClientInfo(clientInfo)
        clientInfo.setFrom("from IP address")
        val response = new Response(request)
        val challResp = new ChallengeResponse(ChallengeScheme.HTTP_BASIC)
        challResp.setIdentifier("userid")
        challResp.setSecret("password")
        request.setChallengeResponse(challResp)
        request.setMethod(Method.GET)
        request.setResourceRef("/test/resource?ref=value");
        request.setResourceRef("https://127.0.0.1:8091/dicom/get?media_type=text/html&PatientID=000001");
        request.setProtocol(Protocol.HTTPS);
        val audit = new Audit("TestingAudit", request, "test description")
        Thread.sleep(1001)
        audit.commit(null)
    }
}
