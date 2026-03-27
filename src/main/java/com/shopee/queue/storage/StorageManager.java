package com.shopee.queue.storage;

/**
 * ROLE: The Disk Controller.
 * WHAT IT DOES: Prevents files from growing infinitely. When a log file hits 100MB, it "rolls over" and creates a new one seamlessly.
 * RELATIONSHIPS: Owned by `MessageQueue`. Manages multiple `LogSegment` and `IndexSegment` objects.
 */
public class StorageManager implements IStorageEngine {
    @Override
    public long append(MessagePacket packet) {
        // TODO: Implement append logic (Person 2)
        return 0;
    }

    @Override
    public MessagePacket read(long offset) {
        // TODO: Implement read logic (Person 2)
        return null;
    }

    @Override
    public long getLatestOffset() {
        return 0;
    }

    @Override
    public void close() {
        // TODO: Cleanup resources
    }
}
