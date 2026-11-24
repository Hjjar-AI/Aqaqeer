# Remaining-work plan

[Requirements](requirements.md) define acceptance; [architecture](architecture.md) describes mechanisms; [validation](validation.md) owns dated evidence. This backlog grants no execution authorization.

<a id="android-ui-cleanup--source-implemented--2026-11-17"></a>
<a id="deferred-search-and-grouping-preferences--p2"></a>
<a id="demo-and-separate-flavor-builds--p1"></a>
<a id="initial-attribution-free-baseline--2026-11-16"></a>
<a id="responsive-ui-follow-up--source-implemented--2026-11-17"></a>
<a id="review-fixes-and-home-placement--source-implemented--2026-11-15"></a>
<a id="review-fixes-home-placement"></a>
<a id="search-and-grouping-verification--p2"></a>
<a id="string-resource-organization--source-implemented--2026-11-17"></a>
<a id="user-correction-follow-up--2026-11-12"></a>
<a id="validated-ui-refactoring--source-implemented--2026-11-22"></a>

## Implemented baseline

**Resumed at the user’s request on 2026-11-24.** Preserve existing hosted runs at `e808c13` and retain their valid results before targeting failed or relevantly invalidated cases. Detailed continuation checkpoint (local-only): `local/verification/hosted-completion-20261124/HANDOFF.md`. Final hosted lint and release acceptance are unfinished; do not infer passes from selections.

Current execution authorization — 2026-11-24: complete the listed V1/C1/UI1/UI2/F1/S1/REL1/REL2/CI1 work, finish the missing test batch before execution, run/review hosted workflows and reconcile records. Hosted compilation/packaging, emulator recovery/resource checks, repository/security/tooling checks and final hosted lint are authorized. Keep lint last after source fixes/cleanup. Physical/live campaigns remain stopped; Windows and human acceptance require their actual environments. No conversion migration or public release publication is inferred.

The implemented catalog/admin, sharing/recovery, CSV/PDF/XLSX, demo and presentation baseline is described by [requirements](requirements.md) and [architecture](architecture.md). Recent source includes the native startup-gate repair, bounded dependency-only HTTP429 CI retry, optional card hints, separate C2 medication badges and 140 dp adaptive cards. Preserve settled policies, initial-format version 1, keys/counters and unrelated edits.

Current hosted evidence is reconciled by revision in [validation](validation.md#hosted-completion-batch--2026-11-24). Workflow03 passed620 standard JVM cases/48 intentional skips and653 demo cases/15 skips, both debug/instrumentation-source compilations, release compilation and JDK vectors. The resumed hosted helper batch passed23 Python checks; later local helper contracts passed27. Current policy/actionlint passed. Two CodeQL certificate-pinning recommendations remain open; no blanket security clearance is claimed.

Workflow07 selects223 standard methods and16 demo invocations on each API29/35:255 invocations/237 unique methods. Across retained originals and relevant retries,222/223 standard methods and30/32 demo invocations have scoped passes. All three new BackupScreen methods passed. Remaining Android work is the single bulk-preview selector check and demo search/clear on both APIs, currently consolidated in run45435785614 at `39775cf`; retain every unaffected pass. Empty retry batches now skip Android tooling and emulators. Source completion is not a runtime pass.

Storage37/37, recovery38/38 semantic scenarios and controlled heap-pressure rejection/release/retry passed at `e808c13`. Large-history retry45404941086 at `c98e65b` passed complete180/480-row round trips in21,040,470/56,095,670-byte envelopes with an observed384-MiB hosted runtime, owned-package cleanup and unchanged counter. All five original unsigned runtime graphs match139 reviewed coordinates. The notice texts are ready in source; newly packaged comparison and three production signatures remain pending. User-authorized workflow09 removed26 completed runs after needed evidence and2,176 public hashes were retained/verified; active runs, local evidence, releases and caches were preserved.

After the remaining two methods settle: run the explicitly authorized workflow11 `publication=artifacts version_code=32`, verify downloaded signed artifacts/notices, then final hosted lint after all source fixes/cleanup, and reconcile records. Signing configuration names/public certificate and the read-only code32 suggestion passed preflight; code32 has not been reserved. No GitHub Release publication is authorized. Keep Windows, installed-update, physical/live and human acceptance boundaries explicit; the matrix below retains broader obligations rather than implying that selected automated cases cover every environment.

<a id="v1--deferred-backuprestore-validation-gate--p1"></a>

## V1 — Backup/restore verification gate / P1

**First priority.** Use audit rows BR01–BR20 to select the next authorized batch. Runtime work depends on current compilation, the required harness/artifacts and separate applicable authorization. No development-format conversion or migration inspection is included.

| Remaining work | Completion criterion |
| --- | --- |
| Current payload and authorization | Establish current initial-format round trips, complete history above 10 MiB within the 64 MiB envelope cap, consistent bases/pending/attempts/credentials, identity/authorization/reminder exclusions, role/typed-replacement guards and compatibility restrictions. |
| Failure and restore recovery | Establish Unicode/randomness, malformed/unsupported/reference/over-cap/resource/storage/provider-close/cancellation behavior; authenticate before replacement; cover staging/replacement/activation interruption without data loss or false success. Keep SIGKILL, heap pressure, OS low-memory termination and power loss distinct. |
| Reconciliation and live PIN | Establish restore pause, same-bot connection confirmation, uncertain-attempt/conflict review, current-live-PIN preservation/reauthorization and replacement lineage, followed by separate explicit publication. Do not infer live success from fake or historical source checks. |
| Reminder and maintenance backup | Establish role/day/leave/success-versus-failure reminder boundaries and operation/source-bound fresh-backup receipts. Current hosted simulated maintenance PREPARING retry, post-active verification and logical-cleanup interruption passed within the 38-scenario run. Retain earlier evidence within its dated scope; remaining runtime boundaries must come from failed or unexecuted cases. |

The retained live-PIN/manual-republication/reader-connection extension remains optional while physical/live campaigns are stopped. Broader devices/providers, human print/spoken/visual acceptance, OS low-memory termination and power-loss results stay explicitly unverified; they are not silently converted to passes or required new campaigns.

<a id="ordered-packages"></a>
<a id="pdf-redesign--a5-source-implemented"></a>
<a id="remaining-verification"></a>
<a id="repositoryrelease-follow-up--2026-11-14"></a>
<a id="sharing-review-follow-up--p1"></a>
<a id="ui-review-follow-up--p1"></a>

## Ordered delivery backlog

Old IDs remain for traceability; consolidated rows replace overlapping checklists. Use the current checkpoint above and retain dated passes unless relevant source changes invalidate them. Hosted authorization does not resume local build/lint/device/live work.

| Order / IDs / priority | Dependency | Remaining deliverable / acceptance |
| --- | --- | --- |
| 1 — C1 (`UI-R1`, `U1`, `I1`, `I2`) / P1 | Separate compilation/KSP/test authorization | Current production/debug/release and both instrumentation-source compilations passed at `e808c13`; retain them unless relevant changes invalidate them. The initial version-1 export was regenerated by KSP in workflow 03 at `7048c2c`, matched across four jobs and copied verbatim. The 37-method storage batch at `e808c13` passed fresh initialization/reopening and non-destructive incompatible-storage rejection. Retain that evidence unless storage source changes. Historical exports and hosted passes do not establish this revision. |
| 2 — UI1 (`I3`, `R1`, `D1`) / P1 | C1; separate Android authorization | Verify all four search/tab placements, 360 dp normal/2× text and threshold transitions, 840 dp list/detail, IME/cursor/focus, reader/admin/busy badges, bulk wrapping/retained hidden selections/full preview, grouped highlight scrolling, stale/context/role guards, restored routes/drafts/completion and snackbars Close/timeout/Undo/View, plus clipboard/setup feedback. Include notice/card-tip session dismissal, backup snooze, form/secret validation, initial AVAILABLE/LOW/OUT creation/defaults/reset/races, immediate existing-status saves, picker keys/timezones, predictive Back/cancellation, drawer-edge/section swipes, headings, themes/accents/artwork and animation-scale behavior. Include source preview rendering when authorized; human visual/spoken acceptance is optional. |
| 3 — UI2 (`I3`, `D1`, part of `R4`) / P1 | C1; relevant UI1 checks | Verify retained Home rows during recomputation with fresh-only bulk/tab markers; section/grouping/pharmacy/role invalidation; fetched-marker accumulation/no-change/failure and current-plus-next session lifetime; rotation/background/task/process distinctions and midnight/Sunday boundaries. Demo: fresh revision-1 seed (80 explicit three-state pairs), untouched old installed rows excluded from the workspace, Reader stock restrictions, simulated Admin CRUD/status/bulk/Undo/taxonomy/pending/History, reset/role lifetime, stale/protected targets and no standard-data/identity/DB/network side effects, with sample publication simulated and external files written only through explicit export. Verify splash decode/cancellation, skeleton-free loading, grouping defaults and cross-section search including already-visible IDs/empty-current matches. |
| 4 — F1 (`R2`) / P2 | C1; relevant UI1 command guards | Finish CSV clear/retry/alias/duplicate/storage/cancellation/provider/resource cases without re-running valid passes unnecessarily. Complete configurable PDF/XLSX coverage: all-medication/published/local sources, filters/fields/aggregation, 0–5 selected sections under output guards, mixed Arabic/English, long fields/metadata and workbook continuation, A5/A4/orientation/density, source snapshots, provider failures and PDF fail-not-truncate at 1,000 pages. Wider provider and human print campaigns remain excluded. |
| 5 — S1 (`R3`, `R4`) / P2 | C1; V1 for restore-dependent cases | Verify taxonomy-name/section-count conflict choices, rename/retry/order, empty shared publish/reconnect versus draft bootstrap, uncertain attempts, rejected-snapshot PIN preservation, cooldown/reconnect/large-ID acknowledgment/cancellation, numeric connection passwords and boot/process/clock-aware PIN delays. Cover database lease/reset concurrency, protected-cache invalidation, exact authenticated publication time, leave/recovery, pharmacy-midnight/resume/clock changes, missing/future/24-hour refresh ages, validated connectivity, saved-theme startup and optional civil Hijri. Preserve pending bases, timestamps and operational identity. |
| 6 — REL1 (`I4`, `M1`, `M2`, `R5`) / P1 | C1; applicable V1/UI/functional gates; original signing identity | Establish local/hosted reservation coordination, wrapper argument/locking/Windows behavior and offline Python reservation cases, production key agreement, three signed architecture outputs/artifact transfer and installed-update continuity. Preserve consumed codes/tags and working caches. Inspect detailed native-strip diagnostics for graphics path/DataStore/SQLCipher on an authorized build before changing packaging. Verify resources/native libraries, process/Keystore/automated-accessibility and shared-workstation output/IDE/JDK paths. Retain original APK/signing/version provenance. |
| 7 — REL2 (`M6`) / P1 | Fresh authorized production runtime graph | Reconcile exact release `runtime-components.txt` coordinates with retained third-party notices and add required upstream notices before distribution. Source/cached-family inventories do not establish the shipping graph. |
| 8 — CI1 (`M4`) / P2 | Committed current workflows; separate dispatch authorization | Verify cache behavior, redacted full-history scanning, preview-first debug-release cleanup and actionlint update proposals. Confirm cross-job configuration-cache reuse, revised signing setup and test-worker CDS suppression only on relevant authorized runs. Establish portable harness/init-script prerequisites and runtime portability without replacement caches. Editor picker reload/interaction remains a separate workstation task. |

Current workflow selections and commands belong in [build](build.md#manual-quality-and-security-workflows); they are not pass counts. Repair stale test APIs/fixtures only within an explicitly authorized batch. Demo-wrapper failure isolation/refusal of unexpected real work and Android instrumentation prerequisites remain unverified where not covered by recorded results.

For UI1/UI2, verify the branded starting-window hand-off on cold and warm starts before and after Android 12. Cover fast and slow catalog opening, recovery, activity recreation and disabled animations. Confirm sharp native artwork and direct routing without a blank frame or a separate startup spinner. Verify admin location hints from stocked and OUT sections, per-section dismissal, and title alignment when switching into bulk selection. Measure time to the first app frame and to usable Home separately. Include the Home results layout with large RTL text, applied filters and a search that matches only other sections. Verify that narrowing or clearing a query shows the start of the fresh grid, including the current section heading when searching.

<a id="baseline-profile-proposal--prioritized-separate-future-project"></a>
<a id="performance-tooling--planned"></a>

## Optional startup optimization — PERF / P3

Keep Baseline Profile work active, after the backup audit; independent design may proceed alongside delivery work. **No speed measurements, Macrobenchmark comparison or measurement-based adoption gate is requested.** No speed improvement is established.

1. Design an isolated generator/consumer setup using the pinned toolchain and explicit standard/demo selectors. Keep production signing, APK numbering and shared caches unchanged.
2. With separate source/test-authoring authorization, define deterministic startup, Home browsing/search, section switching and detail-opening journeys using public local/demo data. Ensure any profile consumed by standard covers its production paths.
3. With separate build and owned-hosted-emulator authorization, generate the profile through guided app runs and verify its packaging in the intended artifact. Generation is execution even though no speed is timed; keep it out of ordinary builds and automatic CI.
4. Retain profile sources, artifact/source provenance, an opt-in regeneration recipe and regeneration triggers. Report generation/packaging evidence without promising a measured benefit.

<a id="baseline-and-current-stop"></a>

## Deferred work and delivery boundaries

Physical/live campaigns and local lint remain stopped. Extra Telegram-resource campaigns, broader devices/providers and human visual/print/spoken TalkBack acceptance remain outside required delivery, with outcomes unverified. Hosted work does not resume local/device/live activity. On an explicitly authorized physical resume, verify serial/package/signer/install/run ownership and fresh instrumentation, diagnose the interrupted checkpoint, retain valid passes and finish only relevant missing checks.

Deferred `M5`: dedicated publishing seam, further test/coverage review and inventory-consumer review/removal. Also defer image/font reduction, component gallery/screenshot testing, tiny-file merging, spare-token expansion, palette reorganization, dead-composable deletion and speculative source/card optimization.

Repository deferrals remain: screenshot protection, automatic PR checks, scheduled scans, attestations, extra style/security tools, commit-signature enforcement, CLI dispatch launcher, generated release notes, blame-ignore revisions, reproducibility comparisons, full-SHA server enforcement, dependency submission/checksum verification/locking, release immutability, verification-runner provenance, checker exclusions, generated-file metadata and duplicate-file work. Preserve `Final`, CODEOWNERS, workflow independence, debug tags and manual changelog maintenance.

Documentation/source maintenance grants no test inspection/authoring/execution, migration, Gradle/build/packaging/lint, profile generation, device/network or workflow authorization. Workstation guides/configuration are not execution permission. Preserve existing outputs, keys, counters, installation identities and original evidence.
