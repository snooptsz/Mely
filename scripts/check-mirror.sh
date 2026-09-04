#!/usr/bin/env bash
set -euo pipefail

fail() { echo "ERROR: $*" >&2; exit 1; }

required=(README.md MIRROR_POLICY.md MIRROR_SOURCE.json SECURITY.md .gitignore)
for f in "${required[@]}"; do
  [[ -f "$f" ]] || fail "missing required mirror file: $f"
done

python3 - <<'PY'
import json, re
p='MIRROR_SOURCE.json'
with open(p, encoding='utf-8') as f:
    d=json.load(f)
assert d.get('authority')=='gitlab', 'authority must remain gitlab'
assert d.get('authoritative_project')=='m3ly/mely', 'unexpected authoritative project'
assert d.get('authoritative_branch')=='main', 'unexpected authoritative branch'
assert re.fullmatch(r'[0-9a-f]{40}', d.get('source_commit','')), 'source_commit must be a full SHA-1'
assert d.get('github_authoritative') is False, 'GitHub must not be authoritative'
assert d.get('production_deployments_from_github') is False, 'GitHub production deploys must remain disabled'
PY

# Block file classes that should never be present in this public mirror.
while IFS= read -r path; do
  case "$path" in
    .env|.env.*|*.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|*.mobileprovision|*.sqlite|*.sqlite3|*.db|*.dump|*.tfstate|*.tfstate.*|*.kubeconfig|kubeconfig|*.bak|*.backup)
      [[ "$path" == ".env.example" ]] || fail "prohibited file class present: $path" ;;
  esac
done < <(git ls-files)

# Conservative credential-pattern check over tracked text. This is not a replacement
# for provider-native secret scanning; it is a deterministic baseline guard.
if git grep -nEI '(BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{20,}|glpat-[A-Za-z0-9_-]{20,}|-----BEGIN PRIVATE KEY-----)' -- . ':!scripts/check-mirror.sh'; then
  fail "possible credential material detected"
fi

# There must be no production deployment workflow in the public baseline.
if [[ -d .github/workflows ]]; then
  if git grep -nEI '(deploy[[:space:]_-]*(prod|production)|environment:[[:space:]]*production|wrangler[[:space:]]+deploy|supabase[[:space:]]+db[[:space:]]+push|kubectl[[:space:]]+apply|terraform[[:space:]]+apply)' -- .github/workflows; then
    fail "production deployment instruction detected in GitHub workflow"
  fi
fi

echo "M3ly mirror baseline verification passed."
