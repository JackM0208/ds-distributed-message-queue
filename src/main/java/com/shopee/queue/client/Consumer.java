package com.shopee.queue.client;

import com.shopee.queue.network.protocol.MessagePacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client-side SDK for consuming messages from the distributed queue.
 * This class pulls messages one-by-one and tracks its own progress (offset).
 */
public class Consumer {
    private final String host;
    private final int port;
    private final String topic;
    private long currentOffset;

    public Consumer(String host, int port, String topic, long startOffset) {
        this.host = host;
        this.port = port;
        this.topic = topic;
        this.currentOffset = startOffset;
    }

    /**
     * Connects to the broker and tries to pull a single message.
     * @return The MessagePacket received, or null if no new data exists.
     */
    public MessagePacket poll() {
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // 1. Create a Pull Request (Type 1)
            // We put the offset we WANT into the messageId field
            MessagePacket request = new MessagePacket(topic, null, currentOffset, 1);

            // 2. Send request to Broker
            out.writeObject(request);
            out.flush();

            // 3. Receive Response
            Object response = in.readObject();
            if (response instanceof MessagePacket) {
                MessagePacket received = (MessagePacket) response;

                // Check if the Broker found data (messageId will NOT be -1)
                if (received.getMessageId() != -1) {
                    return received;
                }
            }

        } catch (Exception e) {
            System.err.println("[Consumer] Connection error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Starts a continuous loop to consume all messages from the topic.
     */
    public void startConsuming() {
        System.out.println("[Consumer] Started. Subscribed to: " + topic);
        System.out.println("[Consumer] Starting from Offset: " + currentOffset);

        while (true) {
            MessagePacket packet = poll();

            if (packet != null) {
                // SUCCESS: We found a message!
                String content = new String(packet.getPayload(), StandardCharsets.UTF_8);

                System.out.println("==========================================");
                System.out.println(" RECEIVED MESSAGE #" + packet.getMessageId());
                System.out.println(" CONTENT: " + content);
                System.out.println("==========================================");

                // Move to the next message ID
                currentOffset = packet.getMessageId() + 1;

            } else {
                // EMPTY: No more messages for now.
                // We sleep for a bit so we don't spam the network.
                try {
                    System.out.println("[Consumer] Queue empty. Waiting for new messages...");
                    Thread.sleep(2000); // Wait 2 seconds
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public static void main(String[] args) {
        // Start consumer for "flash_sale_orders" starting at message 0
        Consumer consumer = new Consumer("127.0.0.1", 8888, "flash_sale_orders", 0);
        consumer.startConsuming();
    }
}