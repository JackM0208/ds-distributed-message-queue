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

        try (Socket socket = new Socket(host, port);
             java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(socket.getOutputStream());
             java.io.ObjectInputStream in = new java.io.ObjectInputStream(socket.getInputStream())) {
            
            logger.info("Successfully connected to the Broker!");
            
            // Create a test MessagePacket
            com.shopee.queue.network.protocol.MessagePacket packet = new com.shopee.queue.network.protocol.MessagePacket(
                "shopee-orders", 
                "Sample Order Data".getBytes(), 
                1001L, 
                0 // Type 0: Publish
            );

            // Send packet
            out.writeObject(packet);
            out.flush();
            logger.info("Sent MessagePacket: {}", packet);

            // Wait for ACK
            Object response = in.readObject();
            if (response instanceof com.shopee.queue.network.protocol.MessagePacket) {
                com.shopee.queue.network.protocol.MessagePacket ack = (com.shopee.queue.network.protocol.MessagePacket) response;
                logger.info("Received ACK from Broker: {}", ack);
            }
            
            logger.info("Closing connection.");
        } catch (Exception e) {
            logger.error("Failed to connect or communicate with Broker: {}", e.getMessage(), e);
        }
    }
}
