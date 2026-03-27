package com.shopee.queue.core;

/**
 * ROLE: The Crash-Recovery Tracker.
 * WHAT IT DOES: Remembers that "Consumer Group A is currently at offset #500 for shopee-orders". Persists this to a system file.
 * RELATIONSHIPS: Updated by `ClientHandler` when an ACK packet arrives. Read by `ClientHandler` when a consumer reconnects.
 */
public class ConsumerOffsetManager {
    // TODO: Maintain a persistent mapping of (Consumer Group, topic) -> last offset
}
