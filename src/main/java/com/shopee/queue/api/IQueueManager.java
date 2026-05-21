package com.shopee.queue.api;

import com.shopee.queue.network.protocol.MessagePacket;
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
     * @param payload a MessagePacket object of the message content.
     */
    
    void pushMessage(String topicName, MessagePacket pushMessage);

    /**
     * Pulls a message from a topic starting at a specific offset.
     * @param topicName Name of the topic.
     * @param offset The starting position to read from.
     * @return a MessagePacket with topic name wanted
     */

    MessagePacket pullMessage(String topicName, long offset);

    void commitOffset(String consumerId, String topicName, long offset);

    long getOffsetForConsumer(String consumerId, String topicName);
}
