package org.jboss;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.soa.esb.actions.AbstractActionLifecycle;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.message.Message;

import org.apache.log4j.Logger;

public class PrintMessageStuff extends AbstractActionLifecycle {

    private static final String NODE_ID = "NODE_ID";
    private static final String ACTION = "action";
    private static final String COUNT = "COUNT";
    private Logger log = Logger.getLogger(this.getClass());
    private static final String GATEWAY_TIMESTAMP = "GATEWAY_TIMESTAMP";
    private static final AtomicInteger completedCount = new AtomicInteger(0);
    private String nodeId = null;
    
    protected ConfigTree	_config;
    
    public PrintMessageStuff(ConfigTree config) { 
        _config = config;
        nodeId = System.getProperty("java.rmi.server.codebase");
    } 
  
    public Message process(Message message) {
	try {
	    if(message.getBody().get(GATEWAY_TIMESTAMP) == null)
		message.getBody().add(GATEWAY_TIMESTAMP, new Date());

	    log.info("action = "+_config.getAttribute(ACTION) +" : duration = "+(System.currentTimeMillis() - ((Date)message.getBody().get(GATEWAY_TIMESTAMP)).getTime()));
    	    return message;
	} catch(Throwable x) {
                x.printStackTrace();
		return null;
	}
    }

    public Message sayNothing(Message message) {
        return message;
    }

    public Message countMessages(Message message) {
        int count = completedCount.incrementAndGet();
	log.info("count = "+count);
        message.getProperties().setProperty(COUNT, count);
	return message;
    }

    public Message echoActionName(Message message) {
        log.info("action = "+_config.getAttribute(ACTION));
        return message;
    }

    public Message printMessageHeader(Message message) {
        log.info("process = "+ message.getHeader());
        return message;
    }

    public Message printFault(Message message) {
        log.info("printFault() reason = "+ message.getFault().getReason() +" : cause = "+message.getFault().getCause());
        return message;
    }

    public Message addNodeId(Message message) {
        message.getProperties().setProperty(NODE_ID, nodeId);
        return message;
    }
}
