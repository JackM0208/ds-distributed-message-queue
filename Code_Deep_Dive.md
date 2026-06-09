# Phân tích Chi tiết Code (Code Deep Dive)

Tài liệu này sẽ đi sâu vào mã nguồn, chỉ đích danh các class, method và giải thích cách chúng hiện thực hóa kiến trúc hệ thống, được chia theo nhiệm vụ của từng người.

---

## 1. Network & Observability (Bạn 1)
Nhiệm vụ: Giao tiếp TCP với Client, giao tiếp WebSocket với Frontend.

### a. `TcpServerImpl.java` & `ClientHandler.java`
Đây là cửa ngõ chính xử lý lưu lượng hàng triệu tin nhắn.
- **`TcpServerImpl`**: Mở ServerSocket tại port 8888. Nó sử dụng một `ExecutorService` (Thread Pool) để tránh tạo quá nhiều thread làm sập server:
  ```java
  // Chờ client kết nối và ném cho Thread Pool xử lý
  Socket clientSocket = serverSocket.accept();
  clientThreadPool.execute(new ClientHandler(clientSocket, queueManager, offsetManager, clusterNode));
  ```
- **`ClientHandler`**: Xử lý từng luồng byte nhận được. Method quan trọng nhất là `processPacket(MessagePacket packet, ...)` dùng để bóc tách `type`:
  - `type == 0` (PUBLISH): Đẩy cho `queueManager.pushMessage()`.
  - `type == 1` (CONSUME): Hỏi `offsetManager` xem đọc đến đâu rồi, và dùng `queueManager.pullMessage(offset)` để lấy tin nhắn trả về.
  - `type == 3, 5` (RAFT): Gọi `clusterNode.handleVoteRequest()` và `handleAppendEntries()`.

### b. `BrokerWebSocketBridge.java` (The Sidecar)
Đây là một "Sidecar" chạy trong cùng process, mở cổng WebSocket độc lập (vd: 9001) để Frontend kết nối.
- Nó kế thừa `WebSocketServer`, lưu danh sách các web client đang xem dashboard: `private final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();`
- Nó đóng gói dữ liệu thành JSON cực nhanh (không dùng thư viện ngoài) qua hàm `json(...)` rồi dùng vòng lặp gửi cho tất cả web browser qua hàm `broadcast(...)`.
- Đặc biệt, nếu Frontend gửi một lệnh `{"action":"produce", "payload":"..."}`, hàm `onMessage()` sẽ đóng gói lại thành `MessagePacket` và đẩy thẳng xuống `queueManager.pushMessage("flash_sale_orders", packet)`. Đây là cách Web Client có thể gửi tin thẳng vào Queue mà không cần qua TCP.

---

## 2. Core & Queue (Bạn 2)
Nhiệm vụ: Phân luồng dữ liệu, quản lý Topic, Offset, làm trung gian giữa RAM và Disk.

### a. `QueueManagerImpl.java` & `MessageQueue.java`
Đây là nơi điều phối chính. Mọi yêu cầu từ Network đều qua đây trước.
- **`QueueManagerImpl`**: Dùng `ConcurrentHashMap` để lưu map giữa tên Topic và `MessageQueue`. 
  - `pushMessage`: Lấy queue ra và gọi `.addMessage()`.
  - `pullMessage`: Lấy queue ra và gọi `.pullMessage()`.
- **`MessageQueue`**: Hiện tại đã bỏ `LinkedBlockingQueue` (Lưu RAM) và chuyển thẳng xuống `IStorageManager` (Lưu Disk). Điều này có nghĩa là "RAM" ở đây đã được tận dụng bởi OS (Page Cache) thông qua cách Hưng viết file I/O.
  ```java
  // Code thực tế trong MessageQueue
  public void addMessage(MessagePacket packet) {
      storageManager.appendToLog(topicName, packet.getPayload());
  }
  ```

### b. `ConsumerOffsetManager.java`
Đảm nhận việc ghi nhớ vị trí đọc.
- Nó lưu offset trong RAM bằng `offsetMap = new ConcurrentHashMap<>()` để tra cứu cực nhanh (O(1)).
- Hàm `commitOffset` ghi RAM xong sẽ gọi `saveOffsets()`, dùng `ObjectOutputStream` đẩy thẳng cái Map xuống file `data/offsets.dat`. Nhờ vậy mà khi khởi động lại Broker (`loadOffsets()`), nó có thể khôi phục lại bộ nhớ.

---

## 3. Storage Layer (Hưng)
Nhiệm vụ: Chịu trách nhiệm ghi xuống Đĩa cứng với hiệu năng cao nhất, không bị thắt cổ chai (bottleneck).

### a. `LogSegment.java` (Ghi dữ liệu thật)
Đây là nơi lưu trữ payload.
- Nó dùng `RandomAccessFile` và `FileChannel`.
- Khi ghi, nó dùng **Append-only** (Chỉ ghi đè lên cuối file):
  ```java
  public synchronized long append(byte[] data) throws IOException {
      ByteBuffer byteBuffer = ByteBuffer.wrap(data);
      while (byteBuffer.hasRemaining()) {
          fileChannel.write(byteBuffer);
      }
      return physicalPosition;
  }
  ```
  Nhờ FileChannel, OS sẽ tối ưu hóa ghi tuần tự, đẩy tốc độ tiệm cận tốc độ RAM (vì được ghi vào Page Cache trước khi flush xuống đĩa).

### b. `IndexSegment.java` (Bản đồ tra cứu)
Làm sao để tìm tin nhắn số 1000 cực nhanh giữa file hàng GB?
- Hưng dùng **Zero-copy** qua `MappedByteBuffer`. Toàn bộ file index được map thẳng lên RAM:
  ```java
  this.mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 10_000_000);
  ```
- Mỗi entry chiếm đúng **20 bytes** cố định (8 byte Offset + 8 byte Tọa độ FileLog + 4 byte Chiều dài).
- Khi tìm tin nhắn thứ N, nó tính ra tọa độ RAM là `N * 20`, đọc thẳng giá trị ra. Cực kỳ tối ưu.

---

## 4. Cluster & Client SDK (Bạn 3)
Nhiệm vụ: Xây dựng cơ chế chịu lỗi (Raft) và công cụ cho lập trình viên.

### a. `RaftNodeImpl.java` (Cơ chế Bầu cử & Đồng bộ)
Lõi của Distributed System.
- Khi một node chạy, nó gọi `startElectionTimer()`. Nếu sau vài giây không thấy Leader nào nói gì (heartbeat), nó sẽ gọi `becomeCandidate()`.
- Nó tăng `currentTerm++`, và dùng `clusterClient` gọi TCP (port 8888 của các node khác) bằng loại gói tin `type == 3` (VOTE_REQUEST).
- Khi thắng cử (`becomeLeader()`), nó bật vòng lặp `startHeartbeat()` đẩy `dummyData` hoặc dữ liệu thật (type == 5, APPEND_ENTRIES) cho các node Follower.
- Nếu thấy Term của node khác cao hơn, nó sẽ gọi `stepDown()` trở về làm Follower. Mọi bước chuyển trạng thái đều gọi `saveClusterState()` xuống đĩa.

### b. `Producer.java` & `Consumer.java`
Đây là thư viện Client.
- Thay vì để người dùng tự mở Socket và ObjectOutputStream, SDK bọc sẵn vào class `Producer` có method `send()`.
- `send()` sẽ chuyển đổi payload thành `MessagePacket` (type 0), gửi đi, và đợi chặn (`in.readObject()`) cho đến khi nhận được ACK (type 2) từ Server.

---

## 5. Mảnh ghép cuối: Docker & Môi trường chạy
File `docker-compose.yml` định nghĩa 3 Broker chạy chung.
- Ở đó `command: ["8888"]` truyền thẳng cổng TCP vào hàm `main()` của `BrokerMain.java`.
- Ở phần `volumes`, thư mục `data/node-1` của máy bạn được map vào `/app/data` trong Docker. Điều này đảm bảo Hưng ghi `LogSegment` và `offset.dat` trong Container, nhưng dữ liệu thực tế vẫn hiển hiện trên máy tính thật của bạn.
- Cổng `9001:9001` được mở ra ngoài để cái `BrokerWebSocketBridge` (phần của Bạn 1) có thể tuồn dữ liệu Raft và Memory ra ngoài cho Frontend vẽ biểu đồ.
