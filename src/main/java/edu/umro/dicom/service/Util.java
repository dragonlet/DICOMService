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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.restlet.Request;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Parameter;
import org.restlet.data.Preference;

public class Util {

    private static final String MEDIA_TYPE_PARAMETER_NAME = "media_type";

    /** DICOM date format. */
    private static final SimpleDateFormat DICOM_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    /** Human friendly date format. */
    private static final SimpleDateFormat HUMAN_DATE_FORMAT = new SimpleDateFormat("EEE MMM dd, yyyy");

    /** DICOM time format. */
    private static final SimpleDateFormat DICOM_TIME_FORMAT = new SimpleDateFormat("HHmmss");

    /** Human friendly time format. */
    private static final SimpleDateFormat HUMAN_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");


    public static String getHtmlHead(String title) {
        return 
        "<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Transitional//EN' 'http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd'>\n" +
        "<html xmlns='http://www.w3.org/1999/xhtml'>\n" +
        "<head>\n" +
        "    <meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1' />\n" +
        "    <title>" + title + "</title>\n" +
        "    <link rel='stylesheet' type='text/css' href='/example.css'/>\n" +
        "    <meta name='author' content='Joost de Valk, http://www.joostdevalk.nl/' />\n" +
        "    <link href='http://www.joostdevalk.nl/' rev='made' />\n" +
        "    <script type='text/javascript' src='/sortable_us.js'></script>\n" +
        "</head>\n" +
        "\n" +
        "<br><a href='/'>Home</a></br>\n" +
        "\n" +
        "<body>\n" +
        "\n";
    }


    /**
     * Get the list of parameters and their values (foo=bar parts of URL).
     * Set all parameter names to lower case.
     * 
     * @param request Request from client.
     * 
     * @return list of parameters and their values.
     */
    public static HashMap<String, String> getParameterList(Request request) {
        HashMap<String, String> parameterList = new HashMap<String, String>();
        Form form = request.getResourceRef().getQueryAsForm();
        for (Parameter parameter : form) {
            String name = parameter.getName().toLowerCase();
            parameterList.put(name, parameter.getValue());
        }
        return parameterList;
    }


    /**
     * Get the list of names of the types of media that the client will accept.
     * Also, if a media type is found in the parameter list, then remove it to
     * indicate that the parameter has been recognized by it's name.  It
     * is up to the caller to determine whether or not the media type names are
     * valid and what to do with them.  If present, the media type specified in
     * the URL is listed first. 
     * 
     * @param request Request from client.
     * 
     * @param parameterList List of URL parameters.
     * 
     * @return 
     */
    public static ArrayList<String> getMediaTypeNameList(Request request, HashMap<String, String> parameterList) {
        ArrayList<String> mediaTypeList = new ArrayList<String>();
        String value = parameterList.get(MEDIA_TYPE_PARAMETER_NAME);
        if (value != null) {
            mediaTypeList.add(value);
            parameterList.remove(MEDIA_TYPE_PARAMETER_NAME);
        }

        for (Preference<MediaType> pmt : request.getClientInfo().getAcceptedMediaTypes()) {
            String name = pmt.getMetadata().getName();
            if (!mediaTypeList.contains(name)) {
                mediaTypeList.add(name);
            }
        }

        return mediaTypeList;
    }


    /**
     * Format a DICOM date (YYYYMMDD) to a more human readable form.
     * 
     * @param dicomDate Date in DICOM format.
     * 
     * @return Date formatted in more friendly format.
     */
    public static String formatDicomDate(String dicomDate) {
        if ((dicomDate == null) || (dicomDate.length() != 8) || (dicomDate.replaceAll("[0-9]", "").length() != 0)) {
            return dicomDate;
        }
        Date date = null;
        try {
            date = DICOM_DATE_FORMAT.parse(dicomDate);
            return HUMAN_DATE_FORMAT.format(date);
        }
        catch (ParseException e) {
        }

        return dicomDate;
    }


    /**
     * Format a DICOM time (HHMMSS.sssss) to a more human readable form.
     * 
     * @param dicomTime Date in DICOM format.
     * 
     * @return Date formatted in more friendly format.
     */
    public static String formatDicomTime(String dicomTime) {
        if (dicomTime == null) {
            return dicomTime;
        }
        dicomTime = dicomTime.trim();
        if (dicomTime.indexOf('.') != -1) {
            dicomTime = dicomTime.replaceAll("\\..*", "");
        }
        Date date = null;
        try {
            date = DICOM_TIME_FORMAT.parse(dicomTime);
            return HUMAN_TIME_FORMAT.format(date);
        }
        catch (ParseException e) {
        }

        return dicomTime;
    }
    
    
    /**
     * Read the first portion of a file.
     * 
     * @param file File to read.
     * 
     * @param size Number of bytes to read.
     * 
     * @return buffer containing start of file.
     * 
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static byte[] readHead(File file, int size) throws FileNotFoundException, IOException {
        byte[] buf = new byte[size];
        new FileInputStream(file).read(buf);
        return buf;
    }
}
