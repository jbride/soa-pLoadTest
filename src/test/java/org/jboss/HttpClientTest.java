package org.jboss;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Hashtable;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.commons.io.IOUtils;

/**
 *  HttpClientTest
 *  JA Bride
 *  17 March, 2011
 */
public class HttpClientTest {

    private static final String ID = "clientId";
    private static final String BIND_ADDRESS = "bindAddress";
    private static final String FINAL_MESSAGE = "FINAL_MESSAGE";
    private static final String COMMA = ",";
    private static Logger log = Logger.getLogger(HttpClientTest.class);

    private static int clientCount = 0;
    private static int messagesPerClient = 0;
    private static int rampUpInterval = 0;
    private static boolean logTrnxResult = true;
    private static int totalCount = 0;
    private static long totalDuration = 0;
    private static int timesInvoked = 1;
    private static String targetUrlBase = null;
    private static ExecutorService execObj = null;
    private static BigDecimal bdThousand;
    private static final ConcurrentMap<String, AtomicInteger> serverNodeCountHash = new ConcurrentHashMap<String, AtomicInteger>();

    public static void main(String args[]) {
        try {
            bdThousand = new BigDecimal(1000);
            String clientCountString = System.getProperty("org.jboss.clientCount");
            if(clientCountString == null)
                throw new Exception("clientCount must be an integer");
            clientCount = Integer.parseInt(clientCountString);

            String messagesPerClientString = System.getProperty("org.jboss.messagesPerClient");
            if(messagesPerClientString == null)
                throw new Exception("messagesPerClient must be an integer");
            messagesPerClient = Integer.parseInt(messagesPerClientString);

            String logString = System.getProperty("org.jboss.logTrnxResult");
            if(logString != null)
                logTrnxResult = Boolean.parseBoolean(logString);

            String stString = System.getProperty("org.jboss.sleepTimeMillis");
            int sleepTime = 0;
            if(stString != null)
                sleepTime = Integer.parseInt(stString);

            String rampString = System.getProperty("org.jboss.rampUpInterval");
            if(rampString != null)
                rampUpInterval = Integer.parseInt(rampString);

            String socketString = System.getProperty("org.jboss.httpTest.httpdSocketAddress");
            StringBuilder targetUrlBuilder = new StringBuilder("http://");
            targetUrlBuilder.append(socketString);
            targetUrlBuilder.append("/rest/cclt/inVmMessage/");
            targetUrlBase = targetUrlBuilder.toString();

            log.info("main() targetUrlBase = "+targetUrlBase+"    : rampUpInterval = "+rampUpInterval);

            execObj = Executors.newFixedThreadPool(clientCount);

            for(int t=1; t <= clientCount; t++) {
                Runnable siClient = new HTTPClient(t, sleepTime);
                execObj.execute(siClient);
                Thread.sleep(rampUpInterval);
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
        }
    }

    private static synchronized long computeAverageDuration(long x) {
        totalDuration = totalDuration + x;
        long averageDuration = totalDuration / timesInvoked;
        timesInvoked++;
        return averageDuration;
    }

    private static synchronized int computeTotal(int x) {
        totalCount = totalCount + x;
        return totalCount;
    }

    static class HTTPClient implements Runnable {
        private int id = 0;
        int counter = 0;
        long threadStart = 0L;
        boolean keepWaiting = true;
        String targetUrl = null;
	int sleepTime = 0;
 
        public HTTPClient(int id, int sleepTime) {
            this.id = id;
            this.sleepTime = sleepTime;
            targetUrl = targetUrlBase+id;
        }

        public void run() {
            DefaultHttpClient httpclient = null;
            try {
                HttpGet httpGet = new HttpGet(targetUrl);
                final AtomicInteger completedCount = new AtomicInteger(0);
                threadStart = System.currentTimeMillis();
                for(counter = 1; counter <= messagesPerClient; counter++) {
                    final long sendTime = System.currentTimeMillis();
                    httpclient = new DefaultHttpClient();
                    HttpResponse response = httpclient.execute(httpGet);
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(statusCode != 200) {
                        log.error("HTTP Response with following status code = "+statusCode);
                        Thread.sleep(1000);
                        sleepTime = sleepTime + 10;
                        counter--;
                    }else {
		    	HttpEntity httpEntity = response.getEntity();
                        InputStream contentStream = httpEntity.getContent();
                        String content = IOUtils.toString(contentStream);
                        contentStream.close();
                        int firstComma = content.indexOf(COMMA);
                        String nodeId = content.substring(0, firstComma);
                        serverNodeCountHash.putIfAbsent(nodeId, new AtomicInteger(0));
                        serverNodeCountHash.get(nodeId).incrementAndGet();
                    	if(logTrnxResult)
                     	   log.info("TRNX_COMPLETE:\t"+content+"\t"+counter+"\t"+(System.currentTimeMillis() - sendTime)); 
		    	completedCount.incrementAndGet();
                    }
                    httpclient.getConnectionManager().shutdown();
                    Thread.sleep(sleepTime);
                }
                long duration = (System.currentTimeMillis() - threadStart);
                BigDecimal aveDuration = new BigDecimal(computeAverageDuration(duration)).divide(bdThousand);
                int tCount = computeTotal(completedCount.get());
                log.info("THREAD_COMPLETE!\t"+id+"\t"+tCount+"\t"+duration+"\t"+aveDuration);
            } catch(Throwable x) {
                log.info("run() id  = "+id+"    :   counter = "+counter+"    :    Throwable = "+x.getLocalizedMessage());
		x.printStackTrace();
            } finally {
                try {
                    httpclient.getConnectionManager().shutdown();
                } catch(Exception x) {
                    x.printStackTrace();
                }
            }
        }
    }
}
