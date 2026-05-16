package com.shopee.queue.cluster;

import com.shopee.queue.common.config.BrokerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the logic for replicating message data from the leader to followers.
 */
public class Replicator {
    private static final Logger logger = LoggerFactory.getLogger(Replicator.class);
    private final ClusterClient clusterClient = new ClusterClient();
    
    /**
     * Pushes message segments to a set of target nodes via network.
     * @param term current Raft term.
     * @param data byte array to replicate.
     */
    public void pushToFollowers(long term, byte[] data) {
        for (String node : BrokerConfig.CLUSTER_NODES) {
            logger.info("Replicating to node {} (Term {})", node, term);
            clusterClient.sendAppendEntries(node, term, data);
        }
    }
}


