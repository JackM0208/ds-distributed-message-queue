package com.shopee.queue.cluster;

import com.shopee.queue.protocol.MessagePacket;

/**
 * CONTRACT FOR PERSON 4 (Cluster & Consensus)
 * Manages High Availability and data consistency between brokers.
 */
public interface IConsensusModule {
    /**
     * Checks if this specific node is currently the leader for a given topic.
     * Only the leader can accept writes.
     */
    boolean isLeader(String topic);

    /**
     * Returns the ID of the current cluster leader.
     */
    String getLeaderId();

    /**
     * Replicates a message to follower nodes.
     * @param packet The message packet to replicate.
     */
    void replicate(String topic, MessagePacket packet);

    /**
     * Registers a new broker node in the cluster.
     */
    void registerNode(String nodeId, String address);

    /**
     * Handles an incoming vote request or ping from another node.
     */
    void handleHeartbeat();
}
