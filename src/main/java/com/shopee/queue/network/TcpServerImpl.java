package com.shopee.queue.network;

import com.shopee.queue.api.IServer;
import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.core.ConsumerOffsetManager;
import com.shopee.queue.common.config.BrokerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of the network server using standard Java TCP Sockets.
 * Uses a Thread Pool to handle multiple concurrent client connections.
 */
public class TcpServerImpl implements IServer {
    private static final Logger logger = LoggerFactory.getLogger(TcpServerImpl.class);
    
    private final IQueueManager queueManager;
    private final ConsumerOffsetManager offsetManager;
    private final IClusterNode clusterNode;
    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService clientThreadPool;
    private boolean running = false;
    private Thread listenerThread;

    public TcpServerImpl(IQueueManager queueManager, ConsumerOffsetManager offsetManager, IClusterNode clusterNode, int port) {
        this.queueManager = queueManager;
        this.offsetManager = offsetManager;
        this.clusterNode = clusterNode;
        this.port = port;
        // Sử dụng Thread Pool để tránh việc tạo quá nhiều Thread làm treo hệ thống
        this.clientThreadPool = Executors.newFixedThreadPool(10);
    }


    /**
     * Bắt đầu lắng nghe các kết nối từ Client.
     * Chạy trên một luồng riêng (listenerThread) để không chặn luồng chính.
     */
    @Override
    public void startServer() {

        int port = BrokerConfig.DEFAULT_PORT;
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("TCP Server started, listening on port {}", port);

            listenerThread = new Thread(() -> {
                while (running && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        logger.info("New connection from {}", clientSocket.getRemoteSocketAddress());
                        
                        // Submit handler to thread pool
                        clientThreadPool.execute(new ClientHandler(clientSocket, queueManager, offsetManager, clusterNode));
                        
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
            clientThreadPool.shutdown();
        } catch (IOException e) {
            logger.error("Error closing server socket: {}", e.getMessage());
        }
    }
}

