package edu.umro.DicomTest;

import java.io.File;
import java.util.Date;

import org.restlet.Application;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Server;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Template;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.MapVerifier;
import org.restlet.Component;
import org.restlet.data.Parameter;
import org.restlet.util.Series;



class AuthenticatedSampleApplication extends Application {
    @Override
    public void handle(Request request, Response response) {
        response.setEntity("Hi from AuthenticatedSecureServer AuthenticatedSampleApplication " + new Date(), MediaType.TEXT_PLAIN);
        response.setStatus(Status.SUCCESS_OK);
    }
}


public class AuthenticatedSecureServer extends ServerResource {

    public static void main(String[] args) throws Exception {

        System.out.println("Running SecureServer ...");
        // Create a new Component.
        Component component = new Component();

        // Add a new HTTPS server listening on port 8183
        Server server = component.getServers().add(Protocol.HTTPS, 8184);

        Series<Parameter> parameters = server.getContext().getParameters();


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

        parameters.add("keystorePath", kp.getAbsolutePath());
        parameters.add("keystorePassword", "da89c396a639798fbba86620b4b351fd2fff35bc631766845b8d6a3737e6d9ce8eae8bbcf190ee4a");
        parameters.add("keyPassword", "password");
        parameters.add("keystoreType", "JKS");


        component.getDefaultHost().setDefaultMatchingMode(Template.MODE_STARTS_WITH);

        AuthenticatedSampleApplication authenticatedSampleApplication = new AuthenticatedSampleApplication();


        // Create a simple password verifier
        MapVerifier verifier = new MapVerifier();
        verifier.getLocalSecrets().put("gooduser", "goodpass".toCharArray());

        // Create a guard
        ChallengeAuthenticator guard = new ChallengeAuthenticator(
                component.getContext(), ChallengeScheme.HTTP_BASIC, "Tutorial");
        guard.setVerifier(verifier);

        guard.setNext(authenticatedSampleApplication);


        // Attach the sample application.
        component.getDefaultHost().attach("", guard);
        component.getDefaultHost().attach("/", guard);

        // Start the component.
        component.start();
    }

}