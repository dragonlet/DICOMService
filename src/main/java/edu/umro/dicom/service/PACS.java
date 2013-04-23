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

import edu.umro.dicom.common.Util;
import edu.umro.util.UMROException;

/**
 * Represent a DICOM PACS
 * 
 * @author irrer
 *
 */
public class PACS {

    public static enum Compression {
        UN,
        J1,
        J2,
        J3,
        J4,
        J5,
        J6,
        N1,
        N2,
        N3,
        N4;

        /**
         * Given the text representation of a compression, return the
         * corresponding compression.  This is case insensitive and
         * trimmed of whitespace.
         * 
         * @param text Text version of compression.
         * 
         * @return Matching compression, or null if not valid.
         */
        public static Compression textToCompression(String text) {
            for (Compression c : Compression.values()) {
                if (text.trim().equalsIgnoreCase(c.toString())) {
                    return c;
                }
            }
            return null;
        }
    };

    /** AE Title */
    public final String aeTitle;

    /** Host name or IP address. */
    public final String host;

    /** Port number. */
    public final int    port;

    /** Type of compression. */
    public final Compression compression;

    
    /**
     * Construct a new PACS
     * 
     * @param aeTitle
     * @param host
     * @param port
     * @param compression
     */
    public PACS (String aeTitle, String host, int port, Compression compression) {
        this.aeTitle = aeTitle;
        this.host = host;
        this.port = port;
        this.compression = compression;
    }

    /**
     * Construct a new PACS defaulting to no compression
     * 
     * @param aeTitle
     * @param host
     * @param port
     */
    public PACS (String aeTitle, String host, int port) {
        this.aeTitle = aeTitle;
        this.host = host;
        this.port = port;
        this.compression = Compression.UN;
    }

    @Override
    public String toString() {
        return aeTitle + " " + host + ":" + port + (compression == null ? "" : (" compr:" + compression));
    }

    /**
     * Find the given PACS based on AE title.  If not found, return null.  Comparisons
     * are case insensitive and leading and trailing whitespace trimmed.
     * 
     * @param aeTitle Search for PACS with this AE title.
     * 
     * @return Matching PACS or null if not found.
     * 
     * @throws RemoteException
     * @throws UMROException 
     */
    public static PACS findPacs(String aeTitle) throws RemoteException, UMROException {
        if (aeTitle == null) {
            return null;
        }
        aeTitle = aeTitle.trim();
        for (PACS pacs : ServiceConfig.getInstance().getPacsList()) {
            if (pacs.aeTitle.trim().equalsIgnoreCase(aeTitle)) {
                return pacs;
            }
        }
        return null;
    }


    /**
     * Get the list of PACS as HTML.
     * 
     * @return HTML representation of all PACS.
     * 
     * @throws RemoteException If configuration is unavailable.
     * @throws UMROException 
     */
    public static String getPACSListAsHTML() throws RemoteException, UMROException {
        String title = "List of Configured PACS";
        StringBuffer html = new StringBuffer(Util.getHtmlHead(title));
        html.append("<center><p><h2>" + title + "</h2><p>\n");
        html.append("<table class='sortable' id='anyid' cellpadding='0' cellspacing='0'>\n");

        html.append("  <tr>\n");
        html.append("    <th>AE&nbsp;Title&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>\n");
        html.append("    <th>Host</th>\n");
        html.append("    <th>Port</th>\n");
        html.append("  </tr>\n\n");

        boolean even = true;
        for (PACS pacs : ServiceConfig.getInstance().getPacsList()) {
            html.append("  <tr class='" + (even ? "even" : "odd") + "'>\n");
            html.append("    <td>" + pacs.aeTitle + "</td>\n");
            html.append("    <td>" + pacs.host    + "</td>\n");
            html.append("    <td>" + pacs.port    + "</td>\n");
            html.append("  </tr>\n\n");
        }
        
        html.append("</table></center>\n");
        html.append("</body></html>\n");
        return html.toString();
    }


    /**
     * Get the list of PACS as XML.
     * 
     * @return XML representation of all PACS.
     * 
     * @throws RemoteException If configuration is unavailable.
     * @throws UMROException 
     */
    public static String getPACSListAsXML() throws RemoteException, UMROException {
        StringBuffer xml = new StringBuffer("<?xml version='1.0' encoding='utf-8'?>\n");
        xml.append("<PacsList>\n");
        for (PACS pacs : ServiceConfig.getInstance().getPacsList()) {
            xml.append("  <PACS AETitle='" + pacs.aeTitle + "' Host='" + pacs.host + "' Port='" + pacs.port + "'/>\n");
        }
        xml.append("</PacsList>\n");
        return xml.toString();
    }
}
