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

Core catalog/admin, sharing/recovery, imports/reports, demo and presentation sources are implemented. [Post-fix workflow 03](validation.md#post-fix-hosted-result--2026-11-23) at `e19e835` passed 545 JVM cases per flavor, JDK vectors, both production debug/instrumentation-source compilations and standard release compilation; original failures and the transient Maven 429 retry remain recorded. Earlier passes apply only to their recorded revisions. See the [current checkpoint](validation.md#current-verification-checkpoint--2026-11-16) and [UI refactoring evidence](validation.md#validated-ui-refactoring--2026-11-22).

The documentation-only [backup/restore audit](validation.md#backuprestore-requirement-to-evidence-audit--2026-11-22) compares all applicable requirements with recorded evidence. It does not inspect tests or establish a new application pass. Close only missing or relevantly invalidated checks; preserve applicable earlier results.

<a id="v1--deferred-backuprestore-validation-gate--p1"></a>

## V1 — Backup/restore verification gate / P1

**First priority.** Use audit rows BR01–BR20 to select the next authorized batch. Runtime work depends on current compilation, the required harness/artifacts and separate applicable authorization. No development-format conversion or migration inspection is included.

| Remaining work | Completion criterion |
| --- | --- |
| Current payload and authorization | Establish current initial-format round trips, complete history above 10 MiB within the 64 MiB envelope cap, consistent bases/pending/attempts/credentials, identity/authorization/reminder exclusions, role/typed-replacement guards and compatibility restrictions. |
| Failure and restore recovery | Establish Unicode/randomness, malformed/unsupported/reference/over-cap/resource/storage/provider-close/cancellation behavior; authenticate before replacement; cover staging/replacement/activation interruption without data loss or false success. Keep SIGKILL, heap pressure, OS low-memory termination and power loss distinct. |
| Reconciliation and live PIN | Establish restore pause, same-bot connection confirmation, uncertain-attempt/conflict review, current-live-PIN preservation/reauthorization and replacement lineage, followed by separate explicit publication. Do not infer live success from fake or historical source checks. |
| Reminder and maintenance backup | Establish role/day/leave/success-versus-failure reminder boundaries and operation/source-bound fresh-backup receipts. Finish simulated maintenance PREPARING retry, post-active verification and logical-cleanup interruption; retain six earlier maintenance, three export, four native-SQL and seven focused-method passes within their dated scope. |

The retained live-PIN/manual-republication/reader-connection extension remains optional while physical/live campaigns are stopped. Broader devices/providers, human print/spoken/visual acceptance, OS low-memory termination and power-loss results stay explicitly unverified; they are not silently converted to passes or required new campaigns.

<a id="ordered-packages"></a>
<a id="pdf-redesign--a5-source-implemented"></a>
<a id="remaining-verification"></a>
<a id="repositoryrelease-follow-up--2026-11-14"></a>
<a id="sharing-review-follow-up--p1"></a>
<a id="ui-review-follow-up--p1"></a>

## Ordered delivery backlog

Old IDs remain for traceability; consolidated rows replace overlapping checklists. The search-matrix fixture and thirteen HomeScreen calls are repaired and hosted-verified without production/role-policy changes or weakened assertions. Retain those passes; the tooltip deprecation has a source fix pending compilation. Hosted authorization does not resume local build/lint/device/live work.

| Order / IDs / priority | Dependency | Remaining deliverable / acceptance |
| --- | --- | --- |
| 1 — C1 (`UI-R1`, `U1`, `I1`, `I2`) / P1 | Separate compilation/KSP/test authorization | Production/debug/release and both instrumentation-source compilations passed at `e19e835`; retain them unless relevant changes invalidate them. Remaining: Regenerate/review the initial Room version-1 export from current models without hand-writing its identity hash. Establish fresh initialization and non-destructive incompatible-storage rejection. Historical exports and hosted passes do not establish this revision. |
| 2 — UI1 (`I3`, `R1`, `D1`) / P1 | C1; separate Android authorization | Verify all four search/tab placements, 360 dp normal/2× text and threshold transitions, 840 dp list/detail, IME/cursor/focus, reader/admin/busy badges, bulk wrapping/retained hidden selections/full preview, grouped highlight scrolling, stale/context/role guards, restored routes/drafts/completion and snackbars Close/timeout/Undo/View, plus clipboard/setup feedback. Include notice/card-tip session dismissal, backup snooze, form/secret validation, initial AVAILABLE/LOW/OUT creation/defaults/reset/races, immediate existing-status saves, picker keys/timezones, predictive Back/cancellation, drawer-edge/section swipes, headings, themes/accents/artwork and animation-scale behavior. Include source preview rendering when authorized; human visual/spoken acceptance is optional. |
| 3 — UI2 (`I3`, `D1`, part of `R4`) / P1 | C1; relevant UI1 checks | Verify retained Home rows during recomputation with fresh-only bulk/tab markers; section/grouping/pharmacy/role invalidation; fetched-marker accumulation/no-change/failure and current-plus-next session lifetime; rotation/background/task/process distinctions and midnight/Sunday boundaries. Demo: fresh revision-1 seed (80 explicit non-UNKNOWN pairs), untouched old installed rows excluded from the workspace, Reader stock restrictions, simulated Admin CRUD/status/bulk/Undo/taxonomy/pending/History, reset/role lifetime, stale/protected targets and no standard-data/identity/DB/network/publication/file side effects. Verify splash decode/cancellation, skeleton-free loading, grouping defaults and cross-section search including already-visible IDs/empty-current matches. |
| 4 — F1 (`R2`) / P2 | C1; relevant UI1 command guards | Finish CSV clear/retry/alias/duplicate/storage/cancellation/provider/resource cases without re-running valid passes unnecessarily. Complete A5 export/render coverage: three presets, 1–5 sections, mixed Arabic/English, long fields/metadata, category/page continuation, source snapshots, bidi/digits and fail-not-truncate at 1,000 pages. Wider provider and human print campaigns remain excluded. |
| 5 — S1 (`R3`, `R4`) / P2 | C1; V1 for restore-dependent cases | Verify taxonomy-name/section-count conflict choices, rename/retry/order, empty shared publish/reconnect versus draft bootstrap, uncertain attempts, rejected-snapshot PIN preservation, cooldown/reconnect/large-ID acknowledgment/cancellation, numeric connection passwords and boot/process/clock-aware PIN delays. Cover database lease/reset concurrency, protected-cache invalidation, exact authenticated publication time, leave/recovery, pharmacy-midnight/resume/clock changes, missing/future/24-hour refresh ages, validated connectivity, saved-theme startup and optional civil Hijri. Preserve pending bases, timestamps and operational identity. |
| 6 — REL1 (`I4`, `M1`, `M2`, `R5`) / P1 | C1; applicable V1/UI/functional gates; original signing identity | Establish local/hosted reservation coordination, wrapper argument/locking/Windows behavior and offline Python reservation cases, production key agreement, three signed architecture outputs/artifact transfer and installed-update continuity. Preserve consumed codes/tags and working caches. Inspect detailed native-strip diagnostics for graphics path/DataStore/SQLCipher on an authorized build before changing packaging. Verify resources/native libraries, process/Keystore/automated-accessibility and shared-workstation output/IDE/JDK paths. Retain original APK/signing/version provenance. |
| 7 — REL2 (`M6`) / P1 | Fresh authorized production runtime graph | Reconcile exact release `runtime-components.txt` coordinates with retained third-party notices and add required upstream notices before distribution. Source/cached-family inventories do not establish the shipping graph. |
| 8 — CI1 (`M4`) / P2 | Committed current workflows; separate dispatch authorization | Verify cache behavior, redacted full-history scanning, preview-first debug-release cleanup and actionlint update proposals. Confirm cross-job configuration-cache reuse, revised signing setup and test-worker CDS suppression only on relevant authorized runs. Establish portable harness/init-script prerequisites and runtime portability without replacement caches. Editor picker reload/interaction remains a separate workstation task. |

Current workflow selections and commands belong in [build](build.md#manual-quality-and-security-workflows); they are not pass counts. Repair stale test APIs/fixtures only within an explicitly authorized batch. Demo-wrapper failure isolation/refusal of unexpected real work and Android instrumentation prerequisites remain unverified where not covered by recorded results.

For UI1/UI2, verify the branded starting-window hand-off on cold and warm starts before and after Android 12. Cover fast and slow catalog opening, recovery, activity recreation and disabled animations. Confirm direct routing without a blank frame or a separate startup spinner. Measure time to the first app frame and to usable Home separately. Include the Home results layout with large RTL text, applied filters and a search that matches only other sections. Verify that narrowing or clearing a query shows the start of the fresh grid, including the current section heading when searching.

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
