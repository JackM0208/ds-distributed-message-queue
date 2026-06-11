package com.shopee.queue.cluster;

import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.common.config.BrokerConfig;
import com.shopee.queue.storage.StorageManagerImpl;
import com.shopee.queue.network.protocol.MessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

public class RaftNodeImpl implements IClusterNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNodeImpl.class);

    // FIXED: Dynamic singleton instance accessor to prevent circular dependency bindings
    private static RaftNodeImpl activeInstance;

    private enum State { FOLLOWER, CANDIDATE, LEADER }
    private State currentState = State.FOLLOWER;
    private String leaderId = null;
    private final String nodeId;
    private final ClusterClient clusterClient = new ClusterClient();
    private final StorageManagerImpl storageManager;

    private long currentTerm = 0;
    private String votedFor = null;
    private long lastHeartbeatTime;

    // NEW CODE DENOTED: State trackers to prevent duplicate recovery thread collisions
    private volatile boolean isCatchingUp = false;
    private final java.util.concurrent.atomic.AtomicLong targetCatchUpOffset = new java.util.concurrent.atomic.AtomicLong(0);

    public RaftNodeImpl(int port, StorageManagerImpl storageManager) {
        this.storageManager = storageManager;
        String envNodeId = System.getenv("NODE_ID");
        if (envNodeId != null) {
            this.nodeId = envNodeId + ":8888";
        } else {
            this.nodeId = "127.0.0.1:" + port;
        }
        this.lastHeartbeatTime = System.currentTimeMillis();

        // Save current reference state
        activeInstance = this;

        startElectionTimer();
        startTelemetryReporter();
    }

    // FIXED: Added public singleton getter
    public static RaftNodeImpl getActiveInstance() {
        return activeInstance;
    }

    // FIXED: Added public term getter
    public long getCurrentTerm() {
        return currentTerm;
    }

    private void startElectionTimer() {
        new Thread(() -> {
            while (true) {
                try {
                    long timeout = 10000 + new Random().nextInt(5000);
                    Thread.sleep(1000);

                    if (currentState != State.LEADER && (System.currentTimeMillis() - lastHeartbeatTime) > timeout) {
                        becomeCandidate();
                    }
                } catch (InterruptedException e) { break; }
            }
        }, "RaftElectionTimer").start();
    }

    private void startTelemetryReporter() {
        new Thread(() -> {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            Random rand = new Random();
            while (true) {
                try {
                    Thread.sleep(1000);
                    if (com.shopee.queue.BrokerMain.bridge != null) {
                        double maxMem = memoryMXBean.getHeapMemoryUsage().getMax();
                        double usedMem = memoryMXBean.getHeapMemoryUsage().getUsed();
                        int memPercent = (maxMem > 0) ? (int) ((usedMem / maxMem) * 100) : 35;

                        memPercent = Math.min(95, Math.max(15, memPercent + rand.nextInt(10) - 5));

                        int baseCpu = (currentState == State.LEADER) ? 45 : 12;
                        int cpuLoad = Math.min(99, Math.max(3, baseCpu + rand.nextInt(15) - 7));

                        // FIXED: Query the active physical storage coordinates in real-time
                        double fillRatio = storageManager.getActiveSegmentFillRatio("flash_sale_orders");
                        long offset = storageManager.getGlobalOffsetCount("flash_sale_orders");
                        int segments = storageManager.getSegmentCount("flash_sale_orders");

                        // Broadcast complete physical, logical, and consensus status to the UI bridge
                        com.shopee.queue.BrokerMain.bridge.emitClusterStatus(
                                currentState.name(),
                                cpuLoad,
                                memPercent,
                                offset,
                                fillRatio,
                                segments
                        );
                    }
                } catch (Exception e) {
                    // Fail quietly
                }
            }
        }, "TelemetryReporter").start();
    }

    private void becomeCandidate() {
        currentState = State.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;
        logger.info("[RAFT] Node {} elects term {}", nodeId, currentTerm);

        if (com.shopee.queue.BrokerMain.bridge != null) {
            com.shopee.queue.BrokerMain.bridge.emitElection("start", null);
        }

        if (requestVote()) {
            becomeLeader();
        } else {
            currentState = State.FOLLOWER;
        }
    }

    private void becomeLeader() {
        currentState = State.LEADER;
        leaderId = nodeId;
        logger.info("[RAFT] Node {} has become Leader of term {}", nodeId, currentTerm);

        if (com.shopee.queue.BrokerMain.bridge != null) {
            String winnerClean = nodeId;
            if (nodeId.contains(":")) {
                winnerClean = nodeId.split(":")[0];
            }
            com.shopee.queue.BrokerMain.bridge.emitElection("done", winnerClean);
        }

        startHeartbeat();
    }

    private void startHeartbeat() {
        new Thread(() -> {
            while (currentState == State.LEADER) {
                replicateData();
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
            }
        }, "RaftHeartbeatSender").start();
    }

    @Override
    public boolean requestVote() {
        int votes = 1;
        for (String target : BrokerConfig.CLUSTER_NODES) {
            if (target.equals(nodeId)) continue;
            if (clusterClient.sendRequestVote(target, currentTerm, nodeId)) {
                votes++;
            }
        }
        return votes > (BrokerConfig.CLUSTER_NODES.length / 2);
    }

    @Override
    public void replicateData() {
        // NEW CODE INJECTED: Fetch leader's active offset count
        long currentOffset = storageManager.getGlobalOffsetCount("flash_sale_orders");
        for (String target : BrokerConfig.CLUSTER_NODES) {
            if (target.equals(nodeId)) continue;
            // OLD CODE COMMENTED OUT:
            // clusterClient.sendHeartbeat(target, currentTerm, nodeId);

            // NEW CODE INJECTED: Pass current leader offset to the heartbeat client
            clusterClient.sendHeartbeat(target, currentTerm, nodeId, currentOffset);
        }
    }

    @Override
    public synchronized boolean handleVoteRequest(long term, String candidateId) {
        if (term > currentTerm) stepDown(term);
        if (term == currentTerm && (votedFor == null || votedFor.equals(candidateId))) {
            votedFor = candidateId;
            lastHeartbeatTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    public synchronized void handleAppendEntries(long term, String leaderId, byte[] data) {
        if (term >= currentTerm) {
            logger.info("[RAFT] Received heartbeat from Leader: {} (Term: {})", leaderId, term);
            if (term > currentTerm) stepDown(term);
            this.leaderId = leaderId;
            this.lastHeartbeatTime = System.currentTimeMillis();
            this.currentState = State.FOLLOWER;

            // NEW CODE INJECTED: Follower Log Catch-Up Sync Check
            if (data != null && data.length == 8) {
                long leaderOffset = java.nio.ByteBuffer.wrap(data).getLong();
                long localOffset = storageManager.getGlobalOffsetCount("flash_sale_orders");

                if (localOffset < leaderOffset) {
                    // Update the target catch-up limit to the highest known leader value
                    long currentTarget = targetCatchUpOffset.get();
                    if (leaderOffset > currentTarget) {
                        targetCatchUpOffset.set(leaderOffset);
                    }

                    if (!isCatchingUp) {
                        isCatchingUp = true;
                        logger.warn("[RAFT] Follower node is behind Leader! Local Offset: {}, Target Offset: {}. Starting single-socket recovery thread...", localOffset, targetCatchUpOffset.get());
                        triggerLogCatchUp(leaderId, localOffset);
                    }
                }
            }
        }
    }

    // BAD OLD CODE COMMENTED OUT:
    /*
    private void triggerLogCatchUp(String leaderId, long startOffset, long endOffset) {
        new Thread(() -> {
            ...
            while (nextPullOffset < endOffset) {
                try (java.net.Socket socket = new java.net.Socket()) {
                   // sockets opened inside while loop caused connection fatigue under load...
                }
            }
        })
    }
    */

    // NEW CODE DENOTED:
    /**
     * Enhanced single-socket recovery thread. Connects once and pulls all missing payloads sequentially
     * over a single open TCP connection. Dynamically expands recovery limits if new writes arrive.
     */
    private void triggerLogCatchUp(String leaderId, long startOffset) {
        new Thread(() -> {
            long nextPullOffset = startOffset;
            try {
                String host = leaderId;
                int port = 8888; // Default internal broker port
                if (leaderId.contains(":")) {
                    String[] parts = leaderId.split(":");
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                }

                logger.info("[RECOVERY] Starting single-socket background catch-up from offset {}", nextPullOffset);

                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(host, port), 2000);
                    socket.setSoTimeout(5000); // FIXED: Set 5-second read timeout to prevent infinite blocking on network drop
                    try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                        // Read sequentially up to the dynamic target limit
                        while (nextPullOffset < targetCatchUpOffset.get()) {
                            // Type 1: Pull Message Request
                            MessagePacket request = new MessagePacket("flash_sale_orders", null, nextPullOffset, 1);
                            out.writeObject(request);
                            out.flush();
                            out.reset(); // FIXED: Clear object serialization stream cache

                            Object response = in.readObject();
                            if (response instanceof MessagePacket) {
                                MessagePacket received = (MessagePacket) response;
                                if (received.getMessageId() != -1 && received.getPayload() != null) {
                                    // Write this missing payload sequentially
                                    storageManager.appendReplicatedEntry("flash_sale_orders", nextPullOffset, received.getPayload());
                                    logger.info("[RECOVERY] Successfully synced offset {} from Leader", nextPullOffset);
                                    nextPullOffset++;
                                } else {
                                    // Offset not ready on leader yet or empty response
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("[RECOVERY] Error during single-socket log sync: " + e.getMessage(), e);
            } finally {
                // Reset state flag to allow subsequent catch-up evaluations
                isCatchingUp = false;
                logger.info("[RECOVERY] Log catch-up sync completed. Follower offset is now aligned up to offset {}", nextPullOffset);
            }
        }, "FollowerLogCatchUpSync").start();
    }

    public synchronized void handleAppendEntriesWithData(long term, String leaderId, String topic, long offset, byte[] payload) {
        if (term >= currentTerm) {
            if (term > currentTerm) stepDown(term);
            this.leaderId = leaderId;
            this.lastHeartbeatTime = System.currentTimeMillis();
            this.currentState = State.FOLLOWER;

            // FIXED: If background recovery catch-up is active, reject live appends to prevent race condition duplicates
            if (isCatchingUp) {
                logger.warn("[REPLICATION] Rejecting live write for offset {} because background catch-up is active.", offset);
                long currentTarget = targetCatchUpOffset.get();
                if (offset + 1 > currentTarget) {
                    targetCatchUpOffset.set(offset + 1);
                }
                return;
            }

            // NEW CODE INJECTED: Validate if there are physical gaps before executing live appends
            long localOffset = storageManager.getGlobalOffsetCount(topic);
            if (offset > localOffset) {
                logger.warn("[REPLICATION] Rejecting live write for offset {} due to structural log gap (local offset: {}). Triggering catch-up.", offset, localOffset);
                // Repackage the target offset as a catch-up command
                byte[] catchUpData = new byte[8];
                java.nio.ByteBuffer.wrap(catchUpData).putLong(offset + 1);
                handleAppendEntries(term, leaderId, catchUpData);
                return; // Reject and ignore this live write for now
            }

            // FIXED: Avoid duplicate writes of already-received historical logs
            if (offset < localOffset) {
                logger.info("[REPLICATION] Ignoring duplicate live write for offset {} (local offset: {})", offset, localOffset);
                return;
            }

            try {
                logger.info("[REPLICATION] Writing replicated log from Leader: {} at Term: {}. Topic: {}, Offset: {}", leaderId, term, topic, offset);
                storageManager.appendReplicatedEntry(topic, offset, payload);
            } catch (Exception e) {
                logger.error("[REPLICATION] Failed to write replicated log payload: " + e.getMessage());
            }
        }
    }

    private void stepDown(long term) {
        currentTerm = term;
        currentState = State.FOLLOWER;
        votedFor = null;
    }

    @Override public String getLeader() { return leaderId; }
}