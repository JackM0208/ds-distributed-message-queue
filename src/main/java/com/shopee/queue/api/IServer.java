package com.shopee.queue.api;

/**
 * Interface representing the network server.
 * Defines the contract for accepting incoming connections from Producers and Consumers.
 * Implementations manage the lifecycle of the socket binding, thread management
 * for client handling, and graceful shutdown procedures.
 */
public interface IServer {

    /**
     * Starts the network server and begins listening for incoming connections.
     */
    void startServer();

    /**
     * Gracefully shuts down the network server, closes all active client
     * connections, and releases port bindings.
     */
    void stopServer();
}