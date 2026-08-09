#!/bin/sh
# Create one consistent PostgreSQL custom-format backup, retain local backups
# for RETENTION_DAYS, and optionally upload to OCI Object Storage.
set -eu

: "${PGHOST:?PGHOST is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${BACKUP_DIR:=/backups}"
: "${RETENTION_DAYS:=7}"

case "$RETENTION_DAYS" in
  ''|*[!0-9]*) echo "RETENTION_DAYS must be a non-negative integer" >&2; exit 2 ;;
esac

umask 077
mkdir -p "$BACKUP_DIR"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
filename="${PGDATABASE}-${timestamp}.dump"
temporary="${BACKUP_DIR}/.${filename}.tmp"
destination="${BACKUP_DIR}/${filename}"

# A trap prevents interrupted runs from leaving a file that looks restorable.
trap 'rm -f "$temporary"' EXIT HUP INT TERM
pg_dump --format=custom --compress=9 --no-owner --no-acl \
  --file="$temporary" "$PGDATABASE"
mv "$temporary" "$destination"
trap - EXIT HUP INT TERM

# Only files following this script's naming convention are eligible for removal.
find "$BACKUP_DIR" -type f -name "${PGDATABASE}-*.dump" \
  -mtime "+${RETENTION_DAYS}" -delete

if [ -n "${OCI_BACKUP_URI:-}" ]; then
  if ! command -v oci >/dev/null 2>&1; then
    echo "OCI_BACKUP_URI set, but OCI CLI is unavailable; local backup retained" >&2
    exit 0
  fi
  case "$OCI_BACKUP_URI" in
    oci://*/*) ;;
    *) echo "OCI_BACKUP_URI must be oci://BUCKET/OPTIONAL_PREFIX" >&2; exit 2 ;;
  esac

  target="${OCI_BACKUP_URI#oci://}"
  bucket="${target%%/*}"
  prefix="${target#*/}"
  [ "$prefix" = "$target" ] && prefix=""
  object_name="${prefix:+${prefix%/}/}${filename}"
  namespace="${OCI_NAMESPACE:-$(oci os ns get --query data --raw-output)}"

  # Authentication uses OCI_CLI_AUTH (instance principal on the VM) or a local CLI config.
  oci os object put --namespace-name "$namespace" --bucket-name "$bucket" \
    --name "$object_name" --file "$destination" --force
fi

echo "Backup completed: $destination"
