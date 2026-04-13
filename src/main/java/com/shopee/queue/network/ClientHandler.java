package com.shopee.queue.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;

/**
 * Handler for individual client connections.
 * This class runs in its own thread and manages the lifecycle of a single 
 * TCP connection from a Producer or Consumer.
 */
public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    
    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try {
            logger.info("Handling request from {}", clientSocket.getRemoteSocketAddress());
            
            // For now, we just keep the connection open or close it immediately
            // In the future, this will use ObjectInputStream to read MessagePackets
            
            // To simulate work, we'll keep the connection open for a bit
            // Thread.sleep(1000); 

        } catch (Exception e) {
            logger.error("Error in ClientHandler for {}: {}", 
                         clientSocket.getRemoteSocketAddress(), e.getMessage());
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                    logger.info("Connection with {} closed.", clientSocket.getRemoteSocketAddress());
                }
            } catch (IOException e) {
                logger.error("Error closing client socket: {}", e.getMessage());
            }
        }
    }
}
