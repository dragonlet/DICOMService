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
import java.rmi.RemoteException;
import java.security.KeyStore;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.umro.dicom.common.TrustStore;
import edu.umro.dicom.service.CFind.QueryLevel;
import edu.umro.util.Log;
import edu.umro.util.OpSys;
import edu.umro.util.OpSys.OpSysId;
import edu.umro.util.UMROException;
import edu.umro.util.Utility;
import edu.umro.util.XML;

/**
 * Configuration for the DICOM service.
 * 
 * @author irrer
 *
 */
public class ServiceConfig {

    /** name of environment variable that indicates where configuration file is. */
    private static final String CONFIG_DIR_ENV = "DICOM_REST_CONFIG";

    /** Name of configuration file. */
    private static final String CONFIG_FILE_NAME = "dicomsvcConfig.xml";

    /** Default directory for audit records. */
    private static final File DEFAULT_AUDIT_DIR =
        new File((OpSys.getOpSysId() == OpSysId.WINDOWS)
                ? "C:\\Program Files\\UMRO\\dicomsvc\\log\\audit"
                        : "/var/log/dicomsvc/audit"); 

    /** list of directories to search for configuration files. */
    private static final String[] CONFIG_DIR_LIST = {
        "src\\main\\resources",
        "src/main/resources",
        "."
    };
    
    /** Default prompt for user name and password if none is configured. */
    private static final String DEFAULT_LDAP_PROMPT = "Enter your name and password.";

    /** Parsed configuration. */
    private Document config = null;

    /** Directory to be used for recording and audit of user activity. */
    private File auditDirectory = null;

    /** Directory to be used for temporary files. */
    private File temporaryDirectory = null;

    /** Instance of configuration. */
    private static ServiceConfig instance = null;

    /** Name of file used to get configuration. */
    private String configFileName = null;

    /** List of PACS that this server hosts. */
    private PACS[] hostedPacs = null;

    /** XStor FS directory that contains the DICOM objects. */
    private String xstorFSDir[] = null;


    /**
     * Container for MR CT configuration parameters.
     * 
     * @author irrer
     *
     */
    public class MRCTConfig {
        final public String aeTitle;
        final public int port;
        final public String exchange;
        final public String agentName;
        final public String user;
        final public String destinationPacs;
        final public String dataRoutingKey;
        final public String dataExchange;

        public MRCTConfig(Node top) throws UMROException {
            aeTitle = XML.getValue(top, "AETitle/text()");
            port = Integer.parseInt(XML.getValue(top, "Port/text()"));
            exchange = (XML.getValue(top, "Exchange/text()") == null) ? "" : XML.getValue(top, "Exchange/text()");
            agentName = XML.getValue(top, "AgentName/text()");
            user = XML.getValue(top, "User/text()");
            destinationPacs = XML.getValue(top, "DestinationPacs/text()");
            dataRoutingKey = XML.getValue(top, "DataRoutingKey/text()");
            dataExchange = XML.getValue(top, "DataExchange/text()");
        }
    }

    private MRCTConfig mrCtConfig = null;

    private String getValue(String path) {
        try {
            return XML.getValue(config, path + "/text()");
        }
        catch (UMROException e) {
            Log.get().warning("Could not get " + path + "  from configuration.");
        }
        return null;
    }
    
    /**
     * Parse an XML file into a DOM.
     * 
     * @param fileName File to parse.
     * 
     * @return DOM, or null on failure.
     */
    private Document parse(String fileName) {
        try {
            Log.get().fine("Attempting to parse XML file " + fileName);
            Document document = XML.parseToDocument(Utility.readFile(new File(fileName)));
            if (document != null) {
                Log.get().fine("Using configuration file: " + fileName);
            }
            return document;
        }
        catch (Exception e) {
        }
        return null;
    }


    /**
     * Read the configuration from a file that must be named <code>CONFIG_FILE_NAME</code>.  First try getting the name of the configuration
     * file from the <code>CONFIG_DIR_ENV</code> (specifies the directory) environment variable.  If that does not work,
     * then try each of the directories in <code>CONFIG_DIR_LIST</code> (list of directories).
     */
    public ServiceConfig() {
        String dirName = System.getenv(CONFIG_DIR_ENV);
        if (dirName == null) {
            Log.get().info("Environment variable " + CONFIG_DIR_ENV + " is not set and is being ignored.");
        }
        else {
            Log.get().info("Environment variable " + CONFIG_DIR_ENV + " is set to " + dirName + " which is being searched for config files...");
            while (dirName.endsWith(File.separator)) {
                dirName = dirName.substring(0, dirName.length()-1);
            }
            dirName += File.separator;
            configFileName = dirName + CONFIG_FILE_NAME;
            config = parse(configFileName);
        }
        for (int d = 0; (config == null) && (d < CONFIG_DIR_LIST.length); d++) {
            dirName = CONFIG_DIR_LIST[d];
            Log.get().info("Checking directory " + dirName + " for config files.");
            if (config == null) {
                configFileName = dirName + File.separator + CONFIG_FILE_NAME;
                config = parse(configFileName);
                if (config != null) {
                    Log.get().info("Using config file: " + (new File(configFileName).getAbsoluteFile()));
                }
            }
        }
        if (config == null) {
            Log.get().severe("Unable to find application configuration file " + CONFIG_FILE_NAME);
        }
    }


    /**
     * Get a static instance of the configuration.
     * 
     * @return A static instance of the configuration.
     */
    public static synchronized ServiceConfig getInstance() {
        if (instance == null) {
            instance = new ServiceConfig();
        }
        return instance;
    }


    /**
     * Get the maximum number of entries that can be returned before a find fails.
     * 
     * @return The maximum number of entries that can be returned before a find fails.
     * 
     * @throws NumberFormatException
     * @throws UMROException
     */
    public long getCFindLimit() throws NumberFormatException, UMROException {
        return Long.parseLong(XML.getValue(config, "/DicomServiceConfig/CFindLimit/text()"));
    }


    /**
     * Get the HTTP port from which the server should serve.
     * 
     * @return Port number
     * 
     * @throws NumberFormatException
     * @throws UMROException
     */
    public int getPort() throws NumberFormatException, UMROException {
        return Integer.parseInt(XML.getValue(config, "/DicomServiceConfig/Port/text()"));
    }


    /**
     * Get the expiration age in days of DICOM files on STAGING PACS.
     * 
     * @return Port number
     * 
     * @throws NumberFormatException
     * @throws UMROException
     */
    public int getExpirationAge() throws NumberFormatException, UMROException {
        return Integer.parseInt(XML.getValue(config, "/DicomServiceConfig/ExpirationAge/text()"));
    }


    /**
     * Get the AE title that that server should call itself.
     * 
     * @return AE Title
     * 
     * @throws UMROException
     */
    public PACS[] getHostedPACS() throws UMROException {
        if (hostedPacs == null) {
            try {
                NodeList nodeList = XML.getMultipleNodes(config, "/DicomServiceConfig/AETitleList/AETitle");
                for (int n = 0; n < nodeList.getLength(); n++) {
                    String host = null;
                    try {
                        host = XML.getValue(nodeList.item(n), "@Host").trim();
                    } catch (Exception e) { host = null; }
                    if ((host == null) || (nodeList.getLength() == 1) || host.equalsIgnoreCase(OpSys.getHostIPAddress()) || host.equalsIgnoreCase(OpSys.getHostName())) {
                        int low = Integer.parseInt(XML.getValue(nodeList.item(n), "@Low").trim());
                        int high = Integer.parseInt(XML.getValue(nodeList.item(n), "@High").trim());
                        if (low > high) {  // swap if out of order
                            int x = low;
                            low = high;
                            high = x;
                        }
                        String aePrefix = XML.getValue(nodeList.item(n), "text()");
                        ArrayList<PACS> pacsList = new ArrayList<PACS>();
                        for (int port = low; port <= high; port++) {
                            pacsList.add(new PACS(aePrefix+port, host, port));
                        }
                        hostedPacs = new PACS[pacsList.size()];
                        for (int p = 0; p < pacsList.size(); p++) {
                            hostedPacs[p] = pacsList.get(p);
                        }
                        return hostedPacs;
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            throw new UMROException("Configuration is unable to find /DicomServiceConfig/AETitleList/AETitle to use for host " + OpSys.getHostIPAddress());
        }
        return hostedPacs;
    }


    /**
     * Get the AE title that that server should call itself.
     * 
     * @return AE Title
     * 
     * @throws UMROException
     */
    public MRCTConfig getMRCT() throws UMROException {
        if (mrCtConfig == null) {
            mrCtConfig = new MRCTConfig(XML.getSingleNode(config, "/DicomServiceConfig/MRCT"));
        }
        return mrCtConfig;
    }


    /**
     * Get the AMQP broker host or null if not configured.
     * 
     * @return The AMQP broker host or null if not configured
     * 
     * @throws UMROException
     */
    public String getAmqpBrokerHost() throws UMROException {
        return getValue("/DicomServiceConfig/AMQPBroker/Host");
    }


    /**
     * Get the port of the AMQP broker, -1 if not configured or mis-configured.
     * 
     * @return AMQP broker port or -1.
     * 
     * @throws UMROException
     */
    public int getAmqpBrokerPort() throws UMROException {
        String text = getValue("/DicomServiceConfig/AMQPBroker/Port");
        try {
            return Integer.parseInt(text);
        } catch (Exception e){}
        
        return -1;
    }


    /**
     * Get the URL of the LDAP service.
     * 
     * @return URL of the LDAP service.
     * 
     */
    public String getUserAuthenticationLdapUrl() {
        return getValue("/DicomServiceConfig/UserAuthenticationLdapUrl");
    }
    
    
    /**
     * Get the format of the LDAP service.
     * 
     * @return Format of the LDAP service.
     * 
     */
    public String getUserAuthenticationLdapFormat() {
        return getValue("/DicomServiceConfig/UserAuthenticationLdapFormat");
    }
    
    
    /**
     * Get the number of minutes to cache credentials.
     * 
     * @return Number of minutes to cache credentials.
     * 
     */
    public long getCredentialCacheTime() {
        try {
            String text = getValue("/DicomServiceConfig/CredentialCacheTime");
            long time = Long.parseLong(text);
            time = (time < 1) ? 0 : time;
            return time;
        }
        catch (Exception e){};
        return 0;
    }
    
    
    /**
     * Get the prompt to be presented to the user when they enter
     * their user name and password.
     * 
     * @return Authentication prompt.
     */
    public String getUserAuthenticationLdapPrompt() {
        String prompt = getValue("/DicomServiceConfig/UserAuthenticationLdapPrompt");
        return (prompt == null) ? DEFAULT_LDAP_PROMPT : prompt;
    }
    

    /**
     * Get the URL of the login service which provides authorization information.
     * 
     * @return URL of the login service.
     * 
     */
    public String getLoginServiceUrl() {
        return getValue("/DicomServiceConfig/LoginServiceURL");
    }


    /**
     * Get the AE title that that server should call itself.
     * 
     * @return AE Title
     * 
     */
    public String getDicomListAETitle() {
        return getValue("/DicomServiceConfig/DicomListAETitle");
    }


    /**
     * Get the AE title that that server should call itself.
     * 
     * @return AE Title
     * 
     */
    public String getTcpNoDelay() {
        return getValue("/DicomServiceConfig/TcpNoDelay");
    }


    /**
     * Get the XStor FS directory that contains the DICOM objects.
     * 
     * @return XStor FS directory that contains the DICOM objects
     * 
     */
    public String[] getXStorFSDir() {
        if (xstorFSDir == null) {
            try {
                NodeList nodeList = XML.getMultipleNodes(config, "/DicomServiceConfig/XStorFSDir");
                if (nodeList.getLength() == 0) {
                    return xstorFSDir;
                }
                xstorFSDir = new String[nodeList.getLength()];
                for (int n = 0; n < nodeList.getLength(); n++) {
                    try {
                        xstorFSDir[n] = null ;
                        xstorFSDir[n] = XML.getValue(nodeList.item(n), "text()") ;
                    } catch (Exception e) { Log.get().info("XStorFSDir directory failed"); }
                }
            }
            catch (Exception e) {
                Log.get().warning("Could not get XStorFSDir from configuration: " + e);
                return null;
            }
        }
        return xstorFSDir;
    }


    /**
     * Get the list of configured PACS.
     * 
     * @return List of PACS
     * 
     * @throws UMROException
     */
    public ArrayList<PACS>getPacsList() throws UMROException {
        ArrayList<PACS> pacsList = new ArrayList<PACS>();
        NodeList nodeList = XML.getMultipleNodes(config, "/DicomServiceConfig/PACSList/PACS");
        for (int n = 0; n < nodeList.getLength(); n++) {
            Node node = nodeList.item(n);
            NodeList attrList = XML.getMultipleNodes(node, "@*");
            String aeTitle = null;
            String host = null;
            int port = -1;
            PACS.Compression compression = null;
            for (int a = 0; a < attrList.getLength(); a++) {
                Node attr = attrList.item(a);
                String name = attr.getNodeName();
                String value = attr.getNodeValue();

                if (name.equals("AETitle")) {
                    aeTitle = value;
                }
                if (name.equals("Host")) {
                    host = value;
                }
                if (name.equals("Port")) {
                    port = Integer.parseInt(value);
                }
                if (name.equals("Compression")) {
                    compression = PACS.Compression.textToCompression(value);
                }
            }
            pacsList.add(new PACS(aeTitle, host, port, compression));
        }
        return pacsList;
    }


    /**
     * Get the list of DICOM tags for the given C-FIND query level.
     * 
     * @param queryLevel Level of C-FIND query.
     * 
     * @return List of DICOM tag names.
     * 
     * @throws RemoteException
     */
    public ArrayList<String> getCFindTagList(CFind.QueryLevel queryLevel) throws UMROException {
        ArrayList<String> tagList = new ArrayList<String>();
        String xPath = "/DicomServiceConfig/CFindTagList[@Level='" + queryLevel.toString() + "']/Tag/text()";
        NodeList nodeList = XML.getMultipleNodes(config, xPath);
        for (int n = 0; n < nodeList.getLength(); n++) {
            tagList.add(nodeList.item(n).getNodeValue());
        }
        return tagList;
    }


    /**
     * Get the list of tags whose values should be anonymized when
     * anonymization is done.
     * 
     * @return List of tags.
     * 
     * @throws RemoteException
     */
    public ArrayList<String> getAnonymizeList() throws UMROException {
        ArrayList<String> tagList = new ArrayList<String>();
        String xPath = "/DicomServiceConfig/AnonymizeList/Tag/text()";
        NodeList nodeList = XML.getMultipleNodes(config, xPath);
        for (int n = 0; n < nodeList.getLength(); n++) {
            tagList.add(nodeList.item(n).getNodeValue());
        }
        return tagList;
    }


    /**
     * Determine which trust stores are available and return a
     * list of them.  They must be readable, end in '.jks', and
     * provide a working password.
     * 
     * @return List of available trust stores.
     */
    public ArrayList<TrustStore> getTrustStoreList() {
        ArrayList<TrustStore> list = new ArrayList<TrustStore>();
        NodeList nodeList = null;    
        try {
            nodeList = XML.getMultipleNodes(config, "/DicomServiceConfig/javax.net.ssl.trustStore.dir/text()");
            for (int tsd = 0; tsd < nodeList.getLength(); tsd++) {
                Node node = nodeList.item(tsd);
                File dir = new File(node.getNodeValue());
                String storepass = XML.getValue(node, "../@storepass");
                String keypass = XML.getValue(node, "../@keypass");
                if (dir.isDirectory()) {
                    for (File jksFile : dir.listFiles()) {
                        if (jksFile.getName().toLowerCase().endsWith(".jks")) {
                            try {
                                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                                ks.load(new FileInputStream(jksFile), storepass.toCharArray());
                                list.add(new TrustStore(jksFile, keypass, storepass));
                            }
                            catch (Exception e) {
                                Log.get().warning("Ignoring java key store file " + jksFile.getAbsolutePath() + " : " + e);
                            }
                        }
                    }
                }
            }
        }
        catch (UMROException e) {
            Log.get().warning("Unable to parse list of javax.net.ssl.trustStore.dir.  You will not be able to communicate with the DICOM service.");
        }
        return list;
    }


    /**
     * Get the directory that is to contain the audit records.
     * 
     * @return The directory that is to contain the audit records.
     */
    public synchronized File getAuditDirectory() {
        if (auditDirectory == null) {
            try {
                Node node = XML.getSingleNode(config, "/DicomServiceConfig/AuditDirectory[@OS='" + OpSys.getOpSysId().toString() + "']/text()");
                auditDirectory = new File(node.getNodeValue());
            }
            catch (Exception e) {
                auditDirectory = DEFAULT_AUDIT_DIR;
                Log.get().warning("Unable to get AuditDirectory from configuration file " +
                        configFileName + " because " + e + " . Using default of: " + auditDirectory.getAbsolutePath());
            }
            auditDirectory.mkdirs();
            if (auditDirectory.isDirectory()) {
                Log.get().info("Using audit directory " + auditDirectory.getAbsolutePath());
            }
            else {
                Log.get().warning("Unable to create audit directory: " + auditDirectory.getAbsolutePath() + " .  Auditing will not be performed.");
            }
        }
        return auditDirectory;
    }


    /**
     * Get the authorization list, which is a list of groups that the user must
     * be a member of (at least one) to be authorized.
     * 
     * @return The authorization list.
     */
    public Node getAuthorizationList() {
        try {
            return XML.getSingleNode(config, "/DicomServiceConfig/AuthorizationList");
        } catch (UMROException e) {
            return null;
        }
    }


    /**
     * Get the white list list, which is a list of sources that do not require
     * authentication or authorization.
     * 
     * @return The white list.
     */
    public Node getWhiteList() {
        try {
            return XML.getSingleNode(config, "/DicomServiceConfig/WhiteList");
        } catch (UMROException e) {
            return null;
        }
    }


    /**
     * Get the directory used for temporary files.
     * 
     * @return The directory used for temporary files.
     */
    public File getTemporaryDir() {
        if (temporaryDirectory == null) {
            try {
                Node node = XML.getSingleNode(config, "/DicomServiceConfig/TemporaryDirectory[@OS='" + OpSys.getOpSysId().toString() + "']/text()");
                temporaryDirectory = new File(node.getNodeValue());
            }
            catch (Exception e) {
                String dirName = System.getProperty("java.io.tmpdir");
                dirName = (dirName == null) ? "." : dirName;
                temporaryDirectory = new File(new File(dirName), "dicomsvc-tmp");
                Log.get().warning("Unable to get TemporaryDirectory from configuration file " +
                        configFileName + " because " + e + "  . Using default of: " + temporaryDirectory.getAbsolutePath());
            }
            temporaryDirectory.mkdirs();
            if (temporaryDirectory.isDirectory()) {
                Log.get().info("Using temporary directory " + temporaryDirectory.getAbsolutePath());
            }
            else {
                Log.get().severe("Unable to create temporary directory: " + temporaryDirectory.getAbsolutePath() + " .  Some operations may fail.");
            }
        }
        return temporaryDirectory;
    }


    /**
     * Self test.
     * 
     * @param args Ignored
     * 
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        ServiceConfig config = new ServiceConfig();
        
        {
            for (String dirName : config.getXStorFSDir()) System.out.println("XStorFSDir directory list: " + dirName);
        }
        
        {
            File file = config.getTemporaryDir();
            System.out.println("Temporary directory: " + file.getAbsolutePath());
        }
        {
            PACS[] pacsList = config.getHostedPACS();
            for (PACS pacs : pacsList) {
                System.out.println("    hosted PACS: " + pacs);
            }
        }
        {
            System.out.println("List of PACS:");
            ArrayList<PACS> pacsList = config.getPacsList();
            for (PACS p : pacsList) {
                System.out.println("    " + p);
            }
        }
        System.out.println();
        {
            for (QueryLevel queryLevel : QueryLevel.values()) {
                System.out.println("List of tags for query level: " + queryLevel);
                ArrayList<String> tagList = config.getCFindTagList(queryLevel);
                for (String tagName : tagList) {
                    System.out.println("    " + tagName);
                }
            }
        }
        {
            System.out.println("List of anonymizing tags");
            ArrayList<String> tagList = config.getAnonymizeList();
            for (String tagName : tagList) {
                System.out.println("    " + tagName);
            }
        }
        System.out.println();
        {
            System.out.println("getCFindLimit: " + config.getCFindLimit());
        }
    }
}
