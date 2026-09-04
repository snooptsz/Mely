# M3LY Android hotfix validation provenance

This directory is a **build-only validation harness**, not the authoritative M3LY source tree and not a production messenger.

Authoritative source: private GitLab project `m3ly/mely`.

Production release branch at the time this harness was created: `release/m3ly-live-beta-20260902`.

Hotfix release SHA: `cad0dc04415bce904fe67e999f1a009d02c5ea1b`.

Validated behaviors:

- browser-approved Android device pairing against the production `m3ly-api` route;
- pairing proof remains after the URL fragment (`#`) and is not sent in the HTTP query;
- authenticated current-device revocation through `m3ly-device-logout`;
- local Android bearer credential deletion after logout;
- a visible Logout control in the validation header.

This harness must never be treated as authoritative or promoted as the full M3LY production application.
