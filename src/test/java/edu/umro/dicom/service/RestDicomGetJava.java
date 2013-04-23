package edu.umro.dicom.service;

import java.util.Date;
import java.util.HashMap;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Status;


/**
 * Respond to a HTTP GET for DICOM objects.
 * 
 * @author irrer
 *
 */
public class RestDicomGetJava extends Restlet {

    @Override
    public void handle(Request request, Response response) {
        /*
        if (!request.getMethod().equals(Method.PUT)) {
            setError(response, Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, "Only PUT is supported.");
            return;
        }
        response.setEntity(msg, MediaType.TEXT_HTML);
        
        HashMap<String, String> parameterList = Util.getParameterList(request);

        
        
        
        String pacsAETitle = getAETitle(request);
        String seriesId = getSeriesId(request);
        MediaType mediaType = getMediaType(request);
        
        cmoveSeries(pacsAETitle, seriesId);
        */
    }
}
