package com.shopee.queue.cluster;

import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.common.config.BrokerConfig;
import com.shopee.queue.network.protocol.MessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class RaftNodeImpl implements IClusterNode {
    private static final Logger logger = LoggerFactory.getLogger(RaftNodeImpl.class);

    private enum State { FOLLOWER, CANDIDATE, LEADER }
    private State currentState = State.FOLLOWER;
    private String leaderId = null;
    private final String nodeId; 
    private final ClusterClient clusterClient = new ClusterClient();

    private long currentTerm = 0;
    private String votedFor = null;
    private long lastHeartbeatTime;

    public RaftNodeImpl(int port) {
        this.nodeId = "127.0.0.1:" + port;
        this.lastHeartbeatTime = System.currentTimeMillis();
        startElectionTimer();
    }

    private void startElectionTimer() {
        new Thread(() -> {
            while (true) {
                try {
                    long timeout = 10000 + new Random().nextInt(5000);
                    Thread.sleep(1000); // Check mỗi 1000ms
                    
                    if (currentState != State.LEADER && (System.currentTimeMillis() - lastHeartbeatTime) > timeout) {
                        becomeCandidate();
                    }
                } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    private void becomeCandidate() {
        currentState = State.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;
        logger.info("[RAFT] Node {} elects term {}", nodeId, currentTerm);
        
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
        startHeartbeat();
    }

    private void startHeartbeat() {
        new Thread(() -> {
            while (currentState == State.LEADER) {
                replicateData();
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    @Override
    public boolean requestVote() {
        int votes = 1; // Tự bầu cho mình
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
        for (String target : BrokerConfig.CLUSTER_NODES) {
            if (target.equals(nodeId)) continue;
            clusterClient.sendHeartbeat(target, currentTerm, nodeId);
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

            logger.info("[RAFT] Receveid heartbeat from Leader: {} (Term: {})", leaderId, term);

            if (term > currentTerm) stepDown(term);
            this.leaderId = leaderId;
            this.lastHeartbeatTime = System.currentTimeMillis();
            this.currentState = State.FOLLOWER;
        }
    }

    private void stepDown(long term) {
        currentTerm = term;
        currentState = State.FOLLOWER;
        votedFor = null;
    }

    @Override public String getLeader() { return leaderId; }
}