package com.shopee.queue.client;

import com.shopee.queue.network.protocol.MessagePacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client-side SDK for producing messages to the distributed queue.
 * This class handles the TCP connection and serialization of MessagePackets.
 */
public class Producer {
    private final String host;
    private final int port;

    public Producer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Sends a message to a specific topic and waits for an Acknowledgement (ACK).
     * @param topic Topic to send to (e.g., "flash_sale_orders")
     * @param payload The actual data (e.g., order details)
     */
    public void send(String topic, byte[] payload) {
        System.out.println("[Producer] Attempting to connect to Broker at " + host + ":" + port);

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // 1. Create a MessagePacket
            // Topic:  flash_sale_orders
            // Payload: The byte array
            // MessageId: 0 (The Broker will assignthe real ID)
            // Type: 0 (Type 0 = Produce Request)
            MessagePacket packet = new MessagePacket(topic, payload, 0, 0);

            // 2. Send the packet to the Broker
            out.writeObject(packet);
            out.flush();
            System.out.println("[Producer] Message sent to topic [" + topic + "]. Waiting for ACK...");

            // 3. Receive the Response (ACK) from the Broker
            Object response = in.readObject();
            if (response instanceof MessagePacket) {
                MessagePacket ack = (MessagePacket) response;

                // Type 2 is ACK (as defined in our ClientHandler logic)
                if (ack.getType() == 2) {
                    System.out.println("--------------------------------------------------");
                    System.out.println("[SUCCESS] Broker acknowledged the message!");
                    System.out.println("[INFO] Official Message ID assigned: " + ack.getMessageId());
                    System.out.println("[INFO] Timestamp: " + ack.getTimeCreated());
                    System.out.println("--------------------------------------------------");
                } else {
                    System.out.println("[ERROR] Received unexpected packet type: " + ack.getType());
                }
            }

        } catch (Exception e) {
            System.err.println("[CRITICAL] Could not communicate with Broker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test execution: Sends a successful flash sale order.
     */
    public static void main(String[] args) {
        // 1. Create the producer pointing to localhost:8888
        Producer producer = new Producer("127.0.0.1", 8888);

        // 2. Prepare the order data (Simulating a Shopee Flash Sale)
        String orderDetails = "ORDER_CONFIRMED: UserID=99, Item=iPhone 15 Pro, Price=999.00 USD, Status=SUCCESS";
        byte[] data = orderDetails.getBytes(StandardCharsets.UTF_8);

        // 3. Send to the "flash_sale_orders" topic
        producer.send("flash_sale_orders", data);
    }
}