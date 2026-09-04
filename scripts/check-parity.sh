#!/usr/bin/env bash
set -euo pipefail

# Supply the authoritative GitLab commit SHA from a trusted GitLab-side job or a
# human verification step. This script never authenticates to GitLab and needs no secret.
: "${AUTHORITATIVE_SHA:?Set AUTHORITATIVE_SHA to the reviewed GitLab source commit}"

recorded="$(python3 - <<'PY'
import json
with open('MIRROR_SOURCE.json', encoding='utf-8') as f:
    print(json.load(f)['source_commit'])
PY
)"

if [[ ! "$AUTHORITATIVE_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "ERROR: AUTHORITATIVE_SHA must be a full lowercase Git commit SHA" >&2
  exit 1
fi

if [[ "$recorded" != "$AUTHORITATIVE_SHA" ]]; then
  echo "ERROR: mirror source marker differs from authoritative SHA" >&2
  echo "recorded=$recorded" >&2
  echo "authoritative=$AUTHORITATIVE_SHA" >&2
  exit 1
fi

echo "Parity marker matches authoritative GitLab SHA: $recorded"
