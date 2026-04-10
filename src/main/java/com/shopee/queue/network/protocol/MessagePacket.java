package com.shopee.queue.network.protocol;

import java.io.Serializable;

/**
 * Data Transfer Object (DTO) for all messages sent across the network.
 * Includes topic metadata, message payload, and a unique message ID.
 */

public class MessagePacket implements Serializable {

    private static final long serialVersionUID = 1L;

    private String topic;
    private byte[] payload;
    private long messageId;
    private long timeCreated;
    private int type; 

    /*
    type 0: Producer sends this, the payload is containing real data , asking to save data.
    type 1: Consumer sends this, the payload is probably empty, this is just for requesting data.
    type 2: Consumer sends this, it's an ACK.
    */

    public MessagePacket(String topic, byte[] payload, long messageId, int type){
        this.topic = topic;
        this.payload = payload;
        this.messageId = messageId;
        this.type = type;
        this.timeCreated = System.currentTimeMillis();
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public byte[] getPayload() { return payload; }
    public void setPayload(byte[] payload) { this.payload = payload; }

    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }
    
    public void setType(int type){  this.type = type; }
    public int getType(){   return this.type; }

    public long getTimeCreated() { return this.timeCreated; }

    @Override 
    public String toString(){
        return "MessagePacket{" + 
               "id = " + messageId + System.lineSeparator() + 
               "topic = " + topic + System.lineSeparator() + 
               "type = " + type + System.lineSeparator() + 
               "payloadSize = " + (payload != null ? payload.length : 0) + " bytes" + 
               "}";
    }
}
