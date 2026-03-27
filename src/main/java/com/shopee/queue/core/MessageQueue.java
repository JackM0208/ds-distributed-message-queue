package com.shopee.queue.core;

/**
 * ROLE: The Logical Queue.
 * WHAT IT DOES: Represents one specific topic. Assigns the auto-incrementing Offset ID to incoming messages.
 * RELATIONSHIPS: Owned by `QueueManager`. Wraps and delegates actual saving to `StorageManager`.
 */
public class MessageQueue {
    private final String topicName;
    // TODO: Maintain current offset
    // TODO: Reference to StorageManager

    public MessageQueue(String topicName) {
        this.topicName = topicName;
    }
}
