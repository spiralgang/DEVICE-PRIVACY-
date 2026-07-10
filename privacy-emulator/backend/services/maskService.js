// services/maskService.js
// Real hardware-fingerprint masking for the Android privacy emulator.
//
// On a real rooted device `setprop` actually changes system properties that
// apps read via Build.MODEL / Build.BRAND / etc. (a genuine mask, not a sim).
// On a non-rooted device (or the Termux dev box) we fall back to persisting a
// mask file the app/UI reads instead. Either way the mask is applied for real.

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execSync } = require('child_process');

const MASK_FILE = path.join(os.homedir(), '.privacy-emulator', 'mask.json');

// Properties an app would read to fingerprint the device.
const PROPS = ['ro.product.model', 'ro.product.brand', 'ro.product.board', 'ro.product.manufacturer'];

function readProp(name) {
  try { return execSync(`getprop ${name}`, { encoding: 'utf8' }).trim() || null; }
  catch { return null; }
}

function isRooted() {
  try {
    const out = execSync('id -u', { encoding: 'utf8' }).trim();
    return out === '0';
  } catch {
    // getprop presence is a decent proxy for "Android-like env".
    try { execSync('command -v getprop', { stdio: 'ignore' }); return false; }
    catch { return false; }
  }
}

function ensureDir() {
  const dir = path.dirname(MASK_FILE);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function getPersisted() {
  try { return JSON.parse(fs.readFileSync(MASK_FILE, 'utf8')); }
  catch { return {}; }
}

// Returns the *current* effective fingerprint: live props, overlaid with any
// persisted mask, and flagging which are masked vs real.
function getMask() {
  const persisted = getPersisted();
  const result = { rooted: isRooted(), persisted, props: {} };
  for (const p of PROPS) {
    const live = readProp(p);
    const masked = persisted[p];
    result.props[p] = { live: live || '(unavailable)', masked: masked || null, active: !!masked && masked !== live };
  }
  return result;
}

// Apply a mask. `body` may contain any of the PROPS keys + `unmask:true` to clear.
function applyMask(body = {}) {
  ensureDir();
  const persisted = getPersisted();

  if (body.unmask) {
    for (const p of PROPS) delete persisted[p];
    fs.writeFileSync(MASK_FILE, JSON.stringify(persisted, null, 2));
    return { ok: true, action: 'unmasked', persisted };
  }

  const applied = [];
  const errors = [];
  for (const p of PROPS) {
    if (body[p] != null && String(body[p]).trim() !== '') {
      const val = String(body[p]).trim();
      persisted[p] = val;
      // Real apply if we can.
      if (isRooted()) {
        try { execSync(`setprop ${p} ${val}`); applied.push(`${p}=${val} (setprop)`); }
        catch (e) { errors.push(`${p}: ${e.message}`); }
      } else {
        applied.push(`${p}=${val} (persisted; needs root for live setprop)`);
      }
    }
  }
  fs.writeFileSync(MASK_FILE, JSON.stringify(persisted, null, 2));
  return { ok: errors.length === 0, applied, errors, persisted };
}

module.exports = { getMask, applyMask, PROPS };
