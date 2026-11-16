# Remaining-work plan

[Requirements](requirements.md) define acceptance; [architecture](architecture.md) describes implementation; [validation](validation.md) records results. This plan grants no execution authorization.

## Initial attribution-free baseline — 2026-11-16

Approved production changes cover initial-v1 attribution-free storage/files, timestamp/operation-ID merge ordering, fresh APK numbering, Home/cards/detail/filter/selection changes, two-session fetched markers, Settings/navigation, forms, Maintenance, History and source previews. [Validation](validation.md#initial-attribution-free-baseline--2026-11-16) records source checks and limits. Earlier results below predate this baseline and do not validate it. The user subsequently authorized test repair/creation without local execution, then workflow 03 with JVM scope only.

| ID / priority | Dependency | Remaining work / acceptance |
| --- | --- | --- |
| I1 / P1 — Generated schema | Separate compilation/KSP authorization | Regenerate and review the initial Room version-1 export from current models. The retained export is historical; do not handwrite its identity hash. |
| I2 / P1 — Compilation and format acceptance | Separate authorization; I1 | Hosted workflow 03 passed both production/JVM flavor compilations and 545 JVM cases per flavor, including initial-format rejection/round trips and merge ordering. Remaining: Android instrumentation compilation/runtime, fresh initialization and non-destructive incompatible-database acceptance. No conversions are in scope. |
| I3 / P1 — UI/session acceptance | Separate authorization; I2 | All four search/tab placements, large text, narrow/wide layouts, stable selection, role restrictions, refresh failures/no-change fetches, marker accumulation/refresh and current-plus-next session lifetime, task/process versus rotation/backgrounding, Sunday/midnight boundaries. Check History, menus, forms, disclosures and previews. |
| I4 / P1 — Reservation acceptance | Separate packaging/hosted authorization | Next local and fresh hosted reservations yield code 1 / 0.0.1, then increment monotonically with locking. Preserve old tags and live publication/key/installation identities. No packaging or remote reservation was performed. |

Test repair/creation is now authorized; 16 new Kotlin methods and 4 offline Python reservation methods are authored, alongside existing fixture/API/UI repairs. Workflow 03 supports `scope=jvm` (both JVM flavors and public JDK vectors; coverage off). The final correction run passed both JVM flavors (545 cases each) and public JDK vectors. Sorted-map fingerprint serialization and nonempty CSV clearing fixtures are verified there. Retain the original passes/failure evidence in [validation](validation.md#test-source-follow-up--2026-11-16). Workflow 07 source selects 64 standard methods plus 7 demo invocations per API; no emulator dispatch is authorized. Python reservation tests remain unexecuted and are not part of the JVM selection. Local tests/Gradle/build/compilation/lint, other workflow dispatches, device/live access and migrations remain unauthorized. Existing independent stops remain in force.

Warning/cache follow-up: redundant detail/drawer conditions and deprecated copy API are corrected in source. Shared setup skips Gradle only in signing-only jobs, retaining JDK/SDK and all signer checks; build caches are unchanged. Final hosted JVM verification passed both flavors with zero Kotlin source warnings; the unrelated test-JVM class-sharing notice remains. The next independently authorized signing execution remains pending. No packaging dispatch is authorized by this follow-up.

## Baseline and current stop

Core administration, sharing/recovery, search/grouping, demo and presentation workflows are implemented. Current builds/packaging, selected hosted CI, demo initialization/refresh and browsing checks are [user-reported complete](validation.md#user-reported-completion--2026-11-13); do not repeat them without a relevant change or new request.

**Physical/live campaigns and local lint remain stopped.** Extra Telegram-resource campaigns, additional device/emulator/provider coverage and human visual/print/spoken TalkBack acceptance are excluded from required delivery. Their outcomes remain unverified. Production signing/update continuity still requires the signing identity. Source/document maintenance and existing hosted configuration do not resume stopped activities.

On an explicitly authorized physical resume, verify serial/package/signer/install/run ownership and fresh instrumentation first. Diagnose the missing instrumentation at the interrupted checkpoint, retain valid passes, run only unfinished checks and recheck owned resources before cleanup. Final lint requires separate applicable authorization.

<a id="sharing-review-follow-up--p1"></a>
<a id="ui-review-follow-up--p1"></a>
<a id="ordered-packages"></a>

<a id="review-fixes-home-placement"></a>

## Review fixes and Home placement — source implemented / 2026-11-15

The approved source batch implements clean forward-gap refresh, scoped deletion review/recomputed effects, restored-entity tombstone cleanup, multiple-attempt rejection, manual refresh on an unvalidated available network, taxonomy state/target checks, blank CSV skipping, explicit missing/unrelated-pin republication, maintenance coordination guidance, independent bottom bars/defaults/keyboard behavior and the 60 ms splash exit. Focused Home bar/recovery composables retain screen-owned workflow state. Existing PIN/password, upward-only requirements, refresh triggers, history retention and recovery safeguards remain.

**RV1 / P1 — tests authored, execution pending:** the user authorized test inspection/authoring for later GitHub Actions execution. The follow-up adds 15 JVM method declarations (deletion dependencies/reassignments/restored tombstones, pin classification and blank CSV numbering/caps) and 28 Android method declarations (gap guards/race, missing targets, pin recovery/failures/durable attempts, all four Home placements/keyboard/compact heights, taxonomy resets and preference persistence/recreation). The existing branch/gap assertion and strict-policy method name were updated without weakening branch/local-work rejection. Workflow 03 discovers the JVM additions; workflow 07 selects seven demo invocations per API plus 58 standard invocations with `standard_functional=true`. These are source selections, not results. Source checks are recorded in [validation](validation.md#review-fixes-home-placement).

Remaining: compilation and execution of this batch; manual-refresh eligibility on an available unvalidated network versus automatic guards; splash timing/disabled animations, measured Add clearance and broader large-text/modal/recreation interactions; maintenance warning acceptance. No production test seams were added. Local tests/builds/packaging/lint, migrations, device/live campaigns and hosted dispatch remain unauthorized for this follow-up; the user will run the workflows later. Existing stops remain.

## Remaining verification

Current implementation and verified selections are recorded in [validation](validation.md#current-hosted-evidence--2026-11-14): workflow 03 passed 520 JVM cases per flavor plus compilation/JDK vectors, and workflow 07 passed 42 selected Android invocations. The expanded source batch and selected bulk/PIN/CSV/report coverage are complete. Keep those passes; remaining work below extends beyond that selection. Test authoring was authorized with execution reserved for the user's workflows; physical/live campaigns and local lint remain stopped.

| ID / priority | Dependencies | Remaining checks / completion criteria |
| --- | --- | --- |
| **R1 / P1 — Admin workflow** | Domain commands | Beyond the passed bulk/Undo/stale-target cases: hidden selections/full preview, role/busy/modal/context races, saved editor/navigation drafts and completion restoration. Check clipboard/setup feedback, View/snackbar targeting and admin sheets/dialogs/keyboard/detail panes; automated accessibility semantics remain separate from selected Home presentation checks. |
| **R2 / P2 — Imports/reports** | R1; shared transactions | Beyond the passed CSV/report cases: remaining clear/retry/provider/resource interactions and the full A5 layout matrix below. |
| **R3 / P2 — Sharing/security/recovery** | Reconciliation; V1 | Merge taxonomy-name/section-count choices and rename/retry, preserved ordering, empty shared publish/reconnect versus draft bootstrap, uncertain-attempt recovery, rejected-snapshot PIN preservation, cooldown/reconnect timing, large-ID acknowledgment, cancellation, new/legacy connection passwords and boot/process/clock-aware PIN delays. Preserve pending bases, change timestamps and operational identity. |
| **R4 / P3 — Storage/time/presentation** | R1 details/history | Fresh initialization, database lease/reset concurrency/cancellation, protected-cache invalidation on successful/failed writes, exact publication-time persistence, restore/leave recovery and safe rejection of unsupported development files. Check pharmacy midnight/resume/clock changes, missing/future/24-hour refresh boundaries, validated connectivity, startup/saved-theme splash and optional civil Hijri alongside authoritative Gregorian dates. |
| **R5 / P3 — Release readiness** | R1–R4; V1 | Production signing/update continuity, resources/native libraries and retained process/Keystore/resource/automated-accessibility checks. Debug signing/artifact evidence is retained; production signing and installed-update continuity remain unverified. |
| **PERF / P3 — Performance tooling** | Stable startup/Home flows; compatible pins | Implement the planned configuration/scenarios below; no generation, benchmark or measurement run is authorized. |

Repair stale test APIs/fixtures only if encountered in an explicitly authorized batch. Demo wrapper failure-isolation/diagnostics and refusal of unexpected real work are not separately established by the reported startup checks.

<a id="demo-and-separate-flavor-builds--p1"></a>
<a id="deferred-search-and-grouping-preferences--p2"></a>
<a id="search-and-grouping-verification--p2"></a>
<a id="user-correction-follow-up--2026-11-12"></a>

Completed demo, search/grouping and Home layout checks are recorded once in validation; they are not open implementation packages.

### Performance tooling — planned

Plan an isolated Baseline Profile generation/consumption target and repeatable offline startup/Home-scroll Macrobenchmark scenarios, using compatible pinned tooling, documented opt-in commands and source/artifact/device/result provenance. Keep performance campaigns out of ordinary builds and automatic hosted execution. Scenario/test development follows the repository's separate authorization rules. Completion requires reviewable configuration/scenario source and commands; generation, compilation, execution and measured improvement require separately applicable authorization. The user requested planning without running the tooling.

### PDF redesign — A5 source implemented

The compact category-grouped A5 portrait design is implemented and compiled. Selected multi-page Arabic and empty-report PDF assertions passed in workflow 07; the desktop mockup remains illustrative.

Remaining: complete the export/render matrix for three presets, 1–5 sections, mixed Arabic/English, long names/notes/metadata and category/page continuation beyond the selected cases. Keep the current provider/cancellation, bidi/digit and 1,000-page contracts. Broader device/provider and human print acceptance remain excluded from delivery; physical/live/local-lint stops and the no-local-emulator choice remain in effect.

<a id="v1--deferred-backuprestore-validation-gate--p1"></a>

## V1 — Backup/restore verification gate / P1

Depends on implemented backup/restore and explicit validation resumption. Verify current [backup/restore requirements](requirements.md#11-backup-and-restore): complete history above 10 MiB within the 64 MiB envelope cap; excluded identity/authorization/reminders; Unicode passphrases/randomness; malformed/schema/role/resource/cancellation/provider-close/storage failures; staging/replacement/activation interruption; live-PIN/conflict/uncertain-attempt/replacement-lineage recovery; isolated restore→reconcile→explicit republication. Failures preserve data and success state without blindly replaying transport steps. Only separately authorized upgrade checks belong here; development formats are unsupported.

Maintenance **PREPARING**, post-active verification and logical-cleanup interruption remain unfinished. Retain the six maintenance, three export, four native-SQL and seven focused backup-method passes. Complete the retained live PIN/manual-republication/reader-connection extension only on authorized resumption. SIGKILL/controlled-pressure evidence does not establish OS low-memory termination or power-loss behavior.

## Repository/release follow-up — 2026-11-14

Source changes implement shared debug/production reservation, wrapper/cache integration, isolated debug signing, production architecture outputs, retained mappings/receipts, maintenance workflows and repository/application housekeeping. Details and commands are in [build](build.md#repository-and-release-maintenance); checks actually executed are in [validation](validation.md).

| ID / priority | Dependencies | Remaining completion evidence |
| --- | --- | --- |
| M1 / P1 — Packaging | Wrapper changes; existing cache ownership | Wrapper argument/locking/Windows checks remain pending. Retain successful debug/demo/unsigned release evidence and failed reservations 1–3 in validation. |
| M2 / P1 — Distribution | M1; permanent debug/production identities | Production key agreement, three-output signing/artifact transfer and installed-update continuity remain unverified. Hosted debug signing/upload passed; independent local APK inspection timed out. |
| M4 / P2 — Manual hosted checks | M1; committed workflows | Dispatch cache, secret-history, cleanup-preview and actionlint-update checks only with applicable authorization. Preserve pending/incomplete status until executed. Keep current resource limits. Code 4 saved a new basic-cache key after the build-script fix; its signing job restored that exact entry. Another full build with unchanged key inputs and configuration-cache reuse remain unverified. |
| M5 / P2 — Deferred test work | Later batch authorization | Dedicated publishing seam, further backup/coverage review and inventory-consumer review/removal remain deferred. Expanded JVM and selected standard instrumentation outcomes are recorded in validation; this row grants no broader execution. |
| M6 / P1 — Dependency notices | Fresh production runtime graph | Reconcile the source/cached-runtime-family notice inventory against the release-generated runtime-components.txt inventory of exact transitive coordinates and retain additional upstream notices before distribution; no dependency resolution was authorized in this batch. |

Portable campaign tools require the existing campaign-specific init script via `--verification-init`, plus an existing SDK and accessible Gradle cache. A fresh checkout does not contain that private harness setup; it now fails before device/build work if missing. Its reproducible harness export and runtime portability remain part of the later authorized test batch, not an executed pass.

Keep screenshot protection, automatic PR checks, scheduled scans, attestations, extra style tools, commit-signature enforcement, a CLI workflow launcher and speculative source splitting excluded. Generated release notes, blame-ignore revisions and reproducibility comparisons remain deferred.

The 2026-11-15 focused repository work keeps full-SHA server enforcement, dependency submission/checksum verification/locking, release immutability, verification-runner provenance, checker-exclusion changes, generated-file metadata and duplicate-file work deferred. Extra security scanners require a separate evaluation; no integration or execution is planned here. Existing workflow separation, `Final`, CODEOWNERS, debug tags and manual changelog maintenance remain unchanged. Applied GitHub controls and source evidence are recorded in validation.

Shared-workstation source/configuration changes are source-only. Future authorized builds must verify managed output lookup, IDE cache/JDK selection, wrappers and preserved export/version behavior; no build or test execution is granted by these records. Existing generated data remains unmoved.
