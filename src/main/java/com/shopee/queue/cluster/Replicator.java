package com.shopee.queue.cluster;

import com.shopee.queue.common.config.BrokerConfig;
import com.shopee.queue.network.protocol.MessagePacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles replication tasks. Sends newly appended log payload blocks and committed consumer
 * offsets to cluster Followers to keep cluster state synchronized.
 */
public class Replicator {
    private static final int TIMEOUT_MS = 1000;

    // Asynchronous thread pool for parallel replication execution
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Replicates a message payload to all operational followers in the cluster.
     */
    public void pushToFollowers(String topic, long offset, byte[] data) {
        String selfNodeId = System.getenv("NODE_ID");
        if (selfNodeId == null) {
            selfNodeId = "broker-1"; // Fallback identifier
        }

        // Query the active consensus Term from the singleton wrapper
        RaftNodeImpl activeNode = RaftNodeImpl.getActiveInstance();
        long currentTerm = (activeNode != null) ? activeNode.getCurrentTerm() : 0;

        for (String target : BrokerConfig.CLUSTER_NODES) {
            String targetNodeName = target.split(":")[0];

            // Skip sending replication requests to self
            if (targetNodeName.equals(selfNodeId)) {
                continue;
            }

            final String targetAddress = target;
            final String senderNodeName = selfNodeId;
            final long termVal = currentTerm;

            // Execute replication task asynchronously in parallel
            executor.submit(() -> {
                String host = targetAddress.split(":")[0];
                int port = 8888; // Default internal broker port

                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                        // Type 6 (AppendEntries packet) containing data payload
                        MessagePacket packet = new MessagePacket(topic, data, offset, 6);
                        packet.setSenderId(senderNodeName);
                        packet.setTerm(termVal); // FIXED: Synchronize the Term index value

                        out.writeObject(packet);
                        out.flush();

                        // Read confirmation response (ACK)
                        in.readObject();

                        // Emit successful websocket telemetry event
                        if (com.shopee.queue.BrokerMain.bridge != null) {
                            com.shopee.queue.BrokerMain.bridge.emitReplication(targetNodeName, offset);
                        }
                    }
                } catch (Exception e) {
                    // Fail quietly if target follower is currently dead/offline
                }
            });
        }
    }

    /**
     * Replicates a committed consumer offset to all followers (Type 7).
     */
    public void pushOffsetToFollowers(String consumerId, String topic, long offset) {
        String selfNodeId = System.getenv("NODE_ID");
        if (selfNodeId == null) {
            selfNodeId = "broker-1";
        }

        RaftNodeImpl activeNode = RaftNodeImpl.getActiveInstance();
        long currentTerm = (activeNode != null) ? activeNode.getCurrentTerm() : 0;

        byte[] payload = consumerId.getBytes(StandardCharsets.UTF_8);

        for (String target : BrokerConfig.CLUSTER_NODES) {
            String targetNodeName = target.split(":")[0];
            if (targetNodeName.equals(selfNodeId)) {
                continue;
            }

            final String targetAddress = target;
            final String senderNodeName = selfNodeId;
            final long termVal = currentTerm;

            executor.submit(() -> {
                String host = targetAddress.split(":")[0];
                int port = 8888;

                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                        // Type 7: Offset Replication Packet
                        MessagePacket packet = new MessagePacket(topic, payload, offset, 7);
                        packet.setSenderId(senderNodeName);
                        packet.setTerm(termVal); // FIXED: Synchronize Term index value

                        out.writeObject(packet);
                        out.flush();

                        in.readObject(); // Read offset replication ACK
                    }
                } catch (Exception e) {
                    // Fail quietly if follower is dead
                }
            });
        }
    }
}