package com.shopee.queue.client;

import com.shopee.queue.network.protocol.MessagePacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client-side SDK for producing messages to the distributed queue.
 * Implements Bootstrap failover routing and active Leader redirection.
 */
public class Producer {
    private static final int[] BOOTSTRAP_PORTS = {8888, 8889, 8890};
    private static final String HOST = "127.0.0.1";
    private static final int CONNECT_TIMEOUT_MS = 1000;

    private int mapNodeToHostPort(String nodeAddress) {
        if (nodeAddress == null) return 8888;
        if (nodeAddress.contains("broker-2")) return 8889;
        if (nodeAddress.contains("broker-3")) return 8890;
        return 8888;
    }

    public void send(String topic, byte[] payload) {
        boolean success = false;
        int targetPort = BOOTSTRAP_PORTS[0];
        int attempts = 0;

        while (attempts < BOOTSTRAP_PORTS.length * 2) {
            System.out.println("[Producer] Connecting to broker target: " + HOST + ":" + targetPort);

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, targetPort), CONNECT_TIMEOUT_MS);

                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    MessagePacket packet = new MessagePacket(topic, payload, 0, 0);
                    out.writeObject(packet);
                    out.flush();

                    Object response = in.readObject();
                    if (response instanceof MessagePacket) {
                        MessagePacket ack = (MessagePacket) response;

                        if (ack.getType() == 2 && ack.getMessageId() >= 0) {
                            System.out.println("--------------------------------------------------");
                            System.out.println("[SUCCESS] Clustered persistence and replication completed!");
                            System.out.println("[INFO] Official Offset ID assigned on disk: " + ack.getMessageId());
                            System.out.println("--------------------------------------------------");
                            success = true;
                            break;
                        }

                        else if (ack.getType() == 2 && ack.getMessageId() == -2) {
                            String activeLeader = ack.getSenderId();
                            int redirectedPort = mapNodeToHostPort(activeLeader);
                            System.out.println("[CLUSTER] Target node was a follower. Redirecting to active leader: "
                                    + activeLeader + " (Host Port: " + redirectedPort + ")");
                            targetPort = redirectedPort;
                            attempts++;
                            continue;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[WARN] Connection failed on port: " + targetPort + ". Trying next bootstrap node...");
                targetPort = BOOTSTRAP_PORTS[(attempts + 1) % BOOTSTRAP_PORTS.length];
                attempts++;
            }
        }

        if (!success) {
            System.err.println("[CRITICAL] Write execution failed: All cluster bootstrap nodes are unreachable.");
        }
    }

    public static void main(String[] args) {
        Producer producer = new Producer();
        // FIXED: Added newline character (\n) to the end of the order details payload for clean visual file partitioning
        String orderDetails = "ORDER_CONFIRMED: UserID=99, Item=iPhone 15 Pro, Price=999.00 USD, Status=SUCCESS\n";
        byte[] data = orderDetails.getBytes(StandardCharsets.UTF_8);

        producer.send("flash_sale_orders", data);
    }
}