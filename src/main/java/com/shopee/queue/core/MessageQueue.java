package com.shopee.queue.core;

import com.shopee.queue.api.IStorageManager;
import com.shopee.queue.network.protocol.MessagePacket;

import java.io.IOException;

/**
 * Representation of a topic-specific message queue.
 * Links the high-level topic logic with the physical Storage Manager.
 */
public class MessageQueue {
    private final String topicName;
    private final IStorageManager storageManager;

    public MessageQueue(String topicName, IStorageManager storageManager) {
        this.topicName = topicName;
        this.storageManager = storageManager;
    }

    public void addMessage(MessagePacket packet) {
        try {
            storageManager.appendToLog(topicName, packet.getPayload());
        } catch (IOException e) {
            // Handle storage error
        }
    }

    public MessagePacket pullMessage(long offset) {
        try {
            byte[] data = storageManager.readFromOffset(topicName, offset);
            if (data == null) return null;
            return new MessagePacket(topicName, data, offset, 1); // type 1 = Consume
        } catch (IOException e) {
            return null;
        }
    }

    public String getTopicName() {
        return topicName;
    }
}

