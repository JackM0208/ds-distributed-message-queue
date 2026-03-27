package com.shopee.queue.network;

/**
 * ROLE: The Translator & Router.
 * WHAT IT DOES: Reads raw bytes from the network, converts them into a `MessagePacket`. If it's a Publish request, it routes it to the `QueueManager`. If it's a Consume request, it reads from the queue and flushes bytes back to the client.
 * RELATIONSHIPS: Bridges `TcpServer` with `QueueManager` and `ConsumerOffsetManager`.
 */
public class ClientHandler implements Runnable {
    // TODO: Read/Write from Socket
    // TODO: Delegate work based on message type (Publish/Consume/ACK)
    
    @Override
    public void run() {
        // TODO: Request processing loop
    }
}
