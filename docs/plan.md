# Remaining-work plan

[Requirements](requirements.md) define acceptance; [architecture](architecture.md) describes implementation; [validation](validation.md) records results. This plan grants no execution authorization.

## Baseline and current stop

Core administration, sharing/recovery, search/grouping, demo and presentation workflows are implemented. Current builds/packaging, selected hosted CI, demo initialization/refresh and browsing checks are [user-reported complete](validation.md#user-reported-completion--2026-11-13); do not repeat them without a relevant change or new request.

**Physical/live campaigns and local lint remain stopped.** Extra Telegram-resource campaigns, additional device/emulator/provider coverage and human visual/print/spoken TalkBack acceptance are excluded from required delivery. Their outcomes remain unverified. Production signing/update continuity still requires the signing identity. Source/document maintenance and existing hosted configuration do not resume stopped activities.

On an explicitly authorized physical resume, verify serial/package/signer/install/run ownership and fresh instrumentation first. Diagnose the missing instrumentation at the interrupted checkpoint, retain valid passes, run only unfinished checks and recheck owned resources before cleanup. Final lint requires separate applicable authorization.

<a id="sharing-review-follow-up--p1"></a>
<a id="ui-review-follow-up--p1"></a>
<a id="ordered-packages"></a>

## Remaining verification

The latest Home changes (section swipes, narrower drawer, category grouping/two-column defaults, simplified search/filters, inline accent dots and unavailable-card stripes) have source-only checks. The expanded 40-entry demo seed and its guarded refresh also have source-only checks. Compilation and runtime verification of these changes remain pending explicit authorization; earlier browsing completion does not cover them.

| ID / priority | Dependencies | Remaining checks / completion criteria |
| --- | --- | --- |
| **R1 / P1 — Admin workflow** | Domain commands | Single/bulk edits, hidden selections/full preview, stale/deleted/requested versus unchecked values, role/busy/modal/context races, atomic Undo, saved editor/navigation drafts and completion restoration. Check history, clipboard/setup feedback and View/snackbar targeting; admin sheets/dialogs/keyboard/detail panes and automated accessibility semantics remain separate from completed Home browsing. |
| **R2 / P2 — Imports/reports** | R1; shared transactions | CSV duplicate/row/clear/retry behavior and report data/resource/provider correctness. A5 PDF layout is implemented in source; actual export/render verification remains pending below. |
| **R3 / P2 — Sharing/security/recovery** | Reconciliation; V1 | Merge taxonomy-name/section-count choices and rename/retry, preserved ordering, empty shared publish/reconnect versus draft bootstrap, uncertain-attempt recovery, rejected-snapshot PIN preservation, cooldown/reconnect timing, large-ID acknowledgment, cancellation, new/legacy connection passwords and boot/process/clock-aware PIN delays. Preserve pending bases, attribution and identity. |
| **R4 / P3 — Storage/time/presentation** | R1 details/history | Fresh initialization, database lease/reset concurrency/cancellation, protected-cache invalidation on successful/failed writes, exact publication-time persistence, restore/leave recovery and safe rejection of unsupported development files. Check pharmacy midnight/resume/clock changes, missing/future/24-hour refresh boundaries, validated connectivity, startup/saved-theme splash and optional civil Hijri alongside authoritative Gregorian dates. |
| **R5 / P3 — Release readiness** | R1–R4; V1 | Production signing/update continuity, resources/native libraries and retained process/Keystore/resource/automated-accessibility checks. Production/debug signing environments are configured; first signing/artifact and installed-update evidence remain unverified. |
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

The user selected a compact category-grouped table on A5; portrait is the stated default. Source now provides a medication/details column, one labeled status column per section, repeated metadata/category/table headings and complete long-row continuation with repeated statuses. Existing capture/source selection, CSV behavior, fields, Arabic/bidi/digits, 1,000-page cap and provider/cancellation behavior are preserved. A desktop illustrative preview uses fictional data; it is not Android PDF rendering or human/print acceptance.

Remaining: separately authorized compilation and actual Android PDF export/render review for the three presets, 1–5 sections, mixed Arabic/English, long names/notes/metadata, category/page continuation and empty results. Existing physical/live/local-lint stops and no-local-emulator choice remain in effect. Broader device/provider coverage and human print acceptance remain excluded from required delivery.

<a id="v1--deferred-backuprestore-validation-gate--p1"></a>

## V1 — Backup/restore verification gate / P1

Depends on implemented backup/restore and explicit validation resumption. Verify current [backup/restore requirements](requirements.md#11-backup-and-restore): complete history above 10 MiB within the 64 MiB envelope cap; excluded identity/authorization/reminders; Unicode passphrases/randomness; malformed/schema/role/resource/cancellation/provider-close/storage failures; staging/replacement/activation interruption; live-PIN/conflict/uncertain-attempt/replacement-lineage recovery; isolated restore→reconcile→explicit republication. Failures preserve data and success state without blindly replaying transport steps. Only separately authorized upgrade checks belong here; development formats are unsupported.

Maintenance **PREPARING**, post-active verification and logical-cleanup interruption remain unfinished. Retain the six maintenance, three export, four native-SQL and seven focused backup-method passes. Complete the retained live PIN/manual-republication/reader-connection extension only on authorized resumption. SIGKILL/controlled-pressure evidence does not establish OS low-memory termination or power-loss behavior.

## Repository/release follow-up — 2026-11-14

Source changes implement shared debug/production reservation, wrapper/cache integration, isolated debug signing, production architecture outputs, retained mappings/receipts, maintenance workflows and repository/application housekeeping. Details and commands are in [build](build.md#repository-and-release-maintenance); checks actually executed are in [validation](validation.md).

| ID / priority | Dependencies | Remaining completion evidence |
| --- | --- | --- |
| M1 / P1 — Packaging | Wrapper changes; existing cache ownership | Workflow 06 standard-debug compilation/packaging passed on `2d98992d`, code 4, after the Preview fixes. Keep failed reservations 1–3. Demo build/signing also passed on 0071d3c7; production unsigned build/inspection passed in workflow 08. Release startup-helper absence/progress correction is source-only; the original startup job failed and the duplicate was cancelled. Wrapper argument/locking/Windows checks remain pending. |
| M2 / P1 — Distribution | M1; permanent debug/production identities | Code-4 standard debug signing/verification and signed-bundle upload passed on GitHub. Independent local APK inspection was not completed (download timeout). Production key agreement, three-output signing/artifact transfer and installed-update continuity remain unverified. |
| M3 / P1 — GitHub controls | Reviewable ruleset JSON; owner access | Apply reviewed rulesets when explicitly requested; enable/review private reporting, Dependabot alerts/security updates and full-SHA enforcement. [Observed settings](evidence/repository-review-20261114.md). Release immutability remains unenabled. |
| M4 / P2 — Manual hosted checks | M1; committed workflows | Dispatch cache, startup, secret-history, cleanup-preview and actionlint-update checks only with applicable authorization. Preserve pending/incomplete status until executed. Keep current resource limits. Code 4 saved a new basic-cache key after the build-script fix; its signing job restored that exact entry. Another full build with unchanged key inputs and configuration-cache reuse remain unverified. |
| M5 / P2 — Deferred test work | Later batch start | Publishing seam/offline tests; focused search/backup/bulk/PIN coverage review; inventory-consumer review and removal only if unused. Do not start this review now. Coverage report configuration does not authorize test execution. |
| M6 / P1 — Dependency notices | Fresh production runtime graph | Reconcile the source/cached-runtime-family notice inventory against the release-generated runtime-components.txt inventory of exact transitive coordinates and retain additional upstream notices before distribution; no dependency resolution was authorized in this batch. |

Portable campaign tools require the existing campaign-specific init script via `--verification-init`, plus an existing SDK and accessible Gradle cache. A fresh checkout does not contain that private harness setup; it now fails before device/build work if missing. Its reproducible harness export and runtime portability remain part of the later authorized test batch, not an executed pass.

Keep screenshot protection, automatic PR checks, scheduled scans, attestations, extra style tools, commit-signature enforcement, a CLI workflow launcher and speculative source splitting excluded. Generated release notes, blame-ignore revisions and reproducibility comparisons remain deferred.
