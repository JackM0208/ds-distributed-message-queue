package com.shopee.queue.cluster;

import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.common.config.BrokerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Random;
import java.util.Properties;

/**
 * Implementation of a cluster node using a simplified Raft consensus algorithm.
 */
public class RaftNodeImpl implements IClusterNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNodeImpl.class);

    private enum State { FOLLOWER, CANDIDATE, LEADER }
    private State currentState = State.FOLLOWER;
    private String leaderId = null;
    private final String nodeId = "node-" + new Random().nextInt(1000);
    private final ClusterClient clusterClient = new ClusterClient();
    private final String statePath = "data/cluster.state";

    // RAFT STATE (Persistent)
    private long currentTerm = 0;
    private String votedFor = null;

    public RaftNodeImpl() {
        loadClusterState();
        startElectionTimer();
    }

    private void startElectionTimer() {
        new Thread(() -> {
            try {
                // Simplified election timeout
                Thread.sleep(5000 + new Random().nextInt(5000));
                if (leaderId == null && currentState != State.LEADER) {
                    becomeCandidate();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Chuyển trạng thái sang ứng viên (CANDIDATE) và bắt đầu cuộc bầu cử.
     * Quy trình: Tăng nhiệm kỳ (Term) -> Tự bỏ phiếu -> Gửi yêu cầu bầu cử tới các Node khác.
     */
    private void becomeCandidate() {
        currentState = State.CANDIDATE;
        currentTerm++; // Tăng nhiệm kỳ để thể hiện đây là cuộc bầu cử mới
        votedFor = nodeId; // Tự bỏ phiếu cho chính mình
        saveClusterState(); // Lưu trạng thái xuống đĩa cứng ngay lập tức
        
        logger.info("Node {} (Term {}) đang tranh cử Leader...", nodeId, currentTerm);
        
        if (requestVote()) {
            becomeLeader();
        } else {
            // Nếu không đủ số phiếu (Quorum), quay lại làm Follower và chờ bầu cử lại
            currentState = State.FOLLOWER;
            startElectionTimer();
        }
    }


    private void becomeLeader() {
        currentState = State.LEADER;
        leaderId = nodeId;
        logger.info("Node {} (Term {}) won election! Now LEADER.", nodeId, currentTerm);
        startHeartbeat();
    }

    private void startHeartbeat() {
        new Thread(() -> {
            while (currentState == State.LEADER) {
                try {
                    replicateData();
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    @Override
    public boolean requestVote() {
        int votesReceived = 1;
        int quorum = (BrokerConfig.CLUSTER_NODES.length / 2) + 1;

        for (String node : BrokerConfig.CLUSTER_NODES) {
            if (clusterClient.sendRequestVote(node, currentTerm)) {
                votesReceived++;
            }
            if (votesReceived >= quorum) return true;
        }
        return false;
    }

    @Override
    public void replicateData() {
        if (currentState == State.LEADER) {
            byte[] dummyData = "heartbeat".getBytes();
            for (String node : BrokerConfig.CLUSTER_NODES) {
                clusterClient.sendAppendEntries(node, currentTerm, dummyData);
            }
        }
    }

    @Override
    public synchronized boolean handleVoteRequest(long term, String candidateId) {
        // Nếu nhiệm kỳ gửi đến thấp hơn nhiệm kỳ hiện tại -> Từ chối
        if (term < currentTerm) return false;

        // Nếu nhiệm kỳ gửi đến cao hơn -> Cập nhật nhiệm kỳ và chuyển về Follower
        if (term > currentTerm) {
            stepDown(term);
        }

        // Nếu chưa bầu cho ai trong nhiệm kỳ này -> Đồng ý bầu
        if (votedFor == null || votedFor.equals(candidateId)) {
            votedFor = candidateId;
            saveClusterState();
            return true;
        }

        return false;
    }

    @Override
    public synchronized void handleAppendEntries(long term, String leaderId, byte[] data) {
        // Nếu nhiệm kỳ thấp hơn -> Bỏ qua heartbeat cũ
        if (term < currentTerm) return;

        // Nhận diện Leader mới hoặc cập nhật nhiệm kỳ
        if (term > currentTerm || currentState != State.FOLLOWER) {
            stepDown(term);
        }

        this.leaderId = leaderId;
        // Reset election timer (giả lập bằng cách log)
        // logger.debug("Heartbeat received from {}", leaderId);
    }

    private void stepDown(long newTerm) {
        logger.info("Node {} stepping down to FOLLOWER (New Term: {})", nodeId, newTerm);
        this.currentTerm = newTerm;
        this.currentState = State.FOLLOWER;
        this.votedFor = null;
        this.leaderId = null;
        saveClusterState();
    }

    private void saveClusterState() {
        Properties props = new Properties();
        props.setProperty("currentTerm", String.valueOf(currentTerm));
        props.setProperty("votedFor", votedFor == null ? "" : votedFor);
        try (OutputStream out = new FileOutputStream(statePath)) {
            props.store(out, "Raft Node State");
        } catch (IOException e) {
            logger.error("Failed to save cluster state: {}", e.getMessage());
        }
    }

    private void loadClusterState() {
        File file = new File(statePath);
        if (file.exists()) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
                currentTerm = Long.parseLong(props.getProperty("currentTerm", "0"));
                votedFor = props.getProperty("votedFor", "");
                if (votedFor.isEmpty()) votedFor = null;
            } catch (IOException e) {
                logger.error("Failed to load cluster state: {}", e.getMessage());
            }
        }
    }

    @Override
    public String getLeader() {
        return leaderId;
    }
}





