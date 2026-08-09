#!/bin/sh
# Minimal scheduler for the backup sidecar. It runs once at startup and then
# every 24 hours. Container restart policy handles unexpected failures.
set -u

while :; do
  if ! /scripts/backup.sh; then
    echo "Backup attempt failed; retrying at the next daily interval" >&2
  fi
  sleep 86400
done
