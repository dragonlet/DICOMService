package test;

import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.restlet.representation.OutputRepresentation;
import org.restlet.resource.ResourceException;

class MyRepresentation extends OutputRepresentation {

    public MyRepresentation(MediaType mediaType, long size) {
        super(mediaType, size);
    }
    
    @Override
    public ReadableByteChannel getChannel() throws IOException {
        System.out.println("MyRepresentation.getChannel()");
        return null;
    }

    @Override
    public Reader getReader() throws IOException {
        System.out.println("MyRepresentation.getReader()");
        return null;
    }

    @Override
    public InputStream getStream() throws IOException {
        System.out.println("MyRepresentation.getStream()");
        return null;
    }

    @Override
    public void write(Writer writer) throws IOException {
        System.out.println("MyRepresentation.write(Writer writer");
        throw new ResourceException(Status.SERVER_ERROR_VERSION_NOT_SUPPORTED);
    }

    @Override
    public void write(WritableByteChannel writableChannel) throws IOException {
        System.out.println("MyRepresentation.write(WritableByteChannel writableChannel)");
        throw new ResourceException(Status.SERVER_ERROR_VERSION_NOT_SUPPORTED);
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        System.out.println("MyRepresentation.write(OutputStream outputStream)");
        throw new ResourceException(Status.SERVER_ERROR_VERSION_NOT_SUPPORTED);
    }
}


public class MyServerResource extends ServerResource {

    @Get
    public String toText() throws Exception {
        this.getResponse().setStatus(Status.SUCCESS_OK);
        this.getResponse().setEntity(new MyRepresentation(MediaType.APPLICATION_ZIP, -1));
        return "return from MyServerResource.toText()";
    }

}
