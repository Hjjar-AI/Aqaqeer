# Verification scope

[Validation](validation.md) owns executed evidence; [plan](plan.md) owns remaining checks. A source inventory or selected class/method is not proof of execution or measured branch coverage.

## Evidence by revision and environment

| Evidence | Recorded scope | Limit |
| --- | --- | --- |
| 2026-11-16, `f1293ba`, workflow 03 | Standard and demo each passed 545 JVM cases, with zero failures/errors/skips; public JDK vectors passed. Both compiler logs have zero Kotlin source warnings. | Predates the test-worker CDS flag added in `528830a`; does not establish Android runtime acceptance. |
| 2026-11-16, `68cb2fb`, workflow 10 | Standard/demo debug APK build, signing and publication passed. | Predates the signing-only Gradle opt-out; production signing/update continuity is separate. |
| [2026-11-14 hosted evidence](validation.md#current-hosted-evidence--2026-11-14) | Earlier 520-case JVM result per flavor and 42 selected Android invocations. | Historical revisions/selections; preserve their passes without applying them to later source. |
| [2026-11-13 user report](validation.md#user-reported-completion--2026-11-13) | Broader demo startup/refresh and browsing marked complete by the user. | User-reported evidence, distinct from independently inspected per-case results. |

The current workflow-07 selector contains 7 demo invocations per API and 64 standard invocations. These are source selections, not a new pass count. Android compilation/execution of later UI/session changes and the offline Python reservation cases remain pending. The [current checkpoint](validation.md#current-verification-checkpoint--2026-11-16) links the evidence and its limits.

<a id="recoveryresource-extension"></a>

## Recovery evidence

Dated restore/native-SQL/export/maintenance results and controlled backup-resource measurements are retained in validation. PREPARING retry, post-active verification and logical-cleanup interruption remain unfinished. Valid passes must not be repeated simply because a campaign stopped. SIGKILL, controlled heap pressure, OS low-memory termination and power loss are distinct boundaries.

## Resume protocol

**Physical testing and local lint remain stopped.** Hosted configuration/results do not authorize local/device execution. Existing selected hosted emulator scope is documented in [build](build.md#manual-quality-and-security-workflows); no new broader-device or human-review campaign is required.

On an authorized resume, verify current constructor/result/cleanup contracts before fixture development, finish the authorized files before their first run, and reconcile fresh per-case results with original failures/interruption evidence. Inspect or modify test source only with explicit authorization. Retain source/artifact/device/run provenance; stale reports, missing instrumentation and interrupted hosts are not passes.

Physical helpers require the authorized serial and verified package/signer/install/run ownership. Use exact retries for unfinished cases; recheck ownership before delayed cleanup. See [operations](operations.md#verification-tools) for protected credentials and integration effects, and [build](build.md) for commands.

## Limits

User-reported completion is distinguished from independently inspected per-case evidence. Additional Android/device/provider and human visual/print/spoken TalkBack acceptance are excluded from delivery gates, while their outcomes remain unverified. Extra-resource token-revocation/second-destination campaigns are not awaited. Production signing/update continuity remains open. No new execution results follow from this document cleanup.
