package edu.umro.DicomTest;

import java.util.List;
import java.util.Map;

import org.restlet.Request;
import org.restlet.Server;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.engine.converter.ConverterHelper;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;


public class ServerResourceConverter extends ServerResource {

    public static void main(String[] args) throws Exception {  
        // Create the HTTP server to listen on port 8185
        Server server = new Server(Protocol.HTTP, 8185, ServerResourceConverter.class);
        
        server.getApplication().createInboundRoot();
        
        List<ConverterHelper> converterList = Engine.getInstance().getRegisteredConverters();

        JimXmlConverter JimXmlConverter = new JimXmlConverter();
        converterList.add(JimXmlConverter);
        
        server.start();  
    }

    private void showHeader() {
        Map<String, Object> reqAttr = getRequestAttributes();
        System.out.println("Request Attributes:");
        for (String key : reqAttr.keySet()) {
            System.out.println("    " + key + " : " + reqAttr.get(key));
        }
    }
    


    @Get("html")
    //@Get("xml")
    //public Document represent() {
    public Representation represent1() {
        //showHeader();
        System.out.println("represent1 get html");

        Form headers = (Form)getRequestAttributes().get("org.restlet.http.headers");
        List<Preference<MediaType>> mtList = Request.getCurrent().getClientInfo().getAcceptedMediaTypes();
        for (Preference<MediaType> pref : mtList) {
            System.out.println("    " + pref);
        }
        String value = headers.getFirstValue("Accept");
        System.out.println("Media type value: " + value);
        MediaType accept = mtList.get(0).getMetadata();
        System.out.println("Media type value: " + value + "    Requested media type: " + accept);
        Representation representation = new JimXmlRepresentation(accept);
        return representation;
    }


    @Get("xml")
    //@Get("xml")
    //public Document represent() {
    public Representation represent2() {
        //showHeader();
        System.out.println("represent2 get xml");

        Form headers = (Form)getRequestAttributes().get("org.restlet.http.headers");
        List<Preference<MediaType>> mtList = Request.getCurrent().getClientInfo().getAcceptedMediaTypes();
        for (Preference<MediaType> pref : mtList) {
            System.out.println("    " + pref);
        }
        String value = headers.getFirstValue("Accept");
        System.out.println("Media type value: " + value);
        MediaType accept = mtList.get(0).getMetadata();
        System.out.println("Media type value: " + value + "    Requested media type: " + accept);
        Representation representation = new JimXmlRepresentation(accept);
        return representation;
    }

}
