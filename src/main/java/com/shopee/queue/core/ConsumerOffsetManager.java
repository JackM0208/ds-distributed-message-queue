package com.shopee.queue.core;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and manages consumer progress within the system.
 * Keeps a record of the last consumed message offset for each consumer group.
 */
public class ConsumerOffsetManager {
    private final Map<String, Long> offsetMap = new ConcurrentHashMap<>();
    private final String persistencePath = "data/offsets.dat";

    public ConsumerOffsetManager() {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        loadOffsets();
    }


    /**
     * Lưu lại vị trí đã đọc của Consumer.
     * Cơ chế này giúp khách hàng không bao giờ phải đọc lại tin nhắn cũ sau khi restart.
     * 
     * @param consumerId ID hoặc Group của khách hàng
     * @param topicName Tên chủ đề tin nhắn
     * @param offset Vị trí mới cần lưu
     */
    public synchronized void commitOffset(String consumerId, String topicName, long offset) {
        String key = consumerId + ":" + topicName;
        offsetMap.put(key, offset);
        saveOffsets(); // Ghi xuống đĩa cứng để đảm bảo tính bền vững
    }


    /**
     * Retrieves the last saved offset for a given consumer and topic.
     */
    public long getOffset(String consumerId, String topicName) {
        String key = consumerId + ":" + topicName;
        return offsetMap.getOrDefault(key, 0L);
    }

    private void saveOffsets() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(persistencePath))) {
            oos.writeObject(offsetMap);
        } catch (IOException e) {
            // Log error
        }
    }

    @SuppressWarnings("unchecked")
    private void loadOffsets() {
        File file = new File(persistencePath);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Map<String, Long> loaded = (Map<String, Long>) ois.readObject();
                offsetMap.putAll(loaded);
            } catch (Exception e) {
                // Log error
            }
        }
    }
}

