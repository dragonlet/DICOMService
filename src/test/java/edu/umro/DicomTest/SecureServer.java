package edu.umro.DicomTest;

import java.io.File;
import java.util.Date;

import org.restlet.Application;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Server;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Template;
import org.restlet.Component;
import org.restlet.data.Parameter;
import org.restlet.util.Series;

import edu.umro.util.Utility;


class SampleApplication extends Application {
    @Override
    public void handle(Request request, Response response) {
        response.setEntity("Hi from SecureServer SamplApplication " + new Date(), MediaType.TEXT_PLAIN);
        response.setStatus(Status.SUCCESS_OK);
    }
}


public class SecureServer extends ServerResource {

    public static void main(String[] args) throws Exception {

        System.out.println("Running SecureServer ...");
        // Create a new Component.
        Component component = new Component();

        // Add a new HTTPS server listening on port 8183
        Server server = component.getServers().add(Protocol.HTTPS, 8183);

        Series<Parameter> parameters = server.getContext().getParameters();

        if (System.out != null) {
            // Note refactoring in Restlet version 1.2:
            // com.noelios.restlet.ext.ssl.PkixSslContextFactory moved to org.restlet.ext.ssl.PkixSslContextFactory
            //and
            // com.noelios.restlet.util.DefaultSslContextFactory moved to org.restlet.engine.security.DefaultSslContextFactory");
            //
            // DefaultSslContextFactory is another sslContextFactory that can be used if desired.

            // Using Restlet version 1.1 package:
            //parameters.add("sslContextFactory", "com.noelios.restlet.ext.ssl.PkixSslContextFactory");
            parameters.add("sslContextFactory", "org.restlet.ext.ssl.PkixSslContextFactory");

            File kp = new File(new File("."), "dicom_service.jks");
            {
                File file = kp; // new File("serverX.jks");
                byte[] data = Utility.readBinFile(file);
                System.out.println("data.length: " + data.length);
                System.out.println("data as string: " + new String(data).replaceAll("[^a-zA-Z0-9]", " "));
            }

            parameters.add("keystorePath", kp.getAbsolutePath());
            parameters.add("keystorePassword", "da89c396a639798fbba86620b4b351fd2fff35bc631766845b8d6a3737e6d9ce8eae8bbcf190ee4a");
            parameters.add("keyPassword", "password");
            parameters.add("keystoreType", "JKS");
        }

        component.getDefaultHost().setDefaultMatchingMode(Template.MODE_STARTS_WITH);

        SampleApplication sampleApplication = new SampleApplication();
        // Attach the sample application.
        component.getDefaultHost().attach("", sampleApplication);
        component.getDefaultHost().attach("/", sampleApplication);

        // Start the component.
        component.start();
    }

}