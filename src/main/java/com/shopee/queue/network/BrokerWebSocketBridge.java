package com.shopee.queue.network;

import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.network.protocol.MessagePacket;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BrokerWebSocketBridge
 * ─────────────────────
 * Exposes MQ internals to the HTML5 Flash Sale War Room frontend and processes direct
 * incoming web-based writes.
 */
public class BrokerWebSocketBridge {

    private final String nodeId;
    private final int wsPort;
    private final IQueueManager queueManager;
    private MQWebSocketServer wsServer;

    private static String json(Object... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":");
            Object val = kv[i + 1];
            if (val == null) {
                sb.append("null");
            } else if (val instanceof String) {
                sb.append('"').append(val).append('"');
            } else if (val instanceof Boolean || val instanceof Number) {
                sb.append(val);
            } else {
                sb.append('"').append(val.toString()).append('"');
            }
        }
        return sb.append('}').toString();
    }

    public BrokerWebSocketBridge(String nodeId, int wsPort, IQueueManager queueManager) {
        this.nodeId = nodeId;
        this.wsPort = wsPort;
        this.queueManager = queueManager;
    }

    public void start() throws Exception {
        wsServer = new MQWebSocketServer(wsPort, queueManager);
        wsServer.start();
        System.out.printf("[BRIDGE] Observability telemetry active on ws://0.0.0.0:%d (node=%s)%n",
                wsPort, nodeId);
    }

    public void emitAppend(String topic, long offset, double fileSizeGB) {
        String payload = json(
                "event",    "append",
                "topic",    topic,
                "offset",   offset,
                "node",     nodeId,
                "fileSize", Math.round(fileSizeGB * 1000.0) / 1000.0
        );
        broadcast(payload);
    }

    public void emitClusterStatus(String status, int cpu, int memUsed) {
        String payload = json(
                "event",  "cluster",
                "node",   nodeId,
                "status", status,
                "cpu",    cpu,
                "mem",    memUsed
        );
        broadcast(payload);
    }

    public void emitReplication(String followerId, long offset) {
        String payload = json(
                "event",  "replicate",
                "from",   nodeId,
                "to",     followerId,
                "offset", offset
        );
        broadcast(payload);
    }

    public void emitElection(String type, String winner) {
        String payload = winner != null
                ? json("event", "election", "type", type, "winner", winner)
                : json("event", "election", "type", type);
        broadcast(payload);
    }

    private void broadcast(String msg) {
        if (wsServer != null) {
            wsServer.broadcast(msg);
        }
    }

    private static class MQWebSocketServer extends WebSocketServer {

        private final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();
        private final IQueueManager queueManager;

        MQWebSocketServer(int port, IQueueManager queueManager) {
            super(new InetSocketAddress(port));
            this.queueManager = queueManager;
            setReuseAddr(true);
        }

        @Override
        public void onOpen(WebSocket ws, ClientHandshake hs) {
            clients.add(ws);
            System.out.println("[BRIDGE] Observability portal linked: " + ws.getRemoteSocketAddress());
        }

        @Override
        public void onClose(WebSocket ws, int code, String reason, boolean remote) {
            clients.remove(ws);
            System.out.println("[BRIDGE] Observability portal unlinked: " + ws.getRemoteSocketAddress());
        }

        @Override
        public void onMessage(WebSocket ws, String msg) {
            if (msg.contains("\"action\":\"produce\"")) {
                try {
                    String payload = "WEB_ORDER";
                    if (msg.contains("\"payload\":\"")) {
                        int start = msg.indexOf("\"payload\":\"") + 11;
                        int end = msg.indexOf("\"", start);
                        if (end > start) {
                            // FIXED: Added newline character to the end of parsed web payloads for raw file legibility
                            payload = msg.substring(start, end) + "\n";
                        }
                    }

                    MessagePacket packet = new MessagePacket(
                            "flash_sale_orders",
                            payload.getBytes(StandardCharsets.UTF_8),
                            0,
                            0
                    );

                    queueManager.pushMessage("flash_sale_orders", packet);

                } catch (Exception e) {
                    System.err.println("[BRIDGE] Failed to process incoming web write: " + e.getMessage());
                }
            }
        }

        @Override
        public void onError(WebSocket ws, Exception ex) {
            System.err.println("[BRIDGE] Connection exception: " + ex.getMessage());
        }

        @Override
        public void onStart() {
            // Server socket bound successfully
        }

        public void broadcast(String msg) {
            for (WebSocket client : clients) {
                if (client.isOpen()) {
                    client.send(msg);
                }
            }
        }
    }
}