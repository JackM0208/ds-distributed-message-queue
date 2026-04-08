package com.shopee.queue.network.protocol;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) for all messages sent across the network.
 * Includes topic metadata, message payload, and a unique message ID.
 */
public class MessagePacket implements Serializable {
    private String topic;
    private byte[] payload;
    private long messageId;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }
}
