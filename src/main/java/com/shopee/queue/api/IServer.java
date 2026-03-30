package com.shopee.queue.api;

/**
 * Interface representing the network server.
 * Utilizes Java NIO (New I/O) to handle many concurrent connections efficiently. 
 * Unlike standard blocking I/O which uses one thread per connection, NIO's 
 * non-blocking selectors allow a single thread to monitor multiple channels 
 * for incoming events, leading to a highly scalable, event-driven design.
 */
public interface IServer {

    /**
     * Starts the network server and begins listening for incoming messages.
     */
    void startServer();

    /**
     * Gracefully shuts down the network server and releases resources.
     */
    void stopServer();
}
