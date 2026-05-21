package com.shopee.queue.cluster;

import com.shopee.queue.network.protocol.MessagePacket;
import java.io.*;
import java.net.Socket;

public class ClusterClient {
    public boolean sendRequestVote(String target, long term, String nodeId) {
        try (Socket socket = new Socket(target.split(":")[0], Integer.parseInt(target.split(":")[1]));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            MessagePacket p = new MessagePacket("cluster", null, 0, 4); // Type 4: Vote Req
            p.setTerm(term);
            p.setSenderId(nodeId);
            out.writeObject(p);
            out.flush();

            MessagePacket resp = (MessagePacket) in.readObject();
            return resp.getType() == 5 && resp.getPayload() != null && resp.getPayload()[0] == 1;
        } catch (Exception e) { return false; }
    }

    public void sendHeartbeat(String target, long term, String nodeId) {
        try (Socket socket = new Socket(target.split(":")[0], Integer.parseInt(target.split(":")[1]));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());)
            {
            out.flush(); 

            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            MessagePacket p = new MessagePacket("cluster", null, 0, 6); // Type 6: Heartbeat
            p.setTerm(term);
            p.setSenderId(nodeId);
            out.writeObject(p);
            out.flush();

            in.readObject();
        } catch (Exception e) {}
    }
}