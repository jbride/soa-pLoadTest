package org.jboss;

import org.jboss.soa.esb.actions.AbstractActionLifecycle;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.message.Message;
import org.jboss.soa.esb.actions.ActionLifecycleException;

import org.apache.log4j.Logger;

public class InvokeProblem extends AbstractActionLifecycle {

    private static final String ACTION = "action";
    private Logger log = Logger.getLogger(this.getClass());
    
    protected ConfigTree	_config;
	  
    public InvokeProblem(ConfigTree config) { _config = config; } 
  
    public Message process(Message message) {
    	return message;
    }

    public Message halt(Message message) {
        return null;
    }

    public Message throwCheckedException(Message message) throws Exception{
        throw new Exception("throwCheckedException() intentional checked Exception");
    }
    public Message throwActionLifecycleException(Message message) throws ActionLifecycleException{
        throw new ActionLifecycleException("throwActionLifecycleException() intentional ActionLifecycleException");
    }
    public Message throwRuntimeThrowable(Message message) {
        throw new RuntimeException("throwRuntimeException() intentional RuntimeException");
    }
}
