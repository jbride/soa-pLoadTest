package org.jboss;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.InitialContext;

import javax.jms.*;
import javax.naming.Context;

import org.apache.log4j.Logger;

/**
 *  JMSInvokerLoadTest
 *  JA Bride
 *  23 March, 2008
 */
public class JMSInvokerLoadTest {

    private static final String ID = "clientId";
    private static final String BIND_ADDRESS = "bindAddress";
    private static final String NODE_ID = "NODE_ID";
    private static final String FINAL_MESSAGE = "FINAL_MESSAGE";
    private static Logger log = Logger.getLogger(JMSInvokerLoadTest.class);

    private static String cFactoryName = "/ClusteredConnectionFactory";
    private static int clientCount = 0;
    private static int messagesPerClient = 0;
    private static int byteMessageSize = 1;
    private static boolean isPersistent = false;
    private static String gwDObjName = null;
    private static String rDestinationName = null;
    private static String completionQueueName = null;
    private static int sleepTime = 0;
    private static boolean logTrnxResult = true;
    private static boolean blockForTrnxResponse = true;
    private static int totalCount = 0;
    private static Connection connectionObj = null;
    private static Destination rDestination = null;
    private static Destination completionQueue = null;
    private static Destination gwDObj = null;
    private static ExecutorService execObj = null;
    private static BigDecimal bdThousand;
    private static final ConcurrentMap<String, AtomicInteger> serverNodeCountHash = new ConcurrentHashMap<String, AtomicInteger>();

    public static void main(String args[]) {
        try {
            String clientCountString = System.getProperty("org.jboss.clientCount");
            if(clientCountString == null)
                throw new Exception("clientCount must be an integer");
            clientCount = Integer.parseInt(clientCountString);

            String messagesPerClientString = System.getProperty("org.jboss.messagesPerClient");
            if(messagesPerClientString == null)
                throw new Exception("messagesPerClient must be an integer");
            messagesPerClient = Integer.parseInt(messagesPerClientString);

            if(System.getProperty("org.jboss.isPersistent") != null)
                isPersistent = Boolean.parseBoolean(System.getProperty("org.jboss.isPersistent"));

            String byteMessageSizeString = System.getProperty("org.jboss.messageSize");
            if(byteMessageSizeString != null)
                byteMessageSize = Integer.parseInt(byteMessageSizeString);

            String logString = System.getProperty("org.jboss.logTrnxResult");
            if(logString != null)
                logTrnxResult = Boolean.parseBoolean(logString);

            String stString = System.getProperty("org.jboss.sleepTimeMillis");
            if(stString != null)
                sleepTime = Integer.parseInt(stString);

            String wfrString = System.getProperty("org.jboss.blockForTrnxResponse");
            if(wfrString != null)
                blockForTrnxResponse = Boolean.parseBoolean(wfrString);

            cFactoryName = System.getProperty("org.jboss.connectionFactoryName");;
            gwDObjName = System.getProperty("org.jboss.gatewayDestinationName");
            rDestinationName = System.getProperty("org.jboss.forwardDestination");
            completionQueueName = System.getProperty("org.jboss.finalMessageDestination");

            Context jndiContext = new InitialContext();
            ConnectionFactory cFactory = (ConnectionFactory)jndiContext.lookup(cFactoryName);
            gwDObj = (Destination)jndiContext.lookup(gwDObjName);
            rDestination = (Destination)jndiContext.lookup(rDestinationName);
            completionQueue = (Destination)jndiContext.lookup(completionQueueName);

            //session objects have all of the load-balancing and fail-over magic .... only need one Connection object
            connectionObj = cFactory.createConnection();
            connectionObj.setExceptionListener(new ExceptionListener() {
                public void onException(final JMSException e) {
                    log.error("JMSException = "+e.getLocalizedMessage());
                }
            });
            connectionObj.start();

            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append("\n\tclientCount = "+clientCount);
            sBuilder.append("\n\tmessagesPerClient = "+messagesPerClient);
            sBuilder.append("\n\tbyteMessageSize = "+byteMessageSize);
            sBuilder.append("\n\tisPersistent = "+isPersistent);
            sBuilder.append("\n\tgateway destination = "+gwDObjName);
            sBuilder.append("\n\tresponse destination = "+rDestinationName);
            sBuilder.append("\n\tcompletionQueue = "+completionQueueName);
            sBuilder.append("\n\tsleepTime = "+sleepTime);
            sBuilder.append("\n\tlogTrnxResult = "+logTrnxResult);
            sBuilder.append("\n\tblockForTrnxResponse = "+blockForTrnxResponse);
            log.info(sBuilder.toString());

            execObj = Executors.newFixedThreadPool(clientCount);
            for(int t=1; t <= clientCount; t++) {
                Runnable threadObj = new JMSClient(t);
                execObj.execute(threadObj);
            }

            execObj.shutdown();
            execObj.awaitTermination(1200, TimeUnit.MINUTES);
            log.info("main() all tasks completed on ExecutorService .... server node counts as follows : ");
            Iterator nodeIterator = serverNodeCountHash.keySet().iterator();
            while(nodeIterator.hasNext()) {
                String nodeId = (String)nodeIterator.next();
                log.info("\t"+nodeId+"\t"+serverNodeCountHash.get(nodeId));
            }

        } catch(Throwable x) {
            x.printStackTrace();
        } finally {
            //log.info("main() about to close jms connection = "+connectionObj);
            try {
                if(connectionObj != null)
                    connectionObj.close();
            } catch(Exception x) {
                x.printStackTrace();
            }
        }
    }

    private synchronized static int computeTotal(int x) {
        totalCount = totalCount + x;
        return totalCount;
    }

    static class JMSClient implements Runnable, javax.jms.MessageListener {
        private int id = 0;
        int counter = 0;
        long threadStart = 0L;
        boolean keepWaiting = true;
 
        public JMSClient(int id) {
            this.id = id;
        }

        public void onMessage(Message responseMessage) {
            int tCount = computeTotal(counter);
            log.info("THREAD_COMPLETE! "+id+" "+tCount+" "+((System.currentTimeMillis() - threadStart)/1000));
            keepWaiting = false;
        }

        public void run() {
            Session sessionObj = null;
            MessageProducer m_sender = null;
            MessageConsumer m_consumer = null;
            try {

                /*
                    -- client side load balancing :  sessions created from a single JMS Connection load-balance across different nodes of the cluster
                    -- compare with 'symmetric-cluster' example where a connection object is being instantiated for each node in cluster
                */
                sessionObj = connectionObj.createSession(false, Session.AUTO_ACKNOWLEDGE);
                BytesMessage bMessage = sessionObj.createBytesMessage();
                bMessage.setIntProperty(ID, id);
                byte[] bytePayload = new byte[byteMessageSize];
                for(int s=0; s < byteMessageSize; s++) {
                    bytePayload[s] = (byte)s;
                }
                bMessage.writeBytes(bytePayload);
                m_sender = sessionObj.createProducer(gwDObj);
                
                // in this particular use-case, not using either a unique Id nor timestamp on the message
                m_sender.setDisableMessageID(true);
                m_sender.setDisableMessageTimestamp(true);

                if(isPersistent)
                    m_sender.setDeliveryMode(DeliveryMode.PERSISTENT);
                else
                    m_sender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

                if(blockForTrnxResponse) {
                    m_consumer = sessionObj.createConsumer(rDestination, ID+"="+id, false);
                } else {
                    m_consumer = sessionObj.createConsumer(completionQueue, ID+"="+id, false);
                    m_consumer.setMessageListener(this);
                }

                threadStart = System.currentTimeMillis();
                String response = null;
                for(counter = 0; counter < messagesPerClient; counter++) {
                    long originalTime = System.currentTimeMillis();
                    if(!blockForTrnxResponse && counter == (messagesPerClient -1)) {
                        bMessage.setStringProperty(FINAL_MESSAGE, FINAL_MESSAGE);
                    }
                    m_sender.send(bMessage);
                    if(logTrnxResult)
                        log.info("sendMessages() counter = "+counter);
                    else if(counter > 0 && (counter % 100) == 0)
                        log.info("sendMessages() counter = "+counter);

                    // block on receive() method if configured to do so for every trnx
                    if(blockForTrnxResponse) {
                        Message replyMessage = (Message)m_consumer.receive(60000); //blocks for max 60 seconds
                        if(replyMessage != null) {
                            if(logTrnxResult) {
                                long duration = System.currentTimeMillis() - originalTime;
                                log.info("TRNX_COMPLETE!\t"+System.currentTimeMillis()+"\t"+id+"\t"+counter+"\t"+replyMessage.getStringProperty(BIND_ADDRESS)+"\t"+duration);
                            }
                            String nodeId = replyMessage.getStringProperty(NODE_ID);
                            if(nodeId != null) {
                                serverNodeCountHash.putIfAbsent(nodeId, new AtomicInteger(0));
                                serverNodeCountHash.get(nodeId).incrementAndGet();
                            } else {
                                log.warn("run() reply message does not include property : "+NODE_ID);
                            }
                        } else {
                            response = "OH-NO! messageCount = "+counter+"    :    subscriberId = "+id+"    :    replyMessage = null";
                            break;
                        }
                    }
                    Thread.sleep(sleepTime);
                }

                if(blockForTrnxResponse && (response == null)) {
                    int tCount = computeTotal(counter);
                    response = "THREAD_COMPLETE!\t"+id+"\t"+tCount+"\t "+((System.currentTimeMillis() - threadStart)/1000);
                    log.info(response);
                } else if (!blockForTrnxResponse) {
                    while(keepWaiting) {
                        Thread.sleep(1000);
                    }
                } else {
                    log.info(response);
		}
            } catch(Throwable x) {
                log.info("run() id  = "+id+"    :    Throwable = "+x);
                x.printStackTrace();
            } finally {
                try {
                    if(sessionObj != null) {
                        sessionObj.close();
                    }
                } catch(Exception x) {
                    x.printStackTrace();
                }
            }
        }
    }
}
