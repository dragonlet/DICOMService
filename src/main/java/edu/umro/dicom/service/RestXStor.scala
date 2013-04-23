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
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import org.restlet.Restlet
import org.restlet.Request
import org.restlet.Response
import org.restlet.data.Status
import org.restlet.data.Method
import org.restlet.data.MediaType
import com.pixelmed.dicom.TagFromName
import com.pixelmed.dicom.AttributeList
import edu.umro.util.Log
import com.pixelmed.dicom.DicomInputStream
import com.pixelmed.dicom.AttributeTag


import scala.collection.mutable.HashMap

class RestXStor() extends Restlet {

    private val URL_PREFIX = "http://141.214.124.168:8080/XStorPACS/ob/home?firstView=false&Submit=Submit&patientId=";

    override def handle(request:Request, response:Response) = {

        val AGE_TAG = "ExpirationAge";

        val age:Long = {
                val a = try { request.getResourceRef.getQueryAsForm.getValues(AGE_TAG).toInt }
                catch {
                    case e:Exception =>
                    try { ServiceConfig.getInstance.getExpirationAge }
                    catch { case e2:Exception => -1 }
                }
                a
        }

        val minAge:Long = System.currentTimeMillis - (age * 24 * 60 * 60 * 1000)

        // assume failure
        response.setStatus(Status.SERVER_ERROR_INTERNAL)

        val fsDirList = ServiceConfig.getInstance.getXStorFSDir

        def getObjectsDir:Option[File] = {
                    var list = for (dirName <- fsDirList if new File(dirName, "objects").isDirectory()) yield new File(dirName, "objects")

                    list.toList match {
                        case first :: others => Some(first)
                        case _ => {
                            val msg = "Unable to access XStor directory.  This is probably a problem with the NFS mount between " +
                            "this service and the PACS.  Please contact your system administrator.  Values are configured in the " +
                            "configuration files with the XStorFSDir tag."
                            Log.get.severe(msg)
                            response.setStatus(Status.SERVER_ERROR_INTERNAL, msg)
                            response.setEntity(msg, MediaType.TEXT_PLAIN)
                            new File("fooo")
                            None
                        }
                    }
                }

                /** Number of bytes to read from DICOM file in attempt to get patient name and ID.  Sometimes
                 * DICOM files are long, and it is not necessary to read the entire file just to get these values. */
                val DICOM_HEADER_LENGTH = 4096

                def listFiles(dir:File):Array[File] = {
                    var lst:Array[java.io.File] = if (dir.isDirectory) dir.listFiles else Array[File]()
                    lst
                }
                def seriesDirList(patientDir:File) = listFiles(patientDir).map(study => listFiles(study)).toArray.toList.flatten

                val isGet = if (request.getMethod().equals(Method.GET))
                    true
                    else {
                        response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED)
                        response.setEntity("Only HTTP GET is allowed", MediaType.TEXT_PLAIN)
                        false
                    }


                def patientDataAge(patientDir:File):Long = {
                        var list = seriesDirList(patientDir:File).map(series => series.lastModified).sorted.reverse
                        if (list.length > 0) list.head else -1
                }

                def getPatientNameAndId(dicomFile:File):Option[(String,String)] = {
                        val attributeList = new AttributeList
                        def getAt(tag:AttributeTag) = { val at = attributeList.get(tag); if (at == null) null else at.getSingleStringValueOrNull }
                        def name = getAt(TagFromName.PatientName)
                        def id = getAt(TagFromName.PatientID)
                        try {
                            val header = Util.readHead(dicomFile, DICOM_HEADER_LENGTH);
                            val bais = new ByteArrayInputStream(header)
                            val dicomStream = new DicomInputStream(bais)
                            try attributeList.read(dicomStream)
                            catch { case e:Exception => {Log.get.info("ignoring exception while reading DICOM file header: " + e)}}
                            if ((name == null) || (id == null)) {
                                attributeList.read(dicomFile)
                            }
                            val n = name
                            val i = id
                            Some(name,id)
                        }
                        catch {
                            case e:Exception => {
                                Log.get.warning("RestXStor unable to process XStor DICOM file: " + dicomFile.getAbsoluteFile)
                                None
                            }
                        }
                }

                def patientNameAndId(patientDir:File):Option[(String,String)] = {
                        val dicomFileList = seriesDirList(patientDir:File).map(d => d.listFiles).flatten
                        dicomFileList match {
                            case dicomFile :: _ => getPatientNameAndId(dicomFile)
                            case _ => None
                        }
                }

                /**
                 * 
                 */
                def dataToDelete(patientDir:File):Option[(Long, String,String)] = {
                        val newest = seriesDirList(patientDir).map(d => d.lastModified).sorted.reverse
                        if (newest.length == 0)
                            None
                        else {
                        val newestChange = newest.head
                        val ageInDays = (System.currentTimeMillis - newestChange) / (24 * 60 * 60 * 1000);
                        if (newestChange < minAge)
                            patientNameAndId(patientDir) match {
                                case Some(ni:(String,String)) => {
                                    val newest = seriesDirList(patientDir).map(d => d.listFiles).flatten.map(f => f.lastModified).sorted.reverse.head
                                    Some(newest, ni._1, ni._2)
                                }
                                case _ => None
                        }
                        else None
                        }
                }

                def patientDataFromSubObject(subObject:File):List[(Long,String,String)] = {
                        def isData(d:Option[(Long, String,String)]):Boolean = d match { case None => false ; case _ => true }
                        var x = subObject.listFiles.map(p => dataToDelete(p)).toList.filter(d => isData(d)).flatten
                        x
                }


                def generateHtml(dataToDeleteList:List[(Long, String, String)]):String = {
                        def dataToHtml(modified:Long, name:String, id:String):String = {
                                val ageInDays = (System.currentTimeMillis - modified) / (24 * 60 * 60 * 1000);
                                val n = name.replace('^', ' ').replace(',', ' ')
                                "<tr bgcolor='f9f9f9'><td>" + n + "</td><td><a href='" + URL_PREFIX + id + "' target='_blank'> " + id + " </a>" + "<td align='center'>" + ageInDays.toString + "</td></tr>\n"
                        }

                        val htmlList = dataToDeleteList.map(d => dataToHtml(d._1, d._2, d._3)).fold("") {(acc, s) => acc + s}

                        def htmlForm = "<p><br><form name='Search' method='get'>\n" +
                        "Age Limit: <input type='text' name='" + AGE_TAG + "' value='" + age + "' size='4'> \n" +
                        " &nbsp; &nbsp; &nbsp; &nbsp;<input type='submit' value='Submit'>" +
                        "</form>\n" +
                        "<br>\n"

                        val title = "Expired (Oldest) DICOM Files on<p><br>STAGING PACS"
                            val html = Util.getHtmlHead(title) +
                            "<p><center><p><br><h3>" + title + "</h3><br>\n" +
                            "<p>Age limit (days): " + age + "<p>\n" +
                            "<p>Number of Patients: " + dataToDeleteList.length + "<p>\n" +
                            "<p><table cellspacing='10' cellpadding='15' border='0'>\n" +
                            "<tr bgcolor='efefef'><td>Patient Name</td><td>Patient ID</td><td>Age of Data in Days</td></tr>\n" +
                            htmlList +
                            "</table>\n" +
                            htmlForm +
                            "<p>Click on Patient IDs to search and then delete<br><p>\n" +
                            "<p>*Age = Number of days since data was loaded<br>\n" +
                            "</center>\n" +
                            "\n</html></body>\n"
                            html
                }

                def logEvent(dataToDeleteList: List[(Long, String, String)]):Unit = {
                        def convertDataToLog(modified:Long, name:String, id:String):String = {
                                val age = (System.currentTimeMillis - modified) / (24 * 60 * 60 * 1000.)
                                val ageInDays = "%8.3f".format(age)
                                val n = name.replace('^', ' ').replace(',', ' ')
                                "    PatientName: " + n + "   PatientID: " + id + "  Age of data in days: " + ageInDays + "\n"
                        }
                        Log.get.info("Age limit: " + age + " days." +
                                "  List of patient data to delete:\n" + dataToDeleteList.map(d => convertDataToLog(d._1, d._2, d._3)).fold("") {(acc, s) => acc + s})
                }

                try {
                    if (isGet) {
                        if (fsDirList == null) {
                            var msg = "This service is not configured to support removal of old files.  Have the " +
                            		"system administrator edit the configuration file and add XStorFSDir entries to enable it."
                                Log.get.info(msg)
                                response.setStatus(Status.SERVER_ERROR_NOT_IMPLEMENTED, msg)
                                response.setEntity(msg, MediaType.TEXT_PLAIN)
                        }
                        else {
                            getObjectsDir match {
                                case Some(objectsDir) => {
                                    val subObjectList = objectsDir.listFiles.filter(f => f.isDirectory)
                                    val dataToDeleteList = subObjectList.map(sub => patientDataFromSubObject(sub)).toList.flatten.sorted

                                    response.setStatus(Status.SUCCESS_OK);
                                    response.setEntity(generateHtml(dataToDeleteList), MediaType.TEXT_HTML)
                                    logEvent(dataToDeleteList)
                                }
                                case _ => ;
                            }
                        }
                    }
                }
                catch {
                    case e:Exception => {
                        val msg = "Unexpected server error\n" + Status.SERVER_ERROR_INTERNAL + "\n" + e
                        Log.get.warning(msg)
                        response.setStatus(Status.SERVER_ERROR_INTERNAL, msg)
                        response.setEntity(msg, MediaType.TEXT_PLAIN)
                        e.printStackTrace
                    }
                }
    }


}

object TestRestXStor {
    def main(args: Array[String]):Unit = {
            try {
                println("TestRestXStor starting...")
                
                def listFiles(dir:File):Array[File] = if (dir.isDirectory) dir.listFiles else Array[File]()
                def seriesDirList(patientDir:File) = patientDir.listFiles.map(study => if (study.isDirectory) study.listFiles else Array[File]()).toArray.toList.flatten;

                val foo = seriesDirList(new File("D:\\tmp"))
                
                
                val restXStor = new RestXStor
                val request = new Request
                request.setMethod(Method.GET)
                restXStor.handle(request, new Response(request))
                println("TestRestXStor done")
            }
            catch {
                case e:Exception => e.printStackTrace
            }
    }
}
