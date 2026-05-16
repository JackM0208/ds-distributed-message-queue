package com.shopee.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates a cluster of 3 Brokers to test Raft Leader Election.
 */
public class ClusterStressTest {
    private static final Logger logger = LoggerFactory.getLogger(ClusterStressTest.class);

    public static void main(String[] args) throws InterruptedException {
        logger.info("Starting Cluster Stress Test with 3 Brokers...");

        // Start Broker 1 (Port 8888)
        startBroker(8888, "Broker-A");

        // Start Broker 2 (Port 8889)
        startBroker(8889, "Broker-B");

        // Start Broker 3 (Port 8890)
        startBroker(8890, "Broker-C");

        logger.info("All brokers requested to start. Watch the logs for election results!");
        
        // Keep the main thread alive
        Thread.sleep(60000);
        logger.info("Cluster Stress Test completed.");
        System.exit(0);
    }

    private static void startBroker(int port, String name) {
        new Thread(() -> {
            logger.info("Launching {} on port {}", name, port);
            BrokerMain.main(new String[]{String.valueOf(port)});
        }, name).start();
    }
}
