package com.shopee.queue.core;

import com.shopee.queue.api.IQueueManager;

/**
 * Implementation of the Queue Manager.
 * Orchestrates topic management and coordinates message flow between producers 
 * and the storage engine.
 */
public class QueueManagerImpl implements IQueueManager {

    @Override
    public void createTopic(String topicName) {
        // Topic creation logic
    }

    @Override
    public void pushMessage(String topicName, byte[] payload) {
        // Message push logic
    }

    @Override
    public byte[] pullMessage(String topicName, long offset) {
        // Message pull logic
        return new byte[0];
    }
}
