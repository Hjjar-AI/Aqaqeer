# Repository instructions

## Scope and authorization

- Read applicable AGENTS files and current work records first; verify production source before trusting historical findings. `docs/requirements.md` is authoritative; `docs/architecture.md` describes mechanisms, `docs/plan.md` remaining work, and `docs/validation.md` executed evidence. Crypto contracts live under `docs/crypto/`.
- Do not inspect, create, modify or execute tests without an explicit request. Do not inspect/change migrations without an explicit migration request. Ask before Gradle, compilation, packaging or lint unless that activity is already authorized in the session. Manual version changes need authorization; an authorized build may reserve its normal wrapper code.
- Reuse authorization within its scope; do not repeatedly request it. During test development keep lint off. Run final lint only after other authorized work/cleanup finishes and explicit lint authorization exists; a new stop or approval gate takes precedence.
- Track authorization/stops by activity and environment: hosted CI work does not resume local builds/lint, physical tests or live integration. Stop only the activity the user stops, clean up its owned resources and preserve its checkpoint; continue independent authorized work. Reuse explicit authorization within its scope; instructions or workflow definitions alone do not grant execution permission.
- Preserve unrelated edits. Fix evidenced problems, reuse existing components/interfaces and avoid speculative rewrites. During tests, production changes require an evidenced blocking bug within scope or explicit authorization for a test seam. Never weaken behavioral assertions to obtain passes.

## Toolchain and device ownership

- `app/` is Kotlin/Compose; `docs/` canonical guidance; `assets/branding/` selected artwork; `tools/` public utilities; `local/` ignored machine state/private evidence. Follow pinned versions and `docs/build.md`; JDK 21 targets Java 17.
- Preserve shared SDK/tools and working caches. Diagnose ownership, group membership, ACLs and ownership-only operations before repairs/downloads. Repair only affected paths; download only proven-missing artifacts. Never create a fresh cache to bypass access failures.
- Use wrappers for packaging. Preserve `local/versioning/last-code.txt` and its lock; non-packaging checks must not advance it, while authorized packaging reserves its normal code. Never reset or manually override the counter to recover from a failed build.
- Bind every ADB command to the authorized target serial. Physical campaigns require the identified phone; hosted emulator helpers must verify their owned ephemeral emulator and refuse physical devices. Serialize shared namespaces. Record package, signer, artifact, UID/install identity and run ownership before mutation; recheck before delayed cleanup. Package name alone does not prove ownership.
- Preserve existing phone apps/data and private signing/Keystore keys. Never uninstall or clear data to bypass a signer conflict. Remove only owned temporary packages/resources; confirm absence and retain cleanup failures accurately. Integration tools require authorization for their device/network effects.

## Verification and records

- For an authorized batch, finish planned test files before its first run. Check dependencies, current constructors, result contracts and real cleanup semantics before writing fixtures. Validate fault/pressure preconditions with observations. Retain valid passes; retry only failed/unexecuted checks or checks invalidated by relevant changes.
- Default checks are production call-site/state/semantics review, whitespace and changed-document local links. Documentation-only work needs link/integrity checks, not builds, tests, lint, devices or network access.
- Credentials belong in ignored protected storage: `local/verification/credentials/telegram-test-config.local.json`, mode 0600. Never print secrets, token URLs or credential hashes; never embed them in source/APKs/logs. Public crypto fixtures are not production credentials.
- Keep remaining work in the plan and completed evidence/limits in validation. Distinguish source, compilation, runtime and human acceptance; count scenarios separately from phase invocations. Missing instrumentation, interrupted hosts and missing prerequisites are not app passes or automatically app bugs.
- `local/verification/manifest.json` inventories essential public evidence. Before cleanup, follow manifest/document/result references and verify retained hashes. Keep a useful preview, source/artifact/device provenance, fresh results and original per-case evidence for failures/interruption. Prune only confirmed-unused generated or redundant files; preserve unique downloads, credentials, keys, counters and reusable tooling. Never hash protected credentials.
