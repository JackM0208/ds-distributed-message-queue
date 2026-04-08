distributed-queue/
├── pom.xml
└── src/
└── main/
└── java/
└── com/
└── shopee/
└── queue/
│
├── BrokerMain.java             
│   # ROLE: The Entry Point.
│   # WHAT IT DOES: The `public static void main` method. It wires everything together.
│   # RELATIONSHIPS: Instantiates the `QueueManager`, starts the `TcpServer`, and bootstraps the `RaftNode`.
│
├── config/                     
│   └── BrokerConfig.java
│       # ROLE: Global Configuration (Single Source of Truth).
│       # WHAT IT DOES: Holds constants like MAX_SEGMENT_SIZE (100MB), PORT (9092), and DATA_DIR.
│       # RELATIONSHIPS: Read by `StorageManager`, `TcpServer`, and `QueueManager`. No hardcoded magic numbers in the logic!
│
├── exceptions/                 
│   └── BrokerException.java
│       # ROLE: Standardized Error Handling.
│       # WHAT IT DOES: A custom RuntimeException wrapper.
│       # RELATIONSHIPS: Thrown by `StorageManager` (e.g., Disk Full) or `ClientHandler` (Malformed Packet). Caught by `TcpServer` to return a clean error to the client instead of crashing the broker.
│
├── protocol/                   
│   └── MessagePacket.java      
│       # ROLE: The Data Transfer Object (DTO).
│       # WHAT IT DOES: Represents a single unit of work (e.g., a Shopee order). Knows how to serialize itself to bytes and deserialize from bytes.
│       # RELATIONSHIPS: Passed around by EVERY component. Created by `ClientHandler`, stored by `LogSegment`, pulled by `Replicator`.
│
├── core/                       
│   ├── QueueManager.java       
│   │   # ROLE: The Traffic Cop (Singleton).
│   │   # WHAT IT DOES: Holds a Thread-Safe map of all active queues (e.g., Map<"shopee-orders", MessageQueue>). Creates new queues on the fly.
│   │   # RELATIONSHIPS: `ClientHandler` asks this class: "Give me the queue named X".
│   │
│   ├── MessageQueue.java       
│   │   # ROLE: The Logical Queue.
│   │   # WHAT IT DOES: Represents one specific topic. Assigns the auto-incrementing Offset ID to incoming messages.
│   │   # RELATIONSHIPS: Owned by `QueueManager`. Wraps and delegates actual saving to `StorageManager`.
│   │
│   └── ConsumerOffsetManager.java
│       # ROLE: The Crash-Recovery Tracker.
│       # WHAT IT DOES: Remembers that "Consumer Group A is currently at offset #500 for shopee-orders". Persists this to a system file.
│       # RELATIONSHIPS: Updated by `ClientHandler` when an ACK packet arrives. Read by `ClientHandler` when a consumer reconnects.
│
├── storage/                    
│   ├── StorageManager.java     
│   │   # ROLE: The Disk Controller.
│   │   # WHAT IT DOES: Prevents files from growing infinitely. When a log file hits 100MB, it "rolls over" and creates a new one seamlessly.
│   │   # RELATIONSHIPS: Owned by `MessageQueue`. Manages multiple `LogSegment` and `IndexSegment` objects.
│   │
│   ├── LogSegment.java         
│   │   # ROLE: The Muscle (Append-Only Log).
│   │   # WHAT IT DOES: Uses Java NIO to sequentially write raw `MessagePacket` bytes to the physical hard drive.
│   │   # RELATIONSHIPS: Called exclusively by `StorageManager`.
│   │
│   └── IndexSegment.java       
│       # ROLE: The Speed Engine (Memory-Mapped File).
│       # WHAT IT DOES: Maps logical offsets (ID #500) to physical byte locations (Byte 4,096) for O(1) instant read lookups.
│       # RELATIONSHIPS: Called exclusively by `StorageManager`.
│
├── network/                    
│   ├── TcpServer.java          
│   │   # ROLE: The Front Door.
│   │   # WHAT IT DOES: Listens on a port (e.g., 9092). Accepts incoming TCP connections from Producers and Consumers.
│   │   # RELATIONSHIPS: When a connection arrives, it spawns a `ClientHandler` thread and hands the socket to it.
│   │
│   └── ClientHandler.java      
│       # ROLE: The Translator & Router.
│       # WHAT IT DOES: Reads raw bytes from the network, converts them into a `MessagePacket`. If it's a Publish request, it routes it to the `QueueManager`. If it's a Consume request, it reads from the queue and flushes bytes back to the client.
│       # RELATIONSHIPS: Bridges `TcpServer` with `QueueManager` and `ConsumerOffsetManager`.
│
├── cluster/                    
│   ├── RaftNode.java           
│   │   # ROLE: The Consensus Brain (High Availability).
│   │   # WHAT IT DOES: Pings other brokers. Holds elections. Decides if *this* broker is the Leader or a Follower for a specific queue.
│   │   # RELATIONSHIPS: Blocks the `QueueManager` from accepting writes if this broker is not the Leader.
│   │
│   └── Replicator.java         
│       # ROLE: The Data Copier.
│       # WHAT IT DOES: If this broker is the Leader, this class tails the `LogSegment` and forwards newly appended bytes to Follower brokers via internal TCP sockets.
│       # RELATIONSHIPS: Reads from `StorageManager`, sends data to other nodes' `TcpServer`.
│
└── client/                     
├── Producer.java
│   # ROLE: The External Writer API.
│   # WHAT IT DOES: A library for the Shopee Web Server to use. Batches orders together and sends them over TCP to the Broker.
│   # RELATIONSHIPS: Talks to the Broker's `TcpServer`.
│
└── Consumer.java
# ROLE: The External Reader API.
# WHAT IT DOES: A library for the Shopee Payment Server. Polls the broker for new messages and sends back Acknowledgments (ACKs) when done.
# RELATIONSHIPS: Talks to the Broker's `TcpServer`.