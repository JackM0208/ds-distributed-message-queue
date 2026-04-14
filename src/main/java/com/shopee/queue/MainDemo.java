package com.shopee.queue;

import com.shopee.queue.core.QueueManagerImpl;
import com.shopee.queue.core.ConsumerOffsetManager;
import com.shopee.queue.client.Producer;
import com.shopee.queue.client.Consumer;

public class MainDemo {
    public static void main(String[] args) throws InterruptedException {
        // Khởi tạo bộ não trung tâm
        QueueManagerImpl queueManager = new QueueManagerImpl();
        ConsumerOffsetManager offsetManager = new ConsumerOffsetManager();
        // Khởi tạo 
        queueManager.createTopic("TOPIC_ORDER");
        queueManager.createTopic("TOPIC_SHIPPING");
        System.out.println("[System] Đã khởi tạo các Topic trên Broker.");

        // Khởi tạo 1 Producer chung
        Producer producer = new Producer("localhost:8888", queueManager);

        // Khởi tạo 2 Consumer cho 2 Topic khác nhau
        Consumer orderConsumer = new Consumer("Group_Order", "TOPIC_ORDER", queueManager, offsetManager);
        Consumer shipConsumer = new Consumer("Group_Ship", "TOPIC_SHIPPING", queueManager, offsetManager);

        System.out.println("=== BẮT ĐẦU DEMO ĐA TOPIC ===");

        // 4. Luồng Producer: Gửi tin nhắn xen kẽ vào 2 Topic
        new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                // Gửi vào Topic Đặt hàng
                producer.send("TOPIC_ORDER", ("Đơn hàng #" + i).getBytes());
                // Gửi vào Topic Giao hàng
                producer.send("TOPIC_SHIPPING", ("Vận đơn #" + i).getBytes());
                
                try { Thread.sleep(300); } catch (InterruptedException e) {}
            }
        }).start();

        // 5. Luồng Consumer 1: Chỉ "hóng" tin Order
        new Thread(() -> {
            while (true) {
                orderConsumer.poll();
                try { Thread.sleep(800); } catch (InterruptedException e) {}
            }
        }).start();

        // 6. Luồng Consumer 2: Chỉ "hóng" tin Shipping
        new Thread(() -> {
            while (true) {
                shipConsumer.poll();
                try { Thread.sleep(1200); } catch (InterruptedException e) {}
            }
        }).start();

        // Chạy trong 10 giây để xem log nhảy
        Thread.sleep(10000);
        System.out.println("=== KẾT THÚC DEMO ===");
        System.exit(0);
    }
}