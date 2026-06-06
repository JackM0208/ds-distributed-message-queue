import { useState } from "react";
import { X, ArrowRight, ArrowLeft } from "lucide-react";

export default function NodeInternalVisualizer({ onClose }) {
    const [activeStep, setActiveStep] = useState(0);

    const steps = [
        {
            title: "01. Network Ingestion & Packet Parsing",
            classNames: "TcpServerImpl.java | ClientHandler.java | MessagePacket.java",
            desc: "A raw binary byte sequence reaches the broker over TCP port 8888. TcpServerImpl accepts the incoming socket, mapping the connection to a lightweight virtual thread. ClientHandler reads the stream, validates limits, and deserializes the binary payload directly into a MessagePacket DTO.",
            highlight: "network"
        },
        {
            title: "02. Core Routing & Hot Cache Allocation",
            classNames: "QueueManagerImpl.java | MessageQueue.java",
            desc: "The parsed packet is handed over to QueueManagerImpl. The manager reads topic metadata from the packet and maps it to the target topic's volatile MessageQueue structure. It populates the payload directly inside an LRU-based hot memory cache for immediate access.",
            highlight: "core"
        },
        {
            title: "03. Append-Only Local Storage Engine",
            classNames: "StorageManagerImpl.java | LogSegment.java | IndexSegment.java",
            desc: "To guarantee durability, the write is sent to StorageManagerImpl. The manager allocates the active physical file pair, calling LogSegment.java to perform a sequential, high-speed write to the .log file via a NIO FileChannel append. Simultaneously, index offset coordinate structures are logged in IndexSegment.java.",
            highlight: "storage"
        },
        {
            title: "04. Consensus Synchronization & Replication",
            classNames: "RaftNodeImpl.java | Replicator.java",
            desc: "Once local storage succeeds, RaftNodeImpl initiates consensus verification. Replicator.java packages the raw transaction into replication events (Type 6), dispatching parallel asynchronously-replicated payloads out to Followers. The write is officially committed only when the quorum majority confirms the block.",
            highlight: "cluster"
        },
        {
            title: "05. Metric Aggregation & Telemetry Broadcast",
            classNames: "BrokerWebSocketBridge.java",
            desc: "Following a committed write, physical and network load statistics are aggregated. BrokerWebSocketBridge converts JVM heap usage, thread pools, active log segments, and commit offsets into JSON blocks, pushing them instantly down open WebSockets (ports 9001-9003) to keep the War Room dashboard accurate.",
            highlight: "telemetry"
        }
    ];

    const handleNext = () => {
        setActiveStep(prev => (prev + 1) % steps.length);
    };

    const handleLast = () => {
        setActiveStep(prev => (prev - 1 + steps.length) % steps.length);
    };

    // Scoped CSS styles to manage robust node highlighting & animations inside the single Broker Node SVG
    const STYLE_BLOCK = `
        @keyframes internal-glow-network {
            0%, 100% { stroke: #ea580c; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(234,88,12,0.15)); }
            50% { stroke: #ea580c; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(234,88,12,0.65)); }
        }
        @keyframes internal-glow-core {
            0%, 100% { stroke: #0284c7; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(2,132,199,0.15)); }
            50% { stroke: #0284c7; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(2,132,199,0.65)); }
        }
        @keyframes internal-glow-storage {
            0%, 100% { stroke: #8b5cf6; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(139,92,246,0.15)); }
            50% { stroke: #8b5cf6; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(139,92,246,0.65)); }
        }
        @keyframes internal-glow-cluster {
            0%, 100% { stroke: #b45309; stroke-width: 1.2px; filter: drop-shadow(0 0 1px rgba(180,83,9,0.15)); }
            50% { stroke: #b45309; stroke-width: 2.2px; filter: drop-shadow(0 0 5px rgba(180,83,9,0.55)); }
        }
        @keyframes internal-glow-telemetry {
            0%, 100% { stroke: #16a34a; stroke-width: 1.2px; filter: drop-shadow(0 0 1px rgba(22,163,74,0.15)); }
            50% { stroke: #16a34a; stroke-width: 2.2px; filter: drop-shadow(0 0 5px rgba(22,163,74,0.55)); }
        }
        .internal-network-active { animation: internal-glow-network 1.8s ease-in-out infinite; }
        .internal-core-active { animation: internal-glow-core 1.8s ease-in-out infinite; }
        .internal-storage-active { animation: internal-glow-storage 1.8s ease-in-out infinite; }
        .internal-cluster-active { animation: internal-glow-cluster 1.8s ease-in-out infinite; }
        .internal-telemetry-active { animation: internal-glow-telemetry 1.8s ease-in-out infinite; }
    `;

    return (
        <div style={{
            position: "fixed", inset: 0, zIndex: 100,
            backgroundColor: "rgba(15, 23, 42, 0.4)",
            backdropFilter: "blur(4px)",
            display: "flex", alignItems: "center", justifyContent: "center",
            padding: 24,
        }}>
            <style>{STYLE_BLOCK}</style>

            <div style={{
                background: "#ffffff", borderRadius: 16, width: "100%", maxWidth: 1140,
                boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)",
                border: "1px solid #e2e8f0", overflow: "hidden", display: "flex", flexDirection: "column"
            }}>
                {/* Header */}
                <div style={{
                    padding: "16px 24px", borderBottom: "1px solid #f1f5f9",
                    display: "flex", justifyContent: "space-between", alignItems: "center",
                    background: "#f8fafc"
                }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <div style={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: "#8b5cf6" }} />
                        <span style={{ fontSize: 13, fontWeight: "700", color: "#0f172a", letterSpacing: "0.05em" }}>
                            SINGLE BROKER NODE — INTERNAL WORKFLOW DIAGRAM
                        </span>
                    </div>
                    <button
                        onClick={onClose}
                        style={{
                            background: "none", border: "none", cursor: "pointer",
                            color: "#64748b", padding: 4, display: "flex", borderRadius: "50%"
                        }}
                    >
                        <X size={18} />
                    </button>
                </div>

                {/* Grid Visual Canvas layout */}
                <div style={{ display: "grid", gridTemplateColumns: "1fr 380px", height: 540 }}>
                    {/* SVG Internal Broker Map */}
                    <div style={{ position: "relative", background: "#f8fafc", borderRight: "1px solid #f1f5f9", overflow: "hidden" }}>
                        <svg viewBox="0 0 600 400" style={{ width: "100%", height: "100%" }}>
                            {/* Grid Dots */}
                            <defs>
                                <pattern id="internal-dots" x="0" y="0" width="12" height="12" patternUnits="userSpaceOnUse">
                                    <circle cx="1" cy="1" r="0.4" fill="rgba(15,23,42,.08)" />
                                </pattern>
                            </defs>
                            <rect width="100%" height="100%" fill="url(#internal-dots)" />

                            {/* Internal Communication Pipes */}
                            <g stroke="rgba(148, 163, 184, 0.25)" strokeWidth="1.2" strokeDasharray="3 3">
                                <line x1="120" y1="105" x2="300" y2="105" /> {/* Network to Core Manager */}
                                <line x1="300" y1="105" x2="480" y2="105" /> {/* Core Manager to Storage */}
                                <line x1="300" y1="105" x2="390" y2="290" /> {/* Core Manager to Raft/Replicator */}
                                <line x1="390" y1="290" x2="170" y2="290" /> {/* Raft/Replication to WS Bridge */}
                            </g>

                            {/* Client Inbound Port Entry Line */}
                            <g stroke="rgba(234, 88, 12, 0.3)" strokeWidth="1" strokeDasharray="2 2">
                                <line x1="10" y1="105" x2="60" y2="105" />
                            </g>

                            {/* Network Layer Block (TcpServer & Handler) */}
                            <g transform="translate(60, 55)">
                                <rect
                                    width="120"
                                    height="100"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 0 ? "internal-network-active" : ""}
                                    stroke={activeStep === 0 ? "#ea580c" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="60" y="24" textAnchor="middle" fontSize="10" fontWeight="800" fill="#ea580c" style={{ fontFamily: 'monospace' }}>FRONT DOOR</text>
                                <text x="60" y="44" textAnchor="middle" fontSize="9" fontWeight="700" fill="#334155" style={{ fontFamily: 'monospace' }}>TcpServerImpl</text>
                                <text x="60" y="60" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>ClientHandler</text>
                                <rect x="15" y="74" width="90" height="15" rx="3" fill="rgba(234, 88, 12, 0.05)" stroke="rgba(234, 88, 12, 0.2)" strokeWidth="1" />
                                <text x="60" y="84" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#ea580c" style={{ fontFamily: 'monospace' }}>TCP Port 8888</text>
                            </g>

                            {/* Core Logic Layer Block (QueueManager & Hot Cache) */}
                            <g transform="translate(240, 55)">
                                <rect
                                    width="120"
                                    height="100"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 1 ? "internal-core-active" : ""}
                                    stroke={activeStep === 1 ? "#0284c7" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="60" y="24" textAnchor="middle" fontSize="10" fontWeight="800" fill="#0284c7" style={{ fontFamily: 'monospace' }}>CORE ENGINE</text>
                                <text x="60" y="44" textAnchor="middle" fontSize="9" fontWeight="700" fill="#334155" style={{ fontFamily: 'monospace' }}>QueueManagerImpl</text>
                                <text x="60" y="60" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>MessageQueue</text>
                                <rect x="15" y="74" width="90" height="15" rx="3" fill="rgba(2, 132, 199, 0.05)" stroke="rgba(2, 132, 199, 0.2)" strokeWidth="1" />
                                <text x="60" y="84" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#0284c7" style={{ fontFamily: 'monospace' }}>LRU Hot Cache</text>
                            </g>

                            {/* Storage Engine Block (Log & Index NIO Segments) */}
                            <g transform="translate(420, 55)">
                                <rect
                                    width="120"
                                    height="100"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 2 ? "internal-storage-active" : ""}
                                    stroke={activeStep === 2 ? "#8b5cf6" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="60" y="24" textAnchor="middle" fontSize="10" fontWeight="800" fill="#8b5cf6" style={{ fontFamily: 'monospace' }}>DISK PLATES</text>
                                <text x="60" y="44" textAnchor="middle" fontSize="9" fontWeight="700" fill="#334155" style={{ fontFamily: 'monospace' }}>StorageManager</text>
                                <text x="60" y="60" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>Log / IndexSegment</text>
                                <rect x="15" y="74" width="90" height="15" rx="3" fill="rgba(139, 92, 246, 0.05)" stroke="rgba(139, 92, 246, 0.2)" strokeWidth="1" />
                                <text x="60" y="84" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#8b5cf6" style={{ fontFamily: 'monospace' }}>NIO FileChannel</text>
                            </g>

                            {/* Consensus & Cluster Synchronization Block */}
                            <g transform="translate(330, 240)">
                                <rect
                                    width="120"
                                    height="100"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 3 ? "internal-cluster-active" : ""}
                                    stroke={activeStep === 3 ? "#b45309" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="60" y="24" textAnchor="middle" fontSize="10" fontWeight="800" fill="#b45309" style={{ fontFamily: 'monospace' }}>CLUSTER SYNC</text>
                                <text x="60" y="44" textAnchor="middle" fontSize="9" fontWeight="700" fill="#334155" style={{ fontFamily: 'monospace' }}>RaftNodeImpl</text>
                                <text x="60" y="60" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>Replicator</text>
                                <rect x="15" y="74" width="90" height="15" rx="3" fill="rgba(180, 83, 9, 0.05)" stroke="rgba(180, 83, 9, 0.2)" strokeWidth="1" />
                                <text x="60" y="84" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#b45309" style={{ fontFamily: 'monospace' }}>Raft Consensus</text>
                            </g>

                            {/* WebSocket Telemetry Bridge Block */}
                            <g transform="translate(110, 240)">
                                <rect
                                    width="120"
                                    height="100"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 4 ? "internal-telemetry-active" : ""}
                                    stroke={activeStep === 4 ? "#16a34a" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="60" y="24" textAnchor="middle" fontSize="10" fontWeight="800" fill="#16a34a" style={{ fontFamily: 'monospace' }}>OBSERVABILITY</text>
                                <text x="60" y="44" textAnchor="middle" fontSize="9" fontWeight="700" fill="#334155" style={{ fontFamily: 'monospace' }}>WebSocketBridge</text>
                                <text x="60" y="60" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>WebSocketServer</text>
                                <rect x="15" y="74" width="90" height="15" rx="3" fill="rgba(22, 163, 74, 0.05)" stroke="rgba(22, 163, 74, 0.2)" strokeWidth="1" />
                                <text x="60" y="84" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#16a34a" style={{ fontFamily: 'monospace' }}>Ports 9001-9003</text>
                            </g>

                            {/* ── Dynamic Internal Data Packet Animations ── */}

                            {/* Step 1: TCP Port Ingest packet */}
                            {activeStep === 0 && (
                                <g>
                                    <circle r="4.5" fill="#ea580c">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 20,105 L 120,105" />
                                    </circle>
                                    <text x="32" y="94" fill="#ea580c" fontSize="8" fontWeight="bold" style={{ fontFamily: 'monospace' }}>TCP Ingest</text>
                                </g>
                            )}

                            {/* Step 2: Queue Route & Memory Cache Allocation */}
                            {activeStep === 1 && (
                                <g>
                                    <circle r="4.5" fill="#0284c7">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 120,105 L 300,105" />
                                    </circle>
                                    <text x="180" y="94" fill="#0284c7" fontSize="8" fontWeight="bold" style={{ fontFamily: 'monospace' }}>Cache Allocation</text>
                                </g>
                            )}

                            {/* Step 3: Local storage write append */}
                            {activeStep === 2 && (
                                <g>
                                    <circle r="4.5" fill="#8b5cf6">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 300,105 L 480,105" />
                                    </circle>
                                    <text x="345" y="94" fill="#8b5cf6" fontSize="8" fontWeight="bold" style={{ fontFamily: 'monospace' }}>Disk Sync</text>
                                </g>
                            )}

                            {/* Step 4: Outbound consensus replication */}
                            {activeStep === 3 && (
                                <g>
                                    <circle r="4.5" fill="#b45309">
                                        <animateMotion dur="2.2s" repeatCount="indefinite" path="M 300,105 L 390,290" />
                                    </circle>
                                    <text x="345" y="195" fill="#b45309" fontSize="8" fontWeight="bold" style={{ fontFamily: 'monospace' }}>Consensus Sync</text>
                                </g>
                            )}

                            {/* Step 5: WebSocket JSON Telemetry push */}
                            {activeStep === 4 && (
                                <g>
                                    <circle r="4.5" fill="#16a34a">
                                        <animateMotion dur="2.2s" repeatCount="indefinite" path="M 390,290 L 170,290" />
                                    </circle>
                                    <circle r="4" fill="#16a34a">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 170,290 L 80,350" />
                                    </circle>
                                    <text x="245" y="280" fill="#16a34a" fontSize="8" fontWeight="bold" style={{ fontFamily: 'monospace' }}>WebSocket Push</text>
                                </g>
                            )}
                        </svg>
                    </div>

                    {/* Step Explanations side-panel */}
                    <div style={{ padding: "24px 20px", display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
                        <div>
                            <span style={{ fontSize: 9, fontWeight: "800", color: "#8b5cf6", letterSpacing: "0.1em" }} className="mono">
                                INTERNAL PHASE {activeStep + 1} OF {steps.length}
                            </span>
                            <h4 style={{ fontSize: 14, fontWeight: "800", color: "#0f172a", margin: "6px 0 3px 0" }}>
                                {steps[activeStep].title}
                            </h4>
                            <div style={{
                                display: "inline-block", background: "#f1f5f9", padding: "2px 6px",
                                borderRadius: 4, fontSize: 9, color: "#475569", fontWeight: "700"
                            }} className="mono">
                                {steps[activeStep].classNames}
                            </div>
                            <p style={{ fontSize: 11.5, color: "#475569", lineHeight: 1.6, marginTop: 14 }}>
                                {steps[activeStep].desc}
                            </p>
                        </div>

                        {/* Controls */}
                        <div style={{ borderTop: "1px solid #f1f5f9", paddingTop: 16, marginTop: 16 }}>
                            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
                                <div style={{ display: "flex", gap: 6 }}>
                                    {steps.map((_, idx) => (
                                        <button
                                            key={idx}
                                            onClick={() => setActiveStep(idx)}
                                            style={{
                                                width: 8, height: 8, borderRadius: "50%", border: "none", cursor: "pointer",
                                                background: idx === activeStep ? "#8b5cf6" : "#e2e8f0",
                                                transition: "background .2s"
                                            }}
                                        />
                                    ))}
                                </div>

                                <span style={{ fontSize: 10, fontWeight: "700", color: "#64748b" }} className="mono">
                                    MANUAL NAV
                                </span>
                            </div>

                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                                <button
                                    onClick={() => setActiveStep(prev => (prev - 1 + steps.length) % steps.length)}
                                    style={{
                                        padding: "10px 0", background: "#f1f5f9", border: "1px solid #cbd5e1",
                                        borderRadius: 6, color: "#475569", fontSize: 11, fontWeight: "700",
                                        cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6
                                    }}
                                >
                                    LAST STEP
                                </button>
                                <button
                                    onClick={() => setActiveStep(prev => (prev + 1) % steps.length)}
                                    style={{
                                        padding: "10px 0", background: "#8b5cf6", border: "none",
                                        borderRadius: 6, color: "#fff", fontSize: 11, fontWeight: "700",
                                        cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6
                                    }}
                                >
                                    NEXT STEP
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}