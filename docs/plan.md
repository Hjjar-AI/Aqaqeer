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

Workflow 03 rate-limit follow-up: source now waits 120/240 seconds between its three dependency-HTTP-429 attempts. Shared setup separates wrapper and dependency cache paths and calls the SDK manager only for missing pinned packages. The next authorized hosted runs should confirm filtered dependency v2 cache save and restore, the SDK preinstalled path and resulting transfer sizes in 03/06. Verify hosted 4/3/2-GiB Gradle, Kotlin and JVM test limits in 03, and check peak runner memory in an emulator workflow before drawing performance conclusions. Retry coverage is also source-implemented for workflows 02/04/05/06/08/10/11/12, with packaging retries after the wrapper reserves its code. Workflows 07/08/16 also cache API 29/35 system-image packages while recreating fresh AVDs. Hosted confirmation of retry and image-cache restore/save paths remains pending. Local builds/tests/lint and workflow dispatch are not resumed.

Current branding follow-up: Cobalt/silver is selected and the approved PNG is copied unchanged into both logo resources. Home launcher padding alone is reduced by equal −3% insets; the taller redraw is rejected and the original proportions retained. Splash framing retains the approved bitmap inset. Source/raster/reference/link checks are complete; packaged Android/PDF rendering and human acceptance remain unverified. No build or device campaign is resumed by this artwork change.

Current version workflow follow-up: the user requested an initial local/remote reset and confirmed no installed copies. The local counter is0, the remote reservation registry is empty and the next build is0.0.1; existing Releases/APKs/history are retained. Workflow10/11 visibility and blank-by-default automatic production numbering are source-implemented with static checks. Hosted execution of this change remains unverified; the user is pushing commits, and no new build is dispatched here. See [version reset evidence](validation.md#initial-version-sequence-and-automatic-selection--2026-11-24).

**Resumed at the user’s request on 2026-11-24.** Preserve existing hosted runs at `e808c13` and retain their valid results before targeting failed or relevantly invalidated cases. Detailed continuation checkpoint (local-only): `local/verification/hosted-completion-20261124/HANDOFF.md`. The selected hosted batch is complete; the specific acceptance dispositions below remain; do not infer passes from selections.

Current execution authorization — 2026-11-24: complete the listed V1/C1/UI1/UI2/F1/S1/REL1/REL2/CI1 work, finish the missing test batch before execution, run/review hosted workflows and reconcile records. Hosted compilation/packaging, emulator recovery/resource checks, repository/security/tooling checks and final hosted lint are authorized. Keep lint last after source fixes/cleanup. Physical/live campaigns remain stopped; Windows and human acceptance require their actual environments. No conversion migration or public release publication is inferred.

The implemented catalog/admin, sharing/recovery, CSV/PDF/XLSX, demo and presentation baseline is described by [requirements](requirements.md) and [architecture](architecture.md). Recent source includes the native startup-gate repair, bounded dependency-only HTTP429 CI retry, optional card hints, separate C2 medication badges and 140 dp adaptive cards. Preserve settled policies, initial-format version 1, keys/counters and unrelated edits.

Current hosted evidence is reconciled by revision in [validation](validation.md#hosted-completion-batch--2026-11-24). Workflow03 passed620 standard JVM cases/48 intentional skips and653 demo cases/15 skips, both debug/instrumentation-source compilations, release compilation and JDK vectors. The resumed hosted helper batch passed23 Python checks; later local helper contracts passed27. Current policy/actionlint passed. Two CodeQL certificate-pinning recommendations remain open; no blanket security clearance is claimed.

The completed workflow 07 batch selected 223 standard methods and 16 demo invocations on each API 29/35 for 255 invocations across 237 unique methods. Across retained originals and relevant retries, all223 standard methods and32/32 demo invocations have scoped passes. All three new BackupScreen methods and the bulk-preview retry passed. Focus-order repair `103a056` passed search/clear on both APIs in run45437744691; no selected Android method remains failed or unexecuted. Retain every unaffected pass. Empty retry batches skip Android tooling and emulators. Source completion is not a runtime pass.

Storage37/37, recovery38/38 semantic scenarios and controlled heap-pressure rejection/release/retry passed at `e808c13`. Large-history retry45404941086 at `c98e65b` passed complete180/480-row round trips in21,040,470/56,095,670-byte envelopes with an observed384-MiB hosted runtime, owned-package cleanup and unchanged counter. Artifact-only workflow11 `45451228272` at `a9336be` passed code33 production signing for universal/ARM32/ARM64. Downloaded checksums, all139 runtime coordinates and12 complete notice assets match each signed APK. No GitHub Release was created. User-authorized workflow09 removed26 completed runs after needed evidence and2,176 public hashes were retained/verified; active runs, local evidence, releases and caches were preserved.

Initial hosted lint04 `45444132827` found7 errors per flavor. Repair `a9336be` qualifies API27 theme attributes, uses the configuration-aware email reader and reuses lifecycle-collected creation busy state. Affected About/theme methods passed on both APIs; the explicitly authorized artifact-only code33 candidate is signed and verified. Final hosted04 `45453094640` at `b6183b6` passed workflow validation and both production lint flavors with zero errors,82 warnings and10 hints each. No selected hosted check or signed-artifact comparison remains pending. Codes32/33 belong to the earlier sequence; the later explicit initial-version reset is recorded in validation. Keep the specific backup-audit dispositions, Windows, installed-update, physical/live and human acceptance boundaries explicit; selected automated cases do not cover every environment.

<a id="v1--deferred-backuprestore-validation-gate--p1"></a>

## V1 — Backup/restore verification gate / P1

**First priority.** Use audit rows BR01–BR20 to select the next authorized batch. Runtime work depends on current compilation, the required harness/artifacts and separate applicable authorization. No development-format conversion or migration inspection is included.

| Remaining work | Completion criterion |
| --- | --- |
| Payload and backup UI | Resolve only unestablished concurrent-capture combinations and complete typed-name/pending-warning/SAF UI acceptance identified in BR01/02/09. Retain current round trips, complete large histories, exact envelope cap, identity/reminder exclusions and protected-requirement passes. |
| Failure and restore recovery | Use BR04/05/10–12 to identify any unestablished validation/provider/storage combination. Retain current Unicode/encryption, malformed/unsupported/chunk, cap/workspace/pressure, close/cancellation and verified SIGKILL passes; do not repeat them without relevant changes. OS termination and power loss remain separate unavailable boundaries. |
| Reconciliation and live PIN | Establish the complete offline restore-to-review-to-separate-publication matrix, restored-backup/current-live-PIN/pending-PIN preservation, repeated reauthorization and replacement lineage (BR14–17). Retain separate restore-pause, connection-replacement, uncertainty/conflict and changed-verifier passes without treating them as this end-to-end result. Live execution remains stopped. |
| Reminder and maintenance backup | Establish role/day/leave/success-versus-failure reminder boundaries and operation/source-bound fresh-backup receipts. Current hosted simulated maintenance PREPARING retry, post-active verification and logical-cleanup interruption passed within the 38-scenario run. Retain earlier evidence within its dated scope; remaining runtime boundaries must come from failed or unexecuted cases. |

The retained live-PIN/manual-republication/reader-connection extension remains optional while physical/live campaigns are stopped. Broader devices/providers, human print/spoken/visual acceptance, OS low-memory termination and power-loss results stay explicitly unverified; they are not silently converted to passes or required new campaigns.

<a id="ordered-packages"></a>
<a id="pdf-redesign--a5-source-implemented"></a>
<a id="remaining-verification"></a>
<a id="repositoryrelease-follow-up--2026-11-14"></a>
<a id="sharing-review-follow-up--p1"></a>
<a id="ui-review-follow-up--p1"></a>

## Ordered delivery backlog

Workflow 03 #23 at `d12b23f` passed both JVM suites, release compilation, and both instrumentation compilations. It restored the verified seed and saved the first snapshot under the instrumentation prefix. Check direct restore from that prefix on the next ordinary full run. Workflow 06 previously built and signed both debug flavors with code 6. Keep reservations 5 and 6. [Hosted results](validation.md#hosted-workflows-03-and-06--2026-11-25)

The focused Android follow-up is complete. Retained evidence covers 32 Standard invocations on API 35 and four Demo splash invocations across APIs 29 and 35. The final retry passed both Home methods. Workflow 08 passed the packaged WorkManager manifest, runtime notices and release startup on the published production revision. [Hosted results](validation.md#focused-android-follow-up--2026-11-27)

Old IDs remain for traceability; consolidated rows replace overlapping checklists. Use the current checkpoint above and retain dated passes unless relevant source changes invalidate them. Hosted authorization does not resume local build/lint/device/live work.

| Order / IDs / priority | Dependency | Remaining deliverable / acceptance |
| --- | --- | --- |
| 2 — UI1 (`I3`, `R1`, `D1`) / P1 | C1; separate Android authorization | Verify all four search/tab placements, 360 dp normal/2× text and threshold transitions, 840 dp list/detail, IME/cursor/focus, reader/admin/busy badges, bulk wrapping/retained hidden selections/full preview, grouped highlight scrolling, stale/context/role guards, restored routes/drafts/completion and snackbars Close/timeout/Undo/View, plus clipboard/setup feedback. Include notice/card-tip session dismissal, backup snooze, form/secret validation, initial AVAILABLE/LOW/OUT creation/defaults/reset/races, immediate existing-status saves, picker keys/timezones, predictive Back/cancellation, drawer-edge/section swipes, headings, themes/accents/artwork and animation-scale behavior. Include source preview rendering when authorized; human visual/spoken acceptance is optional. |
| 3 — UI2 (`I3`, `D1`, part of `R4`) / P1 | C1; relevant UI1 checks | Verify retained Home rows during recomputation with fresh-only bulk/tab markers; section/grouping/pharmacy/role invalidation; fetched-marker accumulation/no-change/failure and current-plus-next session lifetime; rotation/background/task/process distinctions and midnight/Sunday boundaries. Demo: fresh revision-1 seed (80 explicit three-state pairs), untouched old installed rows excluded from the workspace, Reader stock restrictions, simulated Admin CRUD/status/bulk/Undo/taxonomy/pending/History, reset/role lifetime, stale/protected targets and no standard-data/identity/DB/network side effects, with sample publication simulated and external files written only through explicit export. Verify splash decode/cancellation, skeleton-free loading, grouping defaults and cross-section search including already-visible IDs/empty-current matches. |
| 4 — F1 (`R2`) / P2 | C1; relevant UI1 command guards | Finish CSV clear/retry/alias/duplicate/storage/cancellation/provider/resource cases without re-running valid passes unnecessarily. Complete configurable PDF/XLSX coverage: all-medication/published/local sources, filters/fields/aggregation, 0–5 selected sections under output guards, mixed Arabic/English, long fields/metadata and workbook continuation, A5/orientation/density, source snapshots, provider failures and PDF fail-not-truncate at 1,000 pages. Wider provider and human print campaigns remain excluded. |
| 5 — S1 (`R3`, `R4`) / P2 | C1; V1 for restore-dependent cases | Verify taxonomy-name/section-count conflict choices, rename/retry/order, empty shared publish/reconnect versus draft bootstrap, uncertain attempts, rejected-snapshot PIN preservation, cooldown/reconnect/large-ID acknowledgment/cancellation, numeric connection passwords and boot/process/clock-aware PIN delays. Cover database lease/reset concurrency, protected-cache invalidation, exact authenticated publication time, leave/recovery, pharmacy-midnight/resume/clock changes, missing/future/24-hour refresh ages, validated connectivity, saved-theme startup and optional civil Hijri. Preserve pending bases, timestamps and operational identity. |
| 6 — REL1 (`I4`, `M1`, `M2`, `R5`) / P1 | C1; applicable V1/UI/functional gates; original signing identity | Remaining boundaries are Windows wrapper behavior, installed-update continuity and applicable process/Keystore/physical acceptance. Retain passed offline reservation contracts, durable codes32/33 and the three verified signed architecture artifacts. Preserve consumed codes/tags and working caches. Inspect detailed native-strip diagnostics for graphics path/DataStore/SQLCipher on an authorized build before changing packaging. Verify resources/native libraries, process/Keystore/automated-accessibility and shared-workstation output/IDE/JDK paths. Retain original APK/signing/version provenance. |
| 8 — CI1 (`M4`) / P2 | Committed current workflows; separate dispatch authorization | Verify cache behavior, redacted full-history scanning, preview-first debug-release cleanup and actionlint update proposals. Confirm cross-job configuration-cache reuse, revised signing setup and test-worker CDS suppression only on relevant authorized runs. Establish portable harness/init-script prerequisites and runtime portability without replacement caches. Editor picker reload/interaction remains a separate workstation task. |

Current workflow selections and commands belong in [build](build.md#manual-quality-and-security-workflows); they are not pass counts. Repair stale test APIs/fixtures only within an explicitly authorized batch. Demo-wrapper failure isolation/refusal of unexpected real work and Android instrumentation prerequisites remain unverified where not covered by recorded results.

For UI1/UI2, verify the branded starting-window hand-off on cold and warm starts before and after Android 12. Cover fast and slow catalog opening, recovery, activity recreation and disabled animations. Confirm sharp native artwork and direct routing without a blank frame or a separate startup spinner. Verify admin location hints from stocked and OUT sections, per-section dismissal, and title alignment when switching into bulk selection. Measure time to the first app frame and to usable Home separately. Include the Home results layout with large RTL text, applied filters and a search that matches only other sections. Verify that narrowing or clearing a query shows the start of the fresh grid, including the current section heading when searching.

For UI1, verify the source-implemented Compose styling on copper, cobalt blue, green and rose in Light, Dark and both System resolutions, plus AMOLED black. Include the starting-window handoff, History filter scrolling and close-after-apply on short screens, medication section headings in TalkBack, ordinary icon labels and touch targets, and the destructive controls. Check snackbar appearance in Light, Dark and AMOLED, timeout, Close, Undo/View and both horizontal swipe directions in RTL. Confirm a swipe dismisses only its current message. Existing shared control styling, shortcut chips and the Gregorian picker remain part of the UI acceptance. Check the selected section’s tonal fill and sliding indicator in fixed and scrollable RTL rows, including rapid taps and swipes, large type and disabled animations. Verify that Add and search retain their positions during section switches, including with the keyboard open.

For UI1, verify the [admin tools refresh](validation.md#admin-tools-organization--2026-11-25) in narrow and wide RTL layouts, at normal and 2× text, and across themes. Cover Reader, simulated Admin, local draft, shared, maintenance, update and restore states. Check checklist disclosure and hiding, return navigation, setup guidance with the keyboard open, review filters and publish scope, backup/restore warnings and the local leave confirmation.

For F1, verify the report accordion with narrow RTL layouts, large text and the keyboard open. Check field reordering and reset, both availability levels together, reverse sorting with undated medications last, the fixed A5 page size and configurable side margins in the saved PDF and XLSX. Verify the larger PDF typography and grouped opening metadata in portrait and landscape, including long Arabic values and continuation pages. Check that Next and Previous open the correct panel, collapse the other panels and keep the actions reachable. Cover unavailable published data, explicit local source selection, reload, preset/reset behavior, activity recreation, pharmacy/access reset, preparation failure and retry, stale-preview and late picker-result rejection, picker cancellation and full-count PDF and XLSX output. For UI1, verify that the shared progress header announces the current state and exposes the other stages in connection import, Telegram setup, backup restore, CSV import and maintenance. See [source evidence](validation.md#compose-styling-and-report-export--2026-11-25). These Android checks require applicable authorization.

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
