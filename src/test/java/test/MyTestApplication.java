package test;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.data.Protocol;
import org.restlet.resource.ClientResource;
import org.restlet.routing.Router;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.UniformResource;
import org.restlet.service.StatusService;

class MyStatusService extends StatusService {

    @Override
    public Representation getRepresentation(Status status, Request request, Response response) {
        System.out.println("SUCCESS MyStatusService.getRepresentation(Status status, Request request, Response response");
        System.exit(0);
        return new StringRepresentation("Internal error");
    }

    @Override
    public Status getStatus(Throwable throwable, Request request, Response response) {
        System.out.println("SUCCESS MyStatusService.getStatus(Throwable throwable, Request request, Response response)");
        System.exit(0);
        return super.getStatus(throwable, request, response);
    }

    @Override
    public Status getStatus(Throwable throwable, UniformResource resource) {
        System.out.println("SUCCESS MyStatusService.getStatus(Throwable throwable, UniformResource resource)");
        System.exit(0);
        return super.getStatus(throwable, resource);
    }

}


public class MyTestApplication extends Application {

    /**
     * @param args None required
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        Component c = new Component();
        c.getServers().add(Protocol.HTTP, 8182);
        c.getDefaultHost().attach(new MyTestApplication());
        c.start();
        System.out.println("MyTestApplication started");

        ClientResource cr = new ClientResource("http://localhost:8182/test1");
        cr.setRetryOnError(false);
        try {
            cr.get();
            cr.getResponseEntity().write(System.out);
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }
        c.stop();
        System.out.println("Failed to call any MyStatusService method");
        System.exit(1);
    }


    public MyTestApplication() {
        super();
        setStatusService(new MyStatusService());
    }

    public org.restlet.Restlet createInboundRoot() {
        Router router = new Router(getContext());
        System.out.println("Running test1");
        router.attach("/test1", MyServerResource.class);
        return router;
    };

}
