# Verification scope

[Validation](validation.md) owns executed evidence; [plan](plan.md) owns remaining checks. A source inventory or selected class/method is not proof of execution or measured branch coverage.

## Evidence by revision and environment

Use the [current checkpoint](validation.md#current-verification-checkpoint--2026-11-16) for the latest recorded revision/environment, the [hosted evidence](validation.md#current-hosted-evidence--2026-11-14) for historical JVM/Android selections, and the [dated user report](validation.md#user-reported-completion--2026-11-13) for reported completion without fresh per-case evidence. Compilation, execution, source inventory and user acceptance remain separate.

[Build](build.md#hosted-emulator-screenshots-and-recordings) owns the current workflow-07 selection and invocation counts. A selector is not a new pass count. Later UI/session/source changes and offline Python reservation cases still need applicable compilation/execution evidence; the [backup/restore audit](validation.md#backuprestore-requirement-to-evidence-audit--2026-11-22) maps recorded results to current obligations without inspecting test source.

## Production-readiness test extension — 2026-11-24

Authored against `25c7ee2` with the prior 140 dp card-minimum change retained: **35 new JVM methods and 11 new Android methods**. No existing behavioral assertions were removed or relaxed. This is source authoring, not an execution or coverage result.

| Area | New methods | Assertions |
| --- | ---: | --- |
| Home projection | 13 JVM | Section-specific statuses, Reader stock restriction, query clearing, category/expiry/marker intersections, inactive sections, grouping partitions, deterministic ordering, and cancelled projection refusal. |
| Refresh ownership/policy | 6 JVM | App-owned request survives cancelled waiters, shared original failure, owner cancellation, fresh subsequent requests, and authenticated timestamp/parent/compatibility changes. |
| Snapshot read safety | 10 JVM | Invalid app/bot/group rejection before downstream calls, pharmacy/key cache isolation, update-required cache invalidation, publication/media identity checks, changed-pin retry, token-scoped rate limits, queued cancellation and stream closure. |
| Merge safety | 6 JVM | Explicit null versus concurrent edits, independent section availability, reviewed missing references, normalized taxonomy collisions and malformed operation targets. |
| Grid | 6 Android | Real one/two/three-column geometry at 319/320/321 and 467/468 dp, 1.5× font boundary at 459/460 dp, smaller-font floor, preference changes, full-span notices and stable item state. |
| Medication badges/cards | 5 Android | Read-only semantics, disabled/saving actions and geometry, expiry transitions, and dose/badge reachability with overflow assertions across both card modes, light/dark/AMOLED and 1×/1.5× text. |

Workflow 03 discovers all new JVM methods and compiles both Android flavors: user-run **03 - Checks - Tests and compilation**, `scope=all`, `coverage=true` (or `false` to omit reports). Then user-run **07 - Verify - Emulator with visual evidence**, `standard_functional=true`, on the default branch containing the changes. Its retained 88 standard selections plus 11 new UI methods make **99 standard API-35 invocations**; demo remains **10 per API 29/35**, totaling **119 invocations / 107 unique methods**. Workflow input descriptions, policy expectations and canonical build/CI guidance agree. All prior selections remain.

Only source-contract review, selector/declaration reconciliation, Python syntax parsing, document/inventory integrity and whitespace checks were performed. No tests, Gradle, compilation, packaging, lint, devices, migrations or workflows were executed/dispatched. Grid fixtures use explicit density and assert host width; card checks do not establish human visual or spoken accessibility acceptance. Existing report/export, startup, recovery and release limitations remain in [validation](validation.md) and [plan](plan.md).

## Report and hint test batch — 2026-11-23

Authored against production revision `ddf035017451002d2776d925971b9122c7d6f756` after explicit permission to inspect/update tests. Added **39 JVM methods** and **23 Android methods**; aligned existing report export/long-note fixtures and Home roles. One report production fix prevents ZIP cleanup from suppressing an exception onto itself and replacing the original failure; the writer-failure identity assertion covers it. No test, build, compilation, lint, device or workflow execution occurred locally. These counts describe authored methods, not passes or measured coverage.

| Area | Authored assertions |
| --- | --- |
| Selection and source | Published/local separation without fallback, all-medication defaults, source-bound IDs, active/explicit closed sections, search/category/form/individual intersections, empty selections, overall/any/all aggregation, inclusive dates, expiry thresholds, sorting/grouping, read-only capture. |
| Snapshot/controller | Same captured object for PDF/XLSX, option/source invalidation, late capture/source results, access/pharmacy resets, cancelled destinations, scoped feedback, output exceptions/cancellation. |
| Workbook | Real Android XML/ZIP parsing, relationships/content types, two sheets, text cells without formulas/macros, selected columns, localized values/status styles, RTL/frozen headers/filter table/print settings, ordinary rows, duplicate/long headings and columns beyond Z, empty reports, exact long Unicode/control/CRLF text reconstruction and identified continuation rows, write/cancellation/provider-close errors. |
| PDF | Real provider exports in demo, selected long-note continuation, all A4/A5/orientation/density combinations, compact-versus-comfortable page counts, cancellation; existing nonblank-page assertions preserved. |
| Hints/preferences/UI | Model default off, active AVAILABLE-only hints and Reader/Admin eligibility, unchanged cross-section search, persisted report options and hint toggles with restoration, both card layouts, Settings recreation, narrow RTL/1.5× report controls, required scientific identity, busy state and searchable multi-selection. |

Workflow 03 discovers JVM methods automatically: run **03 - Checks - Tests and compilation**, `scope=all`, `coverage=true` (or `false` to omit optional reports). Then run **07 - Verify - Emulator with visual evidence**, `standard_functional=true`, on the default branch containing this batch. The explicit selection is **10 demo invocations per API 29/35 + 88 standard API-35 invocations = 108 invocations / 96 unique methods**. Existing 64 standard selections are retained; 24 report/preferences/control/Home selections are added. No workflow was dispatched by the agent. Failure logs and original failed assertions must be reviewed before retries; historical passes below retain their original scope/counts.

The subsequent [user-run workflow review](validation.md#user-run-report-workflows--2026-11-23) at `5348f79` records 47 JVM suites per flavor (standard 582 passed / 2 failed / 48 skips; demo 615 passed / 2 failed / 15 skips), plus successful release compilation/JDK vectors. Android compilation was blocked by the missing ID-helper import and unsupported PdfDocument `.use`; API 29 also encountered Maven 429. No Android cases executed. The focused test-source repairs preserve exception provenance/suppression and PDF cleanup, with unchanged assertions/selectors elsewhere; no local run occurred and repair validation remains pending.

Latest [follow-up evidence](validation.md#user-run-report-workflows--2026-11-23) at `ba56374`: demo JVM 617 passed / 15 intentional skips, both instrumentation flavors and release compiled, JDK vectors passed; standard JVM did not execute due to Maven 429. Each demo API passed PDF/XLSX/public-vector/authentication methods and timed out six UI invocations at native launch (8 passes / 12 timeouts total). Standard's 88 selections were blocked before execution. A production native-gate repair and bounded dependency-only CI retry helper now await verification. Six Python methods are separately authored for recovery, exhaustion, compiler/assertion exclusion, mixed dependency failures and signal termination; workflow 03 selects them and no local run occurred. Kotlin method counts and 10/88 Android selections remain unchanged.

Limits remain explicit: Android raster/structure checks cannot prove complete PDF text extraction or actual Excel application/printing acceptance, new-reader persistence does not establish process-death recovery, absent-key DataStore behavior is source-reviewed/model-tested rather than exercised against a fresh installation, and the 1,000-page/Excel-row/memory caps are not pressure-tested by this batch. The human-review PDF helper remains outside workflow 07 because it requires its host-owned acceptance run and artifact collection.

## Earlier domain/demo authored batch — 2026-11-23

The user authorized source alignment/additions and later executed workflow 03 themselves. Added **47 JVM test methods** and **7 Android test methods**; these are authored method counts, not executed scenarios or new passes.

| Area | Authored or aligned checks |
| --- | --- |
| Availability | Three-value serialization/CSV/snapshot contract; removed-state rejection; missing-pair OUT filtering/counts/reports; no-op history and Undo preserving absent rows; sorting and UI labels. |
| Demo domain | Reader published-base visibility, explicit publish/reconnect, authorization, reset/rotation generation, rollback and paused restore. |
| Demo exchange/files | Shared CSV defaults/stale rows/chunks/retry; published/draft reports and presets; real CSV output; encrypted connection/backup round trips; bad password/confirmation/catalog/provider-close failure; production/demo format and flavor isolation. |
| Pin replacement | Exact targeted message ID, full-width ID preservation, invalid IDs, demo no-HTTP boundary; setup rechecks, bot-owned document refusal, unpin failure, revealed older pin and lost-response reconciliation. |
| Android-only additions | Demo A5 PDF render/provider checks and installed-catalog/identity isolation; pin/setup repository recovery with storage. Workflow 03 compiles these without executing them. |

Workflow 03 automatically discovers the JVM files. Use `scope=all`, `coverage=false` on the ref containing the changes; optional `coverage=true` requests reports without a percentage gate. Existing standard HTTP-adapter tests are standard-only because demo deliberately refuses HTTP before opening a connection; a separate demo refusal check preserves that boundary. Hosted-emulator selections remain 7 demo / 64 standard invocations with three selector renames, and no new Android runtime scope is selected or authorized.

<a id="recoveryresource-extension"></a>

## Current executed JVM coverage — 2026-11-23

[Run 45167699776](validation.md#current-hosted-result-and-warning-review--2026-11-23) at `b49eb35`, with coverage enabled, passed all five jobs. Standard discovered 592 cases: 556 passed, 36 intentional flavor skips; demo discovered 592: 577 passed, 15 intentional flavor skips. Both had zero failures/errors across 42 XML suites each. Standard line/branch coverage is 16.28%/15.71%; demo is 18.17%/16.50%. These whole-instrumented-app JVM measurements do not establish Android execution or universal behavior coverage. All new Android methods were compiled only. Test-tooling/compiler warnings and retained original evidence are detailed in validation.

## Warning-fix follow-up — hosted results

At `b9cff93`, user-run [workflow 03](validation.md#hosted-workflows-03-06-and-07--2026-11-23) passed 43 XML suites per flavor: 593 discovered each, standard 557 passed/36 intentional skips, demo 578 passed/15 intentional skips, zero failures/errors. The added startup-agent method passed with coverage enabled; compiler and test-stderr warnings are absent. Line/branch coverage is standard 16.27%/15.71%, demo 18.17%/16.50%.

User-run workflow 07 completed all 7 demo invocations per API (29 and 35), with 6 passed/1 failed each, and all 64 standard API-35 invocations, with 56 passed/8 failed. Across the three jobs: 78 invocations, 68 passed and 10 failed; these are not unique-method counts. Failures are one repeated Settings navigation method, seven Home placement methods with a mismatched projection fixture, and one refresh failure-injection contract mismatch. None were reported as passes based on instrumentation exit code zero. Original per-case logs/results/media and remaining limits are recorded in validation. The review did not execute or retry tests. Subsequent authorized source repairs align Settings lazy navigation, Reader projections and stocked scroll fixtures, and the injected NETWORK read-error contract; assertions and all 7 demo / 64 standard selections remain. User-run workflow 07 at 0bd3da2 subsequently compiled both Android flavors and passed every repaired case: demo 7/7 on each API 29/35, standard 63/64 on API 35 (77 passed/1 failed across 78 invocations). Its sole failure is the CSV cancellation retention test assuming persisted insertion order; both CREATED callback checks passed before its ordering assertion failed. The subsequent source repair sorts the persisted-name comparison, preserves the exact expected list and CREATED callbacks, and adds an explicit cancelled-job assertion; it has not been compiled or executed. No fresh workflow-03 JVM result was supplied. See validation for original evidence and diagnostic limits.

## Recovery evidence

Dated restore/native-SQL/export/maintenance results and controlled backup-resource measurements are retained in validation. PREPARING retry, post-active verification and logical-cleanup interruption remain unfinished. Valid passes must not be repeated simply because a campaign stopped. SIGKILL, controlled heap pressure, OS low-memory termination and power loss are distinct boundaries.

## Resume protocol

**Physical testing and local lint remain stopped.** Hosted configuration/results do not authorize local/device execution. Existing selected hosted emulator scope is documented in [build](build.md#manual-quality-and-security-workflows); no new broader-device or human-review campaign is required.

On an authorized resume, verify current constructor/result/cleanup contracts before fixture development, finish the authorized files before their first run, and reconcile fresh per-case results with original failures/interruption evidence. Inspect or modify test source only with explicit authorization. Retain source/artifact/device/run provenance; stale reports, missing instrumentation and interrupted hosts are not passes.

Physical helpers require the authorized serial and verified package/signer/install/run ownership. Use exact retries for unfinished cases; recheck ownership before delayed cleanup. See [operations](operations.md#verification-tools) for protected credentials and integration effects, and [build](build.md) for commands.

## Limits

User-reported completion is distinguished from independently inspected per-case evidence. Additional Android/device/provider and human visual/print/spoken TalkBack acceptance are excluded from delivery gates, while their outcomes remain unverified. Extra-resource token-revocation/second-destination campaigns are not awaited. Production signing/update continuity remains open. No new execution results follow from this document cleanup.
