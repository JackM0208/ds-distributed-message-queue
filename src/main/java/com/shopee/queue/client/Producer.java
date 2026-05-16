package com.shopee.queue.client;

import com.shopee.queue.network.protocol.MessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Client-side SDK for producing messages to the distributed queue.
 */
public class Producer {
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);
    private final String brokerHost;
    private final int brokerPort;

    public Producer(String brokerHost, int brokerPort) {
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
    }

    /**
     * Sends a message to a specific topic.
     */
    public void send(String topic, byte[] message) {
        try (Socket socket = new Socket(brokerHost, brokerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            MessagePacket packet = new MessagePacket(topic, message, System.currentTimeMillis(), 0);
            out.writeObject(packet);
            out.flush();
            logger.info("Sent message to topic: {}", topic);

            // Wait for ACK
            Object response = in.readObject();
            if (response instanceof MessagePacket && ((MessagePacket) response).getType() == 2) {
                logger.info("Received ACK for topic: {}", topic);
            }
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage());
        }
    }
}

