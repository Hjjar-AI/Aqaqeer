# Remaining-work plan

[Requirements](requirements.md) define acceptance; [architecture](architecture.md) describes implementation; [validation](validation.md) records results. This plan grants no execution authorization.

## Android UI cleanup — source implemented / 2026-11-17

The approved source batch adds session dismissal controls, finite snackbars, focused Home rendering/action bundles, shared Material wrappers, typed navigation with retained encodings, explicit field instructions/visited-state retention, centralized tokens/palettes and configuration-aware UI resource readers. [Source evidence and limits](validation.md#android-ui-cleanup--2026-11-17) distinguish this from compilation/runtime acceptance. No execution authorization is added.

| ID / priority | Dependency | Remaining work / acceptance |
| --- | --- | --- |
| U1 / P1 — Compile and Android acceptance | Separate build/test/device or hosted authorization | Both flavors; snackbar close/timeout/Undo/View and route ownership; independent notice/card-tip dismissal across navigation/rotation, changed content and new sessions; unchanged backup snooze/domain blocks; restored navigation/draft keys and role restrictions; forms/secret visibility and validation; grid keys/scroll/bulk/editor guards, phone two-column layout, large text/IME/wide panes, all accents and manual light/dark/AMOLED contrast. |
| U2 / P2 — Source integrity links | Separate decision for unrelated artwork records | The full source-only checker reports two pre-existing missing artwork links in assets/branding/launcher-revision.md; no UI token/resource/delimiter/whitespace finding was observed. |

Deferred: image/font reduction, numbered-resource renaming/string-file consolidation, component gallery and screenshot testing. Preserve current assets, string IDs, adaptive columns and lifecycle clocks; no measured performance improvement is claimed.

## Initial attribution-free baseline — 2026-11-16

Approved production changes cover initial-v1 attribution-free storage/files, timestamp/operation-ID merge ordering, fresh APK numbering, Home/cards/detail/filter/selection changes, two-session fetched markers, Settings/navigation, forms, Maintenance, History and source previews. [Validation](validation.md#initial-attribution-free-baseline--2026-11-16) records source checks and limits. Earlier results below predate this baseline and do not validate it. The user subsequently authorized test repair/creation without local execution, then workflow 03 with JVM scope only.

| ID / priority | Dependency | Remaining work / acceptance |
| --- | --- | --- |
| I1 / P1 — Generated schema | Separate compilation/KSP authorization | Regenerate and review the initial Room version-1 export from current models. The retained export is historical; do not handwrite its identity hash. |
| I2 / P1 — Compilation and format acceptance | Separate authorization; I1 | Hosted workflow 03 passed both production/JVM flavor compilations and 545 JVM cases per flavor, including initial-format rejection/round trips and merge ordering. Remaining: Android instrumentation compilation/runtime, fresh initialization and non-destructive incompatible-database acceptance. The later [workflow 10 debug build](validation.md#hosted-debug-publication--2026-11-17) compiled/packaged both flavors at `e9dadd4` without running tests. No conversions are in scope. |
| I3 / P1 — UI/session acceptance | Separate authorization; I2 | All four search/tab placements, large text, narrow/wide layouts, stable selection, role restrictions, refresh failures/no-change fetches, marker accumulation/refresh and current-plus-next session lifetime, task/process versus rotation/backgrounding, Sunday/midnight boundaries. Check Home result retention during typing/filtering/availability saves, pharmacy/section/grouping/role invalidation and fresh-only bulk select-visible; large pending-review scrolling/filtering with retained conflict choices and unchanged publication/recovery controls; off-main splash decode and cancellation before hand-off. Check the revised gold/teal/navy launcher artwork, embedded outline, transparency and normal/round icon rendering across launcher backgrounds; check the revised stationary-logo splash with counter-rotating internal teal lines and fade at normal/reduced/disabled system animation scales, cold/warm launch and cancellation, Settings option alignment/independent labels, swatches, direct bordered preview and narrow/large-text scrolling, fresh initial demo revision-1 initialization with 80 explicit non-UNKNOWN pairs, initial grouping-off display and Settings enable/mode persistence, compact drawer spacing/48 dp targets, bordered icon headings/expanded state, Arabic alphabetical category order with Uncategorized last, and wrapped large-text labels, History, menus, forms, disclosures and previews, including the revised 24 dp dosage-form vectors in light/dark/AMOLED and compact bottom-search/filter sizing with normal/large Arabic text, clear action and keyboard, including a stationary pharmacy toolbar during keyboard show/hide; verify reader AVAILABLE/LOW visibility, section-grouped cross-section results without the redundant hint/count/chips below search, and demo «لانزابين» tab availability markers (including active-section-first result groups, empty-current matches, per-section card statuses/keys, bulk isolation, already-visible IDs, LOW and cleared queries), reset and admin sign-in/sign-out freshness, admin Available-only toggling, distinct extra-filter section/scrolling and sheet synchronization, reader inline fetched-chip/clear-action sizing in both placements, rounded tab borders/filled selected tabs and contrast in all themes and both placements, absence of separate quick bars, and fetched-chip appearance/disappearance while retaining a selected filter. |
| D1 / P1 — Demo modes | Separate compilation/runtime authorization | Confirm Reader initial mode and AVAILABLE/LOW visibility; fake Admin medication/status/bulk/Undo/taxonomy edits and pending/History; role switching/reset/process death/rotation; stale targets and protected taxonomy; old installed rows do not enter the workspace; no standard-data/identity/DB writes or connection/network/publication/file-exchange side effects. Source implementation is recorded in validation; no test work or execution is granted here. |
| I4 / P1 — Reservation acceptance | Separate packaging/hosted authorization | Workflow 10 initially reserved code 1 at `68cb2fb`; run 43967349302 reserved code 7 at `e9dadd4` and built/signed/published both debug flavors. Remaining: local/hosted coordination, wrapper locking across platforms and installation/update continuity. Preserve old tags and live publication/key/installation identities; offline Python reservation cases remain unexecuted. |

Test repair/creation is now authorized; 16 new Kotlin methods and 4 offline Python reservation methods are authored, alongside existing fixture/API/UI repairs. Workflow 03 supports `scope=jvm` (both JVM flavors and public JDK vectors; coverage off). The final correction run passed both JVM flavors (545 cases each) and public JDK vectors. Sorted-map fingerprint serialization and nonempty CSV clearing fixtures are verified there. Retain the original passes/failure evidence in [validation](validation.md#test-source-follow-up--2026-11-16). Workflow 07 source selects 64 standard methods plus 7 demo invocations per API; no emulator dispatch is authorized. Python reservation tests remain unexecuted and are not part of the JVM selection. Local tests/Gradle/build/compilation/lint, other workflow dispatches, device/live access and migrations remain unauthorized. Existing independent stops remain in force.

Remaining warning/cache follow-up: confirm the test-only `-Xshare:off` option on a future authorized JVM run. Workflow 10 run 43967349302 confirmed the signing-only Gradle opt-out while build caching stayed enabled. Source checks and prior warning-free compilation belong in [validation](validation.md#warning-and-cache-follow-up--2026-11-16). No packaging dispatch is authorized by documentation maintenance.

## Responsive UI follow-up — source implemented / 2026-11-17

The user selected responsive search secondary controls; pinned controls with compact notices; persistent wide list/detail with pharmacy navigation and detail-local title/actions; subtle predictive-back feedback; drawer-edge swipe reservation; selective heading spacing; state-aware chevrons; disabled search autocorrect/capitalization; available-height/stable-key pickers; shared breakpoint/detail-width limit; focused card caching; bulk-entry-only haptics; appearance-preserving shape tokens; reserved tab icon slots; compact step headers/decorative art; replacement of the empty-state capsule; dead branch removal; and extraction of the list/detail host. Default Scaffold insets and the 180 ms fade are retained. The user subsequently requested removal of skeletons: initial catalog loading retains a simple indicator/label, and Home creates no placeholder cards. The launcher artwork is unchanged. See [source evidence](validation.md#responsive-ui-follow-up--2026-11-17).

Remaining acceptance: search-trailing Filters with leading clear, reader What’s new beside Filters inside the trailing search-field row, admin Add in the former external-filter position without a floating overlay, absent Select shortcut with retained long-press bulk, admin card-to-edit routing and visible per-section immediate availability controls above the existing-medication fields (new-medication initial AVAILABLE/LOW/OUT choices saved atomically with the medication, with draft recreation, reset, stale-section rejection and unchanged defaults); the skeleton-free initial/context-change loading states and retained Home rows during recomputation (source changes after the latest hosted build); narrow/large-text field width and focus across secondary-control relocation; pinned controls and complete notice actions on short/IME windows; Home → medication A → B → back, drawer Home/category navigation, Add clearance, grouping/scroll retention and width changes around 840 dp; predictive completion/cancellation with busy/modals and disabled animations; RTL/LTR drawer-edge versus section drags; Arabic diacritics, headings, tab labels/icon slots and long detail titles; picker keyboard/empty/search/reordering/unchanged sentinel identities; single bulk-entry haptic; compact step semantics; empty-state art and theme/large-text contrast. Workflow 10 run 43967349302 compiled/packaged both debug flavors at `e9dadd4`; the listed Android runtime acceptance remains unverified. Future execution still requires separate authorization. Previous runtime passes do not validate these changes.

## Baseline Profile proposal — prioritized separate future project

**Prioritized planning only, requested by the user; no profile generation, test work, builds, device use or execution is authorized.** Baseline Profile and repeatable Home scrolling measurements are one future project, before further speculative card optimization. A Baseline Profile supplies Android with frequently used code paths to optimize ahead of time. Potential benefits are faster startup and smoother initial interactions; this repository has no measurements establishing a benefit. [Android overview](https://developer.android.com/topic/performance/baselineprofiles/overview).

Proposed scope and order:

1. Review the pinned Android/Compose toolchain and current standard/demo variants; design an isolated profile-generator/measurement module without changing production signing, shared caches or APK numbering. Propose dependencies and flavor selectors before implementation.
2. With separate source/test-authoring authorization, author deterministic startup, initial Home browsing, search, section switch and detail-opening journeys. Use public demo/local fixtures, not live Telegram or personal data; keep setup outside measured interactions. Review whether demo journeys exercise the production paths sufficiently before reusing a profile for standard.
3. With separate build and owned-emulator/device authorization, compare equivalent optimized release/profileable artifacts with and without the profile on the same environment and dataset. Record cold/warm startup, initial display/readiness and scrolling frame timing across repeated runs, with artifact/variant/device provenance. Keep profile generation and measurement execution distinct from ordinary application builds.
4. Adopt only if measurements justify maintenance: retain generated profile sources and the recipe, verify packaging in the intended variant, and document regeneration triggers. Preserve current startup/security behavior. If results are inconclusive, retain the evidence and defer adoption.

Deliverables would be reviewed module/dependency design, authorized generator source, profile files, a repeatable comparison recipe, measured results and documented limits. Costs include an extra module, Android execution time, variant handling and ongoing regeneration. This proposal does not dispatch workflows, reserve version codes or relax existing activity stops.

## Baseline and current stop

Core administration, sharing/recovery, search/grouping, demo and presentation sources are implemented. The [current checkpoint](validation.md#current-verification-checkpoint--2026-11-16) separates inspected hosted results from pending acceptance. The [2026-11-13 user report](validation.md#user-reported-completion--2026-11-13) is historical; retain valid evidence and repeat checks only when relevant changes or authorization justify it.

**Physical/live campaigns and local lint remain stopped.** Extra Telegram-resource campaigns, additional device/emulator/provider coverage and human visual/print/spoken TalkBack acceptance are excluded from required delivery. Their outcomes remain unverified. Production signing/update continuity still requires the signing identity. Source/document maintenance and existing hosted configuration do not resume stopped activities.

On an explicitly authorized physical resume, verify serial/package/signer/install/run ownership and fresh instrumentation first. Diagnose the missing instrumentation at the interrupted checkpoint, retain valid passes, run only unfinished checks and recheck owned resources before cleanup. Final lint requires separate applicable authorization.

<a id="sharing-review-follow-up--p1"></a>
<a id="ui-review-follow-up--p1"></a>
<a id="ordered-packages"></a>

<a id="review-fixes-home-placement"></a>

## Review fixes and Home placement — source implemented / 2026-11-15

The source batch and its dated authored/selected inventories are recorded in [validation](validation.md#review-fixes-home-placement). Its JVM regressions are included in the later passing JVM runs. Historical selected Android passes do not establish the later Home, refresh/recovery or taxonomy changes.

Remaining acceptance is covered by I2/I3 and R1–R4: manual versus automatic refresh eligibility, splash timing/disabled animations, Add-button clearance and large-text/modal/recreation interactions, maintenance warnings and the expanded Android regressions. No production test seam was added. Existing local build/test/lint, device/live and broader-dispatch stops remain. The current workflow-07 source selection is in [build](build.md#hosted-emulator-screenshots-and-recordings); selection is not execution.

## Remaining verification

The [2026-11-14 hosted evidence](validation.md#current-hosted-evidence--2026-11-14) records the earlier JVM and selected Android results; the [current checkpoint](validation.md#current-verification-checkpoint--2026-11-16) records the newer JVM and debug-distribution results. Keep those dated passes. The checks below extend beyond those selections and do not acquire execution authorization from being listed here. Physical/live campaigns and local lint remain stopped.

| ID / priority | Dependencies | Remaining checks / completion criteria |
| --- | --- | --- |
| **R1 / P1 — Admin workflow** | Domain commands | Beyond the passed bulk/Undo/stale-target cases: hidden selections/full preview, role/busy/modal/context races, saved editor/navigation drafts and completion restoration. Check clipboard/setup feedback, View/snackbar targeting and admin sheets/dialogs/keyboard/detail panes; automated accessibility semantics remain separate from selected Home presentation checks. |
| **R2 / P2 — Imports/reports** | R1; shared transactions | Beyond the passed CSV/report cases: remaining clear/retry/provider/resource interactions and the full A5 layout matrix below. |
| **R3 / P2 — Sharing/security/recovery** | Reconciliation; V1 | Merge taxonomy-name/section-count choices and rename/retry, preserved ordering, empty shared publish/reconnect versus draft bootstrap, uncertain-attempt recovery, rejected-snapshot PIN preservation, cooldown/reconnect timing, large-ID acknowledgment, cancellation, existing/new numeric connection passwords and boot/process/clock-aware PIN delays. Preserve pending bases, change timestamps and operational identity. |
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
| M1 / P1 — Packaging | Wrapper changes; existing cache ownership | Wrapper argument/locking/Windows checks remain pending. Retain successful debug/demo/unsigned release evidence and historical pre-reset failed reservations 1–3 in validation. |
| M2 / P1 — Distribution | M1; permanent debug/production identities | Production key agreement, three-output signing/artifact transfer and installed-update continuity remain unverified. Hosted debug signing/upload passed; independent local APK inspection timed out. |
| M4 / P2 — Manual hosted checks | M1; committed workflows | Dispatch cache, secret-history, cleanup-preview and actionlint-update checks only with applicable authorization. Preserve pending/incomplete status until executed. Keep current resource limits. Retain the historical cache receipts and newer exact-key hit evidence in validation. Cross-job project configuration-cache reuse and the revised signing setup remain unverified. |
| M5 / P2 — Deferred test work | Later batch authorization | Dedicated publishing seam, further backup/coverage review and inventory-consumer review/removal remain deferred. Expanded JVM and selected standard instrumentation outcomes are recorded in validation; this row grants no broader execution. |
| M6 / P1 — Dependency notices | Fresh production runtime graph | Reconcile the source/cached-runtime-family notice inventory against the release-generated runtime-components.txt inventory of exact transitive coordinates and retain additional upstream notices before distribution; no dependency resolution was authorized in this batch. |

Portable campaign tools require the existing campaign-specific init script via `--verification-init`, plus an existing SDK and accessible Gradle cache. A fresh checkout does not contain that private harness setup; it now fails before device/build work if missing. Its reproducible harness export and runtime portability remain part of the later authorized test batch, not an executed pass.

Keep screenshot protection, automatic PR checks, scheduled scans, attestations, extra style tools, commit-signature enforcement, a CLI workflow launcher and speculative source splitting excluded. Generated release notes, blame-ignore revisions and reproducibility comparisons remain deferred.

The 2026-11-15 focused repository work keeps full-SHA server enforcement, dependency submission/checksum verification/locking, release immutability, verification-runner provenance, checker-exclusion changes, generated-file metadata and duplicate-file work deferred. Extra security scanners require a separate evaluation; no integration or execution is planned here. Existing workflow separation, `Final`, CODEOWNERS, debug tags and manual changelog maintenance remain unchanged. Applied GitHub controls and source evidence are recorded in validation.

Shared-workstation source/configuration changes are source-only. Future authorized builds must verify managed output lookup, IDE cache/JDK selection, wrappers and preserved export/version behavior; no build or test execution is granted by these records. Existing generated data remains unmoved.
