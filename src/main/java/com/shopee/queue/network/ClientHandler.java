package com.shopee.queue.network;

import com.shopee.queue.api.IClusterNode;
import com.shopee.queue.api.IQueueManager;
import com.shopee.queue.core.ConsumerOffsetManager;
import com.shopee.queue.network.protocol.MessagePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Handler for individual client connections.
 * Processes incoming MessagePackets and interacts with the QueueManager.
 */
public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    
    private final Socket clientSocket;
    private final IQueueManager queueManager;
    private final ConsumerOffsetManager offsetManager;
    private final IClusterNode clusterNode;

    public ClientHandler(Socket socket, IQueueManager queueManager, ConsumerOffsetManager offsetManager, IClusterNode clusterNode) {
        this.clientSocket = socket;
        this.queueManager = queueManager;
        this.offsetManager = offsetManager;
        this.clusterNode = clusterNode;
    }

    /**
     * Luồng xử lý chính: Nhận gói tin -> Phân loại (Publish/Consume/Raft) -> Xử lý.
     * Sử dụng ObjectInputStream để tự động Deserialize các đối tượng MessagePacket.
     */
    @Override
    public void run() {
        try (
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())
        ) {
            while (!clientSocket.isClosed()) {
                try {
                    // Đọc gói tin từ luồng mạng
                    Object obj = in.readObject();
                    if (obj instanceof MessagePacket) {
                        MessagePacket packet = (MessagePacket) obj;
                        processPacket(packet, out);
                    }
                } catch (EOFException e) {
                    // Xảy ra khi Client chủ động đóng kết nối
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error handling client {}: {}", clientSocket.getRemoteSocketAddress(), e.getMessage());
        } finally {
            closeSocket();
        }
    }


    private void processPacket(MessagePacket packet, ObjectOutputStream out) throws Exception {
        int type = packet.getType();
        String topic = packet.getTopic();

        if (type == 0) { // PUBLISH
            logger.info("Received PUBLISH for topic: {}", topic);
            queueManager.pushMessage(topic, packet);
            
            // Send ACK
            MessagePacket ack = new MessagePacket(topic, null, packet.getMessageId(), 2);
            out.writeObject(ack);
            out.flush();
            
        } else if (type == 1) { // CONSUME
            logger.info("Received CONSUME for topic: {}", topic);
            
            // 1. Get last committed offset for this consumer (simulated consumerId)
            String consumerId = "default-group"; 
            long currentOffset = offsetManager.getOffset(consumerId, topic);
            
            // 2. Pull message from that offset
            MessagePacket response = queueManager.pullMessage(topic, currentOffset); 
            
            if (response != null && response.getPayload() != null) {
                // 3. Increment and commit offset if message was found
                offsetManager.commitOffset(consumerId, topic, currentOffset + 1);
            }
            
            out.writeObject(response);
            out.flush();
        } else if (type == 3) { // RAFT VOTE REQUEST
            long incomingTerm = packet.getTerm();
            String candidateId = packet.getTopic(); // Giả lập candidateId gửi trong trường topic
            
            // Ủy quyền việc quyết định bầu cử cho RaftNode
            boolean granted = clusterNode.handleVoteRequest(incomingTerm, candidateId);
            
            MessagePacket voteResponse = new MessagePacket("cluster", null, 0, 4);
            voteResponse.setTerm(granted ? incomingTerm : -1); // Dùng term -1 để báo từ chối
            out.writeObject(voteResponse);
            out.flush();
            
        } else if (type == 5) { // RAFT APPEND ENTRIES (REPLICATION/HEARTBEAT)
            clusterNode.handleAppendEntries(packet.getTerm(), packet.getTopic(), packet.getPayload());
        }
    }



    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (Exception e) {
            logger.error("Error closing socket: {}", e.getMessage());
        }
    }
}

