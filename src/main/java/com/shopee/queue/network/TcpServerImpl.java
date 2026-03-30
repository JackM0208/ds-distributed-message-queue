package com.shopee.queue.network;

import com.shopee.queue.api.IServer;

/**
 * Implementation of the network server using Netty.
 * Netty uses EventLoopGroups for asynchronous event handling:
 * 1. Boss Group: Responsible for accepting incoming connections and passing 
 *    them to the worker group.
 * 2. Worker Group: Handles the actual I/O operations and processes messages 
 *    for all accepted connections.
 */
public class TcpServerImpl implements IServer {

    @Override
    public void startServer() {
        // Netty server startup logic
    }

    @Override
    public void stopServer() {
        // Netty server shutdown logic
    }
}
