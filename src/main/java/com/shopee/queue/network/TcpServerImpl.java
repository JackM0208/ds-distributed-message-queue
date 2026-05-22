package com.shopee.queue.network;

import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of the network server using Java TCP Sockets.
 * Optimized with a JDK 21 Virtual Thread Executor to prevent heavy
 * thread-creation and memory overhead under concurrent client traffic.
 */
public class TcpServerImpl implements IServer {
    private static final Logger logger = LoggerFactory.getLogger(TcpServerImpl.class);

    private ServerSocket serverSocket;
    private boolean running = false;
    private Thread listenerThread;
    private final IQueueManager queueManager;
    private final IClusterNode raftNode;
    private final int port;

    // Reusable Executor Service for managing ClientHandler tasks
    private ExecutorService connectionExecutor;

    public TcpServerImpl(IQueueManager queueManager, IClusterNode raftNode, int port) {
        this.queueManager = queueManager;
        this.raftNode = raftNode;
        this.port = port;
    }

    @Override
    public void startServer() {
        int port = this.port;
        try {
            serverSocket = new ServerSocket(port);
            running = true;

            // OPTIMIZATION: Initialize virtual thread executor (JDK 21+)
            this.connectionExecutor = Executors.newVirtualThreadPerTaskExecutor();

            logger.info("TCP Server started, listening on port {} (Virtual Thread pool active)", port);

            // Run the acceptance loop in a separate thread
            listenerThread = new Thread(() -> {
                while (running && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        logger.info("New connection established from {}", clientSocket.getRemoteSocketAddress());

                        ClientHandler handler = new ClientHandler(clientSocket, queueManager, raftNode);

                        // OPTIMIZATION: Submit task to virtual thread pool
                        connectionExecutor.submit(handler);

                    } catch (IOException e) {
                        if (running) {
                            logger.error("Error accepting connection: {}", e.getMessage());
                        }
                    }
                }
            }, "ServerListenerThread");

            listenerThread.start();

        } catch (IOException e) {
            logger.error("Could not start server on port {}: {}", port, e.getMessage());
        }
    }

    @Override
    public void stopServer() {
        logger.info("Stopping TCP Server...");
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (connectionExecutor != null && !connectionExecutor.isShutdown()) {
                connectionExecutor.shutdown(); // Gracefully release thread pool resources
            }
        } catch (IOException e) {
            logger.error("Error closing server socket: {}", e.getMessage());
        }
    }
}