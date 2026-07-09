// services/runtime.js
// Runtime tier selector baked into the app's functioning:
//   preferred  -> docker + alpine-sandbox (containerized busybox glibc alpine)
//   fallback   -> Termux/userland local shell (the app's own terminal)
// The AI backend uses this to execute code and render artifacts.
const fs = require('fs');
const { execSync, spawnSync } = require('child_process');
const cfg = require('../config.json');

function which(bin) {
  try { return execSync(`command -v ${bin}`, { encoding: 'utf8' }).trim(); }
  catch { return ''; }
}

// Resolve the active runtime tier at startup (and on demand).
function detectRuntime() {
  const rt = cfg.runtime;
  const docker = which('docker');
  const rootfs = rt.preferred.rootfs;
  const containerReady = !!docker && fs.existsSync(rootfs) &&
    // docker must be able to talk to a daemon; if no daemon, fall back.
    (() => {
      try { execSync('docker info >/dev/null 2>&1'); return true; }
      catch { return false; }
    })();

  if (rt.strategy.startsWith('prefer-container') && containerReady) {
    return {
      tier: 'container',
      engine: 'docker-alpine',
      docker,
      rootfs,
      shell: rt.preferred.shell,
      note: rt.preferred.note,
    };
  }
  return {
    tier: 'termux-shell',
    engine: 'local-shell',
    shell: rt.fallback.shell,
    note: rt.fallback.note,
    reason: containerReady ? 'strategy=termux-only' : 'docker/alpine unavailable',
  };
}

// Execute `code` (a shell script / program) in the active runtime.
// Returns { tier, engine, output, code }.
function execute(code, opts = {}) {
  const rt = detectRuntime();
  const lang = opts.lang || 'sh';
  if (rt.tier === 'container') {
    // Run inside the alpine sandbox container.
    const wrapped = `cat > /tmp/run.${lang} <<'EOF'\n${code}\nEOF\nchmod +x /tmp/run.${lang}\n/tmp/run.${lang}\n`;
    const r = spawnSync(rt.docker, [
      'run', '--rm', '-i',
      '-v', `${rt.rootfs}:/workspace`,
      rt.preferred.image, rt.shell, '-c', wrapped,
    ], { encoding: 'utf8', timeout: opts.timeout || 30000 });
    return {
      tier: 'container', engine: 'docker-alpine',
      output: (r.stdout || '') + (r.stderr || ''),
      code: r.status ?? 1,
    };
  }
  // Fallback: Termux/userland shell.
  const r = spawnSync(rt.shell, ['-c', code], {
    encoding: 'utf8', timeout: opts.timeout || 30000,
  });
  return {
    tier: 'termux-shell', engine: 'local-shell',
    output: (r.stdout || '') + (r.stderr || ''),
    code: r.status ?? 1,
  };
}

module.exports = { detectRuntime, execute };
