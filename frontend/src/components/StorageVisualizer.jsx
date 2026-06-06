import { useState } from "react";
import { X, ArrowRight, ArrowLeft } from "lucide-react";

export default function StorageVisualizer({ onClose }) {
    const [activeStep, setActiveStep] = useState(0);

    const steps = [
        {
            title: "01. StorageManagerImpl — Facade Entrypoint",
            classNames: "StorageManagerImpl.java",
            desc: "The entry point for the entire storage layer. It owns the global topicStorageMap. On broker startup, it scans the persistent data/ root directory to discover existing topic folders. When writes arrive, it routes traffic to the targeted topic manager.",
            highlight: "facade"
        },
        {
            title: "02. TopicStorage — Per-Topic Directory",
            classNames: "TopicStorage.java (Nested)",
            desc: "Represents a single topic directory (e.g., data/flash_sale_orders/). It coordinates its sequential offset tracking using a thread-safe AtomicLong globalOffsetCounter. It owns the segments list and decides when the active segment is full to trigger rotation.",
            highlight: "topic"
        },
        {
            title: "03. SegmentPair — Bounded Partition Wrapper",
            classNames: "SegmentPair.java (Nested)",
            desc: "An immutable structural wrapper that binds a sequential .log file and its lookup .index file together. It records the startOffset (the first logical index in this segment) which acts as the segment's base address for relative mathematics.",
            highlight: "segment"
        },
        {
            title: "04. LogSegment — Append-Only Binary Data Log",
            classNames: "LogSegment.java",
            desc: "The physical file writer. It opens the raw .log file in read-write ('rw') mode using a Java NIO FileChannel. Message payloads are written sequentially to the absolute end of the file, completely avoiding slow, random disk seeks.",
            highlight: "log"
        },
        {
            title: "05. IndexSegment — O(1) Binary Coordinate Index",
            classNames: "IndexSegment.java",
            desc: "The physical index locator. It appends fixed-size 20-byte records to the .index file. It translates logical relative offsets directly to physical log coordinates in O(1) time using simple byte multiplication: relativeOffset * 20 bytes.",
            highlight: "index"
        }
    ];

    const handleNext = () => {
        setActiveStep(prev => (prev + 1) % steps.length);
    };

    const handleLast = () => {
        setActiveStep(prev => (prev - 1 + steps.length) % steps.length);
    };

    const STYLE_BLOCK = `
        @keyframes facade-glow {
            0%, 100% { stroke: #8b5cf6; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(139,92,246,0.15)); }
            50% { stroke: #8b5cf6; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(139,92,246,0.65)); }
        }
        @keyframes topic-glow {
            0%, 100% { stroke: #06b6d4; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(6,182,212,0.15)); }
            50% { stroke: #06b6d4; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(6,182,212,0.65)); }
        }
        @keyframes segment-glow {
            0%, 100% { stroke: #ea580c; stroke-width: 1.5px; filter: drop-shadow(0 0 1px rgba(234,88,12,0.15)); }
            50% { stroke: #ea580c; stroke-width: 3px; filter: drop-shadow(0 0 6px rgba(234,88,12,0.65)); }
        }
        .facade-active { animation: facade-glow 1.8s ease-in-out infinite; }
        .topic-active { animation: topic-glow 1.8s ease-in-out infinite; }
        .segment-active { animation: segment-glow 1.8s ease-in-out infinite; }
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
                            PHYSICAL STORAGE SUBSYSTEM — CORE ANATOMY
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

                {/* Content Layout */}
                <div style={{ display: "grid", gridTemplateColumns: "1fr 380px", height: 540 }}>
                    {/* SVG Graphic Canvas */}
                    <div style={{ position: "relative", background: "#f8fafc", borderRight: "1px solid #f1f5f9", overflow: "hidden" }}>
                        <svg viewBox="0 0 600 400" style={{ width: "100%", height: "100%" }}>
                            {/* Grid Dots */}
                            <defs>
                                <pattern id="storage-dots" x="0" y="0" width="12" height="12" patternUnits="userSpaceOnUse">
                                    <circle cx="1" cy="1" r="0.4" fill="rgba(15,23,42,.08)" />
                                </pattern>
                            </defs>
                            <rect width="100%" height="100%" fill="url(#storage-dots)" />

                            {/* Relationship Pipes */}
                            <g stroke="rgba(148, 163, 184, 0.25)" strokeWidth="1.2" strokeDasharray="3 3">
                                <line x1="170" y1="200" x2="250" y2="200" /> {/* Facade to TopicStorage */}
                                <line x1="370" y1="200" x2="430" y2="200" /> {/* TopicStorage to SegmentPair */}
                                <line x1="490" y1="180" x2="490" y2="120" /> {/* Segment to Log file */}
                                <line x1="490" y1="220" x2="490" y2="280" /> {/* Segment to Index file */}
                            </g>

                            {/* 1. StorageManagerImpl Facade Block */}
                            <g transform="translate(40, 100)">
                                <rect
                                    width="130"
                                    height="200"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 0 ? "facade-active" : ""}
                                    stroke={activeStep === 0 ? "#8b5cf6" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="65" y="24" textAnchor="middle" fontSize="10" fontWeight="800" fill="#8b5cf6" style={{ fontFamily: 'monospace' }}>ORCHESTRATOR</text>
                                <text x="65" y="54" textAnchor="middle" fontSize="9" fontWeight="700" fill="#1e293b" style={{ fontFamily: 'monospace' }}>StorageManager</text>
                                <rect x="10" y="80" width="110" height="100" rx="4" fill="rgba(139, 92, 246, 0.03)" stroke="rgba(139, 92, 246, 0.15)" strokeWidth="1" />
                                <text x="65" y="100" textAnchor="middle" fontSize="8" fontWeight="700" fill="#8b5cf6" style={{ fontFamily: 'monospace' }}>topicStorageMap</text>
                                <text x="65" y="125" textAnchor="middle" fontSize="7" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>\"flash_sale_orders\"</text>
                                <text x="65" y="145" textAnchor="middle" fontSize="7" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>\"payment_events\"</text>
                            </g>

                            {/* 2. TopicStorage Block */}
                            <g transform="translate(240, 100)">
                                <rect
                                    width="130"
                                    height="200"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 1 ? "topic-active" : ""}
                                    stroke={activeStep === 1 ? "#06b6d4" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="65" y="24" textAnchor="middle" fontSize="10" fontWeight="800" fill="#06b6d4" style={{ fontFamily: 'monospace' }}>DIRECTORY</text>
                                <text x="65" y="54" textAnchor="middle" fontSize="9" fontWeight="700" fill="#1e293b" style={{ fontFamily: 'monospace' }}>TopicStorage</text>

                                <rect x="10" y="80" width="110" height="40" rx="4" fill="rgba(6, 182, 212, 0.03)" stroke="rgba(6, 182, 212, 0.15)" strokeWidth="1" />
                                <text x="65" y="96" textAnchor="middle" fontSize="7" fontWeight="700" fill="#06b6d4" style={{ fontFamily: 'monospace' }}>globalOffsetCounter</text>
                                <text x="65" y="112" textAnchor="middle" fontSize="8" fontWeight="800" fill="#06b6d4" style={{ fontFamily: 'monospace' }}>AtomicLong</text>

                                <rect x="10" y="135" width="110" height="45" rx="4" fill="rgba(6, 182, 212, 0.03)" stroke="rgba(6, 182, 212, 0.15)" strokeWidth="1" />
                                <text x="65" y="152" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#06b6d4" style={{ fontFamily: 'monospace' }}>List&lt;SegmentPair&gt;</text>
                                <text x="65" y="168" textAnchor="middle" fontSize="7" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>segments list</text>
                            </g>

                            {/* 3. SegmentPair Block */}
                            <g transform="translate(430, 140)">
                                <rect
                                    width="120"
                                    height="120"
                                    rx="8"
                                    fill="#fff"
                                    className={activeStep === 2 ? "segment-active" : ""}
                                    stroke={activeStep === 2 ? "#ea580c" : "#cbd5e1"}
                                    strokeWidth="1.5"
                                />
                                <text x="60" y="24" textAnchor="middle" fontSize="9" fontWeight="800" fill="#ea580c" style={{ fontFamily: 'monospace' }}>SEGMENT PAIR</text>
                                <text x="60" y="50" textAnchor="middle" fontSize="8" fontWeight="700" fill="#1e293b" style={{ fontFamily: 'monospace' }}>SegmentPair.java</text>
                                <rect x="10" y="70" width="100" height="35" rx="4" fill="rgba(234, 88, 12, 0.03)" stroke="rgba(234, 88, 12, 0.15)" strokeWidth="1" />
                                <text x="60" y="85" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#ea580c" style={{ fontFamily: 'monospace' }}>startOffset</text>
                                <text x="60" y="100" textAnchor="middle" fontSize="7" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>baseline long ID</text>
                            </g>

                            {/* 4. LogSegment Block (Top Right) */}
                            <g transform="translate(430, 40)">
                                <rect
                                    width="120"
                                    height="60"
                                    rx="6"
                                    fill="#fff"
                                    stroke={activeStep === 3 ? "#8b5cf6" : "#cbd5e1"}
                                    strokeWidth={activeStep === 3 ? "2" : "1"}
                                />
                                <text x="60" y="20" textAnchor="middle" fontSize="9" fontWeight="800" fill="#8b5cf6" style={{ fontFamily: 'monospace' }}>DATA LOG</text>
                                <text x="60" y="36" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#1e293b" style={{ fontFamily: 'monospace' }}>LogSegment.java</text>
                                <text x="60" y="48" textAnchor="middle" fontSize="7" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>*.log File (NIO)</text>
                            </g>

                            {/* 5. IndexSegment Block (Bottom Right) */}
                            <g transform="translate(430, 300)">
                                <rect
                                    width="120"
                                    height="60"
                                    rx="6"
                                    fill="#fff"
                                    stroke={activeStep === 4 ? "#06b6d4" : "#cbd5e1"}
                                    strokeWidth={activeStep === 4 ? "2" : "1"}
                                />
                                <text x="60" y="20" textAnchor="middle" fontSize="9" fontWeight="800" fill="#06b6d4" style={{ fontFamily: 'monospace' }}>INDEX COORDS</text>
                                <text x="60" y="36" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#1e293b" style={{ fontFamily: 'monospace' }}>IndexSegment.java</text>
                                <text x="60" y="48" textAnchor="middle" fontSize="7" fontWeight="600" fill="#64748b" style={{ fontFamily: 'monospace' }}>*.index (20B Records)</text>
                            </g>

                            {/* Dynamic Animation Packets */}
                            {activeStep === 0 && (
                                <g>
                                    <circle r="4.5" fill="#8b5cf6">
                                        <animateMotion dur="2.2s" repeatCount="indefinite" path="M 170,200 L 250,200" />
                                    </circle>
                                </g>
                            )}
                            {activeStep === 1 && (
                                <g>
                                    <circle r="4.5" fill="#06b6d4">
                                        <animateMotion dur="2.2s" repeatCount="indefinite" path="M 370,200 L 430,200" />
                                    </circle>
                                </g>
                            )}
                            {activeStep === 2 && (
                                <g>
                                    <circle r="4.5" fill="#ea580c">
                                        <animateMotion dur="2s" repeatCount="indefinite" path="M 490,180 L 490,120" />
                                    </circle>
                                    <circle r="4.5" fill="#ea580c">
                                        <animateMotion dur="2s" repeatCount="indefinite" path="M 490,220 L 490,280" />
                                    </circle>
                                </g>
                            )}
                            {activeStep === 3 && (
                                <g>
                                    <circle r="5" fill="#8b5cf6">
                                        <animateMotion dur="1.5s" repeatCount="indefinite" path="M 490,140 L 490,100" />
                                    </circle>
                                </g>
                            )}
                            {activeStep === 4 && (
                                <g>
                                    <circle r="5" fill="#06b6d4">
                                        <animateMotion dur="1.5s" repeatCount="indefinite" path="M 490,260 L 490,300" />
                                    </circle>
                                </g>
                            )}
                        </svg>
                    </div>

                    {/* Step Explanations side-panel */}
                    <div style={{ padding: "24px 20px", display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
                        <div>
                            <span style={{ fontSize: 9, fontWeight: "800", color: "#8b5cf6", letterSpacing: "0.1em" }} className="mono">
                                STORAGE PHASE {activeStep + 1} OF {steps.length}
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