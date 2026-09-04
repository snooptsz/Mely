# M3ly Mirror Policy

## Authority

GitLab project `m3ly/mely` is authoritative. This GitHub repository must never become the source of truth for production code, releases, secrets, deployment configuration, infrastructure state, or operational decisions.

## Publication model

Only content deliberately approved for public release may be mirrored. Prefer an explicit allowlist over copying the private repository wholesale.

### Allowed baseline

- public source code intentionally approved for release
- public documentation
- public API/interface specifications
- public tests and reproducible verification scripts
- public release metadata and checksums
- licences and security contact information

### Never mirror

- `.env*` files except redacted examples containing placeholders only
- API keys, tokens, passwords, cookies, session material or credentials
- signing keys, certificates with private keys, keystores or recovery material
- private CI/CD variables or deployment credentials
- production database dumps, user records, telemetry, message contents or logs containing personal data
- private Supabase, Cloudflare, Android signing, infrastructure or administrator material
- incident evidence that has not been explicitly sanitised for public release
- internal-only architecture, vulnerability details or security controls whose publication creates avoidable risk

## Direction of trust

Changes flow **GitLab -> sanitisation/verification -> GitHub**. GitHub-originated commits must not flow automatically into production or the authoritative GitLab branch. Any useful GitHub contribution must be independently reviewed and re-applied through the authoritative GitLab workflow.

## Branch expectations

`main` should be protected once repository controls are available:

1. prohibit force pushes and deletion;
2. require pull requests for human-authored GitHub changes;
3. require the mirror-baseline verification check;
4. require review for any exception to the mirror allowlist;
5. do not grant GitHub Actions write permission beyond what is strictly necessary;
6. do not configure deployment environments or production secrets in this repository.

Mirror automation, if later introduced, should use a dedicated least-privilege identity restricted to writing mirror content only. It must have no production credentials.

## Parity

`MIRROR_SOURCE.json` records the authoritative source revision represented by this public baseline. Parity means the public mirror corresponds to an explicitly selected, sanitised GitLab revision; it does **not** mean every private GitLab file must exist publicly.

A parity check should fail when:

- the source revision marker is missing or malformed;
- a prohibited file pattern appears;
- obvious credential material is detected;
- required policy files are absent;
- mirror metadata claims a source revision that has not been reviewed for public publication.

## Production isolation

Nothing in this repository may deploy M3ly, alter production infrastructure, change billing/spending, rotate secrets, modify production databases, or make GitHub authoritative.
