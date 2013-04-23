package edu.umro.DicomTest;

import java.io.IOException;
import java.util.List;

import org.restlet.data.MediaType;
import org.restlet.engine.converter.ConverterHelper;
import org.restlet.engine.resource.VariantInfo;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.UniformResource;
import org.w3c.dom.Document;

import edu.umro.util.UMROException;
import edu.umro.util.XML;

public class JimXmlConverter extends ConverterHelper {

    private static final VariantInfo VARIANT_XML = new VariantInfo(
            MediaType.APPLICATION_XML);
    
    @Override
    public List<Class<?>> getObjectClasses(Variant source) {
        List<Class<?>> result = null;

        if (VARIANT_XML.isCompatible(source)) {
            result = addObjectClass(result, Document.class);
        }

        return result;
    }

    
    @Override
    public List<VariantInfo> getVariants(Class<?> source) {
        List<VariantInfo> result = null;

        if (Document.class.isAssignableFrom(source)) {
            result = addVariant(result, VARIANT_XML);
        }
        return result;
    }


    @Override
    public float score(Object source, Variant target, UniformResource resource) {
        float result = -1.0F;

        if (source instanceof Document) {
            result = 0.5F;
            if (MediaType.APPLICATION_XML.isCompatible(target.getMediaType())) {
                result = 1.0F;
            }
        }
        return result;
    }


    @Override
    public <T> float score(Representation source, Class<T> target, UniformResource resource) {
        float result = -1.0F;

        if (target != null) {
            if (Document.class.isAssignableFrom(target)) {
                result = 1.0F;
            }
            else {
                result = 0.5F;
            }
        }
        return result;
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T> T toObject(Representation source, Class<T> target, UniformResource resource) throws IOException {
        Object result = null;

        if (Document.class.isAssignableFrom(target)) {
            try {
                result = XML.parseToDocument(source.getText());
            } catch (UMROException e) {
                IOException ioe = new IOException("Unable to convert String to XML Document");
                ioe.initCause(e);
                throw ioe;
            }
        }

        return (T) result;
    }


    @Override
    public Representation toRepresentation(Object source, Variant target, UniformResource resource) throws IOException {
        Representation result = null;

        if (source instanceof Document) {
            try {
                System.out.println("Converting Document to String");
                result = new StringRepresentation(XML.domToString((Document)source));
            } catch (UMROException e){
                IOException ioe = new IOException("Unable to convert XML Document to String");
                ioe.initCause(e);
                throw ioe;
            }
        }

        return result;
    }
}
