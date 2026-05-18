package com.shopee.queue.api;

public interface IStorageManager {
    // Returns the Global Offset (Message ID) and throws Exception if disk fails
    long appendToLog(String topicName, byte[] data) throws Exception;

    // Returns the byte array of the message
    byte[] readFromOffset(String topicName, long offset) throws Exception;

    // Safely closes all files on shutdown
    void shutdown();
}