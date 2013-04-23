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

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeFactory;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomDictionary;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.network.DicomNetworkException;

import edu.umro.dicom.service.CFind.QueryLevel;
import edu.umro.util.Log;
import edu.umro.util.UMROException;

public class RestDicomList extends Restlet {

    static private final DicomDictionary DICOM_DICTIONARY = new DicomDictionary();

    /** AE Title of PACS to search. */
    static private String dicomListAETitle = null;

    /** HTML tag name for input field. */
    static private final String INPUT_FIELD_NAME = "patientid";

    /** Sub-branch of ReST hierarchy. */
    static public final String URL_BRANCH = "list";

    /** Maximum number of results to retrieve. */
    static private final long LIMIT = Long.MAX_VALUE;

    /** List of attributes to fetch for study C-FIND. */
    private static final AttributeTag[] STUDY_REQUEST_LIST = {
        TagFromName.PatientName,
        TagFromName.PatientBirthDate,
        TagFromName.PatientSex,
        TagFromName.StudyDate,
        TagFromName.StudyTime,
        TagFromName.StudyDescription,
        TagFromName.StudyID,
        TagFromName.ModalitiesInStudy,
        TagFromName.StudyInstanceUID
    };

    /** List of attributes to fetch for study C-FIND. */
    private static final AttributeTag[] SERIES_REQUEST_LIST = {
        TagFromName.PatientName,
        TagFromName.PatientID,
        TagFromName.PatientBirthDate,
        TagFromName.PatientSex,
        TagFromName.SeriesDate,
        TagFromName.SeriesTime,
        TagFromName.Modality,
        TagFromName.SeriesDescription,
        TagFromName.SeriesNumber
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
        StringBuffer html = getHtmlHeader();
        html.append(msg);
        addHtmlTrailer(response, html);
        response.setStatus(status, msg);
    }


    /**
     * Get the AE Title of the PACS to be searched.
     * 
     * @return AE Title of the PACS to be searched.
     */
    private static synchronized String getAETitle() {
        if (dicomListAETitle == null) {
            dicomListAETitle = ServiceConfig.getInstance().getDicomListAETitle();
        }
        return dicomListAETitle;
    }

    private void addEntryForm(StringBuffer html) {
        html.append("<table border='0' cellpadding='10' cellspacing='10' bgcolor='#eefeee'>\n");
        html.append("<tr bgcolor='#ffffff'>\n");
        html.append("<td>\n");
        html.append("<form action='" + URL_BRANCH + "'>\n");
        html.append("Enter Patient Reg Number: <input type='text' name='" + INPUT_FIELD_NAME + "' />\n");
        html.append("</form>\n");
        html.append("</td>\n");
        html.append("</tr>\n");
        html.append("</table>\n");
    }

    private StringBuffer getHtmlHeader() {
        StringBuffer html = new StringBuffer();
        html.append("<html xmlns='http://www.w3.org/1999/xhtml'>\n");
        html.append("<head>\n");
        html.append("<title>List Scans</title>\n");
        html.append("</title>\n");
        html.append("<link rel='stylesheet' type='text/css' href='/example.css'/>\n");
        html.append("</head>\n");
        html.append("<center>\n");

        html.append("<table border='0' cellspacing='30'>\n");
        html.append("<tr>\n");
        {
            html.append("<td><br><p>&nbsp;&nbsp;&nbsp;&nbsp;<img src='/images/umhslogo.gif'></td>\n");
            html.append("<td>\n");
            html.append("\n");

            html.append("<table border='0' cellspacing='30'>\n");
            html.append("<tr><td><h3>Search UMRADONC-ARCHIVE PACS</h3></td></tr>\n");
            html.append("<tr><td>\n");
            addEntryForm(html);
            html.append("</td></tr>\n");
            html.append("</table>\n");

            html.append("</td>\n");
        }
        html.append("</tr>\n");
        html.append("</table>\n");
        html.append("<br><p><br>\n");
        return html;
    }


    private void addHtmlTrailer(Response response, StringBuffer html) {
        html.append("<br><p>Please note: An audit trail is made of all activity.");
        html.append("</center>\n");
        html.append("</html>\n");
        response.setEntity(html.toString(), MediaType.TEXT_HTML);
    }


    private void formatStudyResults(String patientID, ArrayList<AttributeList> list, Response response) {
        StringBuffer html = getResultsHeader(list.get(0));

        html.append("<br><p>\n");

        html.append("<table border='0' cellpadding='10' cellspacing='10' bgcolor='#eeeeff'>\n");

        html.append("<tr bgcolor='#f8f8f8'>\n");
        html.append("<td>Study Date</td>\n");
        html.append("<td>Study Time</td>\n");
        html.append("<td>Study ID</td>\n");
        html.append("<td>Modalities in Study</td>\n");
        html.append("<td>Study Description</td>\n");
        html.append("</tr>\n");

        for (AttributeList al : list) {
            html.append("<tr bgcolor='#ffffff'>\n");
            String studyDate = Util.formatDicomDate(al.get(TagFromName.StudyDate).getSingleStringValueOrEmptyString());
            String pre = "<a title='Show Series' href='/dicom/" + URL_BRANCH + "?StudyInstanceUID=" + al.get(TagFromName.StudyInstanceUID).getSingleStringValueOrEmptyString() + "'>\n";
            html.append("<td>" + pre + studyDate + "</a></td>\n");
            String studyTime = Util.formatDicomTime(al.get(TagFromName.StudyTime).getSingleStringValueOrEmptyString());
            html.append("<td>" + studyTime + "</td>\n");
            html.append("<td>" + al.get(TagFromName.StudyID).getSingleStringValueOrEmptyString() + "</td>\n");
            html.append("<td>" + al.get(TagFromName.ModalitiesInStudy).getSingleStringValueOrEmptyString() + "</td>\n");
            html.append("<td>" + al.get(TagFromName.StudyDescription).getSingleStringValueOrEmptyString() + "</td>\n");
            html.append("</tr>\n");
        }
        html.append("</table><br>\n");


        addHtmlTrailer(response, html);
        response.setStatus(Status.SUCCESS_OK, "Success");
    }


    private StringBuffer getResultsHeader(AttributeList al) {
        StringBuffer html = getHtmlHeader();

        html.append("<table border='0' cellpadding='10' cellspacing='10' bgcolor='#eefeee'>\n");

        html.append("<tr bgcolor='#ffffff'>\n");
        html.append("<td>Reg Number: " + al.get(TagFromName.PatientID).getSingleStringValueOrEmptyString() + "</td>\n");
        html.append("<td>Patient Name: " + al.get(TagFromName.PatientName).getSingleStringValueOrEmptyString() + "</td>\n");
        html.append("</tr>\n");

        html.append("<tr bgcolor='#ffffff'>\n");
        String birth = Util.formatDicomDate(al.get(TagFromName.PatientBirthDate).getSingleStringValueOrEmptyString());
        html.append("<td>Birth: " + birth + "</td>\n");
        html.append("<td>Sex: " + al.get(TagFromName.PatientSex).getSingleStringValueOrEmptyString() + "</td>\n");
        html.append("</tr>\n");

        html.append("</table>\n");
        html.append("<br><p>\n");

        return html;
    }

    private void formatSeriesResults(String studyID, ArrayList<AttributeList> list, Response response) {
        StringBuffer html = getResultsHeader(list.get(0));

        html.append("Number of series: " + list.size() + "<b><p>");

        html.append("<table border='0' cellpadding='10' cellspacing='10' bgcolor='#eeeeff'>\n");

        html.append("<tr bgcolor='#f8f8f8'>\n");
        html.append("<td>Series Date</td>\n");
        html.append("<td>Series Time</td>\n");
        html.append("<td>Modality</td>\n");
        html.append("<td>Number</td>\n");
        html.append("<td>Description</td>\n");
        html.append("</tr>\n");

        for (AttributeList al : list) {
            html.append("<tr bgcolor='#ffffff'>\n");
            String studyDate = Util.formatDicomDate(al.get(TagFromName.SeriesDate).getSingleStringValueOrEmptyString());
            html.append("<td>" + studyDate + "</td>\n");
            String studyTime = Util.formatDicomTime(al.get(TagFromName.SeriesTime).getSingleStringValueOrEmptyString());
            html.append("<td>" + studyTime + "</td>\n");
            html.append("<td>" + al.get(TagFromName.Modality).getSingleStringValueOrEmptyString() + "</td>\n");
            html.append("<td>" + al.get(TagFromName.SeriesNumber).getSingleStringValueOrEmptyString() + "</td>\n");
            html.append("<td>" + al.get(TagFromName.SeriesDescription).getSingleStringValueOrEmptyString() + "</td>\n");
            html.append("</tr>\n");
        }
        html.append("</table><br>\n");

        addHtmlTrailer(response, html);
        response.setStatus(Status.SUCCESS_OK, "Success");
    }


    private void cfindStudy(Request request, Response response, String patientID) throws DicomNetworkException, DicomException, IOException, UMROException {

        AttributeList requestAttributeList = new AttributeList();

        Attribute patientIdAttribute = AttributeFactory.newAttribute(TagFromName.PatientID);
        patientID = patientID.toUpperCase().trim();

        if (patientID.indexOf('*') != -1) {
            String msg =
                "Patient ID '" + patientID +
                "' is invalid because it contains the wild card character '*', which can result in large data sets that take hours to transfer.";
            Log.get().info(msg);
            setError(response, Status.CLIENT_ERROR_BAD_REQUEST, msg);
            return;
        }

        Log.get().info("Getting DICOM study list for patient ID: " + patientID + " by user " + request.getChallengeResponse().getIdentifier());
        patientIdAttribute.addValue(patientID);
        requestAttributeList.put(patientIdAttribute);

        for (AttributeTag tag : STUDY_REQUEST_LIST) {
            requestAttributeList.put(AttributeFactory.newAttribute(tag));
        }

        PACS pacs = null;
        for (PACS p : ServiceConfig.getInstance().getPacsList()) {
            if (p.aeTitle.equalsIgnoreCase(getAETitle())) {
                pacs = p;
                break;
            }
        }
        if (pacs == null) {
            String msg = "Could not find expected PACS " + getAETitle() + " in configuration.";
            Log.get().severe(msg);
            setError(response, Status.SERVER_ERROR_INTERNAL, msg);
            return;
        }

        ArrayList<AttributeList> list = new CFind(pacs, QueryLevel.STUDY, requestAttributeList, LIMIT).getList();

        if (list.isEmpty()) {
            setError(response, Status.CLIENT_ERROR_NOT_FOUND, "No matches for patient ID " + patientID);
        }
        else {
            formatStudyResults(patientID, list, response);
        }

    }


    private void cfindSeries(Request request, Response response, String studyID) throws DicomNetworkException, DicomException, IOException, UMROException {

        Log.get().info("Getting DICOM study list for study " + studyID + " by user " + request.getChallengeResponse().getIdentifier());

        AttributeList requestAttributeList = new AttributeList();

        Attribute studyIdAttribute = AttributeFactory.newAttribute(TagFromName.StudyInstanceUID);
        studyIdAttribute.addValue(studyID);
        requestAttributeList.put(studyIdAttribute);

        for (AttributeTag tag : SERIES_REQUEST_LIST) {
            requestAttributeList.put(AttributeFactory.newAttribute(tag));
        }

        PACS pacs = null;
        for (PACS p : ServiceConfig.getInstance().getPacsList()) {
            if (p.aeTitle.equalsIgnoreCase(getAETitle())) {
                pacs = p;
                break;
            }
        }
        if (pacs == null) {
            String msg = "Could not find expected PACS " + getAETitle() + " in configuration.";
            Log.get().severe(msg);
            setError(response, Status.SERVER_ERROR_INTERNAL, msg);
            return;
        }

        long start = System.currentTimeMillis();
        ArrayList<AttributeList> list = new CFind(pacs, QueryLevel.SERIES, requestAttributeList, LIMIT).getList();
        long elapsed = System.currentTimeMillis() - start;
        Log.get().info("Performed series level C-FIND on " + pacs + " with StudyInstanceUID " + studyID + " and got " + list.size() + " entries in " + elapsed + " milliseconds by user " + request.getChallengeResponse().getIdentifier());

        if (list.isEmpty()) {
            setError(response, Status.CLIENT_ERROR_NOT_FOUND, "No series for study " + studyID);
        }
        else {
            formatSeriesResults(studyID, list, response);
        }

    }


    @Override
    public void handle(Request request, Response response) {
        try {
            if (getAETitle() == null) {
                response.setStatus(Status.SERVER_ERROR_SERVICE_UNAVAILABLE, "This functionality has not been configured.  Configure the DicomListAETitle value to enable.");
            }
            else {
                // assume failure until an operation succeeds.
                setError(response, Status.SERVER_ERROR_INTERNAL, this.getClass() + " Internal server error.");
                if (request.getMethod() == Method.GET) {
                    HashMap<String, String> parameterList = Util.getParameterList(request);
                    for (String key : parameterList.keySet()) {
                        System.out.println("    " + key + " : " + parameterList.get(key));
                    }
                    String patientID = parameterList.get(DICOM_DICTIONARY.getNameFromTag(TagFromName.PatientID).toLowerCase());
                    String studyID = parameterList.get(DICOM_DICTIONARY.getNameFromTag(TagFromName.StudyInstanceUID).toLowerCase());

                    if (patientID != null) {
                        cfindStudy(request, response, patientID);
                    }
                    else {
                        if (studyID != null) {
                            cfindSeries(request, response, studyID);
                        }
                        else {
                            StringBuffer html = getHtmlHeader();
                            addHtmlTrailer(response, html);
                        }
                    }
                }
                else {
                    setError(response, Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Only HTTP GET is supported.");
                }
            }
        }
        catch (Exception e) {
            setError(response, Status.SERVER_ERROR_INTERNAL, "Unexpected error.  Unable to process request: " + e);
            e.printStackTrace();
        }
    }
}
