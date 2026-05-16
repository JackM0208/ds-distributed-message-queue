package com.shopee.queue.client;

import com.shopee.queue.network.protocol.MessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Client-side SDK for consuming messages from the distributed queue.
 */
public class Consumer {
    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);
    private final String brokerHost;
    private final int brokerPort;
    private final String consumerGroup;

    public Consumer(String brokerHost, int brokerPort, String consumerGroup) {
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
        this.consumerGroup = consumerGroup;
    }

    /**
     * Polls the broker for new messages in a topic.
     * @return byte[] message data.
     */
    public byte[] poll(String topic) {
        try (Socket socket = new Socket(brokerHost, brokerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            MessagePacket request = new MessagePacket(topic, null, 0, 1);
            out.writeObject(request);
            out.flush();

            Object response = in.readObject();
            if (response instanceof MessagePacket) {
                MessagePacket packet = (MessagePacket) response;
                if (packet.getPayload() != null) {
                    logger.info("Pulled message from topic: {}", topic);
                    return packet.getPayload();
                }
            }
        } catch (Exception e) {
            logger.error("Error polling message: {}", e.getMessage());
        }
        return null;
    }
}

