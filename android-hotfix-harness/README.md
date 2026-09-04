# M3LY Android Hotfix Validation

This is a temporary side-by-side Android test app used to validate the production pairing and current-device logout hotfix while the canonical GitLab Android release runner is unavailable.

It does **not** contain the M3LY messenger protocol implementation and must not be distributed as the production M3LY application.

Install it beside the production app, run the browser pairing flow, verify that the paired identity appears, then press **Logout** in the header. Successful logout revokes the validation device at the production backend and deletes the local bearer credential.
