package com.shopee.queue.network;

import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.api.IServer;
import com.shopee.queue.common.config.BrokerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Implementation of the network server using standard Java TCP Sockets.
 * This server listens on a configurable port and accepts incoming connections
 * from Producers and Consumers, spawning a new ClientHandler for each.
 */
public class TcpServerImpl implements IServer {
    private static final Logger logger = LoggerFactory.getLogger(TcpServerImpl.class);
    
    private ServerSocket serverSocket;
    private boolean running = false;
    private Thread listenerThread;
    private final IQueueManager queueManager; // ADD THIS

    // ADD THIS CONSTRUCTOR
    public TcpServerImpl(IQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    public void startServer() {
        int port = BrokerConfig.DEFAULT_PORT;
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("TCP Server started, listening on port {}", port);

            // Run the acceptance loop in a separate thread
            listenerThread = new Thread(() -> {
                while (running && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        logger.info("New connection established from {}", clientSocket.getRemoteSocketAddress());
                        
                        // Hand over the socket to a ClientHandler
                        ClientHandler handler = new ClientHandler(clientSocket, queueManager);
                        new Thread(handler).start();
                        
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
        } catch (IOException e) {
            logger.error("Error closing server socket: {}", e.getMessage());
        }
    }
}
