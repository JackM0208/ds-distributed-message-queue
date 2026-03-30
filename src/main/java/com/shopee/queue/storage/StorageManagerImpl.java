package com.shopee.queue.storage;

import com.shopee.queue.api.IStorageManager;

/**
 * Implementation of the storage manager.
 * Manages the persistence of data segments to disk using sequential log-based 
 * writes for high performance.
 */
public class StorageManagerImpl implements IStorageManager {

    @Override
    public void appendToLog(String topicName, byte[] data) {
        // Log append logic
    }

    @Override
    public byte[] readFromOffset(String topicName, long offset) {
        // Offset read logic
        return new byte[0];
    }
}
