# Distributed Message Queue with Persistence

A high-performance, index-backed, consensus-driven distributed message queue built in Java. Designed to handle high-concurrency event streams (such as a Shopee Flash Sale), the system combines persistent append-only logs with Raft consensus-driven replication, a Docker failover orchestration sidecar, and a real-time React telemetry dashboard.

---

## 1. System Architecture & Port Allocations

The ecosystem consists of **5 main components** communicating over distinct network ports:

```
┌────────────────────────────────────────────────────────────────────────┐
│                            React Frontend                              │
│                         (Port 3000 / 5173)                             │
└─────────────▲───────────────────────────────────┬──────────────────────┘
│ (Real-Time Telemetry)             │ (Docker Lifecycle Control)
│ WebSockets (Ports 9001-9003)      │ HTTP POST (Port 3001)
┌─────────────┴─────────────┐           ┌─────────▼──────────┐
│    BrokerWebSocketBridge  │           │ Docker Sidecar     │
│     (Inside JVM Nodes)    │           │ Orchestrator (Node)│
└─────────────▲─────────────┘           └─────────┬──────────┘
│ (In-Memory JVM Hooks)             │ (Shell Commands)
┌─────────────┴─────────────┐                     │
│    Clustered MQ Nodes     │◄────────────────────┘
│ (broker-1, broker-2, -3)  │ docker stop/start broker-x
└───────────────────────────┘
```

| Component | Port | Network Interface | Description |
| :--- | :--- | :--- | :--- |
| **broker-1 (Node A)** | `8888` / `9001` | TCP / WebSocket | JVM Broker TCP Service / WebSocket UI Bridge |
| **broker-2 (Node B)** | `8889` / `9002` | TCP / WebSocket | JVM Broker TCP Service / WebSocket UI Bridge |
| **broker-3 (Node C)** | `8890` / `9003` | TCP / WebSocket | JVM Broker TCP Service / WebSocket UI Bridge |
| **Orchestrator Sidecar** | `3001` | HTTP (REST API) | Node.js gateway that executes container stop/start shells |
| **Observability Portal** | `3000` / `5173` | HTTP (Web Browser)| Vite-React frontend operations console |

---

## 2. Prerequisites

Ensure your host machine has the following tools installed and active:
* **Java Development Kit (JDK)**: Version 21 or higher (configured in your PATH)
* **Apache Maven**: Version 3.x or higher
* **Node.js**: Version 18.x or higher (includes `npm`)
* **Docker & Docker Compose**: Active running daemon (Docker Desktop)

---

## 3. Step-by-Step Run Procedures (Strict Order of Operations)

Follow these steps in the exact order specified to start the clustered messaging ecosystem:

### Step 1: Package Java Sources and Dependency Libraries
Before building the container images, we must compile the Java bytecode and export external dependency JARs (like `Java-WebSocket`) so they are available in the Docker build context.

Open your terminal at the root of `ds-distributed-message-queue/` and run:
```bash
mvn clean package dependency:copy-dependencies -DoutputDirectory=target/dependency
```
*Verify that the compilation succeeds and both `target/classes` and `target/dependency` directories are populated.*

---

### Step 2: Start the Clustered Brokers (Docker Compose)
This starts the three containerized brokers (`broker-1`, `broker-2`, `broker-3`) on their unified bridge network (`mq-network`), mounts persistent local storage volumes on your hard drive, and begins the Raft leader election.

In the same terminal (or a new window at the project root), execute:
```bash
docker-compose up --build
```
*Keep this window open. Watch the logs to verify that the WebSocket bridges are active on ports `9001-9003` and a leader has been elected (e.g., `Node broker-1:8888 has become Leader of term 1`).*

---

### Step 3: Start the Docker Orchestrator Sidecar
The orchestrator converts HTTP command payloads from the UI into standard `docker stop` or `docker start` actions.

Open a **second terminal window**, navigate to the orchestrator directory, and start the Node process:
```bash
cd orchestrator
node index.js
```
*Verify that you see: `[ORCH ...] Docker Orchestration Sidecar active on http://127.0.0.1:3001`.*

---

### Step 4: Start the React Frontend Portal
This compiles your styles, renders the dashboard components, and opens WebSocket client telemetry pipelines.

Open a **third terminal window**, navigate to the frontend directory, and start Vite:
```bash
cd frontend
npm run dev
```
*Vite will start. Open your web browser and navigate to the address shown in the output (typically **`http://localhost:3000/`** or **`http://localhost:5173/`**).*

---

### Step 5: Run the Java Client SDKs (Verification)

Once the portal is open and connected to the running cluster, you can launch the external Java clients to verify data transmission:

* **Terminal A (Start the Consumer)**:
  Runs a polling consumer client that handles automatic leader redirection and commits reading offsets:
  ```bash
  cd ds-distributed-message-queue
  mvn exec:java -Dexec.mainClass="com.shopee.queue.client.Consumer"
  ```

* **Terminal B (Run the Producer)**:
  Dispatches a simulated Shopee Flash Sale order transaction to the cluster:
  ```bash
  cd ds-distributed-message-queue
  mvn exec:java -Dexec.mainClass="com.shopee.queue.client.Producer"
  ```

---

## 4. Demonstration Guide (The Failover Narrative)

To demonstrate the high availability and transactional write-safety of this architecture, follow this narrative flow on your active dashboard:

1. **Prerun Alignment**: Verify that the browser UI shows all 3 nodes healthy and connected (`3/3 ALIVE`). The `offsets` and `diskGB` stats will display the actual historical sizes recovered from your physical drive on startup.
2. **Standard Load Ingestion**: Click the **BUY NOW** button. You will see an immediate order registered on the screen, a replication pulse (orange dot animation) travel to the followers, and the terminal log feed output a live disk append log.
3. **Chaos Burst**: Toggle **Chaos Mode ON**. The client starts flooding the active Leader’s WebSocket with parallel writes. The operations chart will spike, the progress bars inside the Disk Explorer will fill up sequentially, and log files will rotate dynamically on your disk as they hit their size limits.
4. **The Failover Test**: While Chaos Mode is still running, click the red **KILL NODE** button next to the active leader on the UI (this triggers a `docker stop` command behind the scenes).
   * **The Outage**: The killed container goes offline. The node card turns red (`DEAD`) on the UI.
   * **The Election**: The remaining followers detect the heartbeat timeout, transition to candidates, and elect a new Leader within 2 seconds.
   * **The Recovery**: The new leader turns gold (`LEADER`) on your screen. The write traffic continues smoothly, and no data is lost because the previous records were already committed to the survivors' persistent volumes.