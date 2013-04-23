package edu.umro.DicomTest;

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
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.Directory;

public class SimpleServerGet extends Component implements Runnable {
    private static final int PORT = 8080;

    @Override
    public void run() {
        // Create the HTTP server and listen on port PORT
        SimpleServerGet simpleServer = new SimpleServerGet();
        Server server = new Server(Protocol.HTTP, PORT, simpleServer);
        simpleServer.getClients().add(Protocol.FILE);

        getContext().getParameters().add("maxThreads", "200");
        
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
        response.setEntity("No no - Bad client!  Only do GETs.", MediaType.TEXT_PLAIN);

        try {
            if (request.getMethod() == Method.GET) {
                response.setStatus(Status.SUCCESS_OK);
                String msg = "Hello there";
                response.setEntity(msg, MediaType.TEXT_PLAIN);
                System.out.println("server: " + msg);
                return;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        SimpleServerGet simpleServer = new SimpleServerGet();
        new Thread(simpleServer).start();
        Thread.sleep(200);  // cheap trick to make sure that server is running
        for (int t = 0; t < 100; t++) {
            String urlText = "http://localhost:" + PORT + "/";
            ClientResource clientResource = new ClientResource(urlText);
            clientResource.setReferrerRef(urlText);
            Response response = clientResource.getResponse();

            Representation representation = clientResource.get();
            InputStream inputStream = representation.getStream();
            byte[] buf = new byte[16*1024];
            int b;
            int size = 0;
            while ((b = inputStream.read()) != -1) {
                buf[size++] = (byte)b;
            }
            
            Status status = response.getStatus();
            System.out.println("client count: " + (t+1) + "    status: " + status + "    message: " + new String(buf, 0, size));
        }
        System.exit(0);
    }
}