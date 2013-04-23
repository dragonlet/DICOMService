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
import java.rmi.RemoteException;
import java.util.ArrayList;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeFactory;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomDictionary;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.SOPClass;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.ValueRepresentation;
import com.pixelmed.network.DicomNetworkException;
import com.pixelmed.network.FindSOPClassSCU;
import com.pixelmed.network.IdentifierHandler;

import edu.umro.util.Log;
import edu.umro.util.UMROException;

public class CFind extends IdentifierHandler {

    private ArrayList<AttributeList> list = new ArrayList<AttributeList>();

    private long limit = -1;

    private long overLimit = 0;

    public enum QueryLevel {
        PATIENT,
        STUDY,
        SERIES,
        IMAGE;

        /**
         * Convert the query level name to a query level.  Return null on failure.
         * Comparison is case insensitive and discards leading and trailing white space.
         * 
         * @param name Name of query level.
         * 
         * @return Query level or null on failure.
         */
        public static QueryLevel stringToQueryLevel(String name) {
            if (name != null) {
                name = name.trim();
                for (QueryLevel queryLevel : values()) {
                    if (name.equalsIgnoreCase(queryLevel.toString())) {
                        return queryLevel;
                    }
                }
            }
            return null;
        }
    }


    public CFind(PACS pacs, QueryLevel queryLevel, AttributeList attributeList, long lim) throws DicomNetworkException, DicomException, IOException, NumberFormatException, UMROException {
        if (lim > 0) {
            limit = lim;
        }
        else {
            limit = ServiceConfig.getInstance().getCFindLimit();
        }

        // ensure that the query tag is in the list
        if ((attributeList.get(TagFromName.QueryRetrieveLevel) == null) || (attributeList.get(TagFromName.QueryRetrieveLevel).getSingleStringValueOrEmptyString().trim().length() == 0)) {
            Attribute queryAttribute = AttributeFactory.newAttribute(TagFromName.QueryRetrieveLevel, ValueRepresentation.CS);
            queryAttribute.addValue(queryLevel.toString());
            attributeList.put(queryAttribute);
        }

        Log.get().fine("Performing C-FIND for PACS: " + pacs + "  " + queryLevel + "  " + attributeList.toString().replace('\0', ' '));
        String callingAETitle = ServiceConfig.getInstance().getHostedPACS()[0].aeTitle;

        // perform the find
        new FindSOPClassSCU(
                pacs.host,
                pacs.port,
                pacs.aeTitle,
                callingAETitle,
                (queryLevel == QueryLevel.PATIENT) ? SOPClass.PatientRootQueryRetrieveInformationModelFind : SOPClass.StudyRootQueryRetrieveInformationModelFind,
                        attributeList,
                        this,
                        0);
    }


    public ArrayList<AttributeList> getList() throws RemoteException {
        if (overLimit > 0) {
            String msg =
                "The request resulted in an excessive number of results being matched.  The limit of " +
                limit + " was exceeded with a total of " + (limit + overLimit) + " results being fetched.  "+
                "Use the 'limit=MAX' parameter to set the limit, where MAX is the maximum number of entries expected.";
            throw new RemoteException(msg);
        }
        return list;
    }


    /**
     * Support IdentifierHandler interface by responding to an
     * incoming set of attributes.
     *
     * @param attributeList Incoming attributes.
     *
     */

    @Override
    public void doSomethingWithIdentifier(AttributeList attributeList) {
        //logger.trace("Got attributes: " + attributeList.toString().replace('\0', ' '));  // log null chars as blanks
        if (list.size() < limit) {
            list.add(attributeList);
        }
        else {
            overLimit++;
        }
    }


    public static AttributeList constructDefaultList(QueryLevel queryLevel, DicomDictionary dicomDictionary) throws RemoteException, UMROException {
        if (dicomDictionary == null) {
            dicomDictionary = new DicomDictionary();
        }

        AttributeList attributeList = new AttributeList();

        ArrayList<String> tagNameList = ServiceConfig.getInstance().getCFindTagList(queryLevel);

        for (String tagName : tagNameList) {
            AttributeTag tag = dicomDictionary.getTagFromName(tagName);
            byte vr[] = dicomDictionary.getValueRepresentationFromTag(tag);
            Attribute attribute = null;
            try {
                attribute = AttributeFactory.newAttribute(tag, vr);
            }
            catch (DicomException ex) {
                Log.get().severe("Unexpected exception in Studies.addAttribute: " + ex);
            }

            attributeList.put(tag, attribute);
        }
        return attributeList;
    }


    public static void main(String[] args) throws Exception {

        PACS pacs = new PACS("IRRER", "141.214.125.70", 15678);
        AttributeList attributeList = new AttributeList();

        QueryLevel queryLevel = QueryLevel.SERIES;

        attributeList = CFind.constructDefaultList(queryLevel, null);

        CFind cFind = new CFind(pacs, queryLevel, attributeList, -1);
        System.out.println("Number of entries: " + cFind.getList().size());
        for (AttributeList al : cFind.getList()) {
            System.out.println(al.toString().replace('\0', ' ') + "\n\n");
        }
        System.out.println("Number of entries: " + cFind.getList().size());
    }
}
