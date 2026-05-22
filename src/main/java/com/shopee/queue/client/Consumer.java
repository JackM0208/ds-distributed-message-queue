package com.shopee.queue.client;

import com.shopee.queue.network.protocol.MessagePacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client-side SDK for consuming messages from the distributed queue.
 * Tracks offsets and supports Leader redirection and failover routing.
 */
public class Consumer {
    private static final int[] BOOTSTRAP_PORTS = {8888, 8889, 8890};
    private static final String HOST = "127.0.0.1";
    private static final int CONNECT_TIMEOUT_MS = 1000;

    private final String topic;
    private final String consumerId;
    private long currentOffset;
    private int activeLeaderPort;

    public Consumer(String topic, String consumerId) {
        this.topic = topic;
        this.consumerId = consumerId;
        this.activeLeaderPort = BOOTSTRAP_PORTS[0];
        this.currentOffset = fetchInitialOffset();
    }

    private int mapNodeToHostPort(String nodeAddress) {
        if (nodeAddress == null) return 8888;
        if (nodeAddress.contains("broker-2")) return 8889;
        if (nodeAddress.contains("broker-3")) return 8890;
        return 8888;
    }

    private long fetchInitialOffset() {
        int attempts = 0;
        while (attempts < BOOTSTRAP_PORTS.length * 2) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, activeLeaderPort), CONNECT_TIMEOUT_MS);
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    MessagePacket request = new MessagePacket(topic, consumerId.getBytes(StandardCharsets.UTF_8), 0, 3);
                    out.writeObject(request);
                    out.flush();

                    Object response = in.readObject();
                    if (response instanceof MessagePacket) {
                        MessagePacket received = (MessagePacket) response;

                        // Handle redirection
                        if (received.getType() == 2 && received.getMessageId() == -2) {
                            activeLeaderPort = mapNodeToHostPort(received.getSenderId());
                            attempts++;
                            continue;
                        }

                        System.out.println("[Consumer] Fetched saved offset from Broker: " + received.getMessageId());
                        return received.getMessageId();
                    }
                }
            } catch (Exception e) {
                activeLeaderPort = BOOTSTRAP_PORTS[(attempts + 1) % BOOTSTRAP_PORTS.length];
                attempts++;
            }
        }
        return 0;
    }

    private void commitOffset(long processedOffset) {
        int attempts = 0;
        while (attempts < BOOTSTRAP_PORTS.length * 2) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, activeLeaderPort), CONNECT_TIMEOUT_MS);
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    MessagePacket request = new MessagePacket(topic, consumerId.getBytes(StandardCharsets.UTF_8), processedOffset, 2);
                    out.writeObject(request);
                    out.flush();

                    Object response = in.readObject();
                    if (response instanceof MessagePacket) {
                        MessagePacket received = (MessagePacket) response;
                        if (received.getType() == 2 && received.getMessageId() == -2) {
                            activeLeaderPort = mapNodeToHostPort(received.getSenderId());
                            attempts++;
                            continue;
                        }
                        System.out.println("[Consumer] Committed offset: " + processedOffset);
                        break;
                    }
                }
            } catch (Exception e) {
                activeLeaderPort = BOOTSTRAP_PORTS[(attempts + 1) % BOOTSTRAP_PORTS.length];
                attempts++;
            }
        }
    }

    public MessagePacket poll() {
        int attempts = 0;
        while (attempts < BOOTSTRAP_PORTS.length * 2) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, activeLeaderPort), CONNECT_TIMEOUT_MS);
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    MessagePacket request = new MessagePacket(topic, null, currentOffset, 1);
                    out.writeObject(request);
                    out.flush();

                    Object response = in.readObject();
                    if (response instanceof MessagePacket) {
                        MessagePacket received = (MessagePacket) response;

                        if (received.getMessageId() != -1) {
                            return received;
                        }
                        return null; // Empty queue
                    }
                }
            } catch (Exception e) {
                activeLeaderPort = BOOTSTRAP_PORTS[(attempts + 1) % BOOTSTRAP_PORTS.length];
                attempts++;
            }
        }
        return null;
    }

    public void startConsuming() {
        System.out.println("[Consumer] Subscribed to: " + topic + " starting from Offset: " + currentOffset);
        while (true) {
            MessagePacket packet = poll();
            if (packet != null) {
                String content = new String(packet.getPayload(), StandardCharsets.UTF_8);
                System.out.println("==========================================");
                System.out.println(" RECEIVED MESSAGE #" + packet.getMessageId());
                System.out.println(" CONTENT: " + content);
                System.out.println("==========================================");

                currentOffset = packet.getMessageId() + 1;
                commitOffset(currentOffset);
            } else {
                try {
                    System.out.println("[Consumer] Queue empty. Awaiting writes...");
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public static void main(String[] args) {
        Consumer consumer = new Consumer("flash_sale_orders", "aminh's laptop");
        consumer.startConsuming();
    }
}