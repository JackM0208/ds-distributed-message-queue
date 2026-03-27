package com.shopee.queue.core;

/**
 * ROLE: The Traffic Cop (Singleton).
 * WHAT IT DOES: Holds a Thread-Safe map of all active queues (e.g., Map<"shopee-orders", MessageQueue>). Creates new queues on the fly.
 * RELATIONSHIPS: `ClientHandler` asks this class: "Give me the queue named X".
 */
import java.util.Collection;
import java.util.List;

import com.shopee.queue.protocol.MessagePacket;
import com.shopee.queue.storage.IStorageEngine;
import com.shopee.queue.cluster.IConsensusModule;

public class QueueManager implements IQueueService {

    @Override
    public long publish(String topic, MessagePacket packet) {
        return 0;
    }

    @Override
    public Collection<MessagePacket> consume(String topic, String consumerGroup) {
        return List.of();
    }

    @Override
    public void acknowledge(String topic, String consumerGroup, long offset) {

    }

    @Override
    public void ensureTopicExists(String topic) {

    }
}
