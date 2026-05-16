package com.shopee.queue.api;

/**
 * Interface representing a node within a distributed cluster using Raft Consensus.
 * Raft provides high availability and consistency through:
 * 1. Leader Election: Only one node acts as a leader at any time.
 * 2. Log Replication: The leader replicates its log to all follower nodes.
 * 3. Safety: Guarantees that only nodes with an up-to-date log can become leader.
 */
public interface IClusterNode {

    boolean requestVote();

    void replicateData();

    /**
     * Xử lý yêu cầu bầu cử từ Node khác.
     */
    boolean handleVoteRequest(long term, String candidateId);

    /**
     * Xử lý gói tin nhân bản dữ liệu hoặc Heartbeat từ Leader.
     */
    void handleAppendEntries(long term, String leaderId, byte[] data);

    /**
     * Identifies the current leader of the cluster.
     * @return String The unique ID of the current leader node.
     */
    String getLeader();
}


