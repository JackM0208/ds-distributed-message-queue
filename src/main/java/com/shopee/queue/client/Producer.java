package com.shopee.queue.client;

import com.shopee.queue.network.protocol.MessagePacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Producer {
    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public Producer(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
    }

    private void connect() {
        try {
            this.socket = new Socket(host, port);
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush(); // Crucial to flush headers immediately
            this.in = new ObjectInputStream(socket.getInputStream());
            System.out.println("[Producer] Connected to Broker at " + host + ":" + port);
        } catch (Exception e) {
            System.err.println("[Producer] Failed to connect: " + e.getMessage());
        }
    }

    public void send(String topic, byte[] payload) {
        try {
            if (socket == null || socket.isClosed()) {
                connect();
            }

            MessagePacket packet = new MessagePacket(topic, payload, 0, 0);

            out.writeObject(packet);

            // FIX: CRITICAL FOR LONG-LIVED CONNECTIONS!
            // Prevents Java from caching the object and causing Memory Leaks / Corrupted state
            out.reset();

            out.flush();
            System.out.println("[Producer] Sent message to topic: " + topic);

            Object response = in.readObject();
            if (response instanceof MessagePacket) {
                MessagePacket ack = (MessagePacket) response;
                if (ack.getType() == 2) {
                    System.out.println("[Producer] Success! Broker saved message. Assigned ID: " + ack.getMessageId());
                }
            }

        } catch (Exception e) {
            System.err.println("[Producer] Failed to send message: " + e.getMessage());
            e.printStackTrace(); // Print full trace locally if something breaks
        }
    }

    public void close() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Producer producer = new Producer("127.0.0.1", 8888);

        String data = "Hello Shopee, I am buying an iPhone 15!";
        producer.send("flash_sale_orders", data.getBytes());
        producer.send("flash_sale_orders", "Buying an iPad!".getBytes());

        producer.close();
    }
}