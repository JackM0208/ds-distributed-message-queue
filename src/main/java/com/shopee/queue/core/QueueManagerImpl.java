package com.shopee.queue.core;

import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IStorageManager;
import com.shopee.queue.network.protocol.MessagePacket;
import com.shopee.queue.common.exceptions.QueueEx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ điều phối hàng đợi (Queue Manager).
 * Đóng vai trò là "Cảnh sát giao thông" điều phối luồng dữ liệu giữa 
 * Network Layer (Client) và Storage Layer (Ổ cứng).
 */
public class QueueManagerImpl implements IQueueManager {


    private final Map<String, MessageQueue> map = new ConcurrentHashMap<>();
    private final IStorageManager storageManager;

    public QueueManagerImpl(IStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    @Override
    public void createTopic(String topicName) {
        // create a MessageQueue obj if it's a new topic, otherwise do nothing 
        map.computeIfAbsent(topicName, name -> new MessageQueue(name, storageManager));
    }

    @Override
    public void pushMessage(String topicName, MessagePacket pushMessage) {
        MessageQueue queueForThisTopic = map.get(topicName);
        if (queueForThisTopic == null) { 
            throw new QueueEx(topicName); 
        }
        queueForThisTopic.addMessage(pushMessage);
    }

    @Override
    public MessagePacket pullMessage(String topicName, long offset) {
        MessageQueue queueForThisTopic = map.get(topicName);
        if (queueForThisTopic == null) {
            return null;
        }
        return queueForThisTopic.pullMessage(offset);
    }
}

