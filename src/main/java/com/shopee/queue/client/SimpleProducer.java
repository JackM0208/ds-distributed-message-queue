package com.shopee.queue.client;

import com.shopee.queue.common.config.BrokerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

/**
 * A simple TCP client to verify connectivity to the Broker.
 */
public class SimpleProducer {
    private static final Logger logger = LoggerFactory.getLogger(SimpleProducer.class);

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = BrokerConfig.DEFAULT_PORT;

        logger.info("Connecting to Broker at {}:{}...", host, port);

        try (Socket socket = new Socket(host, port)) {
            logger.info("Successfully connected to the Broker!");
            
            // Just wait a moment to keep the connection visible in logs
            Thread.sleep(2000);
            
            logger.info("Closing connection.");
        } catch (IOException e) {
            logger.error("Failed to connect to Broker: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
