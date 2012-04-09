package org.jboss;

import org.jboss.soa.esb.actions.AbstractActionLifecycle;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.message.Message;
import org.jboss.soa.esb.actions.ActionLifecycleException;
import org.jboss.soa.esb.ConfigurationException;

import org.apache.log4j.Logger;

public class IntroduceDelay extends AbstractActionLifecycle {

    private static final String DELAY_MILLIS = "DELAY_MILLIS";
    private Logger log = Logger.getLogger(this.getClass());
    private int delayMillis = 0;
    
    protected ConfigTree _config;
	  
    public IntroduceDelay(ConfigTree config) throws ConfigurationException{ 
        _config = config; 
        delayMillis = Integer.parseInt(_config.getRequiredAttribute(DELAY_MILLIS));
    } 
  
    public Message process(Message message) throws ActionLifecycleException {
        try {
            Thread.sleep(delayMillis);
        } catch(Exception x) {
            throw new ActionLifecycleException("process() Exception = "+x.getLocalizedMessage());
        }
    	return message;
    }

    public Message synchronizedDelay(Message message)  throws ActionLifecycleException {
        try {
            synchronized(_config) {
                Thread.sleep(delayMillis);
            }
        } catch(Exception x) {
            throw new ActionLifecycleException("synchronizedDelay() Exception = "+x.getLocalizedMessage());
        }
    	return message;
    }
}
