# Remaining-work plan

[Requirements](requirements.md) define acceptance. [Validation](validation.md) records completed checks by revision. This plan lists remaining work and grants no execution authorization.

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

<a id="implemented-baseline"></a>

## Start here

1. **Select the next V1 backup/restore batch.** Use the [BR01–BR20 audit](validation.md#backuprestore-requirement-to-evidence-audit--2026-11-22) to pick only open boundaries. For each case, identify the missing evidence, required fixture or artifact, and execution environment.
2. **Check the prerequisites for the selected work (C1).** Use relevant retained passes. Confirm compilation on the selected revision and affected variants where evidence is missing. For storage work, check the matching schema export and applicable fresh-open, reopen, or incompatible-schema rejection evidence. Obtain the applicable authorization before authoring tests or running builds, packaging, workflows, or devices.
3. **Run and close the selected batch.** Record the source, artifact, environment, and per-case outcomes in [validation](validation.md). Keep failed, blocked, and unexecuted items here. Retry only those cases or cases invalidated by a relevant change.

For the latest evidence, see [Home and branding](validation.md#repository-home-and-branding-follow-up--2026-11-27), [focused Android runs](validation.md#focused-android-follow-up--2026-11-27), and the [hosted completion checkpoint](validation.md#hosted-completion-batch--2026-11-24).

## Android 6 preview / P1

The source sets API 23 for Standard and Demo, with compatibility paths for encrypted files, PIN retries, network observation and Arabic presentation. This preview starts with a fresh installation and synthetic fixtures. Prior releases and Android runs remain evidence of their original source.

1. Push the candidate to the default branch. Run workflow 03 with `scope=all` and `coverage=false` for JVM vectors and compilation. Run workflow 04 for production lint at the lower API floor. Run workflow 07 with `android6_checks=true` (default), `demo_checks=false`, `standard_functional=false`, and both retry fields blank. Confirm both app and instrumentation manifest merges, then review Android 6 results, PIN process/reboot receipts and the two directions of the backup/PIN exchange. A higher dependency minimum blocks the preview. [Exact selections and evidence](test-coverage.md#android-6-preview)
2. Run workflow 08 with `android6_startup=true` (default) and blank `expected_certificate_sha256`. Review the minified launch and cleanup results, and compare per-ABI download sizes. After acceptance, run workflow 11 with `publication=draft` and blank `version_code`. Confirm the production certificate, API 23 v1 verification for each distributed APK, and APK selection guidance before publishing the same draft as a prerelease. [Build and signing guide](build.md#hosted-emulator-screenshots-and-recordings)
3. Keep the manual UI review open for separate verification. Check navigation-bar icon contrast and splash appearance, Arabic PDF shaping and pagination, keyboard clearance in medication/PIN/search/password forms, all drawer routes, connection-file generation and clipboard warning, and spoken Arabic TalkBack. Keep automated Home keyboard, export, network availability and splash results scoped to their selected cases.
4. On separately authorized low-memory physical ARM hardware, install the signed release, measure cold start, large-catalog scrolling and the largest realistic backup/restore with KDF and peak-heap observations. Verify Keystore reopen after reboot and a screen-lock change, rehearse an in-place update once a same-signer artifact pair exists, and check live Telegram fetch and publish. These remain beyond the emulator preview. Local builds, tests and lint are stopped for this work. The owner will push and run GitHub Actions.

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

### 2. UI1 / P1 — Home controls, presentation, and admin tools

After C1, verify remaining Android UI behavior using the [product acceptance rules](requirements.md#9-medication-behavior-and-presentation). Cover:

- All four Home bar placements, narrow RTL at 360 dp and 2× text, the 840 dp split-pane threshold, and IME/focus behavior. Check stable Add/search positions during section switches, fixed and scrollable tab indicators during rapid interaction, grouped highlights, query clear/scroll, and bulk selection through hidden rows and full preview.
- Cold and warm native splash handoff before and after Android 12, including fast/slow load, recovery, recreation, and reduced motion. Check generated artwork in release APKs. Keep any startup timing study separate from functional acceptance.
- Reader, Admin, and busy controls, stock and expiry badges, active-section availability changes and creation defaults, save/failure/race/draft/Undo, picker keys and timezones, and destructive actions. Check themes, short-screen History filters, notice/card-tip dismissal, backup snooze, actionable snackbars and RTL swipes, headings/labels/touch targets, predictive Back and drawer gestures.
- The admin tools in narrow and wide RTL at normal and large text. Check role and access states, checklist and step disclosure, setup and review navigation with the IME, publish scope, backup/restore warnings, and local leave confirmation. Review the shared progress header in connection import, Telegram setup, restore, CSV import, and maintenance.
- **Separate typography review.** Compare Arabic headings and UI text with the Latin About version and email. Include long mixed-script names, Western digits, and 2× text. Record the comparison before choosing a font change.

### 3. UI2 / P1 — Browse state and Demo isolation

After C1 and related UI1 checks, establish fresh-only bulk/tab actions, correct retained rows during recomputation, and invalidation on section, grouping, pharmacy, and role changes. Cover fetched-marker accumulation and expiry across no-change/failure, rotation, background/task/process transitions, and pharmacy midnight/Sunday. Check cross-section search with already-visible IDs and empty current matches, grouping defaults, and skeleton-free loading.

For Demo, verify the fresh revision-1 seed and 80 explicit stock pairs, workspace isolation from old installed rows, Reader restrictions, simulated Admin edits and undo, reset and role lifetime, stale/protected targets, and pending/History behavior. Confirm simulated publication and explicit export boundaries, including protection of Standard data and identity.

### 4. F1 / P2 — Import and report exports

After C1 and related UI1 guards, close CSV clear/retry, alias, duplicate, storage, cancellation, provider, and resource cases. Verify the report accordion at narrow RTL and large text with the keyboard, including Back/Next, preparation and retry, scope changes, recreation, stale results, late picker responses, and cancellation.

Open saved PDF and XLSX outputs to verify all-medication/published/local sources, filters and fields, overall and per-section availability, 0–5 selected sections, field reordering/reset, descending sort with undated items last, A5 orientation and margins, mixed-script text, metadata and pagination, and full output counts. Cover source snapshots and output/provider guards. Retain the scoped passes for [1,000-page overflow](validation.md#pre-production-risk-regressions--2026-11-27) and the [print palette](validation.md#repository-home-and-branding-follow-up--2026-11-27).

### 5. S1 / P2 — Sharing and recovery guards

After C1, and after V1 for restore-dependent cases, cover conflict choices, rename/retry/order, empty shared publication/reconnect versus draft bootstrap, uncertain attempts and PIN preservation on rejection, cooldown and large-ID acknowledgment/cancellation, and numeric passwords and boot/process/clock-aware PIN delays. Verify database lease/reset, protected-cache invalidation, authenticated publication timestamps, leave/recovery, clock changes and refresh ages, validated connectivity, saved-theme startup, and optional civil Hijri. Preserve pending bases and operational identity.

### 6. REL1 / P1 — Release and installed-update continuity

After the applicable V1/UI/functional gates, identify an eligible same-signer artifact pair and obtain separate emulator authorization for the [installed-update rehearsal](build.md#installed-update-rehearsal). Confirm an in-place upgrade preserves installation identity and data. Keep Windows wrapper verification and applicable process, Keystore, resource, native-library, and workstation acceptance tied to their actual environments. Inspect native-strip diagnostics on an authorized build before changing packaging. Preserve the original signed artifacts and version receipts. [Earlier signing evidence](validation.md#hosted-completion-batch--2026-11-24)

### 7. CI1 / P2 — Hosted tooling and portability

On relevant authorized runs, confirm direct instrumentation-cache restore, dependency and system-image cache behavior, hosted memory settings, cross-job configuration-cache reuse, revised signing setup, and test-worker CDS suppression. Verify redacted history scanning, preview-first debug-release cleanup, actionlint proposals, and the open CodeQL certificate-pinning findings. Check harness/init-script portability and Demo wrapper failure isolation. Treat editor-picker interaction as a separate workstation task. [Commands and workflow selections](build.md#manual-quality-and-security-workflows)

<a id="baseline-profile-proposal--prioritized-separate-future-project"></a>
<a id="performance-tooling--planned"></a>

## Optional startup optimization — PERF / P3

Baseline Profile generation is the next performance project after the Home source change. Backup verification keeps its delivery priority. **No speed measurements, Macrobenchmark comparison or measurement-based adoption gate is requested.**

Follow the [Baseline Profile instructions](../baselineprofile/AGENTS.md) when implementing the generator.

1. Design an isolated opt-in generator and app consumer using the pinned toolchain and explicit Standard and Demo selectors. Use Standard journeys for the artifact shipped to pharmacies. Keep generation outside normal CI, production signing and version reservation.
2. With separate source/test-authoring authorization, define deterministic startup, Home browsing/search, section switching and detail-opening journeys using public local/demo data. Ensure any profile consumed by standard covers its production paths.
3. With separate build and owned-hosted-emulator authorization, generate the profile through guided app runs and verify its packaging in the intended artifact. Generation is execution even though no speed is timed; keep it out of ordinary builds and automatic CI.
4. Retain profile sources, artifact/source provenance, an opt-in regeneration recipe and regeneration triggers. Report generation/packaging evidence without promising a measured benefit.

<a id="baseline-and-current-stop"></a>

## Deferred work and delivery boundaries

Physical and live campaigns and local lint remain stopped. Wider devices/providers, extra Telegram campaigns, and human visual, print, and spoken TalkBack acceptance remain optional and unverified. Use the repository’s ownership and evidence rules on an authorized resume.

Deferred `M5`: dedicated publishing seam, further test/coverage review and inventory-consumer review/removal. Also defer image/font reduction, component gallery/screenshot testing, tiny-file merging, spare-token expansion, palette reorganization, dead-composable deletion and speculative source/card optimization.

Repository deferrals remain: screenshot protection, automatic PR checks, scheduled scans, attestations, extra style/security tools, commit-signature enforcement, CLI dispatch launcher, generated release notes, blame-ignore revisions, reproducibility comparisons, full-SHA server enforcement, dependency submission/checksum verification/locking, release immutability, verification-runner provenance, checker exclusions, generated-file metadata and duplicate-file work. Preserve `Final`, CODEOWNERS, workflow independence, debug tags and manual changelog maintenance.

Documentation/source maintenance grants no test inspection/authoring/execution, migration, Gradle/build/packaging/lint, profile generation, device/network or workflow authorization. Workstation guides/configuration are not execution permission. Preserve existing outputs, keys, counters, installation identities and original evidence.
