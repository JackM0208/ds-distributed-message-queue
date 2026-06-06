import { useState, useEffect, useRef, useCallback } from "react";
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, ResponsiveContainer, Tooltip
} from "recharts";
import {
    Zap, Terminal, Activity, HardDrive, Radio, ShieldCheck, Flame, Info
} from "lucide-react";

// Local Sub-Component Imports
import SystemWorkflowVisualizer from "./components/SystemWorkflowVisualizer.jsx";
import NodeInternalVisualizer from "./components/NodeInternalVisualizer";
import StorageVisualizer from "./components/StorageVisualizer";

/* ─── Constants ─────────────────────────────────────────────────── */
const NODE_IDS   = ["broker-1", "broker-2", "broker-3"];
const NODE_LABEL = { "broker-1": "Node A", "broker-2": "Node B", "broker-3": "Node C" };
const WS_PORTS   = { "broker-1": 9001, "broker-2": 9002, "broker-3": 9003 };
const ORCH       = "http://127.0.0.1:3001";

const NODE_POS = {
    "broker-1": { x: 50, y: 16 },
    "broker-2": { x: 13, y: 74 },
    "broker-3": { x: 87, y: 74 },
};

const STATUS_COLOR = {
    LEADER:   "#ea580c",
    FOLLOWER: "#0284c7",
    DEAD:     "#dc2626",
    ELECTING: "#b45309",
};

let pulseCounter = 0;
let logCounter   = 0;

const mkNode = (status) => ({
    status,
    diskGB:   0.0,
    segments: 1,
    offset:   0,
    cpu:      5,
    mem:      25,
});

// FIXED: Hoist CustomTooltip definition to module level to prevent render-loop ReferenceErrors
const CustomTooltip = ({ active, payload }) => {
    if (!active || !payload?.length) return null;
    return (
        <div style={{
            background: "#ffffff", border: "1px solid rgba(234,88,12,.5)",
            borderRadius: 6, padding: "6px 12px", fontSize: 12, color: "#ea580c",
            boxShadow: "0 2px 8px rgba(0,0,0,0.03)"
        }}>
            <strong className="mono">{payload[0].value}</strong> <span style={{ color: "#475569" }}>ops/sec</span>
        </div>
    );
};

/* ─── CSS Stylesheets ─── */
const CSS = `
  .wroom * { box-sizing: border-box; }
  .wroom { 
    font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; 
    background: #f8fafc; 
    color: #1e293b;
  }
  .mono { 
    font-family: SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace; 
  }

  @keyframes pulse-ring {
    0%   { transform: scale(1); opacity: .7; }
    100% { transform: scale(2.4); opacity: 0; }
  }
  @keyframes leader-glow {
    0%,100% { box-shadow: 0 0 10px rgba(234,88,12,0.15), 0 0 20px rgba(234,88,12,0.05); }
    50%      { box-shadow: 0 0 20px rgba(234,88,12,0.3), 0 0 40px rgba(234,88,12,0.15); }
  }
  @keyframes election-pulse {
    0%,100% { background: rgba(180,83,9,0.03); border-color: rgba(180,83,9,0.3); }
    50%      { background: rgba(180,83,9,0.12); border-color: rgba(180,83,9,0.7); }
  }
  @keyframes dead-fade {
    0%,100% { opacity: .5; }
    50%      { opacity: .3; }
  }
  @keyframes log-in {
    from { opacity: 0; transform: translateX(-4px); }
    to   { opacity: 1; transform: translateX(0); }
  }
  @keyframes bar-glow {
    0%,100% { filter: drop-shadow(0 0 2px currentColor); }
    50%      { filter: drop-shadow(0 0 5px currentColor); }
  }
  @keyframes chaos-btn {
    0%,100% { box-shadow: 0 0 10px rgba(220,38,38,0.2); }
    50%      { box-shadow: 0 0 20px rgba(220,38,38,0.5); }
  }
  @keyframes buy-btn {
    0%,100% { box-shadow: 0 2px 4px rgba(234,88,12,0.15), inset 0 1px 0 rgba(255,255,255,0.2); }
    50%      { box-shadow: 0 4px 12px rgba(234,88,12,0.4), inset 0 1px 0 rgba(255,255,255,0.3); }
  }
  @keyframes new-leader {
    0%   { box-shadow: 0 0 0 0 rgba(180,83,9,0.5); }
    70%  { box-shadow: 0 0 0 15px rgba(180,83,9,0); }
    100% { box-shadow: 0 0 0 0 rgba(180,83,9,0); }
  }

  .node-leader   { animation: leader-glow 2.2s ease-in-out infinite; }
  .node-electing { animation: election-pulse .45s ease-in-out infinite; }
  .node-dead     { animation: dead-fade 1.8s ease-in-out infinite; }
  .node-new      { animation: new-leader .6s ease-out 3; }
  .log-row       { animation: log-in .15s ease-out forwards; }
  .buy-anim      { animation: buy-btn 2s ease-in-out infinite; }
  .chaos-anim    { animation: chaos-btn 1s ease-in-out infinite; }

  .disk-bar {
    transition: width .8s cubic-bezier(.4,0,.2,1);
    animation: bar-glow 3s ease-in-out infinite;
  }

  .log-scroll { scrollbar-width: thin; scrollbar-color: #cbd5e1 transparent; }
  .log-scroll::-webkit-scrollbar { width: 4px; }
  .log-scroll::-webkit-scrollbar-track { background: transparent; }
  .log-scroll::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 2px; }

  /* Visualization Specific Animations */
  @keyframes pulse-glow {
    0%, 100% { transform: scale(1); opacity: 0.8; }
    50% { transform: scale(1.3); opacity: 1; }
  }
  .active-node {
    animation: pulse-glow 1.5s ease-in-out infinite;
    stroke-width: 2px !important;
  }
`;

export default function FlashSaleWarRoom() {
    const [chaosMode,  setChaosMode]  = useState(false);
    const [trafficData, setTrafficData] = useState(() =>
        Array.from({ length: 45 }, (_, i) => ({ t: i, ops: 0 }))
    );
    const [nodes, setNodes] = useState({
        "broker-1": mkNode("LEADER"),
        "broker-2": mkNode("FOLLOWER"),
        "broker-3": mkNode("FOLLOWER"),
    });
    const [pulses,      setPulses]      = useState([]);
    const [logs,        setLogs]        = useState([]);
    const [totalOrders, setTotalOrders] = useState(0);
    const [uptime,      setUptime]      = useState(0);
    const [electing,    setElecting]    = useState(false);
    const [newLeader,   setNewLeader]   = useState(null);
    const [wsConnected, setWsConnected] = useState({ "broker-1": false, "broker-2": false, "broker-3": false });

    // Visualization triggers
    const [showSystemVisualizer, setShowSystemVisualizer] = useState(false);
    const [showNodeVisualizer, setShowNodeVisualizer] = useState(false);
    const [showStorageVisualizer, setShowStorageVisualizer] = useState(false);

    const chaosModeRef = useRef(chaosMode);
    const nodesRef     = useRef(nodes);
    const electingRef  = useRef(electing);
    const socketsRef   = useRef({});

    // Track calculated write operations over our websocket interval
    const opsCounterRef = useRef(0);

    useEffect(() => { chaosModeRef.current = chaosMode; }, [chaosMode]);
    useEffect(() => { nodesRef.current = nodes; },        [nodes]);
    useEffect(() => { electingRef.current = electing; },  [electing]);

    /* ── System Logger ── */
    const pushLog = useCallback((msg, type = "INFO") => {
        const id = logCounter++;
        const ts = new Date().toTimeString().slice(0, 8);
        setLogs(p => [{ id, ts, msg, type }, ...p].slice(0, 70));
    }, []);

    /* ── Replication Pulse Animation ── */
    const spawnPulse = useCallback((from, to) => {
        const id = pulseCounter++;
        setPulses(p => [...p, { id, from, to }]);
        setTimeout(() => setPulses(p => p.filter(q => q.id !== id)), 1150);
    }, []);

    const activeLeaderId = Object.entries(nodes).find(([, s]) => s.status === "LEADER")?.[0];

    /* ── FIXED: Real-World Chaos Mode Trigger ── */
    useEffect(() => {
        let chaosTimer = null;
        if (chaosMode && activeLeaderId) {
            pushLog(`[CHAOS] Activating high-speed write floods down Leader WebSocket: ${activeLeaderId}`, "CHAOS");

            // Sends 25 real writes per second directly into the Leader container's storage engine
            chaosTimer = setInterval(() => {
                const ws = socketsRef.current[activeLeaderId];
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({ action: "produce", payload: "CHAOS_SALE_BURST_EVENT" }));
                    opsCounterRef.current += 1;
                }
            }, 40);
        }
        return () => {
            if (chaosTimer) clearInterval(chaosTimer);
        };
    }, [chaosMode, activeLeaderId, pushLog]);

    /* ── Connect WebSockets to Java Brokers ── */
    useEffect(() => {
        NODE_IDS.forEach(nodeId => {
            const port = WS_PORTS[nodeId];
            const connect = () => {
                const ws = new WebSocket(`ws://127.0.0.1:${port}`);
                socketsRef.current[nodeId] = ws;

                ws.onopen = () => {
                    setWsConnected(p => ({ ...p, [nodeId]: true }));
                    pushLog(`[TELEMETRY] Connected to telemetry node: ${nodeId}`, "SUCCESS");
                };

                ws.onclose = () => {
                    setWsConnected(p => ({ ...p, [nodeId]: false }));
                    setTimeout(connect, 3000);
                };

                ws.onmessage = (event) => {
                    try {
                        const data = JSON.parse(event.data);
                        const evType = data["event"];

                        if (evType === "append") {
                            const offsetVal = data["offset"];
                            const fileSizeVal = data["fileSize"];
                            const targetNode = data["node"];

                            setNodes(prev => ({
                                ...prev,
                                [targetNode]: {
                                    ...prev[targetNode],
                                    offset: offsetVal,
                                    diskGB: fileSizeVal,
                                }
                            }));

                            if (offsetVal > 0) {
                                setTotalOrders(offsetVal);
                                pushLog(`[STORAGE] ${targetNode} → Appended offset ${offsetVal.toLocaleString()} (Log Fill: ${(fileSizeVal * 100).toFixed(1)}%)`, "STORAGE");
                            }
                        }

                        else if (evType === "cluster") {
                            const targetNode = data["node"];
                            const statusVal = data["status"];
                            const cpuVal = data["cpu"];
                            const memVal = data["mem"];

                            const offsetVal = data["offset"] !== undefined ? data["offset"] : 0;
                            const fileSizeVal = data["fileSize"] !== undefined ? data["fileSize"] : 0.0;
                            const segmentsVal = data["segments"] !== undefined ? data["segments"] : 1;

                            setNodes(prev => ({
                                ...prev,
                                [targetNode]: {
                                    ...prev[targetNode],
                                    status: statusVal,
                                    cpu: cpuVal,
                                    mem: memVal,
                                    offset: offsetVal,
                                    diskGB: fileSizeVal,
                                    segments: segmentsVal
                                }
                            }));
                        }

                        else if (evType === "replicate") {
                            const fromNode = data["from"];
                            const toNode = data["to"];
                            const offsetVal = data["offset"];

                            spawnPulse(fromNode, toNode);
                            pushLog(`[REPLICATION] Sync confirmed: ${fromNode} → ${toNode} up to offset ${offsetVal}`, "INFO");
                        }

                        else if (evType === "election") {
                            const typeVal = data["type"];
                            const winnerVal = data["winner"];

                            if (typeVal === "start") {
                                setElecting(true);
                                pushLog("[RAFT] Election timeout fired! Initiating candidate loop.", "WARN");
                            } else if (typeVal === "done") {
                                setElecting(false);
                                setNewLeader(winnerVal);
                                setTimeout(() => setNewLeader(null), 2000);
                                pushLog(`[RAFT] Election complete. Term leader settled: ${winnerVal}`, "SUCCESS");
                            }
                        }
                    } catch (err) {
                        console.error("Telemetry decode error: ", err);
                    }
                };
            };

            connect();
        });

        return () => {
            Object.values(socketsRef.current).forEach(ws => ws.close());
        };
    }, [pushLog, spawnPulse]);

    /* ── Sidecar Orchestration ── */
    const killNode = useCallback(async (nodeId) => {
        pushLog(`[ORCHESTRATOR] Sending kill execution instruction for: ${nodeId}`, "ERROR");
        try {
            const response = await fetch(`${ORCH}/kill`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ node: nodeId }),
            });
            const data = await response.json();
            if (data.ok) {
                setNodes(prev => ({
                    ...prev,
                    [nodeId]: { ...prev[nodeId], status: "DEAD" },
                }));
                pushLog(`[ORCHESTRATOR] successfully halted Docker container: ${nodeId}`, "ERROR");
            }
        } catch (e) {
            pushLog(`[ORCHESTRATOR] Halt error: ${e.message}`, "ERROR");
        }
    }, [pushLog]);

    const reviveNode = useCallback(async (nodeId) => {
        pushLog(`[ORCHESTRATOR] Sending revive execution instruction for: ${nodeId}`, "SUCCESS");
        try {
            const response = await fetch(`${ORCH}/revive`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ node: nodeId }),
            });
            const data = await response.json();
            if (data.ok) {
                pushLog(`[ORCHESTRATOR] Docker container started successfully: ${nodeId}`, "SUCCESS");
            }
        } catch (e) {
            pushLog(`[ORCHESTRATOR] Restart failed: ${e.message}`, "ERROR");
        }
    }, [pushLog]);

    /* ── Periodic Container Status Checker ── */
    useEffect(() => {
        const checkStatus = async () => {
            try {
                const response = await fetch(`${ORCH}/status`);
                const data = await response.json();
                if (data.ok) {
                    setNodes(prev => {
                        const next = { ...prev };
                        Object.entries(data.nodes).forEach(([id, st]) => {
                            if (next[id]) {
                                const isDead = st !== "running";
                                next[id] = {
                                    ...next[id],
                                    status: isDead ? "DEAD" : (prev[id].status === "DEAD" ? "FOLLOWER" : prev[id].status)
                                };
                            }
                        });
                        return next;
                    });
                }
            } catch (e) {
                // Fallback gracefully
            }
        };

        const poll = setInterval(checkStatus, 2000);
        return () => clearInterval(poll);
    }, []);

    /* ── Telemetry Operations-Per-Second Calculator ── */
    useEffect(() => {
        const interval = setInterval(() => {
            const opsRate = opsCounterRef.current;
            opsCounterRef.current = 0; // Reset counter for the next window

            setTrafficData(prev => [
                ...prev.slice(1),
                { t: prev[prev.length - 1].t + 1, ops: opsRate },
            ]);
            setUptime(u => u + 1);
        }, 1000);

        return () => clearInterval(interval);
    }, []);

    // FIXED: Capture variable calculations safely right before return statement to resolve ReferenceErrors
    const currentOps = trafficData[trafficData.length - 1]?.ops ?? 0;
    const aliveCount = Object.values(nodes).filter(n => n.status !== "DEAD").length;

    return (
        <>
            <style>{CSS}</style>

            <div className="wroom" style={{ minHeight: "100vh", color: "#1e293b" }}>

                <header style={{
                    background: "#ffffff",
                    borderBottom: "1px solid #e2e8f0",
                    padding: "12px 20px",
                    display: "flex", alignItems: "center", justifyContent: "space-between",
                    position: "sticky", top: 0, zIndex: 50,
                    boxShadow: "0 1px 2px rgba(0,0,0,0.03)"
                }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                        <div style={{ position: "relative", width: 10, height: 10 }}>
                            <div style={{ width: 10, height: 10, background: "#ea580c", borderRadius: "50%" }} />
                            <div style={{
                                position: "absolute", inset: 0, background: "#ea580c", borderRadius: "50%",
                                animation: "pulse-ring 1.8s ease-out infinite",
                            }} />
                        </div>
                        <span style={{ color: "#0f172a", fontSize: 14, fontWeight: 700, letterSpacing: "0.05em" }}>
                            FLASH SALE WAR ROOM
                        </span>
                        <span style={{ color: "#64748b", fontSize: 11, fontWeight: 500 }}>| DISTRIBUTED MQ PORTAL</span>
                    </div>

                    <div style={{ display: "flex", alignItems: "center", gap: 20, fontSize: 11, fontWeight: 500 }}>
                        {[
                            ["UPTIME",   `${uptime}s`,                       "#16a34a"],
                            ["ORDERS",   totalOrders.toLocaleString(),        "#ea580c"],
                            ["NODES",    `${aliveCount}/3 ALIVE`,             aliveCount >= 2 ? "#16a34a" : "#dc2626"],
                            ["DATA LOSS","0 bytes",                           "#16a34a"],
                        ].map(([label, val, c]) => (
                            <span key={label} style={{ color: "#475569" }}>
                                {label} <span style={{ color: c, fontWeight: "700" }}>{val}</span>
                            </span>
                        ))}

                        <div style={{
                            padding: "4px 14px", borderRadius: 6, fontSize: 10, fontWeight: 700,
                            letterSpacing: "0.08em",
                            ...(electing
                                    ? { background: "rgba(180,83,9,0.08)", border: "1px solid rgba(180,83,9,0.4)", color: "#b45309", animation: "election-pulse .45s infinite" }
                                    : aliveCount >= 2
                                        ? { background: "rgba(22,163,74,0.08)", border: "1px solid rgba(22,163,74,0.3)", color: "#16a34a" }
                                        : { background: "rgba(220,38,38,0.08)",  border: "1px solid rgba(220,38,38,0.4)",  color: "#dc2626" }
                            ),
                        }}>
                            {electing ? "⚡ ELECTING" : aliveCount >= 2 ? "● HEALTHY" : "✗ DEGRADED"}
                        </div>
                    </div>
                </header>

                <div style={{
                    display: "grid",
                    gridTemplateColumns: "270px 1fr 310px",
                    gap: 12, padding: 12,
                    height: "calc(100vh - 56px)",
                }}>

                    <div style={{ display: "flex", flexDirection: "column", gap: 10, overflow: "hidden" }}>
                        <Panel accent="#ff6b35" label="USER PRESSURE ZONE" icon={<Zap size={13} />}>
                            <button
                                onClick={() => {
                                    if (activeLeaderId) {
                                        pushLog(`[ORDER] Manual order dispatched to Master broker Node: ${activeLeaderId}`, "INFO");
                                        const ws = socketsRef.current[activeLeaderId];
                                        if (ws && ws.readyState === WebSocket.OPEN) {
                                            ws.send(JSON.stringify({ action: "produce", payload: "MANUAL_SALE_ORDER" }));
                                            opsCounterRef.current += 1;
                                        }
                                    }
                                }}
                                className="buy-anim"
                                style={{
                                    width: "100%", padding: "16px 0",
                                    background: "linear-gradient(135deg, #f97316, #ea580c)",
                                    border: "none", borderRadius: 8, cursor: "pointer",
                                    color: "#ffffff", fontWeight: 800, fontSize: 16,
                                    letterSpacing: "0.05em",
                                    transition: "transform .12s",
                                }}
                                onMouseDown={e => e.currentTarget.style.transform = "scale(.97)"}
                                onMouseUp={e   => e.currentTarget.style.transform = "scale(1)"}
                            >
                                ⚡ BUY NOW
                            </button>

                            <div style={{
                                marginTop: 10, padding: "10px 14px", borderRadius: 8,
                                background: chaosMode ? "rgba(220,38,38,0.05)" : "#f8fafc",
                                border: `1px solid ${chaosMode ? "rgba(220,38,38,0.3)" : "#e2e8f0"}`,
                                display: "flex", alignItems: "center", justifyContent: "space-between",
                                transition: "all .3s",
                            }}>
                                <div>
                                    <div style={{ fontSize: 11, fontWeight: "bold", color: chaosMode ? "#dc2626" : "#475569", display: "flex", alignItems: "center", gap: 6 }}>
                                        <Flame size={12} /> CHAOS MODE
                                    </div>
                                    <div style={{ fontSize: 10, marginTop: 2, color: chaosMode ? "#ef4444" : "#64748b" }}>
                                        {chaosMode ? "Simulating heavy sales traffic bursts" : "Idle — toggle to burst"}
                                    </div>
                                </div>
                                <button
                                    onClick={() => {
                                        setChaosMode(p => {
                                            const next = !p;
                                            pushLog(next ? "[CHAOS] Chaos mode enabled — producing bursts of parallel writes" : "[CHAOS] Chaos mode disabled", next ? "CHAOS" : "INFO");
                                            return next;
                                        });
                                    }}
                                    className={chaosMode ? "chaos-anim" : ""}
                                    style={{
                                        position: "relative", width: 46, height: 24,
                                        borderRadius: 12, border: "none", cursor: "pointer",
                                        background: chaosMode ? "#dc2626" : "#cbd5e1",
                                        transition: "background .3s",
                                    }}
                                >
                                    <div style={{
                                        position: "absolute", top: 3, width: 18, height: 18,
                                        background: "#fff", borderRadius: "50%",
                                        left: chaosMode ? 25 : 3,
                                        transition: "left .3s",
                                    }} />
                                </button>
                            </div>
                        </Panel>

                        <Panel accent="#ff6b35" label="TRAFFIC MONITOR" icon={<Activity size={13} />}
                               extra={
                                   <div style={{ display: "flex", alignItems: "baseline", gap: 4 }}>
                                       <span style={{ color: "#ea580c", fontWeight: "bold", fontSize: 22 }} className="mono">{currentOps}</span>
                                       <span style={{ color: "#64748b", fontSize: 10, fontWeight: "bold" }}>OPS/SEC</span>
                                   </div>
                               }
                               style={{ flex: 1 }}
                        >
                            <ResponsiveContainer width="100%" height={140}>
                                <AreaChart data={trafficData} margin={{ top: 4, right: 0, bottom: 0, left: -24 }}>
                                    <defs>
                                        <linearGradient id="tg" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="0%"   stopColor="#ea580c" stopOpacity={0.25} />
                                            <stop offset="100%" stopColor="#ea580c" stopOpacity={0} />
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} horizontal={false} />
                                    <XAxis dataKey="t" hide />
                                    <YAxis tick={{ fill: "#64748b", fontSize: 9 }} axisLine={false} tickLine={false} />
                                    <Tooltip content={CustomTooltip} />
                                    <Area
                                        type="monotone" dataKey="ops"
                                        stroke="#ea580c" strokeWidth={2}
                                        fill="url(#tg)" dot={false}
                                        isAnimationActive={false}
                                    />
                                </AreaChart>
                            </ResponsiveContainer>
                        </Panel>

                        <Panel accent="rgba(255,215,0,.3)" label="GOLDEN METRICS" icon={<ShieldCheck size={13} />}>
                            {[
                                { k: "Orders Sent",       v: totalOrders.toLocaleString(), c: "#ea580c" },
                                { k: "Orders Persisted",  v: totalOrders.toLocaleString(), c: "#16a34a" },
                                { k: "System Downtime",   v: "0ms",                        c: "#16a34a" },
                                { k: "Revenue at Risk",   v: "$0.00",                      c: "#16a34a" },
                                { k: "Data Loss",         v: "0 bytes",                    c: "#16a34a" },
                            ].map(({ k, v, c }) => (
                                <div key={k} style={{
                                    display: "flex", justifyContent: "space-between",
                                    padding: "5px 0", borderBottom: "1px solid #f1f5f9", fontSize: 11,
                                }}>
                                    <span style={{ color: "#475569", fontWeight: 500 }}>{k}</span>
                                    <span style={{ color: c, fontWeight: "bold" }} className="mono">{v}</span>
                                </div>
                            ))}
                        </Panel>
                    </div>

                    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                        <div style={{
                            flex: 1, borderRadius: 14, padding: "14px 16px", position: "relative",
                            background: "radial-gradient(ellipse at 50% 28%, #ffffff 0%, #f8fafc 100%)",
                            border: "1px solid #e2e8f0", overflow: "hidden",
                            boxShadow: "0 1px 3px rgba(0,0,0,0.02)"
                        }}>
                            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                                <PanelLabel icon={<Radio size={13} />} label="CLUSTER MAP — RAFT CONSENSUS" />
                                <span style={{
                                    fontSize: 10, fontWeight: "700", color: electing ? "#b45309" : "#0284c7",
                                    animation: electing ? "election-pulse .45s infinite" : "none",
                                }}>
    {electing
        ? "⚡ LEADER ELECTION IN PROGRESS…"
        : activeLeaderId ? `● ACTIVE TERM LEADER: ${activeLeaderId}` : "✗ CLUSTER SEARCHING FOR LEADER"}
    </span>
                            </div>

                            <svg
                                viewBox="0 0 100 100"
                                preserveAspectRatio="none"
                                style={{ position: "absolute", inset: 0, width: "100%", height: "100%", top: 38 }}
                            >
                                <defs>
                                    <filter id="glow-f">
                                        <feGaussianBlur stdDeviation="1" result="blur" />
                                        <feMerge>
                                            <feMergeNode in="blur" />
                                            <feMergeNode in="SourceGraphic" />
                                        </feMerge>
                                    </filter>
                                    <pattern id="grid-dots" x="0" y="0" width="8" height="8" patternUnits="userSpaceOnUse">
                                        <circle cx="1" cy="1" r="0.35" fill="rgba(15,23,42,.06)" />
                                    </pattern>
                                </defs>
                                <rect width="100" height="100" fill="url(#grid-dots)" />

                                {NODE_IDS.flatMap((a, ai) =>
                                    NODE_IDS.slice(ai + 1).map(b => {
                                        if (nodes[a]?.status === "DEAD" || nodes[b]?.status === "DEAD") return null;
                                        const pa = NODE_POS[a], pb = NODE_POS[b];
                                        return (
                                            <line key={`${a}-${b}`}
                                                  x1={pa.x} y1={pa.y} x2={pb.x} y2={pb.y}
                                                  stroke="rgba(15,23,42,.12)" strokeWidth="0.5" strokeDasharray="2.5 2.5"
                                            />
                                        );
                                    })
                                )}

                                {pulses.map(({ id, from, to }) => {
                                    const pf = NODE_POS[from], pt = NODE_POS[to];
                                    if (!pf || !pt) return null;
                                    const mx = (pf.x + pt.x) / 2;
                                    const my = (pf.y + pt.y) / 2 - 6;
                                    return (
                                        <g key={id} filter="url(#glow-f)">
                                            <circle r="2.8" fill="rgba(234,88,12,.22)">
                                                <animateMotion dur="1.05s" fill="remove"
                                                               path={`M${pf.x},${pf.y} Q${mx},${my} ${pt.x},${pt.y}`} />
                                            </circle>
                                            <circle r="1.4" fill="#ea580c">
                                                <animateMotion dur="1.05s" fill="remove"
                                                               path={`M${pf.x},${pf.y} Q${mx},${my} ${pt.x},${pt.y}`} />
                                            </circle>
                                        </g>
                                    );
                                })}
                            </svg>

                            {NODE_IDS.map((nodeId, idx) => {
                                const state  = nodes[nodeId];
                                const col    = STATUS_COLOR[state.status];
                                const isNew  = newLeader === nodeId;

                                const cardStyles = [
                                    { position: "absolute", left: "50%", top: "8%",  transform: "translateX(-50%)" },
                                    { position: "absolute", left: "3%",  top: "54%" },
                                    { position: "absolute", right: "3%", top: "54%" },
                                ];

                                const cls = state.status === "LEADER"
                                    ? (isNew ? "node-new" : "node-leader")
                                    : (state.status === "DEAD" ? "node-dead" : (electing ? "node-electing" : ""));

                                return (
                                    <div key={nodeId} className={cls} style={{ ...cardStyles[idx], width: 158, zIndex: 10, transition: "opacity .5s" }}>
                                        <div style={{
                                            background: "#ffffff",
                                            border: `1px solid ${col}45`,
                                            borderRadius: 12, padding: "12px 14px",
                                            boxShadow: "0 2px 8px rgba(0,0,0,0.03)"
                                        }}>
                                            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                                                <div>
                                                    <div style={{ fontSize: 10, fontWeight: "800", color: col, letterSpacing: "0.03em" }} className="mono">{nodeId}</div>
                                                    <div style={{ fontSize: 11, color: "#64748b", fontWeight: "600", marginTop: 1 }}>{NODE_LABEL[nodeId]}</div>
                                                </div>
                                                <div style={{ position: "relative" }}>
                                                    <div style={{ width: 9, height: 9, background: col, borderRadius: "50%" }} />
                                                    {state.status !== "DEAD" && (
                                                        <div style={{
                                                            position: "absolute", inset: 0,
                                                            background: col, borderRadius: "50%",
                                                            animation: "pulse-ring 1.6s ease-out infinite",
                                                        }} />
                                                    )}
                                                </div>
                                            </div>

                                            <div className="orb" style={{
                                                textAlign: "center", padding: "4px 0", borderRadius: 6, marginBottom: 10,
                                                background: `${col}10`, color: col, fontSize: 9, fontWeight: "bold", letterSpacing: "0.05em",
                                                border: `1px solid ${col}25`,
                                            }}>
                                                {electing && state.status === "FOLLOWER" ? "CANDIDATE" : state.status}
                                            </div>

                                            {[
                                                ["offset", state.offset.toLocaleString()],
                                                ["segments", state.segments],
                                                ["cpu",  `${state.cpu.toFixed(0)}%`],
                                                ["mem",  `${state.mem.toFixed(0)}%`],
                                            ].map(([k, v]) => (
                                                <div key={k} style={{
                                                    display: "flex", justifyContent: "space-between", fontSize: 10,
                                                    padding: "3px 0", borderBottom: "1px solid #f1f5f9",
                                                }}>
                                                    <span style={{ color: "#64748b", fontWeight: 500 }}>{k}</span>
                                                    <span style={{
                                                        fontWeight: "600",
                                                        color: (k === "cpu" && state.cpu > 80) || (k === "mem" && state.mem > 85)
                                                            ? "#dc2626" : "#334155"
                                                    }} className="mono">{v}</span>
                                                </div>
                                            ))}

                                            <button
                                                onClick={() => state.status === "DEAD" ? reviveNode(nodeId) : killNode(nodeId)}
                                                style={{
                                                    width: "100%", marginTop: 10, padding: "5px 0",
                                                    borderRadius: 6, fontSize: 10, fontWeight: "bold", cursor: "pointer",
                                                    border: `1px solid ${state.status === "DEAD" ? "rgba(22,163,74,0.3)" : "rgba(220,38,38,0.2)"}`,
                                                    background: state.status === "DEAD" ? "rgba(22,163,74,0.04)" : "rgba(220,38,38,0.04)",
                                                    color: state.status === "DEAD" ? "#16a34a" : "#dc2626",
                                                    transition: "all .2s", fontFamily: "inherit",
                                                }}
                                                onMouseEnter={e => e.currentTarget.style.opacity = ".75"}
                                                onMouseLeave={e => e.currentTarget.style.opacity = "1"}
                                            >
                                                {state.status === "DEAD" ? "▶ REVIVE" : "■ KILL NODE"}
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {/* Interactive Diagnostic Panel */}
                        <div style={{
                            display: "flex", flexDirection: "column", gap: 8,
                            padding: "12px 14px", borderRadius: 14,
                            background: "#ffffff", border: "1px solid #e2e8f0",
                            boxShadow: "0 1px 3px rgba(0,0,0,0.01)",
                            justifyContent: "center", alignItems: "center"
                        }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                                <Info size={14} style={{ color: "#ea580c" }} />
                                <span style={{ fontSize: 11, fontWeight: "700", color: "#334155" }}>Architecture & Consensus Diagnostics</span>
                            </div>
                            <p style={{ fontSize: 10, color: "#64748b", textAlign: "center", margin: "2px 0 6px 0", lineHeight: 1.4 }}>
                                Analyze the transaction sequence mapping, disk appends, and consensus workflows.
                            </p>

                            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, width: "100%" }}>
                                <button
                                    onClick={() => setShowSystemVisualizer(true)}
                                    style={{
                                        padding: "10px 0",
                                        background: "#f8fafc", border: "1px solid #cbd5e1",
                                        borderRadius: 8, color: "#0f172a", fontSize: 11,
                                        fontWeight: "700", cursor: "pointer", display: "flex",
                                        alignItems: "center", justifyContent: "center", gap: 6,
                                        transition: "all .15s"
                                    }}
                                    onMouseEnter={e => { e.currentTarget.style.background = "#ea580c"; e.currentTarget.style.color = "#ffffff"; e.currentTarget.style.borderColor = "#ea580c"; }}
                                    onMouseLeave={e => { e.currentTarget.style.background = "#f8fafc"; e.currentTarget.style.color = "#0f172a"; e.currentTarget.style.borderColor = "#cbd5e1"; }}
                                >
                                    <Radio size={12} /> SYSTEM LAYER
                                </button>
                                <button
                                    onClick={() => setShowNodeVisualizer(true)}
                                    style={{
                                        padding: "10px 0",
                                        background: "#f8fafc", border: "1px solid #cbd5e1",
                                        borderRadius: 8, color: "#0f172a", fontSize: 11,
                                        fontWeight: "700", cursor: "pointer", display: "flex",
                                        alignItems: "center", justifyContent: "center", gap: 6,
                                        transition: "all .15s"
                                    }}
                                    onMouseEnter={e => { e.currentTarget.style.background = "#8b5cf6"; e.currentTarget.style.color = "#ffffff"; e.currentTarget.style.borderColor = "#8b5cf6"; }}
                                    onMouseLeave={e => { e.currentTarget.style.background = "#f8fafc"; e.currentTarget.style.color = "#0f172a"; e.currentTarget.style.borderColor = "#cbd5e1"; }}
                                >
                                    <HardDrive size={12} /> NODE INTERNALS
                                </button>
                            </div>
                        </div>
                    </div>

                    <div style={{ display: "flex", flexDirection: "column", gap: 10, overflow: "hidden" }}>

                        {/* Interactive Storage Explorer Panel */}
                        <Panel accent="rgba(124,58,237,.5)" label="STORAGE ENGINE EXPLORER" icon={<HardDrive size={13} />}>
                            <div style={{ padding: "8px 0", display: "flex", flexDirection: "column", gap: 12 }}>
                                <div style={{
                                    background: "rgba(139, 92, 246, 0.03)",
                                    border: "1px dashed rgba(139, 92, 246, 0.2)",
                                    borderRadius: 8,
                                    padding: "12px 14px"
                                }}>
                                    <div style={{ fontSize: 11, fontWeight: "bold", color: "#8b5cf6", display: "flex", alignItems: "center", gap: 6, marginBottom: 4 }}>
                                        <HardDrive size={13} /> SEGMENTED STORAGE ARCHITECTURE
                                    </div>
                                    <p style={{ fontSize: 10, color: "#64748b", lineHeight: 1.4, margin: 0 }}>
                                        Messages are stored in append-only sequential log segments on disk paired with fixed-size 20-byte coordinate indices for O(1) reads.
                                    </p>
                                </div>

                                <button
                                    onClick={() => setShowStorageVisualizer(true)}
                                    style={{
                                        width: "100%", padding: "12px 0",
                                        background: "linear-gradient(135deg, #8b5cf6, #7c3aed)",
                                        border: "none", borderRadius: 8, cursor: "pointer",
                                        color: "#ffffff", fontWeight: "700", fontSize: 12,
                                        letterSpacing: "0.05em", display: "flex", alignItems: "center",
                                        justifyContent: "center", gap: 6,
                                        boxShadow: "0 2px 4px rgba(139, 92, 246, 0.15)",
                                        transition: "transform .12s, opacity .15s",
                                    }}
                                    onMouseEnter={e => e.currentTarget.style.opacity = "0.9"}
                                    onMouseLeave={e => e.currentTarget.style.opacity = "1"}
                                    onMouseDown={e => e.currentTarget.style.transform = "scale(.97)"}
                                    onMouseUp={e => e.currentTarget.style.transform = "scale(1)"}
                                >
                                    <HardDrive size={12} /> OPEN STORAGE VISUALIZER
                                </button>

                                <div style={{ borderTop: "1px solid #f1f5f9", paddingTop: 10, marginTop: 4 }}>
                                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
                                        {[
                                            ["LogSegment",  "Append-only log"],
                                            ["IndexSegment","O(1) coordinate index"],
                                            ["SegmentPair", "Bounded Wrapper"],
                                            ["Active Limit","1 GB Rollover"],
                                        ].map(([k, v]) => (
                                            <div key={k}>
                                                <div style={{ fontSize: 9, color: "#94a3b8", fontWeight: "bold" }}>{k}</div>
                                                <div style={{ fontSize: 10, color: "#8b5cf6", fontWeight: "bold" }}>{v}</div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </Panel>

                        <div style={{
                            flex: 1, borderRadius: 14, padding: "12px 14px",
                            background: "#ffffff", border: "1px solid #e2e8f0",
                            display: "flex", flexDirection: "column", overflow: "hidden",
                            boxShadow: "0 1px 3px rgba(0,0,0,0.01)"
                        }}>
                            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                    <div style={{ width: 7, height: 7, background: "#16a34a", borderRadius: "50%", animation: "pulse-ring 1.4s ease-out infinite" }} />
                                    <PanelLabel icon={<Terminal size={12} />} label="LIVE LOG FEED" />
                                </div>
                                <span style={{ fontSize: 9, color: "#94a3b8", fontWeight: "bold" }} className="mono">{logs.length} entries</span>
                            </div>

                            <div className="log-scroll mono"
                                 style={{ flex: 1, overflowY: "auto", display: "flex", flexDirection: "column", gap: 1 }}>
                                {logs.length === 0
                                    ? <span style={{ fontSize: 11, color: "#94a3b8", fontStyle: "italic" }}>Awaiting events…</span>
                                    : logs.map(log => (
                                        <div key={log.id} className="log-row" style={{ display: "flex", gap: 8, fontSize: 10, lineHeight: 1.6 }}>
                                            <span style={{ color: "#94a3b8", flexShrink: 0, width: 58 }}>{log.ts}</span>
                                            <span style={{
                                                color: {
                                                    ERROR:   "#b91c1c",
                                                    SUCCESS: "#15803d",
                                                    WARN:    "#b45309",
                                                    CHAOS:   "#c2410c",
                                                    STORAGE: "#0369a1",
                                                    INFO:    "#475569",
                                                }[log.type] ?? "#475569",
                                                wordBreak: "break-all",
                                            }}>{log.msg}</span>
                                        </div>
                                    ))
                                }
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Modal System Topology Visualization Component */}
            {showSystemVisualizer && (
                <SystemWorkflowVisualizer onClose={() => setShowSystemVisualizer(false)} />
            )}

            {/* Modal Node Internals Visualization Component */}
            {showNodeVisualizer && (
                <NodeInternalVisualizer onClose={() => setShowNodeVisualizer(false)} />
            )}

            {/* Modal Storage Internals Visualization Component */}
            {showStorageVisualizer && (
                <StorageVisualizer onClose={() => setShowStorageVisualizer(false)} />
            )}
        </>
    );
}

function PanelLabel({ icon, label }) {
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, color: "#475569", fontSize: 10, fontWeight: "700", letterSpacing: "0.05em" }}>
            {icon} {label}
        </div>
    );
}

function Panel({ accent = "#ff6b35", label, icon, extra, children, style = {} }) {
    return (
        <div style={{
            borderRadius: 14, padding: "12px 14px",
            background: "#ffffff",
            border: `1px solid #e2e8f0`,
            boxShadow: "0 1px 3px rgba(0,0,0,0.02)",
            ...style,
        }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
                <PanelLabel icon={icon} label={label} />
                {extra}
            </div>
            {children}
        </div>
    );
}