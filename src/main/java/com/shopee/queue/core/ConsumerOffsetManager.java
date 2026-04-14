package com.shopee.queue.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and manages consumer progress within the system.
 * Keeps a record of the last consumed message offset for each consumer group.
 */
public class ConsumerOffsetManager {
	private final Map<String, Map<String, Long>> offsetStorage = new ConcurrentHashMap<>();

    /**
     * Updates the saved offset for a given consumer and topic.
     * @param consumerId ID of the consumer.
     * @param topicName Name of the topic.
     * @param offset Last read offset position.
     */
    public void commitOffset(String consumerId, String topicName, long offset) {
    	offsetStorage.computeIfAbsent(topicName, k -> new ConcurrentHashMap<>())
        .put(consumerId, offset);
    }

    /**
     * Retrieves the last saved offset for a given consumer and topic.
     * @param consumerId ID of the consumer.
     * @param topicName Name of the topic.
     * @return long last consumed offset.
     */
    public long getOffset(String consumerId, String topicName) {
    	Map<String, Long> topicOffsets = offsetStorage.get(topicName);
        if (topicOffsets != null && topicOffsets.containsKey(consumerId)) {
            return topicOffsets.get(consumerId);
        }
        // Nếu chưa bao giờ đọc, bắt đầu từ 0
        return 0L;
    }
}
