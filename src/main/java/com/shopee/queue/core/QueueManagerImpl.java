package com.shopee.queue.core;

import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IStorageManager;
import com.shopee.queue.network.protocol.MessagePacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the Queue Manager.
 * Uses a HYBRID APPROACH: Disk for safety, RAM map for speed.
 */
public class QueueManagerImpl implements IQueueManager {

    private final IStorageManager storageManager;
    private final Map<String, MessageQueue> hotCacheMap;

    public QueueManagerImpl(IStorageManager storageManager) {
        this.storageManager = storageManager;
        this.hotCacheMap = new ConcurrentHashMap<>();
    }

    @Override
    public void createTopic(String topicName) {
        hotCacheMap.computeIfAbsent(topicName, name -> new MessageQueue(name));
    }

    @Override
    public void pushMessage(String topicName, MessagePacket pushMessage) {
        try {
            long assignedOffset = storageManager.appendToLog(topicName, pushMessage.getPayload());
            pushMessage.setMessageId(assignedOffset);

            MessageQueue cache = hotCacheMap.computeIfAbsent(topicName, name -> new MessageQueue(name));
            cache.addMessageToCache(assignedOffset, pushMessage);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process pushed message!", e);
        }
    }

    @Override
    public MessagePacket pullMessage(String topicName, long offset) {
        MessageQueue cache = hotCacheMap.computeIfAbsent(topicName, name -> new MessageQueue(name));

        // STEP 1: Try the blazing-fast RAM Cache
        MessagePacket cachedPacket = cache.getMessageFromCache(offset);
        if (cachedPacket != null) {
            return cachedPacket; // CACHE HIT!
        }

        // STEP 2: CACHE MISS! Fetch from physical hard drive.
        try {
            byte[] payload = storageManager.readFromOffset(topicName, offset);

            if (payload == null) {
                return null; // Message does not exist yet.
            }

            MessagePacket recoveredPacket = new MessagePacket(topicName, payload, offset, 1);

            // FIX: Add the disk result back into the RAM Cache so the next read is fast!
            cache.addMessageToCache(offset, recoveredPacket);

            return recoveredPacket;

        } catch (Exception e) {
            throw new RuntimeException("Failed to read message from disk", e);
        }
    }
}