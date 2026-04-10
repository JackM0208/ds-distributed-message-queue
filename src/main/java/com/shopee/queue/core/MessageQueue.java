package com.shopee.queue.core;

import com.shopee.queue.network.protocol.MessagePacket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Representation of a topic-specific message queue.
 * Encapsulates the metadata and logic associated with a single topic.
 */
public class MessageQueue {
    private final String topicName;
    private final BlockingQueue<MessagePacket> queue;

    public MessageQueue(String topicName) {
        this.topicName = topicName;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void addMessage(MessagePacket packet){
        this.queue.offer(packet);
    }

    public MessagePacket pullMessage(){
        return this.queue.poll();
    }

    public String getTopicName() {
        return topicName;
    }
}
