package org.jboss;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.jms.*;

import java.util.Hashtable;
import java.util.Date;

import org.jboss.soa.esb.actions.AbstractActionLifecycle;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.message.Message;
import org.jboss.soa.esb.actions.ActionLifecycleException;
import org.jboss.soa.esb.ConfigurationException;
import org.apache.log4j.Logger;

public class ForwardJMSMessage extends AbstractActionLifecycle {

    private static final String ID = "clientId";
    private static final String GATEWAY_TIMESTAMP = "GATEWAY_TIMESTAMP";
    private static final String NODE_ID = "NODE_ID";
    private static final String FINAL_MESSAGE = "FINAL_MESSAGE";
    protected ConfigTree  _config;
    private String forwardDestination;
    private String completionDestination;
    private boolean isMessagePersistent = true;
    private Logger log = Logger.getLogger(ForwardJMSMessage.class);
    private Connection connectionObj = null;
    private Session session = null;
    private Destination forwardObj = null;
    private Destination completionObj = null;
    private boolean printDuration = false;
    private MessageProducer mProducer = null;
    private MessageProducer completionProducer = null;

    public ForwardJMSMessage(ConfigTree config) throws ConfigurationException { 
        _config = config; 
        forwardDestination = _config.getRequiredAttribute("forwardDestination");
        completionDestination = _config.getRequiredAttribute("completionDestination");
        String connectionFactoryName = _config.getRequiredAttribute("connectionFactoryName");
        String isMessagePersistentString = _config.getRequiredAttribute("isMessagePersistent");
        String namingProviderUrl = _config.getRequiredAttribute("hornetq.naming.provider.url");
        Context hornetqContext = null;
        try {
            isMessagePersistent = Boolean.parseBoolean(isMessagePersistentString);

            Hashtable env = new Hashtable();
            env.put("java.naming.provider.url", namingProviderUrl);
            env.put("java.naming.security.principal", "admin");
            env.put("java.naming.security.credentials", "admin");
            hornetqContext = new InitialContext(env);

            ConnectionFactory qcf = (ConnectionFactory)hornetqContext.lookup( connectionFactoryName );
            connectionObj = qcf.createConnection();
            session = connectionObj.createSession(false, session.AUTO_ACKNOWLEDGE);
            forwardObj = (Destination) hornetqContext.lookup(forwardDestination);
            completionObj = (Destination) hornetqContext.lookup(completionDestination);

            mProducer = session.createProducer(forwardObj);

            // in this particular use-case, not using either a unique Id nor timestamp on the message
            mProducer.setDisableMessageID(true);
            mProducer.setDisableMessageTimestamp(true);

            if(isMessagePersistent)
                mProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
            else
                mProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            completionProducer = session.createProducer(completionObj);


            if(_config.getAttribute("printDuration") != null)
                printDuration = Boolean.parseBoolean(_config.getAttribute("printDuration"));

            log.info("ForwardJMSMessage() forwardDestination = "+forwardDestination+"    :   cfName = "+connectionFactoryName+"  :   isMessagePersistent = "+isMessagePersistent);
        } catch(Exception x) {
            x.printStackTrace();
        } finally {
            if(hornetqContext != null) {
                try {
                    hornetqContext.close();
                } catch(Exception x) { x.printStackTrace(); }
            }
        }
    }
       
    public Message process(Message esbMessage) throws JMSException, NamingException, Exception {
        int id = 0;
        try {

            id = (Integer)esbMessage.getProperties().getProperty(ID);
            BytesMessage responseMessage = session.createBytesMessage();
            responseMessage.setStringProperty(NODE_ID, (String)esbMessage.getProperties().getProperty(NODE_ID));
            responseMessage.setIntProperty(ID, id);

            // send to the 'forwarding destination' or notify receipt of 'final' trnx
            if(esbMessage.getProperties().getProperty(FINAL_MESSAGE) == null) {
                mProducer.send(responseMessage);
            } else {
                log.info("process() sending notification of final message for client = "+id);
                completionProducer.send(responseMessage);
            }

        } catch(Throwable x) {
            x.printStackTrace();
        } finally {
            if(printDuration && esbMessage.getBody().get(GATEWAY_TIMESTAMP) != null)
                log.fatal("\t"+id+"\t"+(System.currentTimeMillis() - ((Date)esbMessage.getBody().get(GATEWAY_TIMESTAMP)).getTime()));
            return esbMessage;
        }
    }

    public void destroy() throws ActionLifecycleException {
        log.info("destroy()");
        try {
          if(mProducer != null)
            mProducer.close();
          if(completionProducer != null)
            completionProducer.close();
          if(session != null)
            session.close();
          if(connectionObj != null) {
            connectionObj.close();
          }
        } catch(Throwable x) {
          x.printStackTrace();
          throw new ActionLifecycleException(x.getLocalizedMessage());
        }
    }
}
