#!/usr/bin/env bash
# CLI alternative to the Conduktor UI. Registers every connector in connector/*.json
# (or only the files you pass as arguments) via the Kafka Connect REST API.
set -euo pipefail
cd "$(dirname "$0")/.."
files=("$@")
[ ${#files[@]} -eq 0 ] && files=(connector/*.json)

for f in "${files[@]}"; do
  name=$(sed -n 's/.*"name": *"\([^"]*\)".*/\1/p' "$f" | head -1)
  code=$(curl -s -o /tmp/connect-resp.json -w '%{http_code}' -X POST \
    -H "Content-Type: application/json" --data @"$f" http://localhost:8083/connectors)
  case "$code" in
    201) echo "created: $name" ;;
    409) echo "already exists: $name" ;;
    *)   echo "FAILED ($code): $name"; cat /tmp/connect-resp.json; echo; exit 1 ;;
  esac
  curl -fsS "http://localhost:8083/connectors/$name/status"; echo
done
