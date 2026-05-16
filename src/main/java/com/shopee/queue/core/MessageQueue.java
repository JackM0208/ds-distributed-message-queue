package com.shopee.queue.core;

import com.shopee.queue.network.protocol.MessagePacket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representation of a topic-specific message queue in RAM.
 * Upgraded to act as a "Hot Cache". It stores the latest messages in memory
 * for blazing-fast reads, while relying on the Storage Layer for actual safety.
 */
public class MessageQueue {
    private final String topicName;

    // Limits RAM usage. E.g., 1000 messages * 10 Topics = 10,000 messages in RAM max.
    private static final int MAX_CACHE_SIZE = 1000;

    // A thread-safe, size-bounded map. (Offset -> MessagePacket)
    private final Map<Long, MessagePacket> cache;

    public MessageQueue(String topicName) {
        this.topicName = topicName;

        // CHANGED: The third parameter is now 'true' (accessOrder).
        // This ensures it behaves as an actual LRU Cache.
        this.cache = Collections.synchronizedMap(new LinkedHashMap<Long, MessagePacket>(MAX_CACHE_SIZE + 1, .75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, MessagePacket> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });
    }

    /**
     * Adds a message to the RAM cache.
     */
    public void addMessageToCache(long offset, MessagePacket packet){
        this.cache.put(offset, packet);
    }

    /**
     * Attempts to read a message instantly from RAM.
     * @return MessagePacket if found, null if it was deleted to save space.
     */
    public MessagePacket getMessageFromCache(long offset){
        return this.cache.get(offset);
    }

    public String getTopicName() {
        return topicName;
    }
}