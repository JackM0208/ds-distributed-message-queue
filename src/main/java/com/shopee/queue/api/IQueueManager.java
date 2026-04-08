package com.shopee.queue.api;

/**
 * Interface for the Queue Manager, which acts as the "Traffic Cop" of the system.
 * It coordinates message production and consumption, manages topics, and ensures 
 * messages are correctly routed to the storage engine or distributed across the cluster.
 */
public interface IQueueManager {

    /**
     * Creates a new topic in the message queue.
     * @param topicName Name of the topic.
     */
    void createTopic(String topicName);

    /**
     * Pushes a message payload to a specific topic.
     * @param topicName Name of the topic.
     * @param payload Byte array of the message content.
     */
    void pushMessage(String topicName, byte[] payload);

    /**
     * Pulls a message from a topic starting at a specific offset.
     * @param topicName Name of the topic.
     * @param offset The starting position to read from.
     * @return Byte array containing message data.
     */
    byte[] pullMessage(String topicName, long offset);
}
