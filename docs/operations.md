# Operations

## Current capabilities

Creation, persistent admin authorization, catalog/availability editing, Telegram setup/publication, connection sharing, cached viewing/refresh, encrypted backup/restore and credential maintenance are implemented. Current results and limits belong in [validation](validation.md), remaining checks in [plan](plan.md). Demo requires no credentials; real credentials belong only in protected ignored storage.

## Telegram setup and publishing

Use one dedicated private group and bot, with one bot-owned encrypted pinned document. Setup must validate bot/group identity, permissions and webhook ownership; use challenge-based getUpdates only during setup, with validated manual group-ID fallback. Never silently remove another integration. Bootstrap uploads, rechecks the pin, pins and authenticates read-back before marking the pharmacy shared. An existing pin blocks bootstrap. Missing pins after prior publication require explicit retained-state recovery. Issue connection files only after a confirmed real snapshot. Wire/caption rules are in [readers](crypto/READERS.md).

Admins coordinate one publisher at a time. Telegram offers no guaranteed distributed lock or conditional atomic write; foreground reconnect retry is best effort. Do not post publication announcements or treat local history as a remote audit. Shared bot token/snapshot key do not cryptographically enforce reader rights.

## Backup/restore

Platform automatic backup/device transfer are excluded. Admin Settings and onboarding provide explicit backup/restore. `.aqbak` uses an exact, unretained free-text passphrase of at least eight characters, confirmed twice on export. Complete encrypted-envelope cap is **64 MiB (67,108,864 bytes)**; connection/snapshot cap is **10 MiB**. All retained device history must survive export/restore; no truncation. Size/resource/cancellation/close failures preserve existing data and do not record success. See [formats](crypto/FORMATS.md) for resource constraints and [requirements](requirements.md#11-backup-and-restore) for full workflow/reminder rules.

Capture catalog/history/tombstones, confirmed base, pending edits, uncertain attempt candidate/base/operation IDs/progress, credentials/key version and verifier consistently. Exclude active device identity, Keystore wrappers and adminMode; retain historical editor attribution only. New phones generate a new identity and receive no old history except an explicit backup's device-local history. Backups are not a complete pharmacy audit.

Validate/decrypt/stage the supported initial payload before transactional replacement; never wipe first. Restore remains local and publication-paused. Reconcile uncertain attempts against live state before acknowledgment/replay. Existing live pharmacy reconciliation or confirmed replacement must preserve the **current live PIN verifier**, including against restored pending PIN changes, and require current-live-PIN authorization before publishing. Observing a changed live verifier revokes restored authorization. Replacement publishes live revision+1 with parent=live publishId; it never rewinds or automatically overwrites the group.

A seven-day in-app reminder counts from first local admin authorization or the latest successful export. Dismissal snoozes seven days; cancelled/failed exports never advance it. Its timestamps are local, excluded from backups and cleared on leave.

After restore, authorize using the backed-up PIN for local work. Confirm the retained group, optionally replacing the token for the same bot. If the live PIN differs, authorize again with that current PIN. Reconciliation never uploads: review the resulting pending changes and publish separately. A different bot/group or unreadable/missing live snapshot cannot silently resume shared publication.

## Credential maintenance

Functional and partial interruption evidence exists; remaining acceptance is recorded in [validation](validation.md). Use Settings → connection/credential maintenance. Maintenance pauses ordinary edits, refresh/reconnect and publication while retaining cached viewing, authorized backup/history and help. Resume the phase shown after interruption. Do not clear app data, replace keys or delete retained staging to bypass recovery. Confirmed Leave explicitly removes this device's maintenance credentials/staging along with local work; it does not undo Telegram actions. Restoring a backup never resumes the exporting device's maintenance journal or authorization.

For same-pharmacy replacement, readers/admins select the new `.aqz`, enter its password, verify the live pharmacy and confirm activation. Different pharmacy UUIDs are rejected. Verification failure/offline use preserves the active connection; cancellation before activation removes only the candidate. Activation downloads again and preserves history, original pending bases and uncertain attempts. Readers receive validated confirmed data without admin rights. An authorized admin explicitly reviews retained intentions/conflicts and authenticates uncertain attempts rather than replaying obsolete transport steps. If the live PIN changed, authorize with that PIN. Finishing review never schedules publication.

For PIN changes, enter the current PIN, new PIN and confirmation, then save/review/publish the normal metadata change. Only this phone's exact authorized change, verified against its retained publication attempt, preserves its authorization, including lost responses. Other live verifier changes revoke authorization. Offline devices are not locked immediately, and no PIN recovery bypass exists.

For rotation/migration, first resolve local pending edits and attempts, start maintenance and authenticate/reconcile the source. Save a **new successful encrypted backup inside this operation** and retain its passphrase separately. The destination must close successfully within the existing complete-envelope **64 MiB** cap; failure, cancellation, earlier backups and reminders do not qualify. Source changes invalidate the receipt and require reconciliation plus another export before proceeding. A backup made outside this wizard does not advance it.

For token/key rotation, retain the source before following the manual BotFather revocation guidance. Enter a replacement token for the same actual bot and group. The app authenticates live data with the old key, then stages a new key/version and publishes at live revision+1 with its parent. Only new-key authenticated read-back enables local activation/completion and later `.aqz` issuance. A failed response leaves resumable protected progress; re-open maintenance and inspect the live result. If the old token was revoked, Cancel cannot restore it: review a verified replacement-token/old-key connection or finish rotation. Unexpected keys/publications require recovery review.

For migration, review source identity and confirm the permission-checked destination bot/private group, which must have no pin. If the source is unavailable, explicitly review retained publication identity/time and the warning about unseen remote edits before backup. Destination publication preserves pharmacy UUID and source revision+1/parent lineage. Before any possible destination publication, cancellation retains the original connection. After a possible pin/media write, reconcile the destination before retry/cancellation; an existing destination publication must be reviewed rather than overwritten. Explicit rollback review requires a currently usable authenticated connection. Never assume a timeout means no remote change occurred.

After verified activation, confirm completion, issue/distribute replacement `.aqz` files and passwords separately, and have recipients use same-pharmacy replacement to retain local work. Reissuing a file/password alone revokes nothing. Rotation cannot recall cached data or old backups. Migration does not erase old snapshots, revoke old bot credentials or stop old devices: coordinate manual source retirement/revocation, otherwise devices may continue publishing a separate obsolete copy. Telegram concurrency remains best effort; coordinate one publisher.

## Verification tools

`local/verification/runs` keeps the latest delivery at the top level and older campaigns under `history/`. The [evidence manifest](../local/verification/manifest.json) indexes their current paths. Historical result/log files retain their original bytes and execution paths; resolve an old recorded path before opening it:

```sh
python3 local/verification/resolve_evidence.py 'runs/OLD-RUN/results.json'
```

The lookup accepts original absolute paths, project-relative `local/verification/...` paths and manifest-relative paths. It only locates retained evidence; it does not run verification or access credential contents. Missing retired artifacts remain missing, with their identity/retirement receipts in the manifest. Keep the current preview, original failure/interruption evidence and reference mappings during future cleanup.

The single private configuration is `local/verification/credentials/telegram-test-config.local.json`, Git-ignored and mode `0600`. Never log it, token-bearing URLs or credential hashes. Do not copy it into source/assets/APKs. [Manifest](../local/verification/manifest.json) inventories retained public artifacts without exposing credentials.

[Preflight](../local/verification/tools/preflight.py) resolves credentials relative to its file and performs read-only Telegram identity/group/membership/webhook checks. [Integration runner](../local/verification/tools/run-real.py) requires freshly built/installed isolated verification application and test APKs, ADB at `/opt/android-shared/Sdk/platform-tools/adb`, and the authorized dedicated group. It injects a protected temporary configuration, runs the real integration case, and removes only test-recorded Telegram messages/pin and injected phone files. It performs device actions and remote mutations: execute only when those checks are explicitly requested. New output goes to ignored `local/verification/runs/<UTC timestamp>/`, preserving baseline evidence. Documentation cleanup alone does not authorize either script.

## Fresh initial database and file baseline

Production uses Room version 1 and initial shared/file schema version 1, without price/currency or historical development-format upgrades. Earlier development databases/files are unsupported; never wipe an installed database or replace keys to bypass a mismatch. Current export/build instructions belong in [build](build.md), implementation ownership in [architecture](architecture.md), and verification status in [validation](validation.md).
