package com.shopee.queue.core;

import com.shopee.queue.protocol.MessagePacket;
import java.util.Collection;

/**
 * CONTRACT FOR PERSON 3 (Core Logic & Orchestration)
 * The primary interface for publishing and consuming messages.
 */
public interface IQueueService {
    /**
     * Publishes a message to a specific topic.
     * @param topic Name of the target topic.
     * @param packet The message payload.
     * @return The assigned offset of the published message.
     */
    long publish(String topic, MessagePacket packet);

    /**
     * Consumes messages from a specific topic for a consumer group.
     * @param topic Name of the topic.
     * @param consumerGroup ID of the consumer group (for offset tracking).
     * @return A collection of message packets.
     */
    Collection<MessagePacket> consume(String topic, String consumerGroup);

    /**
     * Acknowledges that a message has been processed.
     */
    void acknowledge(String topic, String consumerGroup, long offset);

    /**
     * Checks if a topic exists, otherwise creates it.
     */
    void ensureTopicExists(String topic);
}
