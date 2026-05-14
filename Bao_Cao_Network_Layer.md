# Báo Cáo Hoàn Thành: Network Layer (TCP Sockets)

## 🎯 Mục tiêu & Bối cảnh
Xây dựng nền tảng mạng (Network Layer) cho hệ thống Message Queue phân tán. Mục tiêu là cho phép các **Producer** và **Consumer** kết nối tới **Broker** thông qua giao thức TCP bền vững.

---

## 🛠️ Công nghệ & Giải pháp
Thay vì sử dụng các thư viện phức tạp, lớp Network được xây dựng bằng các công cụ chuẩn của Java để đảm bảo tính minh bạch và dễ bảo trì:
- **Java ServerSocket:** Lắng nghe và chấp nhận các kết nối TCP.
- **Multithreading:** Sử dụng luồng riêng (`ServerListenerThread`) để không làm nghẽn hệ thống khi chờ kết nối. Mỗi client mới sẽ được xử lý bởi một luồng `ClientHandler` riêng biệt.
- **Java Serialization:** Tương thích với `MessagePacket.java` (đã hoàn thành bởi Minh Phan).
- **Shutdown Hook:** Cơ chế đóng port an toàn khi tắt ứng dụng.

---

## 📑 Các thành phần đã triển khai

### 1. `TcpServerImpl.java` (Cổng chính)
- Lắng nghe trên cổng **8888** (mặc định trong `BrokerConfig`).
- Tự động spawn luồng xử lý cho mỗi kết nối mới.

### 2. `ClientHandler.java` (Xử lý kết nối)
- Đã được cập nhật đầy đủ logic đọc/ghi dòng dữ liệu (byte stream) thông qua `ObjectInputStream` và `ObjectOutputStream`.
- Có thể ép kiểu thành công các luồng byte sang `MessagePacket.java`.
- Tự động nhận diện `MessagePacket` gửi lên, kiểm tra xem có hợp lệ hay không, và trả về một `MessagePacket` mang type=2 (đại diện cho ACK) qua chiều OutputStream để xác nhận với Client.
- Quản lý đóng mở Socket an toàn (`Graceful Connection Terminate`) thông qua cơ chế bắt Exception khi client ngắt kết nối.

### 3. `BrokerMain.java` (Điều phối hệ thống)
- Khởi tạo tất cả các thành phần.
- Cấu hình đóng server an toàn khi bị tắt đột ngột (Graceful Shutdown).

### 4. `SimpleProducer.java` (Công cụ kiểm tra)
- Đã được cấu hình để gửi một `MessagePacket` với payload thật (`"Sample Order Data"`) tới Broker.
- Đã có khả năng nhận gói tin ACK phản hồi từ Broker thông qua cơ chế Socket hai chiều.

---

## ✅ Kết quả kiểm tra (Verification)
/local test
- **Build:** Sẵn sàng biên dịch thành công 100%.
- **Broker:** Đã khởi động và lắng nghe tại cổng 8888, chờ Object gửi tới.
- **Client Test:** Kết nối thành công. Gửi đi thông điệp: `MessagePacket{id = 1001, topic = shopee-orders, ...}`.
- **Response:** Client nhận lại được trả lời ACK từ Broker (type = 2). Broker không hề gặp tình trạng rò rỉ bộ nhớ (leak memory) hay treo cờ (stuck).
- **Graceful Shutdown:** Khi Client đóng Socket, Broker ghi nhận Exception chính xác và dọn dẹp luồng (thread).

---

## 📢 Lưu ý phối hợp cho Team
-  Đã sử dụng đúng định dạng `MessagePacket` của bạn.
- **(Client SDK):** Bạn có thể bắt đầu xây dựng SDK dựa trên cổng **8888**. Giao thức hiện tại là **TCP** và dữ liệu được truyền dưới dạng **Java Serialized Objects**.
-  Mặc dù hiện tại dùng Socket thuần để đúng yêu cầu bài tập,giữ cấu trúc `IServer` để dễ dàng nâng cấp lên **Netty** sau này nếu cần hiệu năng cực cao.

---
