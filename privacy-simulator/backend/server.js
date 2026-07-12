// server.js — Privacy Simulator / Emulator backend
//
// A REAL emulator, not a simulation: every AI call is routed to Puter.js
// (https://js.puter.com/v2/), which proxies 500+ real models from OpenAI,
// Anthropic, Google, DeepSeek, xAI, Tencent, etc. — keyless, free to the
// developer, no API keys, no private credentials on this machine.
//
// The browser UI calls Puter directly (per-user auth, keyless). This server
// only serves the static UI + a few real, local helpers:
//   GET  /api/status   → what mode/provider/models are live
//   GET  /api/mask     → current device fingerprint (getprop / fallback)
//   POST /api/mask     → apply a hardware mask (setprop if rooted, else persist)
//   POST /api/exec     → run a real local shell command (opt-in, see below)
//
// /api/exec is a genuine local shell on this device. It is OFF by default and
// only runs when the client sends ?confirm=1 AND the server was started with
// ALLOW_EXEC=1. Do not expose this to untrusted networks.

require('dotenv').config();
const path = require('path');
const express = require('express');
const cors = require('cors');
const { getMask, applyMask } = require('./services/maskService');
const { detectRuntime, execute } = require('./services/runtime');
const cfg = require('./config.json');

const app = express();
app.use(cors());
app.use(express.json());

const ALLOW_EXEC = process.env.ALLOW_EXEC === '1';
const RUNTIME = detectRuntime();

// Serve the real emulator UI (Puter.js, keyless, free, real models).
app.use(express.static(path.join(__dirname, 'public')));

// Live catalog of models the emulator exposes (current Puter slugs, Jun 2026).
const MODELS = {
  'gpt-5.4-nano': { provider: 'OpenAI',   label: 'GPT-5.4 Nano',     web: false },
  'gpt-5.3-chat': { provider: 'OpenAI',   label: 'GPT-5.3 Chat',     web: true  },
  'gpt-5.5':      { provider: 'OpenAI',   label: 'GPT-5.5',          web: true  },
  'deepseek-chat':{ provider: 'DeepSeek', label: 'DeepSeek Chat',    web: false },
  'tencent/hy3':  { provider: 'Tencent',  label: 'Tencent Hy3',      web: false },
  'claude-sonnet-4-6': { provider: 'Anthropic', label: 'Claude Sonnet 4.6', web: false },
  'x-ai/grok-4.5':{ provider: 'xAI',      label: 'Grok 4.5',         web: true  },
  'google/gemini-2.5-flash': { provider: 'Google', label: 'Gemini 2.5 Flash', web: true },
};

app.get('/api/status', (_req, res) => {
  res.json({
    mode: 'browser-keyless',
    provider: 'Puter.js',
    billing: 'free to developer (user-pays on Puter)',
    real: true,
    models: MODELS,
    execEnabled: ALLOW_EXEC,
    runtime: { tier: RUNTIME.tier, engine: RUNTIME.engine, note: RUNTIME.note, reason: RUNTIME.reason },
    puter: { apiBase: cfg.puter.apiBase, keyless: cfg.puter.keyless, liveQuery: cfg.puter.liveQueryOnStartup },
  });
});

// Active runtime tier (preferred container vs Termux fallback) + baked config.
app.get('/api/runtime', (_req, res) => {
  res.json({ active: RUNTIME, config: cfg.runtime });
});

// Execute code via the active runtime tier (container preferred, Termux fallback).
// Honors the same opt-in/guard as /api/exec.
app.post('/api/run', (req, res) => {
  if (!ALLOW_EXEC || req.query.confirm !== '1') {
    return res.status(403).json({
      error: 'exec disabled',
      hint: 'start server with ALLOW_EXEC=1 and send ?confirm=1',
    });
  }
  const code = (req.body && req.body.code) || '';
  if (!code) return res.status(400).json({ error: 'no code' });
  try {
    const r = execute(code, { lang: req.body.lang || 'sh', timeout: req.body.timeout || 30000 });
    res.json({ ...r, command: code });
  } catch (e) {
    res.json({ tier: RUNTIME.tier, engine: RUNTIME.engine, output: String(e), code: 1 });
  }
});


// Current / masked device fingerprint.
app.get('/api/mask', (_req, res) => {
  res.json(getMask());
});

app.post('/api/mask', (req, res) => {
  const result = applyMask(req.body || {});
  res.json(result);
});

// Optional real local shell. Opt-in + guarded.
app.post('/api/exec', (req, res) => {
  if (!ALLOW_EXEC || req.query.confirm !== '1') {
    return res.status(403).json({
      error: 'exec disabled',
      hint: 'start server with ALLOW_EXEC=1 and send ?confirm=1 to use the local shell',
    });
  }
  const { execSync } = require('child_process');
  const cmd = (req.body && req.body.command) || '';
  if (!cmd) return res.status(400).json({ error: 'no command' });
  try {
    const out = execSync(cmd, { timeout: 20000, encoding: 'utf8' });
    res.json({ command: cmd, output: out });
  } catch (e) {
    res.json({ command: cmd, output: (e.stdout || '') + (e.stderr || ''), code: e.status ?? 1 });
  }
});

const PORT = process.env.PORT || cfg.server.port || 3000;

// App queries Puter.js' live endpoint on startup (baked into launch flow).
function queryPuterLive() {
  if (!cfg.puter.liveQueryOnStartup) return;
  const url = cfg.puter.apiBase + cfg.puter.chatPath;
  const t0 = Date.now();
  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: 'ping', model: cfg.puter.models.openai.slug, stream: true }),
  }).then(r => {
    console.log(`[live] Puter endpoint reachable (${r.status}) in ${Date.now() - t0}ms — ${url}`);
  }).catch(e => {
    console.log(`[live] Puter endpoint NOT reachable: ${e.message} (browser will use Puter.js SDK directly)`);
  });
}

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Emulator backend on http://localhost:${PORT}`);
  console.log(`UI (real Puter.js, keyless/free): http://localhost:${PORT}/`);
  console.log(`Local shell (/api/exec,/api/run): ${ALLOW_EXEC ? 'ENABLED' : 'disabled (set ALLOW_EXEC=1)'}`);
  console.log(`AI provider: Puter.js (real models, no API key)`);
  console.log(`Runtime tier: ${RUNTIME.tier} via ${RUNTIME.engine} — ${RUNTIME.note}`);
  queryPuterLive();
});

