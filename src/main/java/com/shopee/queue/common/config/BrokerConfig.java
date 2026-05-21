package com.shopee.queue.common.config;

/**
 * Broker configuration constants.
 * Holds default values for port, storage limits, and timeouts.
 */
public final class BrokerConfig {
    public static final int DEFAULT_PORT = 8888;
    public static final long MAX_FILE_SIZE = 1024L * 1024 * 1024; // 1GB
    public static final int REQUEST_TIMEOUT_MS = 30000; // 30s

    public static final String[] CLUSTER_NODES = {"broker-1:8888", "broker-2:8888", "broker-3:8888"};
    
    private BrokerConfig() {
        // Prevent instantiation
    }
}
