package com.shopee.queue.core;

/**
 * Representation of a topic-specific message queue.
 * Encapsulates the metadata and logic associated with a single topic.
 */
public class MessageQueue {
    private final String topicName;

    public MessageQueue(String topicName) {
        this.topicName = topicName;
    }

    public String getTopicName() {
        return topicName;
    }
}
