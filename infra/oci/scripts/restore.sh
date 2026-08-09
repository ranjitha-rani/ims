#!/bin/sh
# Restore a custom-format dump into an existing database.
# Run manually inside the backup container. Example:
# docker compose --env-file .env.oci -f compose.oci.yaml run --rm \
#   postgres-backup /scripts/restore.sh /backups/ims-YYYYMMDDTHHMMSSZ.dump
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /backups/DATABASE-TIMESTAMP.dump" >&2
  exit 2
fi

: "${PGHOST:?PGHOST is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
backup_file="$1"

if [ ! -f "$backup_file" ]; then
  echo "Backup file not found: $backup_file" >&2
  exit 2
fi

# Require an explicit opt-in because --clean replaces application objects.
if [ "${CONFIRM_RESTORE:-}" != "$PGDATABASE" ]; then
  echo "Restore refused. Set CONFIRM_RESTORE=$PGDATABASE to confirm." >&2
  exit 2
fi

pg_restore --list "$backup_file" >/dev/null
pg_restore --clean --if-exists --no-owner --no-acl \
  --exit-on-error --dbname="$PGDATABASE" "$backup_file"
echo "Restore completed from: $backup_file"
