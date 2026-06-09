#!/usr/bin/env node
/**
 * orchestrator/index.js
 * ─────────────────────
 * Sidecar HTTP server that the War Room frontend calls to control the lifecycle of
 * Docker containers—simulating physical node failure & recovery under load.
 *
 * Endpoints:
 *   POST /kill   { "node": "broker-1" }   → docker stop -t 2 broker-1
 *   POST /revive { "node": "broker-1" }   → docker start broker-1
 *   GET  /status                          → { "ok": true, "nodes": { "broker-1": "running", ... } }
 *
 * Environment Variables:
 *   PORT  (Default: 3001)
 *   HOST  (Default: 127.0.0.1)
 *   NODES (Default: broker-1,broker-2,broker-3)
 */

const http = require("http");
const { exec } = require("child_process");
const util = require("util");
const execAsync = util.promisify(exec);

const PORT = process.env.PORT || 3001;
const HOST = process.env.HOST || "127.0.0.1";
const ALLOWED_NODES = (process.env.NODES || "broker-1,broker-2,broker-3").split(",");

/* ── Logging Utilities ──────────────────────────────────────────── */
function log(msg) { console.log(`[ORCH ${new Date().toISOString()}] ${msg}`); }
function err(msg) { console.error(`[ORCH ${new Date().toISOString()}] ERROR: ${msg}`); }

/**
 * Queries container status using a single fast docker ps command.
 */
async function getDockerStatuses() {
    const { stdout } = await execAsync(
        `docker ps --filter name=broker -a --format "{{.Names}} {{.State}}"`
    );
    const lines = stdout.trim().split("\n");
    const statuses = {};
    
    // Initialize default states
    ALLOWED_NODES.forEach(n => {
        statuses[n] = "exited";
    });

    lines.forEach(line => {
        const parts = line.trim().split(/\s+/);
        if (parts.length >= 2) {
            const [name, state] = parts;
            if (statuses[name] !== undefined) {
                statuses[name] = state;
            }
        }
    });
    return statuses;
}

/* ── Request Stream Reader ──────────────────────────────────────── */
function readBody(req) {
    return new Promise((resolve, reject) => {
        let body = "";
        req.on("data", chunk => { body += chunk; });
        req.on("end", () => {
            try {
                resolve(JSON.parse(body || "{}"));
            } catch {
                resolve({});
            }
        });
        req.on("error", reject);
    });
}

/* ── Consolidated CORS Response Helper ──────────────────────────── */
function send(res, status, obj) {
    const body = JSON.stringify(obj);
    res.writeHead(status, {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(body),
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
    });
    res.end(body);
}

/* ── HTTP Request Handler ───────────────────────────────────────── */
async function handler(req, res) {
    const { method, url } = req;

    // CORS Preflight Handshake
    if (method === "OPTIONS") {
        res.writeHead(204, {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type"
        });
        return res.end();
    }

    // GET /status
    if (method === "GET" && url === "/status") {
        try {
            const statuses = await getDockerStatuses();
            return send(res, 200, { ok: true, nodes: statuses });
        } catch (e) {
            err(`Status check failed: ${e.message}`);
            return send(res, 500, { ok: false, error: "Docker daemon connection failed" });
        }
    }

    // POST /kill
    if (method === "POST" && url === "/kill") {
        const { node } = await readBody(req);
        if (!ALLOWED_NODES.includes(node)) {
            return send(res, 400, { ok: false, error: "Validation failed: Unknown node ID" });
        }
        try {
            log(`Triggering shutdown sequence for container: ${node}`);
            // OPTIMIZATION: Stop with a short 2-second timeout to keep the UI snappy
            await execAsync(`docker stop -t 2 ${node}`);
            log(`Container halted: ${node}`);
            return send(res, 200, { ok: true, node, action: "killed" });
        } catch (e) {
            err(`Halt execution error on ${node}: ${e.message}`);
            return send(res, 500, { ok: false, error: e.message });
        }
    }

    // POST /revive
    if (method === "POST" && url === "/revive") {
        const { node } = await readBody(req);
        if (!ALLOWED_NODES.includes(node)) {
            return send(res, 400, { ok: false, error: "Validation failed: Unknown node ID" });
        }
        try {
            log(`Triggering boot sequence for container: ${node}`);
            await execAsync(`docker start ${node}`);
            log(`Container started: ${node}`);
            return send(res, 200, { ok: true, node, action: "revived" });
        } catch (e) {
            err(`Boot execution error on ${node}: ${e.message}`);
            return send(res, 500, { ok: false, error: e.message });
        }
    }

    // Fallback Route
    send(res, 404, { ok: false, error: "Resource path not found" });
}

/* ── Start Server ────────────────────────────────────────────────── */
const server = http.createServer(handler);
server.listen(PORT, HOST, () => {
    log(`Docker Orchestration Sidecar active on http://${HOST}:${PORT}`);
    log(`Configured managed nodes: ${ALLOWED_NODES.join(", ")}`);
});