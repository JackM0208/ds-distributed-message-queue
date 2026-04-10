package com.shopee.queue.core;

import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.network.protocol.MessagePacket;
import com.shopee.queue.common.exceptions.QueueEx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the Queue Manager.
 * Orchestrates topic management and coordinates message flow between producers 
 * and the storage engine.
 */
public class QueueManagerImpl implements IQueueManager {

    private Map<String, MessageQueue> map = new ConcurrentHashMap<>();

    @Override
    public void createTopic(String topicName){

        // create a MessageQueue obj if it's a new topic, otherwise do nothing 
        map.computeIfAbsent(topicName, name -> new MessageQueue(name));
    }

    @Override
    public void pushMessage(String topicName, MessagePacket pushMessage){
        
        MessageQueue queueForThisTopic = map.get(topicName);

        if(queueForThisTopic == null){ 
            throw new QueueEx(topicName); 
        }
        
        queueForThisTopic.addMessage(pushMessage);
    }

    @Override
    public MessagePacket pullMessage(String topicName, long offset){

        // hiện tại chưa implement tính năng đọc theo offset. Cần hoàn thiện phần storage
        MessageQueue queueForThisTopic = map.get(topicName);
        if(queueForThisTopic == null){
            return null;
        }

        return queueForThisTopic.pullMessage();
        
    }
}
