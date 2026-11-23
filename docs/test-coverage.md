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

## Warning-fix follow-up — source only

After the run review, the user requested warning fixes. Added one JVM startup-agent regression method; migrated 12 Compose factories and synchronized callback-state assertions; updated the deprecated Room helper and unnecessary null assertions; gated accessibility pooling cleanup to API 26–32. The original 592-case outcomes above remain unchanged historical measurements. All 7 demo / 64 standard emulator selections remain unchanged. Future workflow-03 scope=all, coverage=true must verify the agent configuration and new sources; Android v2 synchronization still needs separate runtime authorization. No checks were executed during this follow-up.

## Recovery evidence

Dated restore/native-SQL/export/maintenance results and controlled backup-resource measurements are retained in validation. PREPARING retry, post-active verification and logical-cleanup interruption remain unfinished. Valid passes must not be repeated simply because a campaign stopped. SIGKILL, controlled heap pressure, OS low-memory termination and power loss are distinct boundaries.

## Resume protocol

**Physical testing and local lint remain stopped.** Hosted configuration/results do not authorize local/device execution. Existing selected hosted emulator scope is documented in [build](build.md#manual-quality-and-security-workflows); no new broader-device or human-review campaign is required.

On an authorized resume, verify current constructor/result/cleanup contracts before fixture development, finish the authorized files before their first run, and reconcile fresh per-case results with original failures/interruption evidence. Inspect or modify test source only with explicit authorization. Retain source/artifact/device/run provenance; stale reports, missing instrumentation and interrupted hosts are not passes.

Physical helpers require the authorized serial and verified package/signer/install/run ownership. Use exact retries for unfinished cases; recheck ownership before delayed cleanup. See [operations](operations.md#verification-tools) for protected credentials and integration effects, and [build](build.md) for commands.

## Limits

User-reported completion is distinguished from independently inspected per-case evidence. Additional Android/device/provider and human visual/print/spoken TalkBack acceptance are excluded from delivery gates, while their outcomes remain unverified. Extra-resource token-revocation/second-destination campaigns are not awaited. Production signing/update continuity remains open. No new execution results follow from this document cleanup.
