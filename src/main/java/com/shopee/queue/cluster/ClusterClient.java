package com.shopee.queue.cluster;

import com.shopee.queue.network.protocol.MessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handles outgoing network requests to other nodes in the cluster.
 */
public class ClusterClient {
    private static final Logger logger = LoggerFactory.getLogger(ClusterClient.class);

    public boolean sendRequestVote(String targetNode, long term) {
        String[] parts = targetNode.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Type 3 = Vote Request
            MessagePacket voteRequest = new MessagePacket("cluster", null, 0, 3);
            voteRequest.setTerm(term);
            out.writeObject(voteRequest);
            out.flush();

            Object response = in.readObject();
            if (response instanceof MessagePacket && ((MessagePacket) response).getType() == 4) {
                return ((MessagePacket) response).getTerm() >= term; 
            }
        } catch (Exception e) {
            logger.warn("Failed to communicate with node {}: {}", targetNode, e.getMessage());
        }
        return false;
    }

    public void sendAppendEntries(String targetNode, long term, byte[] data) {
        String[] parts = targetNode.split(":");
        try (Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            // Type 5 = Append Entries (Replication)
            MessagePacket appendRequest = new MessagePacket("cluster", data, 0, 5);
            appendRequest.setTerm(term);
            out.writeObject(appendRequest);
            out.flush();
        } catch (Exception e) {
            logger.warn("Failed to replicate data to node {}: {}", targetNode, e.getMessage());
        }
    }

}
