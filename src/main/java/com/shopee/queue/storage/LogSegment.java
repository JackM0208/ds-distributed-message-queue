package com.shopee.queue.storage;

/**
 * ROLE: The Muscle (Append-Only Log).
 * WHAT IT DOES: Uses Java NIO to sequentially write raw `MessagePacket` bytes to the physical hard drive.
 * RELATIONSHIPS: Called exclusively by `StorageManager`.
 */
public class LogSegment {
    // TODO: Write MessagePacket bytes to disk using FileChannel
}
