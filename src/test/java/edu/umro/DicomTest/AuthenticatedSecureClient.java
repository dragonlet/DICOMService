package edu.umro.DicomTest;

import java.io.IOException;

import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.resource.ClientResource;

public class AuthenticatedSecureClient {

    public static void main(String[] args) throws IOException {
        System.setProperty("javax.net.ssl.trustStore"        , "dicom_service.jks");
        System.setProperty("javax.net.ssl.trustStoreType"    , "JKS");

        Reference reference = new Reference("https://141.214.125.70:8184");
        Client client = new Client(Protocol.HTTP);
        Request request = new Request(Method.GET, reference);

        // Send an authenticated request using the Basic authentication scheme.
        ChallengeResponse authentication = new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "gooduser", "goodpass");
        request.setChallengeResponse(authentication);

        // Send the request
        Response response = client.handle(request);
        // Should be 200
        System.out.println(response.getStatus());

        if (response.getStatus().equals(Status.SUCCESS_OK)) {
            System.out.println("Response: " + response.getEntityAsText());
        }

        System.out.println("\n");

        System.out.println("Done");
    }
}