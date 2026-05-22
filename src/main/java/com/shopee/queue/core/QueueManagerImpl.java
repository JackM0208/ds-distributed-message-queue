package com.shopee.queue.core;

import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IStorageManager;
import com.shopee.queue.cluster.Replicator;
import com.shopee.queue.network.protocol.MessagePacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the Queue Manager.
 * Handles writes and routes replication tasks across cluster peers.
 */
public class QueueManagerImpl implements IQueueManager {

    private final IStorageManager storageManager;
    private final Map<String, MessageQueue> hotCacheMap;
    private final ConsumerOffsetManager offsetManager;
    private final Replicator replicator;

    public QueueManagerImpl(IStorageManager storageManager, ConsumerOffsetManager offsetManager) {
        this.storageManager = storageManager;
        this.hotCacheMap = new ConcurrentHashMap<>();
        this.offsetManager = offsetManager;
        this.replicator = new Replicator();
    }

    @Override
    public void createTopic(String topicName) {
        hotCacheMap.computeIfAbsent(topicName, name -> new MessageQueue(name));
    }

    @Override
    public void pushMessage(String topicName, MessagePacket pushMessage) {
        try {
            // 1. Leader persists the entry locally
            long assignedOffset = storageManager.appendToLog(topicName, pushMessage.getPayload());
            pushMessage.setMessageId(assignedOffset);

            MessageQueue cache = hotCacheMap.computeIfAbsent(topicName, name -> new MessageQueue(name));
            cache.addMessageToCache(assignedOffset, pushMessage);

            // 2. Leader replicates the entry to all followers synchronously
            replicator.pushToFollowers(topicName, assignedOffset, pushMessage.getPayload());

        } catch (Exception e) {
            throw new RuntimeException("Failed to process and replicate write command!", e);
        }
    }

    @Override
    public MessagePacket pullMessage(String topicName, long offset) {
        MessageQueue cache = hotCacheMap.computeIfAbsent(topicName, name -> new MessageQueue(name));

        MessagePacket cachedPacket = cache.getMessageFromCache(offset);
        if (cachedPacket != null) {
            return cachedPacket;
        }

        try {
            byte[] payload = storageManager.readFromOffset(topicName, offset);

            if (payload == null) {
                return null;
            }

            MessagePacket recoveredPacket = new MessagePacket(topicName, payload, offset, 1);
            cache.addMessageToCache(offset, recoveredPacket);
            return recoveredPacket;

        } catch (Exception e) {
            throw new RuntimeException("Failed to read message from disk", e);
        }
    }

    @Override
    public void commitOffset(String consumerId, String topicName, long offset){
        this.offsetManager.commitOffset(consumerId, topicName, offset);
        // FIXED: Replicate committed consumer group offset states across the cluster
        this.replicator.pushOffsetToFollowers(consumerId, topicName, offset);
    }

    @Override
    public long getOffsetForConsumer(String consumerId, String topicName){
        return this.offsetManager.getOffset(consumerId, topicName);
    }
}