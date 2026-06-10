package com.shopee.queue.network;

import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.cluster.RaftNodeImpl;
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
        String selfNodeName = System.getenv("NODE_ID");
        if (selfNodeName == null) {
            selfNodeName = "broker-1"; // Fallback node name
        }

        try (
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
        ) {
            out.flush();
            logger.info("Handling request from {}", clientSocket.getRemoteSocketAddress());

            while (!clientSocket.isClosed()) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof MessagePacket) {
                        MessagePacket packet = (MessagePacket) obj;

                        // 1. LEADERSHIP VALIDATION CHECK: Only Leaders handle produce requests
                        if (packet.getType() == 0) {
                            String activeLeader = raftNode.getLeader();

                            // If we aren't the leader, reject and redirect client with offset value '-2'
                            if (activeLeader == null || !activeLeader.startsWith(selfNodeName)) {
                                logger.warn("[CLUSTER] Rejecting write request. Redirecting client to active leader: {}", activeLeader);
                                MessagePacket redirect = new MessagePacket(packet.getTopic(), null, -2, 2);
                                redirect.setSenderId(activeLeader); // Put leader address inside senderId field
                                out.writeObject(redirect);
                                out.flush();
                                continue;
                            }

                            queueManager.pushMessage(packet.getTopic(), packet);
                            MessagePacket ack = new MessagePacket(
                                    packet.getTopic(), null, packet.getMessageId(), 2);

                            out.writeObject(ack);
                            out.flush();
                        }

                        else if (packet.getType() == 1) {
                            long requestedOffset = packet.getMessageId();
                            MessagePacket dataPacket =
                                    queueManager.pullMessage(packet.getTopic(), requestedOffset);

                            if (dataPacket != null) {
                                out.writeObject(dataPacket);
                            } else {
                                out.writeObject(new MessagePacket(packet.getTopic(), null, -1, 1));
                            }

                            out.reset();
                            out.flush();
                        }

                        // 2. LEADERSHIP VALIDATION CHECK: Offset commits must also route to the leader
                        else if (packet.getType() == 2) {
                            String activeLeader = raftNode.getLeader();
                            if (activeLeader == null || !activeLeader.startsWith(selfNodeName)) {
                                MessagePacket redirect = new MessagePacket(packet.getTopic(), null, -2, 2);
                                redirect.setSenderId(activeLeader);
                                out.writeObject(redirect);
                                out.flush();
                                continue;
                            }

                            String topic = packet.getTopic();
                            long processedOffset = packet.getMessageId();
                            String consumerId = new String(packet.getPayload(), StandardCharsets.UTF_8);

                            queueManager.commitOffset(consumerId, topic, processedOffset);

                            out.writeObject(new MessagePacket(topic, null, processedOffset, 2));
                            out.flush();
                        }

                        // 3. INTERNAL OFFSET REPLICATION (Type 7): Follower writes offset committed by leader
                        else if (packet.getType() == 7) {
                            String topic = packet.getTopic();
                            long processedOffset = packet.getMessageId();
                            String consumerId = new String(packet.getPayload(), StandardCharsets.UTF_8);

                            queueManager.commitOffset(consumerId, topic, processedOffset);

                            MessagePacket ack = new MessagePacket("cluster", null, 0, 7);
                            out.writeObject(ack);
                            out.flush();
                        }

                        else if (packet.getType() == 3) {
                            String topic = packet.getTopic();
                            String consumerId = new String(packet.getPayload(), StandardCharsets.UTF_8);

                            long savedOffset = queueManager.getOffsetForConsumer(consumerId, topic);

                            MessagePacket response = new MessagePacket(topic, null, savedOffset, 3);
                            out.writeObject(response);
                            out.reset();
                            out.flush();
                        }

                        else if (packet.getType() == 4) {
                            boolean granted = raftNode.handleVoteRequest(packet.getTerm(), packet.getSenderId());
                            MessagePacket resp = new MessagePacket("cluster", new byte[]{(byte)(granted ? 1 : 0)}, 0, 5);
                            out.writeObject(resp);
                            out.flush();
                        }

                        else if (packet.getType() == 6) {
                            // FIXED: Execute complete Term validation check first to reset follower election timer

                            // BAD OLD CODE COMMENTED OUT:
                            // raftNode.handleAppendEntries(packet.getTerm(), packet.getSenderId(), null);

                            // NEW CODE DENOTED: Pass the packet's payload (the leader's offset) down to the handleAppendEntries receiver
                            raftNode.handleAppendEntries(packet.getTerm(), packet.getSenderId(), packet.getPayload());

                            if (packet.getPayload() != null && !packet.getTopic().equals("cluster") && raftNode instanceof RaftNodeImpl) {
                                // FIXED: Passed all 5 required parameters to match the RaftNodeImpl signature
                                ((RaftNodeImpl) raftNode).handleAppendEntriesWithData(
                                        packet.getTerm(),
                                        packet.getSenderId(),
                                        packet.getTopic(),
                                        packet.getMessageId(),
                                        packet.getPayload()
                                );
                            }

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
                    logger.info("Client {} closed connection.", clientSocket.getRemoteSocketAddress());
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