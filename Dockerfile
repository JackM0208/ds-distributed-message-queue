# Sử dụng JDK 21 bản nhẹ
FROM eclipse-temurin:21-jre-alpine

# Tạo thư mục làm việc trong container
WORKDIR /app

# Copy toàn bộ file .class và thư viện đã build từ máy thật vào container
COPY target/classes /app/classes
COPY target/dependency /app/dependency

# Mở cổng 8888 bên trong container
EXPOSE 8888

# Lệnh chạy Broker (Tham số port sẽ được truyền từ docker-compose)
ENTRYPOINT ["java", "-cp", "/app/classes:/app/dependency/*", "com.shopee.queue.BrokerMain"]