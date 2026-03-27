package com.shopee.queue.network;

/**
 * ROLE: The Front Door.
 * WHAT IT DOES: Listens on a port (e.g., 9092). Accepts incoming TCP connections from Producers and Consumers.
 * RELATIONSHIPS: When a connection arrives, it spawns a `ClientHandler` thread and hands the socket to it.
 */
public class TcpServer {
    // TODO: Listen on ServerSocket
    // TODO: Accept connections and delegate to ClientHandler
}
