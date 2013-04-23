package edu.umro.DicomTest;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.engine.http.connector.BaseClientHelper;
import org.restlet.resource.ClientResource;


/**
 * Host name verifier that does not perform any checks.
 */
class NullHostnameVerifier implements HostnameVerifier {
    public boolean verify(String hostname, SSLSession session) {
        try {
            //sun.security.util.HostNameChecker h;
            Certificate[] certificateList = session.getPeerCertificates();
            for (Certificate certificate : certificateList) {
                if (certificate instanceof X509Certificate) {
                    X509Certificate x509 = (X509Certificate)certificate;
                    try {
                        Collection<List<?>> sanList = x509.getSubjectAlternativeNames();
                        for (List<?> san : sanList) {
                            Object o = san;
                            o = san;
                            System.out.println("    " + san);
                            for (Object os : san) {
                                Object thing = os;
                                thing = os;
                                System.out.println("        " + thing);
                            }
                        }
                    } catch (CertificateParsingException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        } catch (SSLPeerUnverifiedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return true;  // succeed no matter what
    }
}

public class DicomServiceAuthenticatedSecureClient {

    public static void main(String[] args) throws IOException {
        
        {
            HttpsURLConnection.setDefaultHostnameVerifier(new NullHostnameVerifier());
        }
        
        
        System.setProperty("javax.net.ssl.trustStore"        , "src/main/resources/security/dicomsvc.jks");
        System.setProperty("javax.net.ssl.trustStoreType"    , "JKS");

        Reference reference = new Reference("https://141.214.125.70:8091");
        Client client = new Client(Protocol.HTTP);
        Request request = new Request(Method.GET, reference);

        // Send an authenticated request using the Basic authentication scheme.
        ChallengeResponse authentication = new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "SysAdmin", "SysAdmin");
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