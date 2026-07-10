#!/usr/bin/env bash
# stop.sh — explicitly stop the privacy-emulator backend started by run.sh.
# Kills by the exact PID recorded in the pidfile. Idempotent.
set -u
PIDFILE="$HOME/.privacy-emulator.pid"
if [ ! -f "$PIDFILE" ]; then echo "[stop.sh] no pidfile — backend not running (or started elsewhere)"; exit 0; fi
P=$(cat "$PIDFILE" 2>/dev/null)
if [ -z "$P" ]; then echo "[stop.sh] empty pidfile"; rm -f "$PIDFILE"; exit 0; fi
if kill -0 "$P" 2>/dev/null; then
  kill -TERM "$P" 2>/dev/null; sleep 1
  kill -0 "$P" 2>/dev/null && kill -9 "$P" 2>/dev/null
  echo "[stop.sh] stopped backend pid=$P"
else
  echo "[stop.sh] pid $P not running (stale pidfile)"
fi
rm -f "$PIDFILE"
