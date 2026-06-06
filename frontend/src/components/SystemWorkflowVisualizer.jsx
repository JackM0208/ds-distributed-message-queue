import { useState } from "react";
import { X, ArrowRight, ArrowLeft } from "lucide-react";

export default function SystemWorkflowVisualizer({ onClose }) {
    const [activeStep, setActiveStep] = useState(0);

    const steps = [
        {
            title: "01. Client Connection & Write Ingestion",
            classNames: "Producer.java | ClientHandler.java",
            desc: "The client SDK (Producer.java) initiates a direct TCP connection with the active cluster Leader (Node A). It transmits a write payload wrapped in a MessagePacket. Node A's network interface (ClientHandler.java) receives the payload and prepares to process the transaction.",
            highlight: "ingestion"
        },
        {
            title: "02. Leader Append & Index Allocation",
            classNames: "QueueManagerImpl.java | LogSegment.java | IndexSegment.java",
            desc: "The Leader parses the packet payload. It calls LogSegment.java to sequentially write raw bytes to disk using a Java NIO FileChannel. Concurrently, it appends matching position structures to IndexSegment.java, and registers the transaction in its local LRU cache (MessageQueue.java).",
            highlight: "local-append"
        },
        {
            title: "03. Raft Consensus Synchronization",
            classNames: "Replicator.java | MessagePacket.java",
            desc: "The Leader fires Replicator.java to concurrently push dynamic MessagePackets (Type 6) to other active brokers. The followers process via handleAppendEntriesWithData(), checking the Term sequence value and writing replicated logs locally to sync database states.",
            highlight: "replicate"
        },
        {
            title: "04. Consensus Commitment Confirmation",
            classNames: "RaftNodeImpl.java | Replicator.java",
            desc: "Follower brokers send verification ACK packets back to the Leader. Once the replication reaches a quorum majority (>50% of the cluster), the leader commits the offset and returns a successful response message to the waiting Producer.",
            highlight: "quorum-ack"
        },
        {
            title: "05. Consumer Fetch & State Sync",
            classNames: "Consumer.java | StorageManagerImpl.java | ConsumerOffsetManager.java",
            desc: "The consumer requests messages starting at its latest offset. The broker serves the message from memory, falling back to disk if a cache miss occurs. The consumer updates its tracking context and commits the offset back, which then propagates to followers via offset replication (Type 7).",
            highlight: "consumer-read"
        }
    ];

    const handleNext = () => {
        setActiveStep(prev => (prev + 1) % steps.length);
    };

    const handleLast = () => {
        setActiveStep(prev => (prev - 1 + steps.length) % steps.length);
    };

    // Scoped CSS styles to manage robust node highlighting & animations inside the SVG
    const STYLE_BLOCK = `
        @keyframes leader-glow-anim {
            0%, 100% { stroke: #ea580c; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(234,88,12,0.15)); }
            50% { stroke: #ea580c; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(234,88,12,0.65)); }
        }
        @keyframes follower-glow-anim {
            0%, 100% { stroke: #0284c7; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(2,132,199,0.15)); }
            50% { stroke: #0284c7; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(2,132,199,0.65)); }
        }
        @keyframes storage-glow-anim {
            0%, 100% { stroke: #d8b4fe; stroke-width: 1px; filter: drop-shadow(0 0 1px rgba(139,92,246,0.15)); }
            50% { stroke: #a855f7; stroke-width: 2px; filter: drop-shadow(0 0 5px rgba(139,92,246,0.55)); }
        }
        .leader-active-glow {
            animation: leader-glow-anim 1.8s ease-in-out infinite;
        }
        .follower-active-glow {
            animation: follower-glow-anim 1.8s ease-in-out infinite;
        }
        .storage-active-glow {
            animation: storage-glow-anim 1.8s ease-in-out infinite;
        }
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
                        <div style={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: "#ea580c" }} />
                        <span style={{ fontSize: 13, fontWeight: "700", color: "#0f172a", letterSpacing: "0.05em" }}>
                            MQ SYSTEM FLOW ENGINE VISUALIZATION
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

                {/* Enlarged Main Visualization Layout */}
                <div style={{ display: "grid", gridTemplateColumns: "1fr 380px", height: 540 }}>
                    {/* SVG Graphic Canvas */}
                    <div style={{ position: "relative", background: "#f8fafc", borderRight: "1px solid #f1f5f9", overflow: "hidden" }}>
                        <svg viewBox="0 0 600 400" style={{ width: "100%", height: "100%" }}>
                            {/* Grid Background Dots */}
                            <defs>
                                <pattern id="modal-dots" x="0" y="0" width="12" height="12" patternUnits="userSpaceOnUse">
                                    <circle cx="1" cy="1" r="0.4" fill="rgba(15,23,42,.08)" />
                                </pattern>
                            </defs>
                            <rect width="100%" height="100%" fill="url(#modal-dots)" />

                            {/* Base Architecture Topology Path Lines */}
                            <g stroke="rgba(148, 163, 184, 0.2)" strokeWidth="1" strokeDasharray="3 3">
                                <line x1="60" y1="200" x2="300" y2="80" />  {/* Producer to Leader (Node A) */}
                                <line x1="300" y1="80" x2="180" y2="280" /> {/* Leader to Follower B */}
                                <line x1="300" y1="80" x2="420" y2="280" /> {/* Leader to Follower C */}
                                <line x1="300" y1="80" x2="540" y2="200" /> {/* Leader to Consumer */}
                                <line x1="180" y1="280" x2="420" y2="280" /> {/* Connected Mesh: Follower B to Follower C */}
                            </g>

                            {/* Producer Entity Block */}
                            <g transform="translate(20, 180)">
                                <rect width="80" height="40" rx="6" fill="#f1f5f9" stroke="#cbd5e1" strokeWidth="1" />
                                <text x="40" y="24" textAnchor="middle" fontSize="10" fontWeight="700" fill="#475569" style={{ fontFamily: 'monospace' }}>Producer</text>
                            </g>

                            {/* Consumer Entity Block */}
                            <g transform="translate(500, 180)">
                                <rect width="80" height="40" rx="6" fill="#f1f5f9" stroke="#cbd5e1" strokeWidth="1" />
                                <text x="40" y="24" textAnchor="middle" fontSize="10" fontWeight="700" fill="#475569" style={{ fontFamily: 'monospace' }}>Consumer</text>
                            </g>

                            {/* Node A (Leader) */}
                            <g transform="translate(250, 55)">
                                <rect
                                    width="100"
                                    height="50"
                                    rx="8"
                                    fill="#fff"
                                    className={(activeStep === 1 || activeStep === 3) ? "leader-active-glow" : ""}
                                    stroke={(activeStep === 1 || activeStep === 3) ? "#ea580c" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="50" y="22" textAnchor="middle" fontSize="11" fontWeight="800" fill="#ea580c" style={{ fontFamily: 'monospace' }}>Node A</text>
                                <text x="50" y="38" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>LEADER</text>
                            </g>

                            {/* Node B (Follower) */}
                            <g transform="translate(130, 257)">
                                <rect
                                    width="100"
                                    height="45"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 2 ? "follower-active-glow" : ""}
                                    stroke={activeStep === 2 ? "#0284c7" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="50" y="20" textAnchor="middle" fontSize="10" fontWeight="800" fill="#0284c7" style={{ fontFamily: 'monospace' }}>Node B</text>
                                <text x="50" y="34" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>FOLLOWER</text>
                            </g>

                            {/* Node C (Follower) */}
                            <g transform="translate(370, 257)">
                                <rect
                                    width="100"
                                    height="45"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 2 ? "follower-active-glow" : ""}
                                    stroke={activeStep === 2 ? "#0284c7" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="50" y="20" textAnchor="middle" fontSize="10" fontWeight="800" fill="#0284c7" style={{ fontFamily: 'monospace' }}>Node C</text>
                                <text x="50" y="34" textAnchor="middle" fontSize="8" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>FOLLOWER</text>
                            </g>

                            {/* Local Storage Engine Representation */}
                            <g transform="translate(250, 150)">
                                <rect
                                    width="100"
                                    height="30"
                                    rx="4"
                                    fill="#faf5ff"
                                    className={activeStep === 1 ? "storage-active-glow" : ""}
                                    stroke={activeStep === 1 ? "#8b5cf6" : "#cbd5e1"}
                                    strokeWidth="1.2"
                                    strokeDasharray="2 2"
                                />
                                <text x="50" y="18" textAnchor="middle" fontSize="8" fontWeight="700" fill="#8b5cf6" style={{ fontFamily: 'monospace' }}>Disk Log & Index</text>
                            </g>

                            {/* ── Active Data Flow Packets (AnimateMotion Paths) ── */}

                            {/* Step 1: Client Append directly to Leader Node A */}
                            {activeStep === 0 && (
                                <g>
                                    <circle r="5" fill="#ea580c">
                                        <animateMotion dur="2s" repeatCount="indefinite" path="M 60,200 L 300,80" />
                                    </circle>
                                    <text x="145" y="135" fill="#ea580c" fontSize="8.5" fontWeight="bold" style={{ fontFamily: 'monospace' }}>TCP Write</text>
                                </g>
                            )}

                            {/* Step 2: Local Append Persistence */}
                            {activeStep === 1 && (
                                <g>
                                    <circle r="5" fill="#8b5cf6">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 300,80 L 300,150" />
                                    </circle>
                                    <text x="312" y="125" fill="#8b5cf6" fontSize="8.5" fontWeight="bold" style={{ fontFamily: 'monospace' }}>NIO Write</text>
                                </g>
                            )}

                            {/* Step 3: Consensus Replication */}
                            {activeStep === 2 && (
                                <g>
                                    <circle r="5" fill="#0284c7">
                                        <animateMotion dur="2s" repeatCount="indefinite" path="M 300,80 L 180,280" />
                                    </circle>
                                    <circle r="5" fill="#0284c7">
                                        <animateMotion dur="2s" repeatCount="indefinite" path="M 300,80 L 420,280" />
                                    </circle>
                                    <text x="325" y="180" fill="#0284c7" fontSize="8.5" fontWeight="bold" style={{ fontFamily: 'monospace' }}>Replicate</text>
                                </g>
                            )}

                            {/* Step 4: Quorum ACK & Client Confirmation Response */}
                            {activeStep === 3 && (
                                <g>
                                    {/* Parallel ACKs flying back from followers to the Leader */}
                                    <circle r="4" fill="#16a34a">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 180,280 L 300,80" />
                                    </circle>
                                    <circle r="4" fill="#16a34a">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 420,280 L 300,80" />
                                    </circle>

                                    {/* Confirmed client write callback response */}
                                    <circle r="5" fill="#16a34a">
                                        <animateMotion dur="1.8s" repeatCount="indefinite" path="M 300,80 L 60,200" />
                                    </circle>
                                    <text x="140" y="120" fill="#16a34a" fontSize="8.5" fontWeight="bold" style={{ fontFamily: 'monospace' }}>Commit ACK</text>
                                </g>
                            )}

                            {/* Step 5: Consumer Read Flow */}
                            {activeStep === 4 && (
                                <g>
                                    <circle r="5" fill="#ea580c">
                                        <animateMotion dur="2.2s" repeatCount="indefinite" path="M 300,80 L 540,200" />
                                    </circle>
                                    <text x="360" y="125" fill="#ea580c" fontSize="8.5" fontWeight="bold" style={{ fontFamily: 'monospace' }}>Pull Message</text>
                                </g>
                            )}
                        </svg>
                    </div>

                    {/* Step Explanations side-panel */}
                    <div style={{ padding: "24px 20px", display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
                        <div>
                            <span style={{ fontSize: 9, fontWeight: "800", color: "#8b5cf6", letterSpacing: "0.1em" }} className="mono">
                                STEP {activeStep + 1} OF {steps.length}
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
                                                background: idx === activeStep ? "#ea580c" : "#e2e8f0",
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
                                    onClick={handleLast}
                                    style={{
                                        padding: "10px 0", background: "#f1f5f9", border: "1px solid #cbd5e1",
                                        borderRadius: 6, color: "#475569", fontSize: 11, fontWeight: "700",
                                        cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6
                                    }}
                                >
                                    <ArrowLeft size={13} /> LAST STEP
                                </button>
                                <button
                                    onClick={handleNext}
                                    style={{
                                        padding: "10px 0", background: "#ea580c", border: "none",
                                        borderRadius: 6, color: "#fff", fontSize: 11, fontWeight: "700",
                                        cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6
                                    }}
                                >
                                    NEXT STEP <ArrowRight size={13} />
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}