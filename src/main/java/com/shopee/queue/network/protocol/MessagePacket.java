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
    private long term; // Added for Raft Consensus

    /*
    type 0: Producer PUBLISH
    type 1: Consumer POLL
    type 2: ACK
    type 3: Raft VOTE_REQUEST
    type 4: Raft VOTE_RESPONSE
    type 5: Raft APPEND_ENTRIES
    */

    public MessagePacket(String topic, byte[] payload, long messageId, int type){
        this.topic = topic;
        this.payload = payload;
        this.messageId = messageId;
        this.type = type;
        this.timeCreated = System.currentTimeMillis();
        this.term = 0;
    }

    public long getTerm() { return term; }
    public void setTerm(long term) { this.term = term; }


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
