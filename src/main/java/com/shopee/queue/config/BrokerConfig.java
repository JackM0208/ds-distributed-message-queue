package com.shopee.queue.config;

/**
 * ROLE: Global Configuration (Single Source of Truth).
 * WHAT IT DOES: Holds constants like MAX_SEGMENT_SIZE (100MB), PORT (9092), and DATA_DIR.
 * RELATIONSHIPS: Read by `StorageManager`, `TcpServer`, and `QueueManager`. No hardcoded magic numbers in the logic!
 */
public class BrokerConfig {
    public static final long MAX_SEGMENT_SIZE = 100 * 1024 * 1024; // 100MB
    public static final int PORT = 9092;
    public static final String DATA_DIR = "./data";
}
