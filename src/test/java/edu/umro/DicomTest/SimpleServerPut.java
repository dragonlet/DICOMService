package edu.umro.DicomTest;

/** You may copy, modify, and re-use this code as you see fit - Jim Irrer */
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.Server;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.representation.InputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.Directory;

public class SimpleServerPut extends Component implements Runnable {
    private static final int PORT = 8080;

    private static int readToByteArray(InputStream inputStream, byte[] buf) throws IOException {
        int length = 0;
        int b;
        while ((b = inputStream.read()) != -1) {
            buf[length++] = (byte)b;
        }
        return length;
    }
    
    @Override
    public void run() {
        getContext().getParameters().add("maxThreads", "200");
        // Create the HTTP server and listen on port PORT
        SimpleServerPut simpleServer = new SimpleServerPut();
        Server server = new Server(Protocol.HTTP, PORT, simpleServer);
        simpleServer.getClients().add(Protocol.FILE);


        // Create an application  
        Application application = new Application(simpleServer.getContext()) {  
            @Override  
            public Restlet createRoot() {  
                return new Directory(getContext(), "C:");  
            }  
        };
        // Attach the application to the component and start it  
        simpleServer.getDefaultHost().attach("/stuff/", application);
        try {
            server.start();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override  
    public void handle(Request request, Response response) {  
        // assume the worst
        response.setStatus(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
        response.setEntity("No no - Bad client!  Only do PUTs.", MediaType.TEXT_PLAIN);

        try {
            if (request.getMethod() == Method.PUT) {
                InputStream inputStream = request.getEntity().getStream();
                byte[] buf = new byte[64*1024];
                int totalLength = readToByteArray(inputStream, buf);
                response.setStatus(Status.SUCCESS_OK);
                String msg = "Number of bytes received: " + totalLength;
                response.setEntity(msg, MediaType.TEXT_PLAIN);
                System.out.println("server: " + msg);
                return;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String callServer() throws IOException {
        String urlText = "http://localhost:" + PORT + "/";
        ClientResource clientResource = new ClientResource(urlText);
        clientResource.setReferrerRef(urlText);

        byte[] buf = new byte[1000];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte)((int)'a' + (i%26));
        }
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buf);
        Representation representation = new InputRepresentation(byteArrayInputStream, MediaType.APPLICATION_OCTET_STREAM);
        Representation representation2 = clientResource.put(representation);
        byte[] responseBuf = new byte[16*1024];
        int length = readToByteArray(representation2.getStream(), responseBuf);
        Response response = clientResource.getResponse();
        Status status = response.getStatus();
        return "status: " + status + "    message: " + new String(responseBuf, 0, length);
    }
    
    // Start server and call it a bunch of times
    public static void main(String[] args) throws Exception {
        SimpleServerPut simpleServer = new SimpleServerPut();
        new Thread(simpleServer).start();
        Thread.sleep(200);  // cheap trick to make sure that server is running
        // make a bunch of client calls
        for (int t = 0; t < 100; t++) {
            System.out.println("client count: " + (t+1) + "    " + callServer());
        }
        System.exit(0);
    }
}