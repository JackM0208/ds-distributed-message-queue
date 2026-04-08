package com.shopee.queue.client;

/**
 * Client-side SDK for producing messages to the distributed queue.
 * Connects to the broker cluster to send message packets to topics.
 */
public class Producer {
    private String brokerAddress;

    public Producer(String brokerAddress) {
        this.brokerAddress = brokerAddress;
    }

    /**
     * Sends a message to a specific topic.
     * @param topic Topic to send to.
     * @param message Message payload.
     */
    public void send(String topic, byte[] message) {
        // Send logic
    }
}
