package org.jboss;

import java.io.*;
import java.util.Date;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;

import org.jboss.soa.esb.actions.ActionUtils;
import org.jboss.soa.esb.addressing.eprs.JMSEpr;
import org.jboss.soa.esb.helpers.ConfigTree;
import org.jboss.soa.esb.listeners.gateway.DefaultESBPropertiesSetter;
import org.jboss.soa.esb.listeners.gateway.ESBPropertiesSetter;
import org.jboss.soa.esb.listeners.message.AbstractMessageComposer;
import org.jboss.soa.esb.listeners.message.MessageDeliverException;
import org.jboss.soa.esb.message.Body;
import org.jboss.soa.esb.message.Message;
import org.jboss.soa.esb.message.MessagePayloadProxy;
import org.jboss.soa.esb.message.body.content.BytesBody;
import org.jboss.soa.esb.message.format.MessageFactory;
import org.jboss.soa.esb.addressing.eprs.LogicalEPR;
import org.jboss.soa.esb.message.format.MessageType;

import org.apache.log4j.Logger;

/**
 * PURPOSE  :
 *  1)  dictates the faultTo EPR if/when exceptions occur during the action pipeline 
 *  2)  adds an optional timestamp property in the ESB message to facilitate processing time across the length of an action pipeline
 */
public class CustomMessageComposer extends AbstractMessageComposer {

    private static final String SERVICE_CATEGORY = "SERVICE_CATEGORY";
    private static final String SERVICE_NAME = "SERVICE_NAME";
    private static final String SET_TIMESTAMP = "SET_TIMESTAMP";
    private static final String GATEWAY_TIMESTAMP = "GATEWAY_TIMESTAMP"; // timestamp set here as raw JMS message is consumed from the gateway queue
    private ConfigTree config;
    private Logger log = Logger.getLogger(CustomMessageComposer.class);
    private LogicalEPR logicalEPR;
    private ESBPropertiesSetter esbPropertiesStrategy = new DefaultESBPropertiesSetter();
    private MessagePayloadProxy payloadProxy;
    private boolean setTimestamp = false;

    public CustomMessageComposer(ConfigTree config) {
        this.config = config;
        String categoryName = config.getAttribute(SERVICE_CATEGORY);
        String faultToServiceName = config.getAttribute(SERVICE_NAME);
        if (config.getAttribute(SET_TIMESTAMP) != null && Boolean.parseBoolean(config.getAttribute(SET_TIMESTAMP)))
            setTimestamp = true;
        log.info("CustomMessageComposer() categoryName = " + categoryName+ "     : faultToServiceName = " + faultToServiceName+ "  :  setTimestamp = " + setTimestamp);
        logicalEPR = new LogicalEPR(categoryName, faultToServiceName);

        payloadProxy = new MessagePayloadProxy(config);
    }

    @Override
    protected void populateMessage(Message message, Object messagePayload) throws MessageDeliverException {
        log.info("populateMessage() ");
    }

    public Message process(final Object obj) throws JMSException, IOException, MessageDeliverException {
        if (!(obj instanceof javax.jms.Message))
            throw new IllegalArgumentException("Object must be instance of javax.jms.Message");
        final javax.jms.Message jmsMessage = (javax.jms.Message) obj;

        //log.info("process() raw JMS messageId = "+jmsMessage.getJMSMessageID()+"    :   correlationId = "+jmsMessage.getJMSCorrelationID() +"   timestamp = "+jmsMessage.getJMSTimestamp());

        final Message esbMessage = MessageFactory.getInstance().getMessage(MessageType.JAVA_SERIALIZED);

        if (setTimestamp) {
            esbMessage.getBody().add(GATEWAY_TIMESTAMP, new Date());
        }

        setESBMessageBody(jmsMessage, esbMessage);
        esbPropertiesStrategy.setPropertiesFromJMSMessage(jmsMessage, esbMessage);
        esbMessage.getHeader().getCall().setFaultTo(logicalEPR);
        return esbMessage;
    }

    private void setESBMessageBody(final javax.jms.Message fromJMSMessage, final Message toESBMessage) throws JMSException, IOException, MessageDeliverException {
        try {
            byte[] bodyAsBytes = null;
            if (fromJMSMessage instanceof TextMessage) {
                final String text = ((TextMessage) fromJMSMessage).getText();
                payloadProxy.setPayload(toESBMessage, text);
            } else if (fromJMSMessage instanceof BytesMessage) {
                final BytesMessage jBytes = (BytesMessage) fromJMSMessage;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] ba = new byte[1000];
                int iQread;
                while (-1 != (iQread = jBytes.readBytes(ba))) {
                    if (iQread > 0)
                        out.write(ba, 0, iQread);
                    out.close();
                }
                bodyAsBytes = out.toByteArray();
                payloadProxy.setPayload(toESBMessage, bodyAsBytes);


            } else if (fromJMSMessage instanceof ObjectMessage) {
                final Object object = ((ObjectMessage) fromJMSMessage).getObject();
                payloadProxy.setPayload(toESBMessage, object);
            }
          } catch(Exception x) {
              x.printStackTrace();
          } 
    }
}
