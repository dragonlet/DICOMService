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
import java.net.ServerSocket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.logging.Level;

import javax.xml.ws.Holder;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Server;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.service.StatusService;
import org.restlet.util.Series;
import org.restlet.Restlet;

import edu.umro.util.Log;
import edu.umro.util.OpSys;
import edu.umro.dicom.common.TrustStore;

/**
 * Main entry point for DICOM service
 * 
 * @author irrer
 *
 */
public class Root extends Application {

    private Root() {
        super();
    }


    private static void useTrustStore(TrustStore trustStore, Series<Parameter> parameters) {
        parameters.add("keystorePath", trustStore.getKeystoreFile().getAbsolutePath());
        parameters.add("keyPassword", trustStore.getKeyPassword());
        parameters.add("keystoreType", "JKS");
        Log.get().info("Using java keystore file: " + trustStore.getKeystoreFile().getAbsolutePath());
    }


    /**
     * Initialize secure communications with clients via
     * standard Java supported X509 certificate.
     *  
     * @param component Put configuration here.
     * 
     * @param port Port number to serve.
     * 
     * @throws UnknownHostException
     */
    private static void setupCertificate(Component component, int port) throws UnknownHostException {
        Server server = component.getServers().add(Protocol.HTTPS, port);

        Series<Parameter> parameters = server.getContext().getParameters();

        parameters.add("sslContextFactory", "org.restlet.ext.ssl.PkixSslContextFactory");
        ArrayList<TrustStore> trustStoreList = ServiceConfig.getInstance().getTrustStoreList();

        // if there is only one trust store, then use it
        if (trustStoreList.size() == 1) {
            useTrustStore(trustStoreList.get(0), parameters);
            return;
        }

        // try finding a keystore file whose name matches the host name 
        String hostName = OpSys.getHostName();
        for (TrustStore trustStore : trustStoreList) {
            if (trustStore.getKeystoreFile().getAbsolutePath().toLowerCase().matches(".*" + hostName + ".jks$")) {
                useTrustStore(trustStore, parameters);
                return;
            }
        }

        // try finding a keystore file whose name matches the host name
        String ip = OpSys.getHostIPAddress();
        for (TrustStore trustStore : trustStoreList) {
            if (trustStore.getKeystoreFile().getAbsolutePath().toLowerCase().matches(".*" + ip + ".jks$")) {
                useTrustStore(trustStore, parameters);
                return;
            }
        }
        Log.get().severe("Unable to find a viable java keystore file (*.jks) which is necessary for secure communications.  See the README.txt file for instructions on creating one.");
    }


    /**
     * Show system properties to help with diagnosing problems.
     */
    private static void showSystemProperties() {
        final String[] propertyNameList = {
                "java.class.path",
                "java.home",
                "java.vendor",
                "java.version",
                "os.arch",
                "os.name",
                "os.version",
                "user.dir",
                "user.home",
                "user.name"
        };
        StringBuffer text = new StringBuffer("System properties:");
        int max = -1;
        for (String name : propertyNameList) {
            max = (name.length() > max)  ? name.length() : max;
        }

        for (String name : propertyNameList) {
            while (name.length() < max) {
                name = " " + name;
            }
            text.append("\n    " + name + " : " + System.getProperty(name));
        }
        Log.get().info(text.toString());
    }


    private static void setupLoginService() {

        try {
            Log.get().info("Getting LoginService URL ...");
            String urlText = ServiceConfig.getInstance().getLoginServiceUrl();
            if (urlText == null) {
                Log.get().info("System not configured to use login service.");
            }
            else {
                System.out.println("Using LoginService URL : " + urlText);
                URL url = new URL(urlText);
                System.out.println("Constructing loginService ...");
                org.tempuri.LoginService loginService = new org.tempuri.LoginService(url);
                System.out.println("Constructing loginServiceSoap ...");
                org.tempuri.LoginServiceSoap loginServiceSoap = loginService.getLoginServiceSoap();
                System.out.println("Constructed loginServiceSoap");
                Holder<String> major = new Holder<String>();
                Holder<String> minor = new Holder<String>();
                System.out.println("calling loginServiceSoap.getVersion ...");
                loginServiceSoap.getVersion(major, minor);
                System.out.println("after loginServiceSoap.getVersion");
                if ((major.value != null) && (major.value.length() > 0) && (minor.value != null) && (minor.value.length() > 0)) {
                    Log.get().info("Java call to login service to get version succeeded.  major: " + major.value + "    minor: " + minor.value);
                }
                else {
                    Log.get().info("Java call to login service to get version FAILED.");
                    Log.get().info("Java call to login service to get version FAILED.  major: " + major.value + "    minor: " + minor.value);
                }
                try {
                    LoginAccess.healthCheck();
                }
                catch (Exception e) {
                    Log.get().severe("Unexpected error from LoginAccess.isHealthy with exception: " + e);
                    e.printStackTrace();
                }
                catch (Throwable t) {
                    Log.get().severe("Unexpected error from LoginAccess.isHealthy with throwable: " + t);
                    t.printStackTrace();
                }

            }
        }
        catch (Exception e) {
            Log.get().severe("Java Unexpected exception: " + e);
            e.printStackTrace();
        }
        catch (Throwable t) {
            Log.get().severe("Java Unexpected throwable: " + t);
            t.printStackTrace();
        }
    }


    /**
     * Starts the application.
     * 
     * @param args Ignored
     */
    public static void main(String[] args) throws Exception {
        Component component = null;

        //StatusService statusService = new StatusEvaluator();
        StatusService statusService = new StatusEvaluatorJava();
        //statusService.setOverwriting(true);

        try {
            Log.get().info("Starting");
            showSystemProperties();

            int port = ServiceConfig.getInstance().getPort();

            // Start the MRI MR CT catcher
            Log.get().info("Starting MRCT DICOM catcher");
            new MRCT();

            // Start the MRI MR CT catcher
            Log.get().info("Starting multiple DICOM catchers");
            DicomGet.init();

            setupLoginService();

            UserVerifier.getInstance().healthCheck();

            // Create a component
            component = new Component();

            setupCertificate(component, port);
            component.getClients().add(Protocol.FILE);
            component.setStatusService(statusService);

            // Create an application
            Application application = new Root();
            application.setStatusService(statusService);

            // Attach the application to the component and start it
            component.getDefaultHost().attach(application);
            Log.get().info("Listening on port " + ServiceConfig.getInstance().getPort());
            component.start();
        }
        catch (Exception e) {
            String msg = "Unexpected problem.  Exception: " + e;
            System.err.println(msg);
            Log.get().logrb(Level.SEVERE, Root.class.getCanonicalName(), "main", null, msg, e);
        }
        finally {
            String msg = "DICOM Service started.";
            Log.get().info(msg);
            System.out.println(msg);
            //component.stop();
        }
    }


    /**
     * Set up authentication and authorization.
     * 
     * @param router Attach to this router.
     * 
     * @param pattern Pattern for determining authorization.
     * 
     * @param attachment Pattern for attaching to router.
     * 
     * @param restlet Restlet to attach to router.
     */
    private void auth(Router router, String pattern, String attachment, Restlet restlet) {
        ChallengeAuthenticator ca = new ChallengeAuthenticator(getContext(), ChallengeScheme.HTTP_BASIC, ServiceConfig.getInstance().getUserAuthenticationLdapPrompt());
        ca.setVerifier(UserVerifier.getInstance());

        if (ServiceConfig.getInstance().getLoginServiceUrl() == null) {
            ca.setNext(restlet);
        }
        else {
            AuthorizationFilter af = new AuthorizationFilter(getContext(), pattern);
            af.setNext(restlet);
            ca.setNext(af);
        }
        router.attach(attachment, ca);
    }


    /**
     * Set up authentication and authorization.
     * 
     * @param router Attach to this router.
     * 
     * @param pattern Pattern for determining authorization and pattern for attaching to router.
     * 
     * @param restlet Restlet to attach to router.
     */
    private void auth(Router router, String pattern, Restlet restlet) {
        auth(router, pattern, pattern, restlet);
    }


    private String getStaticUri() {
        final String staticContentDir = "staticContent";
        File file = new File(staticContentDir);
        String rootUri = "file:///" + file.getAbsolutePath();                

        // For testing on development Windows system:
        if (!file.isDirectory()) {
            file = new File("src\\main\\resources\\" + staticContentDir);
            rootUri = "file:///" + file.getAbsolutePath().replace('\\', '/');
        }
        Log.get().info("URI for static content: " + rootUri);
        return rootUri;
    }


    private void setTcpNoDelay(Context context) {
        String tcpNoDelay = ServiceConfig.getInstance().getTcpNoDelay();
        if (tcpNoDelay == null) {
            Log.get().info("tcpNoDelay has not been changed.");
        }
        else {
            if (tcpNoDelay.equalsIgnoreCase("true")) {
                context.getParameters().add("tcpNoDelay", "true");
                Log.get().info("Set tcpNoDelay to true");
            }
            if (tcpNoDelay.equalsIgnoreCase("false")) {
                context.getParameters().add("tcpNoDelay", "false");
                Log.get().info("Set tcpNoDelay to false");
            }
        }
    }


    @Override
    public Restlet createInboundRoot() {

        // Create a root router
        Context context = getContext();
        setTcpNoDelay(context);
        Router router = new Router(context);
        router.setDefaultMatchingMode(Template.MODE_STARTS_WITH);

        // Attach the handlers to the root router

        auth(router, "/dicom/cfind", "/dicom/cfind/{queryLevel}", new RestDicomCFind());
        auth(router, "/dicom/put", new RestDicomPut());
        auth(router, "/pacs", new RestPacs());
        auth(router, "/dicom/get", new RestDicomGet());
        auth(router, "/dicom/" + RestDicomList.URL_BRANCH, new RestDicomList());
        auth(router, "/expired", new RestXStor());

        {
            // Serve static content.  No authentication or authorization required.
            Directory directory = new Directory(getContext(), getStaticUri());
            directory.setListingAllowed(true);
            router.attach("/", directory);
        }

        return router;
    }

}
