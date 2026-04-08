package com.shopee.queue.core;

/**
 * Tracks and manages consumer progress within the system.
 * Keeps a record of the last consumed message offset for each consumer group.
 */
public class ConsumerOffsetManager {

    /**
     * Updates the saved offset for a given consumer and topic.
     * @param consumerId ID of the consumer.
     * @param topicName Name of the topic.
     * @param offset Last read offset position.
     */
    public void commitOffset(String consumerId, String topicName, long offset) {
        // Offset commit logic
    }

    /**
     * Retrieves the last saved offset for a given consumer and topic.
     * @param consumerId ID of the consumer.
     * @param topicName Name of the topic.
     * @return long last consumed offset.
     */
    public long getOffset(String consumerId, String topicName) {
        // Offset retrieval logic
        return 0L;
    }
}
