# Kiến trúc Tổng thể: Distributed Message Queue (DMQ)

Chào team! Để mọi người dễ dàng nắm bắt bức tranh toàn cảnh và cách các module ghép nối lại với nhau thành một hệ thống hoàn chỉnh, mình đã tổng hợp lại kiến trúc hệ thống dựa trên sự phân công của nhóm. 

Hệ thống của chúng ta là một **Raft-based Distributed Message Queue**, được thiết kế để chịu lỗi cao, lưu trữ dữ liệu bền vững và có khả năng theo dõi thời gian thực (Real-time Observability) thông qua WebSockets.

---

## 🔗 Luồng Ghép Nối Dữ Liệu (How everything connects)

1. **Client (Producer)** gửi tin nhắn qua TCP đến **Network Layer** của Broker.
2. **Network Layer** nhận gói tin, bóc tách và chuyển cho **Core (QueueManager)**.
3. **Core** xác định Topic, sau đó gọi xuống **Storage Layer** để ghi tin nhắn vào Đĩa cứng.
4. Sau khi ghi đĩa xong, **Core** báo cho **Cluster Layer (Replicator)** để nhân bản (replicate) tin nhắn đó sang các Broker khác trong cụm (Followers).
5. Đồng thời, **Network Layer (WebSocket Bridge)** sẽ phát tín hiệu (emit event) ra ngoài Frontend để cập nhật giao diện giám sát (Dashboard/War Room).
6. **Client (Consumer)** yêu cầu lấy tin, **Network** chuyển lệnh xuống **Core**, **Core** lấy offset hiện tại, tra cứu qua **Storage**, và trả dữ liệu ngược lại cho Client.

---

## 👥 Chi tiết Phân Công & Trách nhiệm (Team Responsibilities)

### 1. Phần Network & Observability (Thành viên 1)
**Vai trò:** Đóng vai trò là "Cánh cửa" của hệ thống, giao tiếp với cả Client (qua TCP) và Frontend (qua WebSocket).

* **`TcpServerImpl` & `ClientHandler`**: Lắng nghe kết nối TCP từ các Producer và Consumer. Nhận byte array, deserialize thành `MessagePacket` và chuyển xuống tầng Core xử lý.
* **`BrokerWebSocketBridge` (NEW)**: Đây là một bước đột phá của hệ thống. Nó hoạt động như một kênh giám sát (Telemetry). Khi có sự kiện xảy ra bên trong Broker (như ghi tin nhắn mới, bầu chọn Leader, hay nhân bản dữ liệu), Bridge này sẽ "bắn" JSON data trực tiếp ra Frontend qua cổng WebSocket (ví dụ: port 9001, 9002).
* **Kết nối**: Layer này sẽ "tiêm" (inject) `IQueueManager` và `IClusterNode` vào để gọi các logic nghiệp vụ.

### 2. Phần Core & Queue (Thành viên 2)
**Vai trò:** Là "Trái tim" và "Bộ não điều phối" (Traffic Cop). Không trực tiếp lưu file hay mở mạng, nhưng là cầu nối liên kết chúng lại.

* **`QueueManagerImpl`**: Quản lý danh sách các `MessageQueue` theo từng `Topic`. Nhận lệnh từ Network và đẩy xuống Storage.
* **Memory & Disk Toggling**: Đảm bảo tin nhắn được đẩy vào RAM (nếu cần xử lý nhanh) và đẩy xuống Disk (thông qua StorageManager) để không bao giờ mất dữ liệu.
* **`ConsumerOffsetManager`**: Ghi nhớ "vệt đọc" (offset) của từng Consumer Group. Biết được ông khách hàng A đã đọc đến tin nhắn số mấy để lần sau trả đúng tin nhắn tiếp theo.
* **Kết nối**: Core gọi xuống Storage (để đọc/ghi) và phản hồi lại cho Network.

### 3. Phần Cluster & Client SDK (Thành viên 3)
**Vai trò:** Quản lý sự sống còn của cụm máy chủ và cung cấp công cụ cho người dùng bên ngoài.

* **Cluster - Leader Election (`RaftNodeImpl`)**: Áp dụng thuật toán Raft. Khi bật hệ thống, 3 Broker sẽ "cãi nhau" xem ai làm Leader dựa trên nhiệm kỳ (Term). Chỉ Leader mới được phép nhận tin nhắn từ Producer.
* **Cluster - Data Replication (`Replicator`)**: Sau khi Leader ghi tin nhắn xuống đĩa (nhờ Core/Storage), nó phải dùng Replicator để đẩy tin nhắn đó sang 2 Broker còn lại (Followers) để backup.
* **Client SDK (`Producer`, `Consumer`)**: Đóng gói các logic TCP phức tạp thành thư viện dễ dùng cho lập trình viên bên ngoài (chỉ cần gọi `producer.send()` hoặc `consumer.poll()`).
* **Kết nối**: Cluster lắng nghe gói tin từ Network, và Client SDK gửi gói tin đến Network.

### 4. Phần Storage Layer (Hưng)
**Vai trò:** Là "Kho báu" của hệ thống, quản lý việc đọc/ghi vật lý xuống ổ cứng với hiệu năng cực cao.

* **`LogSegment`**: Ghi nối tiếp (Append-only) dữ liệu thực tế của tin nhắn. Cơ chế này giúp ổ cứng không phải tìm kiếm (seek), tốc độ ghi có thể sánh ngang RAM.
* **`IndexSegment`**: Lưu "Tọa độ" (Offset -> Byte Position). Sử dụng `MappedByteBuffer` (Zero-copy) để tra cứu cực nhanh xem tin nhắn số 1000 nằm ở Byte thứ mấy trong file Log.
* **Persistence**: Đảm bảo mọi luồng I/O được `flush()` an toàn và đóng file (`close()`) khi tắt máy để không bị hỏng file (corrupted).
* **Kết nối**: Chỉ làm việc trực tiếp với ổ cứng và cung cấp API `IStorageManager` cho tầng Core gọi xuống.

---

## 🐳 Triển khai Docker & Sidecar Pattern (DevOps)

Để chạy 3 Broker cùng lúc mà không bị xung đột, chúng ta sử dụng `docker-compose.yml`.

### Docker Architecture
Trong file `docker-compose.yml`, chúng ta định nghĩa 3 services: **broker-1, broker-2, broker-3**.
* Chúng giao tiếp nội bộ với nhau qua `mq-network`.
* Dữ liệu (Storage của Hưng) được mount (gắn) ra ngoài máy thật thông qua `volumes: ./data/node-1:/app/data`. Nghĩa là dù container bị xóa, dữ liệu vẫn an toàn trên máy bạn.

### Khái niệm "Sidecar" & Web Frontend
Trong kiến trúc Cloud, **Sidecar** là một tiến trình chạy song song với tiến trình chính (Broker) để hỗ trợ các việc phụ trợ (như Logging, Monitor) mà không làm nặng tiến trình chính.

Trong dự án này, **`BrokerWebSocketBridge` đóng vai trò gần giống một In-app Sidecar**:
1. Tiến trình chính là TCP Server (chạy ở cổng 8888, 8889, 8890) xử lý hàng triệu tin nhắn.
2. Song song đó, `BrokerWebSocketBridge` mở cổng WebSocket (9001, 9002, 9003).
3. **Mục đích:** Khi làm ứng dụng Frontend (ví dụ: Màn hình Flash Sale), trình duyệt web (HTML/JS) không thể kết nối TCP trực tiếp đến cổng 8888 được. Trình duyệt chỉ hiểu HTTP hoặc WebSocket.
4. Do đó, Frontend sẽ mở kết nối WebSocket đến `ws://localhost:9001`. Mọi trạng thái bên trong Broker (RAM, CPU, tin nhắn mới) sẽ được Bridge này "tuồn" ra Frontend theo thời gian thực (Real-time).

Điều này giúp Frontend vẽ được các biểu đồ cực kỳ sống động về luồng dữ liệu đang chảy trong hệ thống của các bạn!
