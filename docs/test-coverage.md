# Verification scope

[Validation](validation.md) owns executed evidence; [plan](plan.md) owns remaining checks. A source inventory or selected class/method is not proof of execution or measured branch coverage.

## Evidence by revision and environment

Use the [current checkpoint](validation.md#current-verification-checkpoint--2026-11-16) for the latest recorded revision/environment, the [hosted evidence](validation.md#current-hosted-evidence--2026-11-14) for historical JVM/Android selections, and the [dated user report](validation.md#user-reported-completion--2026-11-13) for reported completion without fresh per-case evidence. Compilation, execution, source inventory and user acceptance remain separate.

[Build](build.md#hosted-emulator-screenshots-and-recordings) owns the current workflow-07 selection and invocation counts. A selector is not a new pass count. Later UI/session/source changes and offline Python reservation cases still need applicable compilation/execution evidence; the [backup/restore audit](validation.md#backuprestore-requirement-to-evidence-audit--2026-11-22) maps recorded results to current obligations without inspecting test source.

## Current authored batch — 2026-11-23

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

User-run workflow 07 completed all 7 demo invocations per API (29 and 35), with 6 passed/1 failed each, and all 64 standard API-35 invocations, with 56 passed/8 failed. Across the three jobs: 78 invocations, 68 passed and 10 failed; these are not unique-method counts. Failures are one repeated Settings navigation method, seven Home placement methods with a mismatched projection fixture, and one refresh failure-injection contract mismatch. None were reported as passes based on instrumentation exit code zero. Original per-case logs/results/media and remaining limits are recorded in validation. The review did not execute or retry tests. Subsequent authorized source repairs align Settings lazy navigation, Reader projections and stocked scroll fixtures, and the injected NETWORK read-error contract; assertions and all 7 demo / 64 standard selections remain. These repairs are not compiled or executed; next user-run checks are workflow 03 scope=all and workflow 07 standard_functional=true.

## Recovery evidence

Dated restore/native-SQL/export/maintenance results and controlled backup-resource measurements are retained in validation. PREPARING retry, post-active verification and logical-cleanup interruption remain unfinished. Valid passes must not be repeated simply because a campaign stopped. SIGKILL, controlled heap pressure, OS low-memory termination and power loss are distinct boundaries.

## Resume protocol

**Physical testing and local lint remain stopped.** Hosted configuration/results do not authorize local/device execution. Existing selected hosted emulator scope is documented in [build](build.md#manual-quality-and-security-workflows); no new broader-device or human-review campaign is required.

On an authorized resume, verify current constructor/result/cleanup contracts before fixture development, finish the authorized files before their first run, and reconcile fresh per-case results with original failures/interruption evidence. Inspect or modify test source only with explicit authorization. Retain source/artifact/device/run provenance; stale reports, missing instrumentation and interrupted hosts are not passes.

Physical helpers require the authorized serial and verified package/signer/install/run ownership. Use exact retries for unfinished cases; recheck ownership before delayed cleanup. See [operations](operations.md#verification-tools) for protected credentials and integration effects, and [build](build.md) for commands.

## Limits

User-reported completion is distinguished from independently inspected per-case evidence. Additional Android/device/provider and human visual/print/spoken TalkBack acceptance are excluded from delivery gates, while their outcomes remain unverified. Extra-resource token-revocation/second-destination campaigns are not awaited. Production signing/update continuity remains open. No new execution results follow from this document cleanup.
