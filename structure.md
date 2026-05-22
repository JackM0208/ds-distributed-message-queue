ds-distributed-message-queue/
├── Bao_Cao_Network_Layer.md
├── Dockerfile
├── README.md
├── classpath.txt
├── data/                            # Persistent storage for node data and log segments
│   ├── node-1/
│   ├── node-2/
│   ├── node-3/
│   └── test-persistence/
├── docker-compose.yml
├── frontend/                        # React/Vite dashboard for monitoring
│   ├── src/
│   │   ├── FlashSaleWarRoom.jsx
│   │   ├── index.css
│   │   └── main.jsx
│   └── package.json
├── knowledge.md
├── orchestrator/                    # Node.js orchestrator for cluster management
├── pom.xml                          # Maven dependencies (Netty, Jackson, etc.)
├── pthDocument.md
├── src/
│   ├── main/java/com/shopee/queue/
│   │   ├── BrokerMain.java          # THE ASSEMBLER. Initializes and starts the broker services.
│   │   ├── api/                     # Service Interfaces (The Contracts)
│   │   │   ├── IClusterNode.java    # Defines Raft-related operations
│   │   │   ├── IQueueManager.java   # Defines topic and message management
│   │   │   ├── IServer.java         # Defines server lifecycle (start/stop)
│   │   │   └── IStorageManager.java # Defines log and index management
│   │   ├── client/                  # External SDKs (Producer/Consumer)
│   │   │   ├── Consumer.java        # Polls messages and sends ACKs
│   │   │   └── Producer.java        # Sends messages to the broker
│   │   ├── cluster/                 # High Availability (Raft implementation)
│   │   │   ├── ClusterClient.java   # Communication between cluster nodes
│   │   │   ├── RaftNodeImpl.java    # Heartbeats, election, and state machine
│   │   │   └── Replicator.java      # Replicates log data from leader to followers
│   │   ├── common/                  # Shared Resources
│   │   │   ├── config/
│   │   │   │   └── BrokerConfig.java # System-wide constants and configurations
│   │   │   └── exceptions/
│   │   │       ├── BrokerEx.java
│   │   │       └── QueueEx.java
│   │   ├── core/                    # Business Logic (Traffic Cop)
│   │   │   ├── ConsumerOffsetManager.java # Tracks reading progress for consumer groups
│   │   │   ├── MessageQueue.java    # Per-topic logic and storage coordination
│   │   │   └── QueueManagerImpl.java # Routes traffic to specific MessageQueues
│   │   ├── network/                 # Front Door (TCP & WebSockets)
│   │   │   ├── BrokerWebSocketBridge.java # Bridges TCP data to monitoring frontend
│   │   │   ├── ClientHandler.java   # Netty handler for decoding/routing packets
│   │   │   ├── TcpServerImpl.java   # Netty-based TCP server implementation
│   │   │   └── protocol/
│   │   │       └── MessagePacket.java # The DTO for network and disk serialization
│   │   └── storage/                 # Disk I/O (The Muscle)
│   │       ├── IndexSegment.java    # Memory-mapped index (Offset -> Position)
│   │       ├── LogSegment.java      # Sequential append-only data files
│   │       └── StorageManagerImpl.java # Manages log/index lifecycle and file rotation
│   └── test/java/com/shopee/queue/   # Unit and Integration Tests
│       ├── cluster/
│       ├── core/
│       ├── network/
│       └── storage/
└── structure.md
