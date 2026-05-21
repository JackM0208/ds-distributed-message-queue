package com.shopee.queue.network;

import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.network.protocol.MessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final IQueueManager queueManager;
    private final Socket clientSocket;

    private final IClusterNode raftNode; 

    public ClientHandler(Socket socket, IQueueManager queueManager, IClusterNode raftNode) {
        this.clientSocket = socket;
        this.queueManager = queueManager;
        this.raftNode = raftNode;
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
                    if (obj instanceof MessagePacket) {
                        MessagePacket packet = (MessagePacket) obj;

                        if (packet.getType() == 0) {
                            queueManager.pushMessage(packet.getTopic(), packet);
                            MessagePacket ack = new MessagePacket(
                                    packet.getTopic(), null, packet.getMessageId(), 2);

                            out.writeObject(ack);
                            out.reset(); // FIX: Clear serialization cache
                            out.flush();

                        } 
                        
                        else if (packet.getType() == 1) {
                            // Consumer asks for a message at a specified offset
                            long requestedOffset = packet.getMessageId();
                            MessagePacket dataPacket =
                                    queueManager.pullMessage(packet.getTopic(), requestedOffset);

                            if (dataPacket != null) {
                                out.writeObject(dataPacket);
                            } else {
                                out.writeObject(new MessagePacket(packet.getTopic(), null, -1, 1));
                            }

                            out.reset(); // FIX: Clear serialization cache
                            out.flush();
                        } 
                        
                        else if (packet.getType() == 2){
                            // Consumer sends ACK. Broker commits offset
                            String topic = packet.getTopic();
                            long procecessedOffset = packet.getMessageId();

                            String consumerId = new String(packet.getPayload(), StandardCharsets.UTF_8);

                            queueManager.commitOffset(consumerId, topic, procecessedOffset);

                            out.writeObject(new MessagePacket(topic, null, procecessedOffset, 2));
                            out.flush();
                        } 
                        
                        else if(packet.getType() == 3){
                            // Consumer asks for the last read offset.
                            String topic = packet.getTopic();
                            String consumerId = new String(packet.getPayload(), StandardCharsets.UTF_8);

                            long savedOffset = queueManager.getOffsetForConsumer(consumerId, topic);

                            MessagePacket response = new MessagePacket(topic, null, savedOffset, 3);
                            out.writeObject(response);
                            out.reset();
                            out.flush();
                        }

                        else if (packet.getType() == 4) { // Nhận yêu cầu bầu cử
                            boolean granted = raftNode.handleVoteRequest(packet.getTerm(), packet.getSenderId());
                            MessagePacket resp = new MessagePacket("cluster", new byte[]{(byte)(granted ? 1 : 0)}, 0, 5);
                            out.writeObject(resp);
                            out.flush();
                        } else if (packet.getType() == 6) { // Nhận Heartbeat
                            raftNode.handleAppendEntries(packet.getTerm(), packet.getSenderId(), packet.getPayload());

                            MessagePacket ack = new MessagePacket("cluster", null, 0, 6);
                            out.writeObject(ack);
                            out.flush();
                            out.reset();
                        }
                    }
                } catch (java.io.EOFException e) {
                    logger.info("Client {} disconnected gracefully.", clientSocket.getRemoteSocketAddress());
                    break;
                } catch (SocketException e) {
                    logger.info("Client {} closed connection (Short-polling hit-and-run).", clientSocket.getRemoteSocketAddress());
                    break;
                } catch (Exception e) {
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