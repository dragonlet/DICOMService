package edu.umro.DicomTest;

import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;


public class FirstServerResource1 extends ServerResource {

    public static void main(String[] args) throws Exception {  
        // Create the HTTP server and listen on port 8185
        new Server(Protocol.HTTP, 8185, FirstServerResource1.class).start();  
     }

     @Get  
     public String toString() {  
        return "hello there, good world";  
     }
}
