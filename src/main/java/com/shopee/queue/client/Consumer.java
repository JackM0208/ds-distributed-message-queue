package com.shopee.queue.client;

/**
 * Client-side SDK for consuming messages from the distributed queue.
 * Periodically pulls messages from a given topic and offset.
 */
public class Consumer {
    private String brokerAddress;
    private String consumerGroup;

    public Consumer(String brokerAddress, String consumerGroup) {
        this.brokerAddress = brokerAddress;
        this.consumerGroup = consumerGroup;
    }

    /**
     * Polls the broker for new messages in a topic.
     * @param topic Topic to poll.
     * @return byte[] message data.
     */
    public byte[] poll(String topic) {
        // Poll logic
        return new byte[0];
    }
}
