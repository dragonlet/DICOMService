package edu.umro.DicomTest;

import java.io.IOException;
import org.restlet.resource.ClientResource;

public class SecureClient {

    public static void main(String[] args) throws IOException {
        System.setProperty("javax.net.ssl.trustStore"        , "dicom_service.jks");
        System.setProperty("javax.net.ssl.trustStoreType"    , "JKS");

        try {
            ClientResource clientResource = new ClientResource("https://141.214.125.70:8183");
            clientResource.get().write(System.out);
        }
        catch (Exception e) {
            System.out.println("Exception: " + e);
        }
        System.out.println("\n");

        System.out.println("Done");
    }
}