package com.shopee.queue.client;

import com.shopee.queue.core.QueueManagerImpl;
import com.shopee.queue.network.protocol.MessagePacket;

/**
 * Client-side SDK for producing messages to the distributed queue.
 * Connects to the broker cluster to send message packets to topics.
 */
public class Producer {
    private String brokerAddress;
    private final QueueManagerImpl queueManager;
    public Producer(String brokerAddress, QueueManagerImpl queueManager) {
        this.brokerAddress = brokerAddress;
        this.queueManager = queueManager;
    }

    /**
     * Sends a message to a specific topic.
     * @param topic Topic to send to.
     * @param message Message payload.
     */
    public void send(String topic, byte[] message) {
    	long messageId = System.currentTimeMillis(); // ID duy nhất
    	MessagePacket packet = new MessagePacket(topic, message, messageId, 0);
    	queueManager.pushMessage(topic, packet);
    	System.out.println("[Producer] Sent Data: " + message + " (ID: " + messageId + ")");
    }
}
