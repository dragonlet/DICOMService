package edu.umro.DicomTest;

import org.restlet.engine.converter.ConverterHelper;
import org.restlet.resource.ClientResource;
import org.w3c.dom.Document;

public class MediaTypeConverter {

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub
        {
            ClientResource cr = new ClientResource("http://myapi.com/path/resource");
            Document doc = cr.get(Document.class);
            
            ConverterHelper x;
        }

    }

}
