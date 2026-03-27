package com.shopee.queue.cluster;

/**
 * ROLE: The Data Copier.
 * WHAT IT DOES: If this broker is the Leader, this class tails the `LogSegment` and forwards newly appended bytes to Follower brokers via internal TCP sockets.
 * RELATIONSHIPS: Reads from `StorageManager`, sends data to other nodes' `TcpServer`.
 */
public class Replicator {
    // TODO: Tail LogSegments
    // TODO: Send data to other brokers in the cluster
}
