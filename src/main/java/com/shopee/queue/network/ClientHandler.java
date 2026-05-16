package com.shopee.queue.network;

import com.shopee.queue.api.IQueueManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final IQueueManager queueManager;
    private final Socket clientSocket;

    public ClientHandler(Socket socket, IQueueManager queueManager) {
        this.clientSocket = socket;
        this.queueManager = queueManager;
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
        ) {
            out.flush(); // Flush headers
            logger.info("Handling request from {}", clientSocket.getRemoteSocketAddress());

            while (!clientSocket.isClosed()) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof com.shopee.queue.network.protocol.MessagePacket) {
                        com.shopee.queue.network.protocol.MessagePacket packet = (com.shopee.queue.network.protocol.MessagePacket) obj;

                        if (packet.getType() == 0) {
                            queueManager.pushMessage(packet.getTopic(), packet);
                            com.shopee.queue.network.protocol.MessagePacket ack = new com.shopee.queue.network.protocol.MessagePacket(
                                    packet.getTopic(), null, packet.getMessageId(), 2);

                            out.writeObject(ack);
                            out.reset(); // FIX: Clear serialization cache
                            out.flush();

                        } else if (packet.getType() == 1) {
                            long requestedOffset = packet.getMessageId();
                            com.shopee.queue.network.protocol.MessagePacket dataPacket =
                                    queueManager.pullMessage(packet.getTopic(), requestedOffset);

                            if (dataPacket != null) {
                                out.writeObject(dataPacket);
                            } else {
                                out.writeObject(new com.shopee.queue.network.protocol.MessagePacket(packet.getTopic(), null, -1, 1));
                            }

                            out.reset(); // FIX: Clear serialization cache
                            out.flush();
                        }
                    }
                } catch (java.io.EOFException e) {
                    logger.info("Client {} disconnected gracefully.", clientSocket.getRemoteSocketAddress());
                    break;
                } catch (Exception e) {
                    // FIX: Print full Stack Trace on server so we aren't blind!
                    logger.error("Connection lost or Server Error for client {}: ", clientSocket.getRemoteSocketAddress(), e);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error setting up streams for {}: ", clientSocket.getRemoteSocketAddress(), e);
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