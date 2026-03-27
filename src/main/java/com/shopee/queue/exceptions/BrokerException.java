package com.shopee.queue.exceptions;

/**
 * ROLE: Standardized Error Handling.
 * WHAT IT DOES: A custom RuntimeException wrapper.
 * RELATIONSHIPS: Thrown by `StorageManager` (e.g., Disk Full) or `ClientHandler` (Malformed Packet). Caught by `TcpServer` to return a clean error to the client instead of crashing the broker.
 */
public class BrokerException extends RuntimeException {
    public BrokerException(String message) {
        super(message);
    }

    public BrokerException(String message, Throwable cause) {
        super(message, cause);
    }
}
