#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# MongoDB → PostgreSQL one-time data migration
#
# Exports 4 MongoDB collections (queues, mappings, userProfiles, namespaces),
# transforms them to match the PostgreSQL schema, and loads via psql.
#
# Prerequisites: mongoexport, jq, psql, uuidgen
# Usage:
#   ./scripts/migrate-mongo-to-postgres.sh              # execute migration
#   ./scripts/migrate-mongo-to-postgres.sh --dry-run    # print SQL only
# =============================================================================

# ── Configuration (override via environment) ─────────────────────────────────
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
MONGO_DB="${MONGO_DB:-mqsim}"

PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_DB="${PG_DB:-appdb}"
PG_USER="${PG_USER:-appuser}"
PG_PASSWORD="${PG_PASSWORD:-apppass}"

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  echo "=== DRY RUN — SQL will be printed but NOT executed ==="
  echo
fi

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

export PGPASSWORD="$PG_PASSWORD"
PSQL_CMD="psql -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB -v ON_ERROR_STOP=1"

# ── Prerequisite check ───────────────────────────────────────────────────────
check_prereqs() {
  local missing=()
  for cmd in mongoexport jq psql uuidgen; do
    if ! command -v "$cmd" &>/dev/null; then
      missing+=("$cmd")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "ERROR: Missing required tools: ${missing[*]}" >&2
    echo "Install them before running this script." >&2
    exit 1
  fi
}

# ── Helper: run or print SQL ─────────────────────────────────────────────────
run_sql() {
  local sql="$1"
  if [[ "$DRY_RUN" == true ]]; then
    echo "$sql"
    echo
  else
    echo "$sql" | $PSQL_CMD --quiet
  fi
}

# ── Helper: extract ISO date from mongoexport $date field ────────────────────
# mongoexport --jsonFormat=canonical outputs {"$date":"2024-01-15T10:30:00.000Z"}
# mongoexport in relaxed mode may output ISODate strings directly.
# jq handles both: if it's an object with $date, extract it; else use as-is.
JQ_DATE_EXTRACT='if type == "object" and has("$date") then .["$date"] elif type == "string" then . else null end'

# ── Helper: escape single quotes for SQL strings ────────────────────────────
sql_escape() {
  echo "$1" | sed "s/'/''/g"
}

# ── Migrate: queues → queue_configs ──────────────────────────────────────────
migrate_queues() {
  echo "── Migrating queues → queue_configs ──"

  local export_file="$WORK_DIR/queues.json"
  mongoexport --uri="$MONGO_URI" --db="$MONGO_DB" --collection=queues --jsonArray --out="$export_file" 2>/dev/null

  local count
  count=$(jq 'length' "$export_file")
  echo "   Exported $count documents from MongoDB"

  if [[ "$count" -eq 0 ]]; then
    echo "   Nothing to migrate."
    return
  fi

  local sql="-- queue_configs migration\n"
  if [[ "$DRY_RUN" == false ]]; then
    sql+="BEGIN;\n"
  fi

  while IFS= read -r doc; do
    local id
    id=$(uuidgen | tr '[:upper:]' '[:lower:]')
    local namespace queue_name concurrency enabled deleted
    namespace=$(sql_escape "$(echo "$doc" | jq -r '.namespace // ""')")
    queue_name=$(sql_escape "$(echo "$doc" | jq -r '.queueName // ""')")
    concurrency=$(sql_escape "$(echo "$doc" | jq -r '.concurrency // "1-1"')")
    enabled=$(echo "$doc" | jq -r '.enabled // true')
    deleted=$(echo "$doc" | jq -r '.deleted // false')

    local updated_at created_at
    updated_at=$(echo "$doc" | jq -r "(.updatedAt // null) | $JQ_DATE_EXTRACT")
    created_at=$(echo "$doc" | jq -r "(.createdAt // null) | $JQ_DATE_EXTRACT")

    local ts_updated ts_created
    ts_updated=$( [[ "$updated_at" == "null" ]] && echo "NULL" || echo "'$updated_at'" )
    ts_created=$( [[ "$created_at" == "null" ]] && echo "NULL" || echo "'$created_at'" )

    sql+="INSERT INTO queue_configs (id, namespace, queue_name, concurrency, enabled, updated_at, created_at, deleted)\n"
    sql+="VALUES ('$id', '$namespace', '$queue_name', '$concurrency', $enabled, $ts_updated, $ts_created, $deleted)\n"
    sql+="ON CONFLICT DO NOTHING;\n"
  done < <(jq -c '.[]' "$export_file")

  if [[ "$DRY_RUN" == false ]]; then
    sql+="COMMIT;\n"
  fi

  run_sql "$(echo -e "$sql")"
  echo "   Loaded into queue_configs"
}

# ── Migrate: mappings → response_mappings ────────────────────────────────────
migrate_mappings() {
  echo "── Migrating mappings → response_mappings ──"

  local export_file="$WORK_DIR/mappings.json"
  mongoexport --uri="$MONGO_URI" --db="$MONGO_DB" --collection=mappings --jsonArray --out="$export_file" 2>/dev/null

  local count
  count=$(jq 'length' "$export_file")
  echo "   Exported $count documents from MongoDB"

  if [[ "$count" -eq 0 ]]; then
    echo "   Nothing to migrate."
    return
  fi

  local sql="-- response_mappings migration\n"
  if [[ "$DRY_RUN" == false ]]; then
    sql+="BEGIN;\n"
  fi

  while IFS= read -r doc; do
    local id
    id=$(uuidgen | tr '[:upper:]' '[:lower:]')
    local namespace queue_name enabled priority deleted
    namespace=$(sql_escape "$(echo "$doc" | jq -r '.namespace // ""')")
    queue_name=$(sql_escape "$(echo "$doc" | jq -r '.queueName // ""')")
    enabled=$(echo "$doc" | jq -r '.enabled // true')
    priority=$(echo "$doc" | jq -r '.priority // "NULL"')
    deleted=$(echo "$doc" | jq -r '.deleted // false')

    # Nested objects: strip _class, keep as JSONB
    local match_json response_json delay_json
    match_json=$(sql_escape "$(echo "$doc" | jq -c '(.match // {}) | del(._class)')")
    response_json=$(sql_escape "$(echo "$doc" | jq -c '(.response // {}) | del(._class)')")
    delay_json=$(sql_escape "$(echo "$doc" | jq -c '(.delay // {}) | del(._class)')")

    local updated_at created_at
    updated_at=$(echo "$doc" | jq -r "(.updatedAt // null) | $JQ_DATE_EXTRACT")
    created_at=$(echo "$doc" | jq -r "(.createdAt // null) | $JQ_DATE_EXTRACT")

    local ts_updated ts_created
    ts_updated=$( [[ "$updated_at" == "null" ]] && echo "NULL" || echo "'$updated_at'" )
    ts_created=$( [[ "$created_at" == "null" ]] && echo "NULL" || echo "'$created_at'" )

    local priority_val
    priority_val=$( [[ "$priority" == "NULL" ]] && echo "NULL" || echo "$priority" )

    sql+="INSERT INTO response_mappings (id, namespace, queue_name, match, response, delay, enabled, priority, updated_at, created_at, deleted)\n"
    sql+="VALUES ('$id', '$namespace', '$queue_name', '$match_json'::jsonb, '$response_json'::jsonb, '$delay_json'::jsonb, $enabled, $priority_val, $ts_updated, $ts_created, $deleted)\n"
    sql+="ON CONFLICT DO NOTHING;\n"
  done < <(jq -c '.[]' "$export_file")

  if [[ "$DRY_RUN" == false ]]; then
    sql+="COMMIT;\n"
  fi

  run_sql "$(echo -e "$sql")"
  echo "   Loaded into response_mappings"
}

# ── Migrate: userProfiles → user_profiles ────────────────────────────────────
migrate_users() {
  echo "── Migrating userProfiles → user_profiles ──"

  local export_file="$WORK_DIR/userProfiles.json"
  mongoexport --uri="$MONGO_URI" --db="$MONGO_DB" --collection=userProfiles --jsonArray --out="$export_file" 2>/dev/null

  local count
  count=$(jq 'length' "$export_file")
  echo "   Exported $count documents from MongoDB"

  if [[ "$count" -eq 0 ]]; then
    echo "   Nothing to migrate."
    return
  fi

  local sql="-- user_profiles migration\n"
  if [[ "$DRY_RUN" == false ]]; then
    sql+="BEGIN;\n"
  fi

  while IFS= read -r doc; do
    local id
    id=$(uuidgen | tr '[:upper:]' '[:lower:]')
    local user_id email first_name last_name password_hash default_namespace role active deleted
    user_id=$(sql_escape "$(echo "$doc" | jq -r '.userId // ""')")
    email=$(sql_escape "$(echo "$doc" | jq -r '.email // ""')")
    first_name=$(sql_escape "$(echo "$doc" | jq -r '.firstName // ""')")
    last_name=$(sql_escape "$(echo "$doc" | jq -r '.lastName // ""')")
    password_hash=$(sql_escape "$(echo "$doc" | jq -r '.passwordHash // ""')")
    default_namespace=$(sql_escape "$(echo "$doc" | jq -r '.defaultNamespace // ""')")
    role=$(sql_escape "$(echo "$doc" | jq -r '.role // "USER"')")
    active=$(echo "$doc" | jq -r '.active // true')
    deleted=$(echo "$doc" | jq -r '.deleted // false')

    # namespaces is an array → JSONB
    local namespaces_json
    namespaces_json=$(sql_escape "$(echo "$doc" | jq -c '.namespaces // []')")

    local created_at last_login
    created_at=$(echo "$doc" | jq -r "(.createdAt // null) | $JQ_DATE_EXTRACT")
    last_login=$(echo "$doc" | jq -r "(.lastLogin // null) | $JQ_DATE_EXTRACT")

    local ts_created ts_last_login
    ts_created=$( [[ "$created_at" == "null" ]] && echo "NULL" || echo "'$created_at'" )
    ts_last_login=$( [[ "$last_login" == "null" ]] && echo "NULL" || echo "'$last_login'" )

    sql+="INSERT INTO user_profiles (id, user_id, email, first_name, last_name, password_hash, namespaces, default_namespace, role, created_at, last_login, active, deleted)\n"
    sql+="VALUES ('$id', '$user_id', '$email', '$first_name', '$last_name', '$password_hash', '$namespaces_json'::jsonb, '$default_namespace', '$role', $ts_created, $ts_last_login, $active, $deleted)\n"
    sql+="ON CONFLICT (user_id) DO NOTHING;\n"
  done < <(jq -c '.[]' "$export_file")

  if [[ "$DRY_RUN" == false ]]; then
    sql+="COMMIT;\n"
  fi

  run_sql "$(echo -e "$sql")"
  echo "   Loaded into user_profiles"
}

# ── Migrate: namespaces → namespaces ─────────────────────────────────────────
migrate_namespaces() {
  echo "── Migrating namespaces → namespaces ──"

  local export_file="$WORK_DIR/namespaces.json"
  mongoexport --uri="$MONGO_URI" --db="$MONGO_DB" --collection=namespaces --jsonArray --out="$export_file" 2>/dev/null

  local count
  count=$(jq 'length' "$export_file")
  echo "   Exported $count documents from MongoDB"

  if [[ "$count" -eq 0 ]]; then
    echo "   Nothing to migrate."
    return
  fi

  local sql="-- namespaces migration\n"
  if [[ "$DRY_RUN" == false ]]; then
    sql+="BEGIN;\n"
  fi

  while IFS= read -r doc; do
    local id
    id=$(uuidgen | tr '[:upper:]' '[:lower:]')
    local name display_name description owner active deleted
    name=$(sql_escape "$(echo "$doc" | jq -r '.name // ""')")
    display_name=$(sql_escape "$(echo "$doc" | jq -r '.displayName // ""')")
    description=$(sql_escape "$(echo "$doc" | jq -r '.description // ""')")
    owner=$(sql_escape "$(echo "$doc" | jq -r '.owner // ""')")
    active=$(echo "$doc" | jq -r '.active // true')
    deleted=$(echo "$doc" | jq -r '.deleted // false')

    # members is an array → JSONB
    local members_json
    members_json=$(sql_escape "$(echo "$doc" | jq -c '.members // []')")

    local created_at
    created_at=$(echo "$doc" | jq -r "(.createdAt // null) | $JQ_DATE_EXTRACT")

    local ts_created
    ts_created=$( [[ "$created_at" == "null" ]] && echo "NULL" || echo "'$created_at'" )

    sql+="INSERT INTO namespaces (id, name, display_name, description, members, owner, created_at, active, deleted)\n"
    sql+="VALUES ('$id', '$name', '$display_name', '$description', '$members_json'::jsonb, '$owner', $ts_created, $active, $deleted)\n"
    sql+="ON CONFLICT (name) DO NOTHING;\n"
  done < <(jq -c '.[]' "$export_file")

  if [[ "$DRY_RUN" == false ]]; then
    sql+="COMMIT;\n"
  fi

  run_sql "$(echo -e "$sql")"
  echo "   Loaded into namespaces"
}

# ── Main ─────────────────────────────────────────────────────────────────────
main() {
  echo "============================================="
  echo " MongoDB → PostgreSQL Data Migration"
  echo "============================================="
  echo
  echo "Source:  $MONGO_URI / $MONGO_DB"
  echo "Target:  postgresql://$PG_HOST:$PG_PORT/$PG_DB"
  echo

  check_prereqs

  if [[ "$DRY_RUN" == false ]]; then
    echo "This will INSERT data into PostgreSQL (using ON CONFLICT DO NOTHING)."
    read -rp "Continue? [y/N] " confirm
    if [[ "$confirm" != [yY] ]]; then
      echo "Aborted."
      exit 0
    fi
    echo
  fi

  migrate_namespaces
  echo
  migrate_users
  echo
  migrate_queues
  echo
  migrate_mappings

  echo
  echo "============================================="
  echo " Migration complete!"
  echo "============================================="

  if [[ "$DRY_RUN" == false ]]; then
    echo
    echo "Row counts:"
    $PSQL_CMD -c "
      SELECT 'namespaces' AS table_name, COUNT(*) FROM namespaces
      UNION ALL SELECT 'user_profiles', COUNT(*) FROM user_profiles
      UNION ALL SELECT 'queue_configs', COUNT(*) FROM queue_configs
      UNION ALL SELECT 'response_mappings', COUNT(*) FROM response_mappings;
    "
  fi
}

main
