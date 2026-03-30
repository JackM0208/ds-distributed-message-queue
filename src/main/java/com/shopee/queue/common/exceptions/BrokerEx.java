package com.shopee.queue.common.exceptions;

/**
 * Custom runtime exception for the distributed message queue broker.
 */
public class BrokerEx extends RuntimeException {
    public BrokerEx(String message) {
        super(message);
    }

    public BrokerEx(String message, Throwable cause) {
        super(message, cause);
    }
}
