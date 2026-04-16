# Distributed Pub/Sub Message Queue: Project Documentation & Review

This document compiles the architectural overview, structural breakdown, and detailed Q&A from the project discussions. It serves as a comprehensive guide for understanding the system's design, components, and workflow.

---

## 1. Project Overview & Architecture

This project is a **Distributed Message Queue**, which acts as a high-performance, persistent "post office" for digital messages. Its primary goal is to reliably store and transmit data between different services, functioning as a high-speed shock absorber (buffer) between fast producers and potentially slow or offline consumers.

### Recent Architectural Changes (Branch: `feature/core-message-packet`)
A recent refactoring transitioned the system to a modular, "Plug-and-Play" design utilizing Dependency Injection.
* **Interface-Driven Design:** The system relies on clean interfaces defined in the `com.shopee.queue.api` package (`IServer`, `IQueueManager`, `IStorageManager`, `IClusterNode`).
* **New Network Protocol:** A core Data Transfer Object (DTO) named `MessagePacket` was introduced for standard communication.
* **Modernized Dependencies:** Integrated Netty for high-performance NIO networking, SLF4J for logging, and JUnit 5 / Mockito for testing.

### The 4 Pillars (Key Components)
The system is divided into four distinct layers:
1. **Network Layer (`IServer` / `TcpServerImpl`):** The "Doorway". Uses Netty to listen for TCP connections from producers and consumers.
2. **Core Logic (`IQueueManager` / `QueueManagerImpl`):** The "Traffic Cop". Manages topics and tracks where consumers left off via offsets.
3. **Storage Layer (`IStorageManager` / `StorageManagerImpl`):** The "Muscle". Writes messages to disk using Append-Only Logs for maximum speed.
4. **Cluster Layer (`IClusterNode` / `RaftNodeImpl`):** The "Brain". Uses the Raft Consensus Algorithm to ensure all brokers in the cluster agree on the stored messages.

---

## 2. Project Structure

Based on the project's layout, here is the functional organization of the codebase:

* **`pom.xml`**: Manages Maven dependencies like Netty and logging libraries.
* **`src/main/java/com/shopee/mq/BrokerMain.java`**: The Assembler and start file. It reads configurations, instantiates implementations, injects them via API interfaces, and starts the server.
* **`common/`**: Contains shared resources like `BrokerConfig.java` (constants, ports, file sizes) and `BrokerEx.java` (custom exceptions).
* **`api/`**: The contracts layer containing pure interface definitions (`IQueueManager`, `IStorageManager`, `IClusterNode`, `IServer`).
* **`core/`**: The business logic and traffic routing, containing `QueueManagerImpl.java` to map topics to queues.
* **`network/`**: The gateway using Netty. Contains `TcpServerImpl.java`, `ClientHandler.java` (translates TCP streams to `MessagePacket`), and `protocol/MessagePacket.java` (the DTO).
* **`cluster/`**: The high-availability brain, featuring `RaftNodeImpl.java` for leader election/heartbeats and `Replicator.java` for copying bytes.
* **`client/`**: External SDKs containing `Producer.java` (sends data) and `Consumer.java` (polls for data).

---

## 3. System Workflows

### Setup & Entry Point
The application starts at `BrokerMain.java`. When run, it performs Dependency Injection in this order:
1. Creates the `StorageManagerImpl`.
2. Creates the `RaftNodeImpl`.
3. Creates the `QueueManagerImpl`.
4. Creates the `TcpServerImpl`.
5. Calls `start()` to open the network port.

### End-to-End Flow: Producer to Consumer
**Phase 1: Inbound Journey (Receiving)**
1. A Producer sends a `MessagePacket` over TCP.
2. `TcpServerImpl` receives the raw bytes.
3. `ClientHandler` decodes the bytes into a `MessagePacket` object.
4. `ClientHandler` calls `IQueueManager.pushMessage()`.
5. `QueueManagerImpl` asks `RaftNodeImpl` if the cluster agrees to commit the message.

**Phase 2: Persistence Heart (Saving)**
1. Once approved, `QueueManagerImpl` calls `IStorageManager.appendToLog()`.
2. `StorageManagerImpl` writes the actual bytes to the physical file (`LogSegment`) using Sequential I/O (appending to the end).
3. It simultaneously writes an entry in the `IndexSegment` (a map) mapping the MessageID to its exact byte position in the log for instant lookup.
4. The Broker sends a "Success" acknowledgement back to the Producer.

**Phase 3: Outbound Journey (Sending)**
1. A Consumer sends a "Pull" request.
2. The `ConsumerOffsetManager` checks where this consumer left off (its offset).
3. `StorageManagerImpl` uses the `IndexSegment` to find the exact byte location and reads the message from the `LogSegment`.
4. The bytes are wrapped in a `MessagePacket` and sent via `TcpServerImpl`.
5. Upon consumer confirmation, `ConsumerOffsetManager` updates its offset record.

---

## 4. Comprehensive Q&A (Review Session)

**Q1: I just pull the main, tell me what has changed, the branch is `feature/core-message-packet`, my friend just did something there, he pushed and now I need to know what he did?**
**A:** Your friend performed a significant architectural overhaul towards a "Plug-and-Play" design using Dependency Injection. Key changes include defining clean interfaces in `com.shopee.queue.api`, introducing a new DTO `MessagePacket` for network communication, rewriting `BrokerMain` to act as an Assembler, modernizing dependencies (Netty, SLF4J, JUnit 5, Lombok), and adding comprehensive unit tests.

**Q2: Give me a high overview over everything of the project, tell me about the components of this project, what does it do, where does it start, give me an example flow.**
**A:** The project is a Distributed Message Queue that reliably stores and transmits data. It starts in `BrokerMain.java` which initializes the components via Dependency Injection. Components include the Network Layer (`TcpServerImpl`), Core Logic (`QueueManagerImpl`), Storage Layer (`StorageManagerImpl`), and Cluster Layer (`RaftNodeImpl`). Flow example: A producer sends a packet -> `TcpServerImpl` receives it -> `ClientHandler` decodes it -> `QueueManager` routes it -> `Raft` validates it -> `StorageManager` writes it to disk -> Broker ACKs the Producer.

**Q3: What is the job of the consumer and producer here? Do I have to run them in different terminals? Can you give a clear setup?**
**A:** Yes, they simulate a distributed system and should be run in separate terminals.
* **Terminal 1 (Broker):** Runs `BrokerMain.java`. Stays open, manages storage and connections.
* **Terminal 2 (Consumer):** Runs the Consumer SDK script. Continually polls the Broker for new messages.
* **Terminal 3 (Producer):** Runs the Producer SDK script. Sends a message to the Broker and exits.

**Q4: They connect through TCP right, can I do that on different computers?**
**A:** Absolutely. By replacing `localhost` with the Broker's actual IP address in the Producer and Consumer code (e.g., `192.168.1.15`), and opening the necessary firewall port (e.g., 8888), you can run them on completely different machines across the network. This provides massive scalability and fault tolerance.

**Q5: Yeah but the producer must use the format of the DTO message packet to send an object of that DTO right?**
**A:** Correct. The `MessagePacket.java` is the "Contract". Both the producer and the broker must agree on this shape. The Producer *serializes* the object into a byte stream, and the Broker *deserializes* it back. If formats don't match, the Broker will throw a serialization error.

**Q6: Okay now I understand that the producer sends MessagePacket to the BrokerMain as a queue and then the consumer tries to read from it so that the broker can store them real fast and the consumer can read later, confirm my knowledge?**
**A:** Your knowledge is 100% correct. This is called *Decoupling and Buffering*. The Broker acts as a high-speed shock absorber using fast Append-Only Logs, allowing it to instantly save bursts of messages while the slower consumer processes them at its own pace, even if it goes offline and reads them later (Persistence).

**Q7: Explain everything inside of the broker, from the moment it receives the message packets to sending them... point out all the classes and the workflow.**
**A:** 1. **Phase 1 (Inbound):** `TcpServerImpl` accepts bytes -> `ClientHandler` decodes to `MessagePacket` -> `QueueManagerImpl` coordinates -> `RaftNodeImpl` syncs with cluster for safety.
2. **Phase 2 (Persistence):** `StorageManagerImpl` is called -> `LogSegment` appends bytes to disk -> `IndexSegment` logs the exact byte location.
3. **Phase 3 (Outbound):** Consumer requests data -> `ConsumerOffsetManager` checks the offset -> `StorageManagerImpl` retrieves the specific bytes from disk -> `TcpServerImpl` sends it out.

**Q8: When the QueueManager does .pushMessage, does it just call the StorageManager? What is the point of the RaftNodeImpl and the leader? Do we use internal queues? Why use bytes for indexing? Do we delete messages when read?**
**A:**
* **QueueManager:** It acts as an Orchestrator; it validates the message and primarily hands it off to `StorageManager` to make it permanent. The "Queue" is actually the file on the disk.
* **RaftNodeImpl (High Availability):** It ensures consensus. The elected "Leader" writes the message and copies it to "Followers." If the leader dies, followers elect a new one, preventing data loss.
* **Internal Queues:** Not used (like `java.util.Queue`) to avoid losing data in RAM during a crash. The physical disk file is the actual queue, and backpressure handles network overflow.
* **Indexing by Bytes vs. Lines:** Bytes allow the OS to perform O(1) constant-time jumps instantly to the correct position (e.g., jump exactly to byte 54,321), whereas counting lines is an O(n) slow scan.
* **Deleting Messages:** We *do not* delete messages after a consumer reads them. This allows multiple consumer groups to read the same message and permits replay if a consumer crashes. Clean-up is handled by a Retention Policy (e.g., delete files older than 7 days).

**Q9: Where does the `ConsumerOffsetManager` live? Inside the broker right?**
**A:** Yes, it lives inside the Broker's core logic (`src/main/java/com/shopee/queue/core/ConsumerOffsetManager.java`). It acts as a tracking table that records the reading progress (offsets) of every consumer group so they don't receive duplicate messages.

Think of it as a Big Table inside the Broker's memory:
| Consumer Group      | Topic    | Last Committed Offset |
|---------------------|----------|-----------------------|
| "Shipping-Service"  | "orders" | 1050                  |
| "Billing-Service"   | "orders" | 980                   |
| "Analytics-Service" | "orders" | 1050                  |

* **The "Stateful" Broker:** Because the Broker keeps this info, the Consumer is "stateless". If a consumer crashes and restarts, it just asks the Broker where it left off.
* **Persistence:** The Broker saves this table to disk (e.g., `__consumer_offsets`) so offsets aren't lost if the Broker restarts.
* **Why not on the Consumer?** If a consumer's disk fails, it loses its place. Plus, the Broker wouldn't be able to monitor if a consumer is lagging behind.

**Q10: Why do I need to replicate the message when storing to files? Where is the "distributed" part of this project? Can I do this with multiple computers?**
**A:** 1. **Why Replicate (The Safety Net):** If you only save a message to one computer, it becomes a Single Point of Failure (SPOF). We replicate (copy) the message to at least two other computers (Followers) before telling the Producer it was successful. This ensures Fault Tolerance.
2. **The "Distributed" Part:** The `RaftNodeImpl` (Consensus Algorithm) and `Replicator` (Data Copier) turn independent brokers into a unified "Cluster". The cluster votes for a Leader, and the Replicator pushes bytes from the Leader to the Followers over the network.
3. **Multiple Computers:** YES! This is how you get the true benefit.
    * **Flow:** Producer sends a message to Node-0 (Leader) -> Node-0 calls `Replicator.pushToFollowers()` to send to Node-1 and Node-2 -> Node-1 and Node-2 save it -> Node-0 sees a majority (2 out of 3) have saved it -> Node-0 tells the Producer "Success."

**Distributed vs. Local System Comparison:**
| Feature       | Local System (1 PC)                 | Distributed System (3+ PCs)             |
|---------------|-------------------------------------|-----------------------------------------|
| Storage       | 1 Hard Drive                        | 3+ Hard Drives (Replicated)             |
| If PC Crashes | System is DOWN; data might be lost. | System stays UP; other nodes take over. |
| Performance   | Limited by one PC's CPU/Disk.       | Can scale by adding more Brokers.       |
| Logic         | Simple file writing.                | Complex networking + Raft Consensus.    |