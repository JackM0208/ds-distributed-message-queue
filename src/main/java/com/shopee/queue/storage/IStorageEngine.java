package com.shopee.queue.storage;

import com.shopee.queue.protocol.MessagePacket;
import java.io.IOException;

/**
 * CONTRACT FOR PERSON 2 (Storage Engine)
 * Defines how data is persisted and retrieved from disk.
 */
public interface IStorageEngine {
    /**
     * Appends a message to the end of the log.
     * @return The offset assigned to this message.
     */
    long append(MessagePacket packet) throws IOException;

    /**
     * Reads a message at a specific logical offset.
     */
    MessagePacket read(long offset) throws IOException;

    /**
     * Returns the highest offset currently stored.
     */
    long getLatestOffset();

    /**
     * Closes current segments and ensures data is flushed to disk.
     */
    void close() throws IOException;
}
