package com.shopee.queue.cluster;

import com.shopee.queue.network.protocol.MessagePacket;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClusterClient {
    private static final int CONNECT_TIMEOUT_MS = 1000; // 1-second timeout limit

    public boolean sendRequestVote(String target, long term, String nodeId) {
        String[] parts = target.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        try (Socket socket = new Socket()) {
            // Bind connection with a explicit fast timeout limit
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                MessagePacket p = new MessagePacket("cluster", null, 0, 4); // Type 4: Vote Req
                p.setTerm(term);
                p.setSenderId(nodeId);
                out.writeObject(p);
                out.flush();

                MessagePacket resp = (MessagePacket) in.readObject();
                return resp.getType() == 5 && resp.getPayload() != null && resp.getPayload()[0] == 1;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public void sendHeartbeat(String target, long term, String nodeId) {
        String[] parts = target.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.flush();

                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                MessagePacket p = new MessagePacket("cluster", null, 0, 6); // Type 6: Heartbeat
                p.setTerm(term);
                p.setSenderId(nodeId);
                out.writeObject(p);
                out.flush();

                in.readObject();
            }
        } catch (Exception e) {
            // Quietly catch connectivity errors during node failures
        }
    }
}