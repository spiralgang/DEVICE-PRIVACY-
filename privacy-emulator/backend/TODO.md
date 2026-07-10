# Privacy Emulator — Build TODO

Status: implemented + verified (ad-hoc, live server). Pushed to branch
`privacy-emulator` of spiralgang/DEVICE-PRIVACY.

## Done
- [x] Convert privacy-emulator into a REAL emulator (no sim) using Puter.js
      (keyless, free, real GPT/DeepSeek/web-search models).
- [x] Bake config into app: `backend/config.json` (Puter endpoints, models,
      runtime tiers) + `public/app-config.js` (browser baked config).
- [x] Runtime tier selector `services/runtime.js`: preferred = docker/alpine
      container; fallback = Termux/userland shell (the app's own terminal).
- [x] Server queries Puter's live endpoint on startup (`queryPuterLive`).
- [x] Endpoints: GET /api/status, /api/runtime, /api/network; POST /api/openai,
      /api/deepseek, /api/research, /api/mask, /api/exec, /api/run.
- [x] App launches the server via `run.sh`; server lifetime bound to the app
      session (pidfile + trap, no daemon, no forever process).
- [x] `stop.sh` for explicit shutdown (exact-PID kill, idempotent).
- [x] Removed the 24/7 node-watchdog supervisor + Termux:Boot hook (per
      "no forever processes ever").

## Open / follow-ups
- [ ] Verify docker daemon availability on-device so the container tier
      activates (currently falls back to Termux shell — by design).
- [ ] Add a landing/status page at `/` if desired (emulator UI is enough for now).
- [ ] Optional: server-side Puter proxy via PUTER_TOKEN for non-browser clients.
- [ ] Wire the Capacitor app (`droidn_build/app`) to call /api/* + Puter config.
