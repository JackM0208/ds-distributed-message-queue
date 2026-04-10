package com.shopee.queue.common.exceptions;

import java.lang.RuntimeException;

public class QueueEx extends RuntimeException {

    public QueueEx(String topicName){
        super("Topic: " + topicName + " does not exist on this Broker! Please try to create the topic first using createTopic() .");
    }
    
}
