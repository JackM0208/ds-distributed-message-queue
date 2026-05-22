package com.shopee.queue;

import com.shopee.queue.api.IServer;
import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IStorageManager;
import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.cluster.RaftNodeImpl;
import com.shopee.queue.core.ConsumerOffsetManager;
import com.shopee.queue.core.QueueManagerImpl;
import com.shopee.queue.network.TcpServerImpl;
import com.shopee.queue.storage.StorageManagerImpl;
import com.shopee.queue.network.BrokerWebSocketBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerMain {
    private static final Logger logger = LoggerFactory.getLogger(BrokerMain.class);

    private final IServer server;
    private final IStorageManager storageManager;

    public static BrokerWebSocketBridge bridge;

    public BrokerMain(IServer server, IStorageManager storageManager) {
        this.server = server;
        this.storageManager = storageManager;
    }

    public void start() {
        logger.info("Starting Distributed Message Queue Broker...");
        if (server != null) {
            server.startServer();
        }
    }

    public void shutdown() {
        logger.info("Shutting down Distributed Message Queue Broker...");
        if (server != null) {
            server.stopServer();
        }
        if (storageManager != null) {
            storageManager.shutdown();
        }
        logger.info("Shutdown complete.");
    }

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8888;

        String envNodeId = System.getenv("NODE_ID");
        String envWsPort = System.getenv("WS_PORT");

        String nodeId = (envNodeId != null) ? envNodeId : "broker-1";
        int wsPort = 9001;
        if (envWsPort != null) {
            try {
                wsPort = Integer.parseInt(envWsPort);
            } catch (NumberFormatException e) {
                // Keep default
            }
        } else {
            if (port == 8889) {
                nodeId = "broker-2";
                wsPort = 9002;
            } else if (port == 8890) {
                nodeId = "broker-3";
                wsPort = 9003;
            }
        }

        StorageManagerImpl storageManager = new StorageManagerImpl();
        ConsumerOffsetManager offsetManager = new ConsumerOffsetManager();
        QueueManagerImpl queueManager = new QueueManagerImpl(storageManager, offsetManager);

        // START WEBSOCKET TELEMETRY BRIDGE (Inject queueManager to allow direct web writes)
        try {
            bridge = new BrokerWebSocketBridge(nodeId, wsPort, queueManager);
            bridge.start();
        } catch (Exception e) {
            logger.error("Failed to start WebSocket telemetry bridge: " + e.getMessage());
        }

        RaftNodeImpl clusterNode = new RaftNodeImpl(port, storageManager);
        TcpServerImpl server = new TcpServerImpl(queueManager, clusterNode, port);

        // Periodically queries physical drive and reports actual offsets & log file sizes
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    if (bridge != null) {
                        double fillRatio = storageManager.getActiveSegmentFillRatio("flash_sale_orders");
                        long offset = storageManager.getGlobalOffsetCount("flash_sale_orders");
                        bridge.emitAppend("flash_sale_orders", offset, fillRatio);
                    }
                } catch (Exception e) {
                    // Fail quietly
                }
            }
        }, "InitialStateReporter").start();

        BrokerMain broker = new BrokerMain(server, storageManager);
        broker.start();
    }
}