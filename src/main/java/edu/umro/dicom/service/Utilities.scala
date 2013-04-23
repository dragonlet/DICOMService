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
import java.util.HashMap
import org.restlet.Restlet
import org.restlet.Request
import org.restlet.Response
import org.restlet.data.Status
import org.restlet.data.Method
import org.restlet.data.MediaType
import org.restlet.data.Parameter
import com.pixelmed.dicom.TagFromName
import com.pixelmed.dicom.DicomDictionary
import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeFactory;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.network.DicomNetworkException;
import com.pixelmed.utils.StringUtilities;
import org.restlet.data.Parameter

object Utilities {


    val MEDIA_TYPE_PARAMETER_NAME = "media_type";

    val AE_TITLE_PARAMETER_NAME = "aetitle";

    val LIMIT_PARAMETER_NAME = "limit";

    val DICOM_MEDIA_TYPE = new MediaType("application/dicom", "DICOM: Digital Imaging and Communications in Medicine")

    val dictionary = new DicomDictionary

    def listToString[T](l:List[T]):String = {
        l match {
            case head :: tail => l.map(s => s.toString).reduceLeft[String] { (acc, n) => acc + ", " + n.toString }
            case _ => ""
        }
    }

    private def splitList(valueList:String):List[String] = {
        valueList match {
            case s:String => s.split("[, \n\t]").toList
            case _ => List[String]()
        }
    }

    def pacsList = ServiceConfig.getInstance().getPacsList().toArray.toList.map(p => p match { case pp:PACS => pp })

    /**
     * Get the list of parameters from the request.  This should only be done once
     * per request and the returned list re-used because it contains state (the
     * <code>recognized</code> value).
     */
    def getParamList(request:Request):List[Param] = {
        val x = request.getResourceRef.getQueryAsForm.toArray.toList
        val pl = request.getResourceRef.getQueryAsForm.toArray.toList.map(s => s match {
            case s:Parameter => new Param(s.getName, s.getValue)
            case _ => println("What is going on?") ; new Param("","")
        })
        pl
    }

    /**
     * Get the named parameter, or nothing if the
     * parameter was not defined or did not specify a value.
     */
    private def getParam(parameterName:String, paramList:List[Param]):Option[Param] = {
        (for (p <- paramList if p.getName.equalsIgnoreCase(parameterName)) yield p) match {
            case head :: _ => Some(head)
            case _ => None
        }
    }


    /**
     * Get the list of values specified by the parameter or an
     * empty list if the parameter was not specified or did not specify
     * any values.
     */
    private def getValueList(parameterName:String, paramList:List[Param]):List[String] = {
        getParam(parameterName, paramList) match {
            case Some(p) => splitList(p.getValue)
            case _ => List[String]()
        }
    }

    /**
     * Get the list of values specified by the given parameter name.
     * 
     * Look in the URL first.  If a supported media type is found in
     * the URL, then use that.  If the URL specifies media types, but
     * none of them are supported, then set an error code and return
     * None.
     * 
     * If the media type was not specified in the URL, then check the
     * HTTP header.  If specified there, and supported, then use it.
     * If specified there and not supported, then set an error code and
     * return None.
     * 
     * If the media type was not specified in the URL or the HTTP header,
     * then return the default media type.
     * 
     */
    def getMediaType(request:Request, response:Response, supported:List[MediaType], paramList:List[Param]):Option[MediaType] = {
        val mediaParam = getParam(MEDIA_TYPE_PARAMETER_NAME, paramList)
        val urlRequestedNameList = mediaParam match {
            case Some(p) => {
                p.setRecogonized
                splitList(p.getValue)
            }
            case _ => List[String]()
        }

        def getMt(requestedNameList:List[String]):Option[MediaType] = {
                val mediaTypeList =
                    supported.map(s => if (requestedNameList.contains(s.getName.toLowerCase)) List(s) else List[MediaType]()).flatten  // keep only matching media type
                    if (mediaTypeList.length > 0) {
                        mediaParam.get.setRecogonized
                        Option(mediaTypeList.head)  // yay - found a match
                    }
                    else {
                        // client did not specify any supported media types
                        val msg =
                            "None of the requested media types is supported. Requested media type(s): " + listToString(urlRequestedNameList) + ". " +
                            " Supported media types: " + listToString(supported.map(s => s.getName))
                            response.setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,msg)
                            response.setEntity(msg, MediaType.TEXT_PLAIN);
                        None
                    }
        }

        if (urlRequestedNameList.length > 0) {
            getMt(urlRequestedNameList)
        }
        else {
            val mtKey = "Accept"
                if (request.getAttributes().containsKey(mtKey)) {
                    val mediaTypeList = request.getAttributes().get(mtKey) match { case s:String => s }
                    getMt(splitList(mediaTypeList))
                }
                else
                    // client did not specify anything, so return default media type
                    Some(supported(0))
        }
    }


    def getDicomDict(response:Response, paramList:List[Param]):Option[DicomDict] = {
        Some(new DicomDict)  // TODO this should check for dictionary being specified in request (support custom dictionaries)
    }


    /**
     * Build an attribute list based on the request with the values specified
     * for each.  DICOM names may optionally be prefixed with 'd' to designate
     * them as DICOM names.  An example URL would contain:
     * 
     *    .../?PatientID=123&PatientName=John Smith
     * 
     * DICOM names are case insensitive, although the values are not.
     * 
     * If a DICOM dictionary specified in the request it is used, if not, the
     * default dictionary if used.  If the client specifies an invalid dictionary
     * then this will fail and return 'None'.
     * 
     */
    def getDicomAttributes(request:Request, response:Response, paramList:List[Param]):Option[AttributeList] = {
        def getAttr(dictionary:DicomDict):AttributeList = {
                val attrList = new AttributeList

                def isDicomParam(param:Param):Boolean = {
                        val tag = dictionary.getTagByCaseInsensitiveName(param.getName)
                        if (tag != null) {
                            val attr = AttributeFactory.newAttribute(tag)
                            attr.addValue(param.getValue)
                            attrList.put(attr)
                            param.setRecogonized
                        }
                        tag != null
                }

                paramList.map(p => isDicomParam(p))

                attrList
        }

        val urlRequestedNameList = getValueList(MEDIA_TYPE_PARAMETER_NAME, paramList).map(s => s.toLowerCase)

        getDicomDict(response:Response, paramList:List[Param]) match {
            case Some(dictionary) => Some(getAttr(dictionary))
            case _ => None
        }
    }


    /**
     * Get an AETitle specification from the request.  To be valid, it must be
     * specified, and must be one of the known PACS.  If it is, then return that
     * single PACS, otherwise None.
     */
    def getPacs(response:Response, paramList:List[Param]):Option[PACS] = {
        def listOfPacs = ServiceConfig.getInstance.getPacsList.toArray.toList.map(s => s match { case s:PACS => s })
        def listOfAETitles = listOfPacs.map(p => p.aeTitle)

        getParam(AE_TITLE_PARAMETER_NAME, paramList) match {
            case Some(aETitleParam) => {
                aETitleParam.setRecogonized
                (for (p <- listOfPacs if (p.aeTitle.equalsIgnoreCase(aETitleParam.getValue))) yield p) match {
                    case p::_ => {
                        Some(p)
                    }
                    case _ =>
                    val msg =
                        "The AETITLE '" + aETitleParam.getValue + "' is not valid.  Possible values are:\n\n    " + listToString(listOfAETitles).replaceAll(", *", "\n    ") + "\n\n"
                        response.setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,msg)
                        response.setEntity(msg, MediaType.TEXT_PLAIN);
                    None
                } 

            }
            case _ =>
            val msg =
                "No AETITLE was specified.  Possible values are: " + listToString(listOfAETitles)
                response.setStatus(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,msg)
                response.setEntity(msg, MediaType.TEXT_PLAIN);
            None
        }
    }


    /**
     * Get the limit specified by the caller.  It must be 
     */
    def getLimit(response:Response, paramList:List[Param]):Option[Int] = {
        val l = getParam(LIMIT_PARAMETER_NAME, paramList)
        getParam(LIMIT_PARAMETER_NAME, paramList) match {
            case Some(p) => {
                p.setRecogonized
                try {
                    val limit = p.getValue.toInt
                    if (limit < 0) {
                        val msg = "Invalid negative number " + limit + " given for limit.\nOnly positive integers are allowed.  Use zero for no limit.";
                        response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
                        response.setEntity(msg, MediaType.TEXT_PLAIN);
                        None
                    } else Some(limit)
                }
                catch {
                    case e:Exception => {
                        val msg = "Invalid number " + p.getValue + " given for limit: " + e + "\nOnly positive integers are allowed.  Use zero for no limit.";
                        response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
                        response.setEntity(msg, MediaType.TEXT_PLAIN);
                        None
                    }
                }
            }
            case None => Some(0)  // If no limit specified, assume 0 == infinite
            case _ => None
        }
    }


    /**
     * Check for any unrecognized parameters, and if none are found, return true (the good, happy thing),
     * otherwise return false and set the response to a failed status with a message
     * describing the problem.
     */
    def checkForUnrecognizedParameters(response:Response, paramList:List[Param]):Boolean = {
        paramList.filter(p => !(p.isRecognized)) match {
            case un if (un.length == 0) => true
            case un => {
                val msg =
                    "The following parameter name(s) are not recognized as valid:\n\n    " +
                    listToString(un.map(u => u.getName)).replaceAll(", *", "\n    ") +
                    "\n\nMost parameter names are case in-sensitive.  Possibly there is an error in spelling?\n"
                    response.setStatus(Status.CLIENT_ERROR_BAD_REQUEST)
                    response.setEntity(msg, MediaType.TEXT_PLAIN);
                false
            }
        }
    }


    /**
     * Get a summary of a DICOM object.
     * 
     * @param attributeList DICOM object.
     * 
     * @return Brief description of DICOM object.S
     */
    def dicomSummary(attributeList:AttributeList):String = {
        def getAttr(tag:AttributeTag):String = {
                attributeList.get(tag) match {
                    case a:Attribute => a.getSingleStringValueOrNull() match {
                        case v:String => "    " + dictionary.getNameFromTag(tag) + " : " + v
                        case _ => ""
                    } 
                    case _ => ""
                }
        }
        getAttr(TagFromName.PatientID) +
        getAttr(TagFromName.PatientName) +
        getAttr(TagFromName.Modality) +
        getAttr(TagFromName.SeriesNumber) +
        getAttr(TagFromName.PatientName) +
        getAttr(TagFromName.InstanceNumber)
    }


    def main(args: Array[String]): Unit = {}

}
