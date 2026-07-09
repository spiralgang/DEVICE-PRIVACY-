#!/usr/bin/env bash
# run.sh — start the privacy-simulator backend bound to THIS shell session.
# The server runs as a tracked child; on exit (Ctrl-C / kill / session end)
# it is killed by exact PID. No daemon, no supervisor, no auto-restart loop.
# Pair with stop.sh for an explicit shutdown.
set -u
cd "$(dirname "$0")"
# Port + heap come from config.json by default; env overrides.
PORT="${PORT:-$(node -e "try{console.log(require('./config.json').server.port)}catch{console.log(3000)}")}"
HEAP="${HEAP:-1536}"
PIDFILE="$HOME/.privacy-simulator.pid"
# ALLOW_EXEC stays OFF unless explicitly enabled (real local shell guard).
ALLOW_EXEC="${ALLOW_EXEC:-0}"
export ALLOW_EXEC

cleanup() {
  if [ -f "$PIDFILE" ]; then
    P=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$P" ] && kill -0 "$P" 2>/dev/null; then
      kill -TERM "$P" 2>/dev/null
      # give it a moment, then force if needed
      sleep 1
      kill -0 "$P" 2>/dev/null && kill -9 "$P" 2>/dev/null
    fi
    rm -f "$PIDFILE"
  fi
  echo "[run.sh] backend stopped"
}
trap cleanup EXIT INT TERM HUP

PORT="$PORT" node --max-old-space-size="$HEAP" server.js &
SRV=$!
echo "$SRV" > "$PIDFILE"
echo "[run.sh] backend pid=$SRV on 0.0.0.0:$PORT (Puter.js emulator). Ctrl-C to stop."

# run.sh stays alive as long as the server runs; exiting kills it via trap.
wait "$SRV"
