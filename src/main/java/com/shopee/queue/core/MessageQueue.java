package com.shopee.queue.core;

import com.shopee.queue.network.protocol.MessagePacket;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Representation of a topic-specific message queue.
 * Encapsulates the metadata and logic associated with a single topic.
 */
public class MessageQueue {
	private final String topicName;
    private final LinkedList<MessagePacket> messages = new LinkedList<>();
    private final int MAX_CAPACITY = 5000; // Giới hạn số lượng tin nhắn trong RAM
    private long headOffset = 0; // Offset của tin nhắn đầu tiên đang còn trong RAM
//    private final BlockingQueue<MessagePacket> queue;
    // dùng List thay cho Blocking Queue vì BlockingQueue chỉ cho lấy thằng đầu tiên và lấy xong là mất, 
    //nên khi Hưng vừa nhấc tin nhắn ra để lưu xuống ổ cứng, thì cái tin nhắn đó biến mất khỏi RAM của Minh ngay lập tức.
    // => Nếu Consumer muốn lấy message bất kì nào đó để xử lí mà message đó bị đưa xuống lưu trữ của Hưng rồi thì lại phải gọi xuống Hưng mất thời gian
    // Ngoài ra BlockingQueue khi đưa message cho Consumer xử lí cũng sẽ đưa theo cái thứ tự FIFO, vậy nếu Consumer muốn xử lí 1 cái tin nhắn có số thứ tự khác thì sao?
    // Thêm nữa là VD có nhiều Consumer cùng xử lí 1 cái message, vậy thì chúng sẽ tranh giành nhau vì BlockingQueue đưa Message cho ai rồi thì Message đó biến mất khỏi RAM.
    // Như vậy chúng ta sẽ dùng 1 kiểu dữ liệu List để lưu các message, mỗi message sẽ có 1 offset để định danh. 
    
    // Tuy nhiên List có điểm yếu hơn so với BlockingQueue là nó ko kiểm soát đc việc khi list rỗng thì Consumer không được và khi đầy thì Producer không được push.
    // Ngoài ra mình sẽ cho thêm 1 cơ chế để không làm đầy đến nỗi hết RAM đó là khi  tất cả các Consumer Group đều báo đã đọc qua offset X, ta  xóa các tin có offset nhỏ hơn X khỏi RAM.
    
    
    public MessageQueue(String topicName) {
        this.topicName = topicName;
    }
//
//    public void addMessage(MessagePacket packet){
//        this.queue.offer(packet);
//    }
//
//    public MessagePacket pullMessage(long offset){
//    	if (offset < messages.size()) {
//            return messages.get((int) offset);
//        }
//        return null; // Hết tin để đọc
//    }
//
    public String getTopicName() {
        return topicName;
    }
    public synchronized void addMessage(MessagePacket packet) {
    	try {
            while (messages.size() >= MAX_CAPACITY) {
                System.out.println("RAM đầy, Producer đợi 500ms để Consumer kịp đọc...");
                wait(500); // Đợi tối đa 500ms
                
                // Sau khi đợi mà vẫn đầy, lúc này mới chấp nhận xóa tin cũ nhất để nhận tin mới
                if (messages.size() >= MAX_CAPACITY) {
                    messages.removeFirst();
                    headOffset++;
                }
            }
            messages.add(packet);
            notifyAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
   }

    // Consumer gọi: Lấy tin theo offset
    public synchronized MessagePacket pullMessage(long offset) {
        int index = (int) (offset - headOffset);

        if (index >= 0 && index < messages.size()) {
            return messages.get(index);
        }
       
        return null;
    }
}
