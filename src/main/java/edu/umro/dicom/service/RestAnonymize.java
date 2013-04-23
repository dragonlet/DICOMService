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

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;

import org.restlet.Request;
import org.restlet.Response;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeFactory;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomDictionary;
import com.pixelmed.dicom.DicomException;

import edu.umro.util.UMROException;


/**
 * Perform anonymization of a DICOM object.
 * 
 * @author irrer
 *
 */
public class RestAnonymize {

    private static final String ANONYMIZE_PARAMETER_NAME = "anonymize";

    private String value = null;

    /**
     * Constructor is private.
     */
    private RestAnonymize() {
    }


    /**
     * Handle a request to get a DICOM object and return an anonymous version of it.
     * 
     * @param request HTTP GET request.
     * 
     * @param response HTTP response.
     * 
     * @param parameterList 
     * 
     * @return
     */
    public static RestAnonymize get(Request request, Response response , HashMap<String, String> parameterList) {

        String value = parameterList.get(ANONYMIZE_PARAMETER_NAME);
        if (value != null) {
            parameterList.remove(ANONYMIZE_PARAMETER_NAME);
            RestAnonymize anonymize = new RestAnonymize();
            anonymize.value = value;
            return anonymize;
        }
        return null;
    }


    /**
     * Replace all of the attributes with tags that should be anonymized with
     * anonymous values.
     * 
     * @param attributeList Values to anonymize.
     * 
     * @param dictionary For interpreting values.
     * 
     * @throws RemoteException
     * @throws DicomException
     * @throws UMROException 
     */
    public void anonymize(AttributeList attributeList, DicomDictionary dictionary) throws RemoteException, DicomException, UMROException {
        dictionary = (dictionary == null) ? new DicomDictionary() : dictionary;
        ArrayList<String> anonymizeList = ServiceConfig.getInstance().getAnonymizeList();
        for (String tagName : anonymizeList) {
            AttributeTag tag = dictionary.getTagFromName(tagName);
            if (tag == null) {
                throw new RemoteException("Unknown DICOM tag in configuration AnonymizeList : " + tagName);
            }
            Attribute attribute = attributeList.get(tag);
            if (attribute != null) {
                attributeList.remove(tag);
                attribute = AttributeFactory.newAttribute(tag, dictionary.getValueRepresentationFromTag(tag));
                attribute.setValue(value);
                attributeList.put(attribute);
            }
        }
    }


    public void anonymize(AttributeList attributeList) throws RemoteException, DicomException, UMROException {
        anonymize(attributeList, new DicomDictionary());
    }


    /**
     * Increment the anonymization value to the next value.  Preserve
     * alpha and numeric characters, and preserve upper and lower case.
     * Punctuation is ignored.
     * 
     * If the value is not long enough and 'wraps around', then prefix
     * it with another digit to make it longer.
     * 
     * 
     * Examples of incrementing:
     * 
     * <li>aaa --> aab</li>
     * <li>0-5 --> 0-6</li>
     * <li>r9Z --> s0A</li>
     * <li>zZz --> 1aAa</li>
     * 
     */
    public void increment() {
        int i;
        StringBuffer val = new StringBuffer(value);
        for (i = val.length()-1; i >= 0; i--) {
            char c = val.charAt(i);
            switch (c) {
            case 'z':
                val.setCharAt(i, 'a');
                break;

            case 'Z':
                val.setCharAt(i, 'A');
                break;

            case '9':
                val.setCharAt(i, '0');
                break;
                
            default:
                if (((c >= 'a') && (c < 'z')) || ((c >= 'A') && (c < 'Z')) || ((c >= '0') && (c < '9'))) {
                    c++;
                    val.setCharAt(i, c);
                    value = val.toString();
                    return;
                }
            }
        }
        value = "1" + val.toString();
    }
    
    
   
    private void testIncr(String initial, String expected) {
        value = initial;
        increment();
        if (value.equals(expected)) {
            System.out.println("Passed test: " + initial + " --> " + value);
        }
        else {
            System.err.println("Failed test: " + initial + " --> " + value + " but exepected " + expected);
        }
    }
    
    public static void main(String[] args) {
        RestAnonymize anonymize = new RestAnonymize();
        anonymize.testIncr("aaa", "aab");
        anonymize.testIncr("0-5", "0-6");
        anonymize.testIncr("r9Z", "s0A");
        anonymize.testIncr("zZz", "1aAa");
    }
}
