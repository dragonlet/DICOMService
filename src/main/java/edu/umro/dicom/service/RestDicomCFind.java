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
import java.util.HashSet;
import java.util.Iterator;
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
import com.pixelmed.dicom.ValueRepresentation;
import com.pixelmed.network.DicomNetworkException;
import com.pixelmed.utils.StringUtilities;

import edu.umro.dicom.common.Util;
import edu.umro.dicom.service.CFind.QueryLevel;
import edu.umro.util.Log;
import edu.umro.util.UMROException;
import edu.umro.util.XML;

public class RestDicomCFind extends Restlet {

    static private final String MEDIA_TYPE_PARAMETER_NAME = "media_type";


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

    private DicomDictionary setDictionary(HashMap<String, String> parameterList) {
        String dictionaryName = parameterList.get("dictionary");  // TODO : set up local dictionaries
        if (dictionaryName != null) {
            // TODO Search 
            String msg =
                "The caller specified that the DICOM dictionary " + dictionaryName +
                " was to be used, but this is not yet supported and is being ignored." +
                "  The standard dictionary will be used instead.";
            Log.get().warning(msg);
        }
        return new DicomDictionary();


    }


    @SuppressWarnings("unchecked")
    private AttributeTag findInDictByName(String normalizedTagName, DicomDictionary dicomDictionary) {
        for (Iterator<AttributeTag> dictTagIter = (Iterator<AttributeTag>)dicomDictionary.getTagIterator(); dictTagIter.hasNext(); ) {
            AttributeTag tag = (AttributeTag)dictTagIter.next();
            String dictTagName = dicomDictionary.getNameFromTag(tag);
            if (normalizedTagName.equalsIgnoreCase(dictTagName)) {
                return tag;
            }
        }
        return null;
    }



    @SuppressWarnings("unchecked")
    private AttributeTag findAttribute(AttributeList attributeList, String tagName, DicomDictionary dicomDictionary) {
        for (Iterator<Attribute> iterator = attributeList.values().iterator(); iterator.hasNext(); ) {
            Attribute attribute = iterator.next();
            String dictName = dicomDictionary.getNameFromTag(attribute.getTag());
            if ((dictName != null) && dictName.equalsIgnoreCase(tagName)) {
                return attribute.getTag();
            }
        }
        return null;
    }


    /**
     * Take all of the parameters that specify DICOM tag=value pairs and
     * set the C-FIND attribute list entries to those values.  If the attributes
     * are not on the default list, then add them and log a warning.  Note that
     * DICOM tag matching is case in-sensitive, and the caller may prefix a
     * tag with dt so as to ensure that it is only interpreted as a DICOM tag
     * and not some other search parameter.
     * 
     * @param requestAttributeList List of attributes to find and return.
     * 
     * @param parameterList List of parameters from URL.
     * 
     * @param dicomDictionary Context in which to use parameter names.
     * 
     * @return The list of parameters that were recognized as DICOM tags.  The caller
     * can use this list to determine if the caller specified any parameters that will
     * not be recognized and should be considered erroneous.
     */
    private ArrayList<String> setAttributeValues(AttributeList requestAttributeList, HashMap<String, String> parameterList, DicomDictionary dicomDictionary) {
        ArrayList<String> recognizedParameters = new ArrayList<String>();
        for (String tagName : parameterList.keySet()) {
            String normalizedTagName = tagName.trim().toLowerCase().replaceFirst("^dt", "");
            String value = parameterList.get(tagName);
            if (value == null) {
                Log.get().warning("Unexpected null value for DICOM tag " + tagName +
                ".  The parameter is being ignored, which could affect the C-FIND results.");
            }
            else {
                value = StringUtilities.removeLeadingOrTrailingWhitespaceOrISOControl(value).trim();

                // first look in the request attributes for a match.  If it is there, then use
                // it.  Search this list before the DICOM dictionary because it is most likely
                // here and this is a smaller list to search.
                AttributeTag dictTag = findAttribute(requestAttributeList, normalizedTagName, dicomDictionary);

                if (dictTag == null) {
                    dictTag = findInDictByName(normalizedTagName, dicomDictionary);
                }
                if (dictTag != null) {  // if the dictTag is null, then it was probably not intended as a DICOM tag, so just ignore it.
                    Attribute requestAttribute = (Attribute)requestAttributeList.get(dictTag);
                    if (requestAttribute == null) {
                        String msg =
                            "Caller attempted to use attribute " + tagName +
                            " with value " + value +
                            " as a search criteria in cfind but it will probably be ignored by the PACS as it is not on the configured list of values accepted by the PACS.";
                        Log.get().warning(msg);
                        byte[] vr = dicomDictionary.getValueRepresentationFromTag(dictTag);
                        try {
                            requestAttribute = AttributeFactory.newAttribute(dictTag, vr);
                        } catch (DicomException ex) {
                            Log.get().warning("Unable to construct new attribute with tag: " + dictTag + "  value rep.: " + vr);
                            requestAttribute = null;
                        }
                    }
                    if (requestAttribute != null) {
                        try {
                            requestAttribute.setValue(value);
                            recognizedParameters.add(tagName);
                        }
                        catch (DicomException ex) {
                            Log.get().warning("Unable to set attribute " + tagName + " to value " + value + " : DicomException: " + ex);
                        }
                    }
                }
            }
        }
        return recognizedParameters;
    }


    private String getHtmlItemSpecification(AttributeTag tag, AttributeList attributeList, DicomDictionary dicomDictionary) {
        String value = attributeList.get(tag).getSingleStringValueOrNull();
        if (value != null) {
            return dicomDictionary.getNameFromTag(tag) + "=" + value;
        }
        return null;
    }


    private String formatHtmlDownload(Request request, AttributeList attributeList, PACS pacs, QueryLevel queryLevel, DicomDictionary dicomDictionary) {
        String specification = null;

        switch (queryLevel) {
            case PATIENT:
                specification = getHtmlItemSpecification(TagFromName.PatientID, attributeList, dicomDictionary);
                break;
            case STUDY:
                specification = getHtmlItemSpecification(TagFromName.StudyInstanceUID, attributeList, dicomDictionary);
                break;
            case SERIES:
                specification = getHtmlItemSpecification(TagFromName.SeriesInstanceUID, attributeList, dicomDictionary);
                break;
            case IMAGE:
                specification = getHtmlItemSpecification(TagFromName.SOPInstanceUID, attributeList, dicomDictionary);
                break;
        }

        if (specification == null) {
            return "  <td></td>";
        }
        else {
            StringBuffer html = new StringBuffer("  <td>");
            String download = request.getHostRef() + "/dicom/get?aetitle=" + pacs.aeTitle + "&media_type=application/zip&" + specification;
            html.append("<a href='" + download + "' >Download</a>");
            html.append("</td>\n");
            return html.toString();
        }
    }


    private String formatHtmlList(Request request, AttributeList attributeList, PACS pacs, QueryLevel queryLevel, DicomDictionary dicomDictionary) {
        String specification = null;

        String level = null;

        switch (queryLevel) {
            case PATIENT:
                specification = getHtmlItemSpecification(TagFromName.PatientID, attributeList, dicomDictionary);
                level = QueryLevel.STUDY.toString();
                break;
            case STUDY:
                specification = getHtmlItemSpecification(TagFromName.StudyInstanceUID, attributeList, dicomDictionary);
                level = QueryLevel.SERIES.toString();
                break;
            case SERIES:
                specification = getHtmlItemSpecification(TagFromName.SeriesInstanceUID, attributeList, dicomDictionary);
                level = QueryLevel.IMAGE.toString();
                break;
            case IMAGE:
                break;
        }

        if (specification == null) {
            return "  <td></td>";
        }
        else {
            StringBuffer html = new StringBuffer("  <td>");
            String download = request.getHostRef() + "/dicom/cfind/" + level + "?aetitle=" + pacs.aeTitle + "&" + specification;
            html.append("<a href='" + download + "' >List</a>");
            html.append("</td>\n");
            return html.toString();
        }
    }


    private boolean isInterestingAttribute(AttributeTag tag, HashSet<AttributeTag> hasValues, DicomDictionary dicomDictionary) {
        if (!hasValues.contains(tag)) return false;
        if (tag.equals(TagFromName.QueryRetrieveLevel)) return false;
        byte[] vr = dicomDictionary.getValueRepresentationFromTag(tag);
        if (ValueRepresentation.isUniqueIdentifierVR(vr)) return false;
        return true;
    }

    
    private String getQueryLevelAsString(QueryLevel queryLevel, ArrayList<AttributeList> responseData) {
        String queryLevelText = queryLevel.toString();
        if (!responseData.isEmpty()) {
            Attribute ql = responseData.get(0).get(TagFromName.QueryRetrieveLevel);
            if (ql != null) {
                String value = ql.getSingleStringValueOrEmptyString().trim();
                if (value.length() > 0) queryLevelText = value;
            }
        }
        return queryLevelText;
    }

    
    private String formatToHtml(Request request, ArrayList<AttributeList> responseData, PACS pacs, QueryLevel queryLevel, DicomDictionary dicomDictionary) {
        StringBuffer html = new StringBuffer(Util.getHtmlHead("CFind"));
        String queryLevelText = getQueryLevelAsString(queryLevel, responseData);
        html.append("Query Level: " + queryLevelText +  " &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Number of entries: " + responseData.size() + "</p>\n");
        html.append("\n");
        html.append("<table class='sortable' id='anyid' cellpadding='0' cellspacing='0'>\n");

        if (!responseData.isEmpty()) {

            // go through all values and eliminate empty columns
            HashSet<AttributeTag> hasValues = new HashSet<AttributeTag>();
            for (AttributeList attributeList : responseData) {
                for (Object oTag : attributeList.keySet()) {
                    AttributeTag tag = (AttributeTag)oTag;
                    Attribute a = attributeList.get(tag);
                    String value = a.getSingleStringValueOrEmptyString();
                    if (!value.isEmpty()) hasValues.add(tag);
                }
            }

            AttributeList first = responseData.get(0);
            html.append("  <tr>\n");
            if (queryLevel != QueryLevel.IMAGE) html.append("    <th>List</th>");
            html.append("    <th>Download</th>");
            for (Object oTag : first.keySet()) {
                AttributeTag tag = (AttributeTag)oTag;
                if (isInterestingAttribute(tag, hasValues, dicomDictionary)) {
                    String title = dicomDictionary.getNameFromTag(tag);
                    html.append("    <th title='" + title + "'>");
                    html.append(dicomDictionary.getFullNameFromTag(tag));
                    html.append("    </th>");
                }
            }
            html.append("  </tr>\n");

            boolean even = true;
            for (AttributeList attributeList : responseData) {
                html.append("  <tr class='" + (even ? "even" : "odd") + "'>\n");
                if (queryLevel != QueryLevel.IMAGE) html.append(formatHtmlList(request, attributeList, pacs, queryLevel, dicomDictionary));
                html.append(formatHtmlDownload(request, attributeList, pacs, queryLevel, dicomDictionary));
                even = !even;
                for (Object oTag : attributeList.keySet()) {
                    AttributeTag tag = (AttributeTag)oTag;
                    if (isInterestingAttribute(tag, hasValues, dicomDictionary)) {
                        html.append("    <td>");
                        Attribute a = attributeList.get(tag);
                        html.append(a.getSingleStringValueOrEmptyString());
                        html.append("</td>\n");
                    }
                }
                html.append("  </tr>\n");
            }
        }
        html.append("\n</html></body>");
        return html.toString();
    }


    private String formatToXml(Request request, ArrayList<AttributeList> responseData, QueryLevel queryLevel, DicomDictionary dicomDictionary) {
        StringBuffer xml = new StringBuffer("<?xml version='1.0' encoding='utf-8'?>\n");
        xml.append("<CFindResultList QueryLevel='" + queryLevel + "'>\n");

        for (AttributeList attributeList : responseData) {
            xml.append("  <CFindResult>\n");
            for (Object oTag : attributeList.keySet()) {
                AttributeTag tag = (AttributeTag)oTag;
                String tagName = dicomDictionary.getNameFromTag(tag);
                String value = attributeList.get(tag).getSingleStringValueOrEmptyString();
                value = value.replace('\0', ' ');
                xml.append("    <"+tagName+">"+XML.escapeSpecialChars(value)+"</"+tagName+">\n");
            }
            xml.append("  </CFindResult>\n");
        }
        xml.append("</CFindResultList>\n");

        return xml.toString();
    }


    private void cfind(Request request, Response response, QueryLevel queryLevel) throws DicomNetworkException, DicomException, IOException, UMROException {
        HashMap<String, String> parameterList = Util.getParameterList(request);
        AttributeList requestAttributeList = CFind.constructDefaultList(queryLevel, null);

        long limit = -1;

        if (parameterList.containsKey("limit")) {
            limit = Long.parseLong(parameterList.get("limit"));
        }


        DicomDictionary dicomDictionary = setDictionary(parameterList);

        // AnonymizeGUI anonymize = AnonymizeGUI.get(request, response, parameterList);

        setAttributeValues(requestAttributeList, parameterList, dicomDictionary);

        ArrayList<AttributeList> responseData = new ArrayList<AttributeList>();

        ArrayList<PACS> pacsList = ServiceConfig.getInstance().getPacsList();
        String pacsValue = parameterList.get("aetitle");
        PACS requestedPacs = null;
        if (pacsValue != null) {
            for (String pacsName : pacsValue.split(",")) {
                for (PACS pacs : pacsList) {
                    if (pacs.aeTitle.equalsIgnoreCase(pacsName.trim())) {
                        requestedPacs = pacs;
                        CFind cFind = new CFind(pacs, queryLevel, requestAttributeList, limit);
                        responseData.addAll(cFind.getList());
                    }
                }
            }
        }
        else {
            for (PACS pacs : pacsList) {
                CFind cFind = new CFind(pacs, queryLevel, requestAttributeList, limit);
                try {
                    ArrayList<AttributeList> resultList = cFind.getList();
                    responseData.addAll(resultList);
                }
                catch (java.rmi.RemoteException ex) {
                    setError(response, Status.CLIENT_ERROR_BAD_REQUEST, ex.getMessage());
                    return;
                }
            }
        }

        if (parameterList.containsKey(MEDIA_TYPE_PARAMETER_NAME) && parameterList.get(MEDIA_TYPE_PARAMETER_NAME).equalsIgnoreCase(MediaType.TEXT_XML.getName())) {
            setError(response, Status.SUCCESS_OK, "Success");
            String xml = formatToXml(request, responseData, queryLevel, dicomDictionary);
            response.setEntity(xml, MediaType.TEXT_XML);

        }
        else {
            String html = formatToHtml(request, responseData, requestedPacs, queryLevel, dicomDictionary);
            setError(response, Status.SUCCESS_OK, "SUCCESS_OK");
            response.setEntity(html, MediaType.TEXT_HTML);
        }
    }


    @Override
    public void handle(Request request, Response response) {
        // assume failure until an operation succeeds.
        setError(response, Status.SERVER_ERROR_INTERNAL, this.getClass() + " Internal server error.");
        try {
            if (request.getMethod() == Method.GET) {
                String subCall = (String)(request.getAttributes().get("queryLevel"));
                QueryLevel queryLevel = QueryLevel.stringToQueryLevel(subCall);
                if (queryLevel != null) {
                    cfind(request, response, queryLevel);
                }
            }
            else {
                setError(response, Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Only HTTP GET is supported.");
            }
        }
        catch (Exception e) {
            setError(response, Status.SERVER_ERROR_INTERNAL, "Unexpected error.  Unable to process request: " + e);
            e.printStackTrace();
        }
    }

}
