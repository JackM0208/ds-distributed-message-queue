# [DS] - Distributed_Message_Queue_Persistence
A distributed message broker with persistence - 80% Project for Distributed Systems course taken at VGU in the SS2026

## 🚀 Getting Started

### Prerequisites
- Java 17
- Maven

### Build the Project
```bash
mvn clean compile
```

### Run the Broker
To start the message broker server:
```bash
mvn exec:java -Dexec.mainClass="com.shopee.queue.BrokerMain"
```
The broker listens on port **8888** by default (configurable in `BrokerConfig.java`).

### Run the Test Producer
To verify connectivity, run the simple producer in another terminal:
```bash
mvn exec:java -Dexec.mainClass="com.shopee.queue.client.Producer"
```

## 📂 Network Protocol
- **Port:** 8888
- **Protocol:** TCP
- **Serialization:** Java Native Serialization (using `MessagePacket` class)
