package com.shopee.queue.client;

import com.shopee.queue.common.config.BrokerConfig;
import com.shopee.queue.core.ConsumerOffsetManager;
import com.shopee.queue.core.QueueManagerImpl;
import com.shopee.queue.network.protocol.MessagePacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
/**
 * Client-side SDK for consuming messages from the distributed queue.
 * Periodically pulls messages from a given topic and offset.
 */
public class Consumer {
	private final String consumerId;
    private final String topic;
    private final QueueManagerImpl queueManager;
    private final ConsumerOffsetManager offsetManager;
    private long currentOffset;
    public Consumer(String consumerId, String topic, QueueManagerImpl queueManager, ConsumerOffsetManager offsetManager) {
        this.consumerId = consumerId;
        this.topic = topic;
        this.queueManager = queueManager;
        this.offsetManager = offsetManager;
        this.currentOffset = offsetManager.getOffset(consumerId, topic);
    }

    /**
     * Polls the broker for new messages in a topic.
     * @param topic Topic to poll.
     * @return byte[] message data.
     */
    public byte[] poll() {
            MessagePacket response = queueManager.pullMessage(topic, currentOffset);

            if (response != null && response.getType() == 0) {
                System.out.println("[Consumer] Received: " + new String(response.getPayload()));

                // Bước 3: Tăng offset
                currentOffset++;

                // Bước 4: Gửi ACK (Type 2) về cho Minh để lưu tiến trình
                // Ta gói offset vào messageId hoặc payload tùy quy ước, ở đây ta dùng hàm commit
                offsetManager.commitOffset(consumerId, topic, currentOffset);
                
                // Giả lập gói tin ACK
                MessagePacket ack = new MessagePacket(topic, null, currentOffset, 2);
                System.out.println("[Consumer] Sent ACK for offset: " + currentOffset);
            }
    	return null;
    }
}
