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
        try (
            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(clientSocket.getOutputStream());
            java.io.ObjectInputStream in = new java.io.ObjectInputStream(clientSocket.getInputStream())
        ) {
            logger.info("Handling request from {}", clientSocket.getRemoteSocketAddress());
            
            while (!clientSocket.isClosed()) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof com.shopee.queue.network.protocol.MessagePacket) {
                        com.shopee.queue.network.protocol.MessagePacket packet = (com.shopee.queue.network.protocol.MessagePacket) obj;
                        logger.info("Received MessagePacket: {}", packet);
                        
                        // Send ACK back
                        com.shopee.queue.network.protocol.MessagePacket ack = new com.shopee.queue.network.protocol.MessagePacket(
                            packet.getTopic(), 
                            null, 
                            packet.getMessageId(), 
                            2 // type 2 = ACK
                        );
                        out.writeObject(ack);
                        out.flush();
                        logger.info("Sent ACK for Message ID: {}", packet.getMessageId());
                    }
                } catch (java.io.EOFException e) {
                    logger.info("Client {} disconnected gracefully.", clientSocket.getRemoteSocketAddress());
                    break;
                }
            }
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
