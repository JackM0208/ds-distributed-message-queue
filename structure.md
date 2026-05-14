distributed-pubsub-mq/
├── pom.xml                          # Maven dependencies (e.g., Netty for network, logging libraries)
└── src/main/java/com/shopee/mq/
├── BrokerMain.java              # THE ASSEMBLER. The only file that knows about all packages.
│                                # It reads the config, instantiates the Impl classes, injects
│                                # them into each other via the API interfaces, and starts the server.
│
├── common/                      # SHARED RESOURCES (Everyone uses this)
│   ├── config/BrokerConfig.java # Holds constants: Ports, Max File Sizes (e.g., 1GB), timeouts.
│   └── exceptions/BrokerEx.java # Custom runtime exceptions so the server doesn't crash silently.
│
├── api/                         # THE CONTRACTS (Team designs this together on Day 1)
│   │                            # RULE: No logic goes here. Only interface definitions.
│   ├── IQueueManager.java       # Defines: createTopic(), pushMessage(), pullMessage()
│   ├── IStorageManager.java     # Defines: appendToLog(), readFromOffset()
│   ├── IClusterNode.java        # Defines: requestVote(), replicateData(), getLeader()
│   └── IServer.java             # Defines: startServer(), stopServer()
│
├── core/                        # PERSON 3: THE TRAFFIC COP (Business Logic)
│   │                            # Goal: Route incoming traffic to the correct disk files.
│   ├── QueueManagerImpl.java    # Implements IQueueManager. Holds a Map<String, MessageQueue>.
│   │                            # When a packet arrives, it finds the right queue and passes it down.
│   ├── MessageQueue.java        # Represents a specific Topic (e.g., "Payments"). It validates
│   │                            # the message and tells the IStorageManager to save it.
│   └── ConsumerOffsetManager.java # The Tracker. Saves a small file to disk tracking that
│                                # "PaymentServiceGroup" has read up to offset #50,000.
│
├── storage/                     # PERSON 2: THE MUSCLE (Disk I/O)
│   │                            # Goal: Never drop a message, write to disk at blazing speeds.
│   ├── StorageManagerImpl.java  # Implements IStorageManager. Manages the lifecycle of files.
│   │                            # If a log file hits 1GB, this class freezes it and creates a new one.
│   ├── LogSegment.java          # THE QUEUE. Uses Java NIO (`FileChannel`) to do sequential
│   │                            # "Append-Only" writes to the hard drive. Bypasses normal Java
│   │                            # memory and writes straight to the OS Page Cache.
│   └── IndexSegment.java        # THE LOOKUP. Uses memory-mapped files (`MappedByteBuffer`).
│                                # Maps Offset #50,000 -> Byte Position 1,024,560 instantly.
│
├── network/                     # PERSON 1: THE FRONT DOOR (TCP & Serialization)
│   │                            # Goal: Handle 10,000+ simultaneous connections without crashing.
│   ├── TcpServerImpl.java       # Implements IServer. Uses Java NIO `ServerSocketChannel` (or Netty).
│   │                            # Listens for connections and delegates them to a thread pool.
│   ├── ClientHandler.java       # Translates raw TCP byte streams into `MessagePacket` objects.
│   │                            # Routes the packet to the `IQueueManager` (Person 3's code).
│   └── protocol/MessagePacket.java # The DTO. Knows how to serialize its fields (Topic, Payload,
│                                # Timestamp) into a raw `byte[]` for network and disk storage.
│
├── cluster/                     # PERSON 4: THE BRAINS (High Availability)
│   │                            # Goal: If someone unplugs Broker 1, Broker 2 takes over instantly.
│   ├── RaftNodeImpl.java        # Implements IClusterNode. Sends heartbeats to other brokers.
│   │                            # If the Leader dies, it initiates a vote to elect a new Leader.
│   └── Replicator.java          # The Copier. Acts as a TCP client inside the broker. It asks the
│                                # Leader's IStorageManager for new bytes and saves them locally.
│
└── client/                      # PERSON 1: THE EXTERNAL SDKs
│                            # Goal: Make it easy for other apps to talk to your Broker.
├── Producer.java            # Used by Shopee Web App. Has a simple method: `send(topic, data)`.
│                            # Under the hood, it connects via TCP and formats the MessagePacket.
└── Consumer.java            # Used by Shopee Payment App. Has a method: `poll()`.
# Connects via TCP, fetches unread messages, and sends an ACK back.