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


import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.data.Method;

import com.pixelmed.dicom.DicomException;
import com.pixelmed.network.DicomNetworkException;
import edu.umro.dicom.common.Util;
import edu.umro.util.Log;
import edu.umro.util.UMROException;

/**
 * Support PACS configuration by clients.
 * 
 * The following is an example curl command that asks for the list of PACS in xml format.  Because
 * the <code>media_type</code> parameter is specified, it overrides the 'Accept' header values. 
 * 
 * curl --header 'Accept: text/html,text/xml' http://141.214.125.70:8091/pacs?media_type=text/xml
 * 
 * @author irrer
 *
 */
public class RestPacs extends Restlet {


    static private final MediaType[] ACCEPTED_MEDIA_TYPE_LIST = {
        MediaType.TEXT_HTML,
        MediaType.TEXT_XML,
        MediaType.ALL
    };


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


    private void get(Request request, Response response) throws DicomNetworkException, DicomException, IOException, UMROException {

        HashMap<String, String> parameterList = Util.getParameterList(request);
        ArrayList<String> mediaTypeNameList = Util.getMediaTypeNameList(request, parameterList);

        // TODO user id collected for auditing 
        String userId = Util.getUserId(request, response, parameterList);

        // Look for superfluous parameters
        if (!parameterList.isEmpty()) {
            String msg = "The following parameter(s) are not supported: ";
            for (String key : parameterList.keySet()) {
                msg += " " + key;
            }
            msg += "   The only accepted parameter is " + Util.MEDIA_TYPE_PARAMETER_NAME;
            setError(response, Status.CLIENT_ERROR_BAD_REQUEST, msg);
            return;
        }

        MediaType mediaType = null;

        if (mediaTypeNameList.isEmpty()) {
            // no media type specified, so default to HTML.
            mediaType = MediaType.TEXT_HTML;
        }
        else {
            for (String mediaTypeName : mediaTypeNameList) {
                if (mediaType == null) {
                    for (MediaType okMt : ACCEPTED_MEDIA_TYPE_LIST) {
                        if (mediaTypeName.equalsIgnoreCase(okMt.getName())) {
                            mediaType = okMt;
                            break;
                        }
                    }
                }
            }
            if (mediaType == null) {
                // One or more media type were given but none are supported.  Tell the client what the
                // problem was and what is acceptable.
                String msg = "Request failed because none of the media types requested is supported.  Use one of:";
                for (MediaType okMt : ACCEPTED_MEDIA_TYPE_LIST) {
                    msg += " " + okMt.getName();
                }
                msg += " as in: ../?" + Util.MEDIA_TYPE_PARAMETER_NAME + "=" + ACCEPTED_MEDIA_TYPE_LIST[0].getName();
                setError(response, Status.CLIENT_ERROR_BAD_REQUEST, msg);
                return;
            }
        }

        // At this point the media type has been determined to be valid, so it's got to be
        // either XML or HTML.

        if (mediaType.getName().equals(MediaType.TEXT_XML.getName())) {
            setError(response, Status.SUCCESS_OK, "Success");
            String xml = PACS.getPACSListAsXML();
            response.setEntity(xml, MediaType.TEXT_XML);
        }
        else {
            String html = PACS.getPACSListAsHTML();
            setError(response, Status.SUCCESS_OK, "Success");
            response.setEntity(html, MediaType.TEXT_HTML);
        }
        Log.get().info("List of PACS fetched by user " + userId + " with media type " + mediaType.getName());

    }


    private void put(Request request, Response response) throws DicomNetworkException, DicomException, IOException {
        // TODO implement
        setError(response, Status.SERVER_ERROR_INTERNAL, this.getClass() + " PACS put not implemented.");
    }


    private void delete(Request request, Response response) throws DicomNetworkException, DicomException, IOException {
        // TODO implement
        setError(response, Status.SERVER_ERROR_INTERNAL, this.getClass() + " PACS delete not implemented.");
    }


    @Override
    public void handle(Request request, Response response) {
        // assume failure until an operation succeeds.
        setError(response, Status.SERVER_ERROR_INTERNAL, this.getClass() + " Internal server error.");
        try {
            if (request.getMethod() == Method.GET) {
                get(request, response);
                return;
            }
            if (request.getMethod() == Method.PUT) {
                put(request, response);
                return;
            }
            if (request.getMethod() == Method.DELETE) {
                delete(request, response);
                return;
            }

            setError(response, Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Only HTTP GET is supported.");

        }
        catch (Exception e) {
            setError(response, Status.SERVER_ERROR_INTERNAL, "Unexpected error.  Unable to process request: " + e);
            e.printStackTrace();
        }
    }

}
