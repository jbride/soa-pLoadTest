package org.jboss;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.jboss.soa.esb.client.ServiceInvoker;
import org.jboss.soa.esb.message.Message;
import org.jboss.soa.esb.message.format.MessageFactory;
import org.jboss.soa.esb.message.format.MessageType;

import org.apache.log4j.Logger;

@Path("/cclt")
public class MessageResource {

    private static final String ID = "clientId";
    private static final String FINAL_MESSAGE = "FINAL_MESSAGE";

    /*  to-do ...set in a properties file that is read at runtime */
    private static final String CATEGORY_NAME = "loadTest";
    private static final String SERVICE_NAME = "invmLoadTest";
    private static final String COUNT = "COUNT";

    private Logger log = Logger.getLogger(MessageResource.class);
    private ServiceInvoker siObj = null;
    private String nodeId = null;

    public MessageResource() throws Exception{
        log.info("constructor() this = "+this);
        siObj =  new ServiceInvoker(CATEGORY_NAME, SERVICE_NAME);
        nodeId = System.getProperty("jboss.bind.address");
    }

    /**
     *  getInVmMessage(int clientId)
     *  sample usage :  curl -v -Haccept:text/plain http://ratwater:8080/rest/cclt/inVmMessage/1
     *  response : <jboss.bind.address>,<total count on server node>,<clientId>
    */
    @GET
    @Path("/inVmMessage/{id}")
    @Consumes("text/plain") @Produces("text/plain")
    public Response getInVmMessage(@PathParam("id") int id) {
        try {
            Message requestMessage = MessageFactory.getInstance().getMessage(MessageType.JBOSS_XML);
            requestMessage.getProperties().setProperty(ID, id);

            Message replyMessage = siObj.deliverSync(requestMessage, 5000);

            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append(nodeId);
            sBuilder.append(",");
            sBuilder.append(replyMessage.getProperties().getProperty(COUNT));
            sBuilder.append(",");
            sBuilder.append(replyMessage.getProperties().getProperty(ID));
            

            ResponseBuilder builder = Response.ok(sBuilder.toString());
            return builder.build();
        } catch(Throwable x) {
            x.printStackTrace();
            return Response.status(500).build();
        }
    }

    /**
     *  getAsynchMessage(int clientId)
     *  sample usage :  curl -v -Haccept:text/plain http://ratwater:8080/rest/cclt/asynchMessage/1
    */
    @GET
    @Path("/asynchMessage/{id}")
    @Consumes("text/plain") @Produces("text/plain")
    public Response getAsynchMessage(@PathParam("id") int id) {
        try {
            ResponseBuilder builder = Response.ok(id);
            return builder.build();
        } catch(Throwable x) {
            x.printStackTrace();
            return Response.status(500).build();
        }
    }

}
