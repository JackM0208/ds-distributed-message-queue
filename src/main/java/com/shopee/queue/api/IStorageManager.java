package com.shopee.queue.api;

/**
 * Interface for the Storage Manager responsible for managing message persistence.
 * Uses "Append-Only Logs" to maximize write throughput. By only writing at the 
 * end of files, we eliminate random seek times, which is highly efficient for 
 * sequential writes on both HDDs and SSDs.
 */
public interface IStorageManager {

    /**
     * Appends a message to the log for a given topic.
     * @param topicName Name of the topic.
     * @param data Byte array to be stored.
     */
    void appendToLog(String topicName, byte[] data);

    /**
     * Reads message data from a specific offset within a topic's log.
     * @param topicName Name of the topic.
     * @param offset The starting position in the log.
     * @return Byte array containing the message data.
     */
    byte[] readFromOffset(String topicName, long offset);
}
