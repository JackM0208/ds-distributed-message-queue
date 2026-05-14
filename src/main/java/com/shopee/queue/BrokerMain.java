package com.shopee.queue;

import com.shopee.queue.api.IServer;
import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IStorageManager;
import com.shopee.queue.api.IClusterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <h1>BrokerMain - The Assembler</h1>
 * <p>
 * This class is the entry point of the Distributed Message Queue Broker.
 * Its primary responsibility is Dependency Injection and Lifecycle Management.
 * </p>
 * 
 * <h3>The Role of the Assembler:</h3>
 * <ul>
 *     <li><b>Component Orchestration:</b> It wires together the Network Layer (Netty), 
 *         the Core Logic (Queue Management), the Storage Engine (Log/Index), 
 *         and the Consensus Module (Raft).</li>
 *     <li><b>Graceful Shutdown:</b> It ensures that all components are stopped 
 *         in the correct order (e.g., stopping the server before flushing logs).</li>
 *     <li><b>Configuration Loading:</b> It initializes the system based on 
 *         {@code BrokerConfig}.</li>
 * </ul>
 * 
 * <h3>Design Philosophy:</h3>
 * <p>
 * By separating the assembly logic from the business logic, we achieve a 
 * "Plug-and-Play" architecture. For example, we could swap the Raft-based 
 * {@code IClusterNode} with a standalone implementation without touching 
 * the {@code IServer} logic.
 * </p>
 */
public class BrokerMain {
    private static final Logger logger = LoggerFactory.getLogger(BrokerMain.class);

    private final IServer server;
    private final IQueueManager queueManager;
    private final IStorageManager storageManager;
    private final IClusterNode clusterNode;

    /**
     * Constructs the Broker by injecting all major components.
     * 
     * @param server The network server (e.g., Netty implementation)
     * @param queueManager The traffic cop managing topics and offsets
     * @param storageManager The muscle handling physical file I/O
     * @param clusterNode The brain handling Raft consensus
     */
    public BrokerMain(IServer server, 
                      IQueueManager queueManager, 
                      IStorageManager storageManager, 
                      IClusterNode clusterNode) {
        this.server = server;
        this.queueManager = queueManager;
        this.storageManager = storageManager;
        this.clusterNode = clusterNode;
    }

    /**
     * Starts the broker and all its sub-components.
     */
    public void start() {
        logger.info("Starting Distributed Message Queue Broker...");
        // 1. Initialize Storage (e.g., recover segments from disk)
        // 2. Start Cluster Node (e.g., join Raft group)
        
        // 3. Start Server (e.g., bind port 8888)
        if (server != null) {
            server.startServer();
        }
    }

    /**
     * Performs a graceful shutdown of the broker.
     */
    public void shutdown() {
        logger.info("Shutting down Distributed Message Queue Broker...");
        // Order matters: Stop accepting requests -> finish pending tasks -> flush data -> stop cluster
        if (server != null) {
            server.stopServer();
        }
    }

    public static void main(String[] args) {
        // Instantiate implementations
        com.shopee.queue.network.TcpServerImpl server = new com.shopee.queue.network.TcpServerImpl();
        com.shopee.queue.core.QueueManagerImpl queueManager = new com.shopee.queue.core.QueueManagerImpl();
        com.shopee.queue.storage.StorageManagerImpl storageManager = new com.shopee.queue.storage.StorageManagerImpl();
        com.shopee.queue.cluster.RaftNodeImpl clusterNode = new com.shopee.queue.cluster.RaftNodeImpl();

        // Assemble the Broker
        BrokerMain broker = new BrokerMain(server, queueManager, storageManager, clusterNode);

        // Add shutdown hook for graceful exit
        Runtime.getRuntime().addShutdownHook(new Thread(broker::shutdown));

        // Start the engine
        broker.start();
    }
}
