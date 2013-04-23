package edu.umro.DicomTest;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;

import org.restlet.data.MediaType;
import org.restlet.representation.WriterRepresentation;

public class JimXmlRepresentation extends WriterRepresentation {

    public JimXmlRepresentation() {
        super(MediaType.TEXT_HTML);
        System.out.println("JimXmlRepresentation constructor");
    }

    public JimXmlRepresentation(MediaType mediaType) {
        super(mediaType);
        System.out.println("JimXmlRepresentation constructor media type: " + mediaType);
    }

    @Override
    public void write(Writer writer) throws IOException {
        MediaType mediaType = getMediaType();
        if (getMediaType().equals(MediaType.TEXT_PLAIN)) {
            writer.write("JimXmlRepresentation.write TEXT_PLAIN " + new Date());
            return;
        }
        if (getMediaType().equals(MediaType.TEXT_XML)) {
            writer.write("<JJJJ>JimXmlRepresentation.write TEXT_XML " + new Date() + "</JJJJ>");
            return;
        }
        if (getMediaType().equals(MediaType.APPLICATION_XML)) {
            writer.write("<JJJJ>JimXmlRepresentation.write TEXT_XML " + new Date() + "</JJJJ>");
            return;
        }
        writer.write("<em>JimXmlRepresentation.write TEXT_HTML " + new Date() + "</em>");
    }

    /*
    @Override
    public ReadableByteChannel getChannel() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Reader getReader() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InputStream getStream() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void write(Writer writer) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void write(WritableByteChannel writableChannel) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        // TODO Auto-generated method stub

    }
    */

}
