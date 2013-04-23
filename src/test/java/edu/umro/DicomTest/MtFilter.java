package edu.umro.DicomTest;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.routing.Filter;

public class MtFilter extends Filter {

    @Override
    public int beforeHandle(Request requst, Response response) {
        System.out.println("MtFilter.beforeHandle doing nothing quickly");
        return CONTINUE;
    }

}
