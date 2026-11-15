# Validation

[Requirements](requirements.md) define acceptance; [plan](plan.md) owns remaining work. The evidence manifest (local-only: `../local/verification/manifest.json`) retains original results, public hashes and provenance. Ignored evidence may be absent in fresh checkouts. Source checks, compilation, runtime results and human acceptance remain distinct.

<a id="review-fixes-home-placement"></a>

## Review fixes and Home placement — 2026-11-15

Implemented source changes cover clean forward-gap refresh under transactional guards; deletion scope/cascade/reassignment review and restored tombstones; multiple-attempt rejection; manual refresh with an available unvalidated network; taxonomy confirmation/active defaults/missing-target errors; blank CSV records with numbering/caps retained; explicit admin missing/unrelated-pin republication using durable attempts/reapproval/read-back; coordination guidance; local bottom-bar preferences with keyboard/Add-button handling; focused UI components; and the 60 ms splash exit. Requirements, mechanisms and both operating guides were updated. Retained security/history/automatic-trigger policies were not redesigned.

Production call-site/state/semantics review and `git diff --check` completed. Local integrity checks resolved **47 local links/anchors across six changed documents**, parsed **six values XML files**, and found all **469 string references** from the 17 affected production files; **21 new strings**, zero missing references or link problems. These are source/integrity checks only. Compilation, runtime, visual/accessibility acceptance and the acceptance scenarios in [RV1](plan.md#review-fixes-home-placement) remain unverified. The initial implementation did not inspect or author tests. The subsequent user-authorized follow-up adds **15 JVM methods and 28 Android methods** across ten test files, and updates two existing method names/expectations for strict lineage versus clean gap catch-up. Coverage source includes scoped deletion/cascades/reassignments/restored tombstones, blank CSV numbering/caps, pin identification/recovery/failure/reconciliation, missing targets, bottom placement/keyboard/compact heights, taxonomy state and persisted/recreated preferences. No behavioral assertions were relaxed to obtain a pass, and no production code or test seams were changed in the authoring follow-up.

The workflow helper source now explicitly selects **7 demo invocations on each of API 29/35 and 58 standard invocations on API 35** with `standard_functional=true` (72 invocations if both demo APIs and standard are requested). The keyboard case configures and reads back its soft-keyboard prerequisite only on the already-verified owned ephemeral emulator. Python AST/YAML syntax, every selected-method declaration, coroutine Unit-return declarations, whitespace and **91 local links across four changed documents** were checked statically with no problems; no workflow was dispatched. Compilation and all new runtime outcomes remain unverified. No tests, builds/packaging/lint, migration work or device/live Telegram actions were executed. Historical hosted results below predate this batch.

## Current hosted evidence — 2026-11-14

| Check | Source / run | Confirmed result |
| --- | --- | --- |
| JVM, compilation and public JDK crypto | `8ad371490957ebed4b7e6f49ab6471ae1c40b4e6`; [03 / 43619086956](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43619086956) | All five jobs passed. **520 JVM cases per flavor**, zero failures/errors/skips: 1,040 flavor executions, 32 XML suites each. Both debug/instrumentation variants and standard release compiled; coverage reports generated. |
| Selected Android behavior and capture | `f2a71a2812d0998fd3144892c2d1d55f363cc797`; [07 / 43624312398](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43624312398), attempt 1 | **42/42 invocations passed**: demo API 29 6/6, demo API 35 6/6, standard API 35 30/30. No skips, missing cases or capture errors. |
| Standard debug delivery | `2d98992da1968a6686868e22ba0d30dbae0050cc`; [06 / 43579514229](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43579514229) | **0.0.4/code 4** built, signed/verified in the isolated debug environment and uploaded as `debug-release-standard-43579514229`. Independent local APK inspection timed out. |
| Demo debug delivery | `0071d3c7913140f60b743492dac4db00ab726017`; [06 / 43580623979](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43580623979) | Build, isolated signing/verification and artifact upload passed. |
| Unsigned production inspection and startup | `b1d777218316da8556d939f0f1a2b5ea12b4dcf6`; [08 / 43644861384](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43644861384) | Both jobs passed: universal/ARM32/ARM64 inspection, owned API-35 emulator launch and 30-second process survival; cleanup confirmed absent. Throwaway signing is not production signing/update acceptance. |

Workflow 07's demo selection covers read-only navigation, LIGHT/DARK/AMOLED Arabic large-text/recreation and two public-envelope checks. It invokes four unique methods six times per API. Each demo artifact retains six PNG captures and four clips; presence was checked, not human visual quality. The 30 standard cases cover CSV chunk commit/cancellation/storage rollback, stale/duplicate/alias behavior, selected reports/PDF, bulk edits/Undo/stale/deleted targets, PIN changes and history filtering. This is selected coverage, not the full instrumentation inventory, complete PDF layout review or spoken/print acceptance.

Latest original logs, metadata, XML/transcripts, results/provenance and captures are retained under local-only `../local/verification/runs/current/ci-run-43619086956/` and `../local/verification/runs/current/ci-run-43624312398/`. Their inventories and the manifest retain hashes; all 42 Android transcripts reconcile with successful single-case completion. GitHub artifacts have one-day retention and may expire under run cleanup.

## Focused repository fixes — 2026-11-15

Source changes protect `.aqz`/private-key files and known workflow outputs, reject sensitive filenames before reading contents, skip deletion-only pushes in the optional hook, retain debug tags while showing the reserved APK version in release titles/notes, categorize reservation failures without raw API output/retries, cover composite actions with Dependabot and provide short bilingual issue forms/private reporting fallback. Workstation-only links are inline paths. No public fixture exemptions were added; no dependency/action versions changed.

GitHub private reporting, Dependabot alerts/security-update PRs and both reviewed branch/tag rulesets were applied and read back; the [settings receipt](evidence/repository-review-20261114.md#focused-settings-update--2026-11-15) records IDs and limits. Local hook activation was not changed.

Source-only integrity checked **243 text files / 221 local links, zero problems**; parsed CI policy checked **15 workflows, zero problems**. Hook and publication-shell syntax, short issue-form YAML and the selected ignore patterns passed lightweight checks. These are source checks, not hook runtime or debug-release publication evidence.

Hosted [08 / 43644861384](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43644861384), attempt 1, passed on `Final`, source `b1d777218316da8556d939f0f1a2b5ea12b4dcf6`. Before dispatch, hosted workflow/helper blob hashes matched the local files containing the existing package-absence correction. Universal/ARM32/ARM64 release inspection passed. The universal APK, throwaway-signed as **0.0.1/code 1**, launched on the owned API-35 `aqaqeer-release-smoke` / `emulator-5554`; its process survived 30 seconds, and the owned installation was removed with absence confirmed. This closes the helper's startup-verification gap, not production signing/update or broader behavioral acceptance. Verification packaging did not create a shared distribution reservation.

Original startup results/ownership/progress, launch/log/screenshot, architecture inspection receipts/mappings, run metadata/logs, artifact inventory and settings read-back are retained under local-only `../local/verification/runs/current/ci-release-43644861384/`, with public-file hashes inventoried in the local evidence manifest. The hosted run builds the earlier source and does not verify the housekeeping changes above. No local builds, Gradle, lint, test-suite inspection/execution or migration inspection accompanied this work.

## Expanded test source — 2026-11-14

The authorized authoring batch expanded the static inventory below; the three separately repaired initial-schema tests are excluded from these figures. JVM parameter rows count separately.

| Source set | Before | Authored cases | Method declarations |
| --- | --- | --- | --- |
| JVM, per flavor | 165 | 520 | 211 |
| Instrumentation | 132 | 155 | 155 |
| Combined inventory | 297 | 675 | 366 |

Five parameterized matrices contribute 314 cases: duplicates 48, search/projection 120, CSV dialect 72, expiry 42 and field merge 32. Focused additions cover preview revisions, protected-cache isolation/invalidation, typed diffs, four-digit/mixed-script PINs, bulk changes/Undo, history filters and CSV/report behavior. Existing fixtures were aligned with current constructors, scientific names, AVAILABLE defaults, typed domain errors, monotonic PIN delays, batch callbacks and UI APIs. Counts are source inventory, not declaration/branch coverage. Current execution evidence is recorded above; host-managed recovery cases retain separate prerequisites.

[Workflow 03](../.github/workflows/verification.yml) runs JVM tests and compiles instrumentation. [Workflow 07](../.github/workflows/emulator.yml) selects the demo cases plus the default-off `standard_functional` API-35 job. [Build](build.md#manual-quality-and-security-workflows) owns current commands, inputs and evidence behavior.

## Resolved failures and retained originals

| Original boundary | Resolution / remaining limit | Local evidence under `../local/verification/` |
| --- | --- | --- |
| [03 / 43617271752](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43617271752), `79afced6c77edb3afa7c5566e93b7ce68da5eb03` | Four JVM failures/flavor: stale CSV brand-clearing assertion and uninitialized UI strings. Instrumentation compilation also lacked the DAO extension import and referenced removed migrations. Fixture/assertion/import repairs passed the later 03 run; initial-schema reopen/unsupported-version tests compiled but were not run. | `runs/current/ci-tests-43617271752/` |
| [07 / 43619188231](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43619188231), `8ad371490957ebed4b7e6f49ab6471ae1c40b4e6` | API 29: four UI crashes from missing Hilt-test resource initialization, two provider RESOURCE_LIMIT failures. API-35 jobs stopped before cases because the ownership parser required `userId` instead of Android 15's `appId`. Corrected runner/parser/provider fixtures passed the latest 07 run. | `runs/current/ci-run-43619188231/` |
| Workflow 06 reservations 1–3: runs 43576529418, 43578262151, 43578840530 | Compose Preview dependency discovery required Android unit-test resources and a standard-release host-test component. Both prerequisites were corrected; code-4 debug delivery passed. Preserve failed reservations. | `runs/current/ci-build-43576529418/`, `ci-build-43578262151/`, `ci-build-43578840530/` under the same `runs/current/` directory |
| [08 / 43580652898](https://github.com/Hjjar-AI/Aqaqeer/actions/runs/43580652898) startup; duplicate 43580701675 cancelled | Helper incorrectly rejected expected package absence. Corrected absence/progress handling passed startup and confirmed cleanup in 08 / 43644861384. The old helper recorded no phase, so the exact original runtime command is not independently established. | New receipts in `runs/current/ci-release-43644861384/`; original hosted outcomes remain historical failures |
| 2026-11-13 dependency/build/cleanup failures | Original scan: 341 coordinates, eight flagged tool/processor versions, no suppressions. Restore callback and cleanup absence-confirmation defects were corrected. Later user-reported success does not rewrite original failures. | `runs/current/ci-remediation-20261113/` |
| Local production lint | Failed after 6m 37s with `AsyncExecutionService.getService must not return null`; copied older reports were stale. Owned wrapper/daemon absent at stop, counter unchanged. No retry. | `runs/current/ci-quality-20261113T062213Z/production-lint.log`, `receipt.json` in that directory |

The successful provider tests use the existing injectable **1 MiB workspace**, assert **2 MiB observed headroom**, and log heap/budget. Public envelopes are at most 266 serialized bytes. They establish provider/authentication behavior, not default production memory eligibility or its 16-MiB reserve policy. The original failed run did not record heap headroom. The ownership correction accepts one numeric `userId`/`appId` on verified user 0, retains install-time/code-path checks and saves raw dumps plus phase/failure evidence. Android-15 `Settings.java` is retained with the failed run; that earlier helper did not retain raw API-35 dumps.

<!-- Retained incoming links to consolidated source/history notes. -->
<a id="arabic-search-and-optional-grouping--2026-11-12"></a>
<a id="ci-downloads-without-a-payment-method--2026-11-13"></a>
<a id="ci-quality-security-and-diagnostics--2026-11-13"></a>
<a id="ci-tests-and-compilation--2026-11-13"></a>
<a id="demo-content-and-flavor-selection--2026-11-13"></a>
<a id="design-critique-follow-up--2026-11-13"></a>
<a id="historical-functional-results--2026-11-1112"></a>
<a id="logic-review-enhancements--2026-11-14"></a>
<a id="phone-feedback-corrections--2026-11-12"></a>
<a id="repositoryrelease-source-maintenance--2026-11-14"></a>
<a id="sharingadmin-review--2026-11-13"></a>
<a id="source-checks"></a>
<a id="ui-lint-and-preview-delivery--2026-11-12"></a>
<a id="verified-review-and-initial-contract--2026-11-13"></a>

## Source/configuration checks

Production mechanisms and product decisions are maintained in [architecture](architecture.md), [requirements](requirements.md) and the [changelog](../CHANGELOG.md), rather than a completed-review diary. Current hosted compilation and selected runtime results supersede earlier blanket “source only” labels for those paths.

The 2026-11-13/14 source reviews covered incremental catalog writes, foreign-key/tombstone ordering, CSV transaction/result boundaries, publication/PIN/restore guards, lazy selections, cache/refresh behavior, history filters, Home presentation, the 40-entry demo seed and A5 reports. Source checks included Kotlin delimiters, XML/resources, current call sites, Python AST, workflow YAML/shell/policy, wrapper/version consistency and local links/whitespace. They are not additional runtime passes. Latest Markdown compaction retained prior heading anchors and passed changed-document links/whitespace checks; the five pre-existing shared-guide warnings remain a repository-relative resolver limitation. Historical absolute `/opt/android-shared/` link warnings came from a repository-relative checker; the referenced guide paths were unchanged. Contrast calculations gave status edges **3.10–12.58:1** and outlines **4.71–6.32:1**; these do not establish rendered/spoken accessibility.

Release configuration source implements shared reservations, isolated debug/production signing, architecture outputs and retained receipts/mappings. The [settings observation](evidence/repository-review-20261114.md) distinguishes observed controls from unapplied proposals. Gitleaks was pinned but not executed during that source batch. Exact shipping-graph notice reconciliation remains pending.

### Signing and cache setup

On 2026-11-14 the user reported creating separate production/debug PKCS12 keys, entering the required environment secrets/fingerprints and completing an off-device backup. Local source/copy files were byte-compared without printing/hashing private keys; files are mode 0600 and private stores 0700. API checks confirmed secret/variable names, not values or key/password agreement. Both environment branch rules were corrected to verified default branch `Final`; reviewers were not added. [Build](build.md#prepare-signing-once) owns repeatable setup. Production signing/update continuity remains unverified.

Code-4 build saved a basic-cache entry after a script change; its isolated signing job restored that exact key. Earlier changed-script runs each missed their keys and saved entries. This proves same-key transfer, not a repeat full-build hit or configuration-cache performance. JDK 21 came from the runner tool cache; SDK metadata fetching did not imply package installation.

### VS Code workflow inputs

All 14 inputs across eight workflows gained exact values/meanings/defaults or text/numeric format hints; parsed comparison confirmed descriptions were the only workflow fields changed in that batch. Policy checks retain input/default and cleanup/opt-in safeguards. GitHub Actions extension 0.32.3 ignored descriptions, so its local node/web bundles were backed up and patched: nine choice/boolean pickers show described rows with marked defaults; five text/numeric prompts retain styled hints, and Escape cancels dispatch. The user confirmed initial hints were visible; final picker interaction remains unconfirmed. Syntax, inverse replacement and installed/backup hashes passed; extension updates can replace the customization. Evidence: local-only `../local/verification/runs/current/vscode-workflow-hints-0.32.3/`. The standalone workstation guide is `/mnt/Common/IDE Related/VS Code GitHub Actions - Hints and Pickers.md`.

## User-reported completion — 2026-11-13

The user marked standard/demo builds, release packaging, selected hosted dependency/cache/security/emulator/cleanup checks, demo startup/refresh with standard-data preservation, and Arabic search/highlights/sorting/grouping/saved filters/Home browsing complete. No fresh per-case evidence accompanied that report. Independently inspected later runs are recorded above; production update continuity, benchmarks and other functional checks are not established by the report.

## Review preview delivery — 2026-11-13

Historical local delivery: **standard preview 0.0.31/code 31**, `com.aqaqeer.app.preview.debug`, Huawei PPA-LX2/API 29, serial `CNXNU21315200207`, fresh UID **10503**. Offline assembly had no compiler/Gradle warnings; signer and installed bytes matched. Counter advanced **30→31 once**. No launch/test/lint accompanied delivery. It predates later source. APK, source/signing/install/schema receipts and original compile failure/retry logs remain under local-only `../local/verification/runs/current/delivery-20261113-review/`.

## Restore process-death extension — 2026-11-11

Historical **22/22 semantic scenarios**, **22 verified SIGKILL deaths / 44 cold starts** passed: nine checkpoints × empty/configured targets, two controls and two repeated-death cases. Staging/replacement/activation preserved data/history/pending/uncertain work and identity, revoked admin and paused publication; cold verifiers observed no extra protected writes/transport calls. No live credentials/network or version advance. SIGKILL does not establish OS low-memory termination or power loss. Reconciled results, cleanup, source and artifact receipts: local-only `../local/verification/runs/history/restore-death-20261111T111227384466Z/`.

## Recovery extension — 2026-11-12

| Campaign | Observed result / limits | Local evidence under `../local/verification/runs/history/` |
| --- | --- | --- |
| Native replacement SQL | **4 cases, 4 deaths, 8 cold starts**; native markers proved open transaction/callback. Rollback left no backup_cells residue. | `restore-death-20261112T041246317346Z/results.json` |
| Export | **3 cases, 2 deaths, 6 cold starts**: control, copy death, after-close death. Partial output failed authentication; closed output authenticated without false success. | `restore-death-20261112T044141746048Z/results.json` |
| Simulated maintenance | **6 passes** through before-active-switch; no duplicate upload/write. PREPARING retry, post-active verification and logical-cleanup interruption remain unfinished. | `restore-death-20261112T044945651757Z/results.json`; `acceptance-20261112T034315Z/stopped-checkpoint.json` |

Host termination/ownership and fresh-process verifiers establish those historical passes. Simulated state is not actual token revocation. Logical cleanup retains the encrypted database; stopped profile cleanup is unconfirmed although package absence was independently confirmed. Missing instrumentation is neither an app failure nor a recovery pass.

## Focused backup/resource/PDF evidence — 2026-11-12

**Seven methods passed**: history-heavy round trips, controlled pressure/retry, three-preset PDF export and four ordinary-I/O regressions. Six initial fixture failures and two interrupted attempts remain retained. Reconciliation and final-artifact provenance: local-only `../local/verification/runs/history/acceptance-20261112T034315Z/standard-reconciled.json` and `final-apks/artifacts.json` beneath that campaign directory.

Envelopes were **21,039,046 / 56,093,446 bytes**, preserving synthetic history; sampled peak PSS **158,081 / 191,253 KiB** on a 384 MiB heap. Headroom **50,318,304 bytes** produced RESOURCE_LIMIT without changing data/work/identity/authorization/reminders; observed release increased headroom to **399,039,720 bytes** and retry passed. The OS low-memory flag remained false. Original measurements: `standard/roundtrips.json` and `standard-pressure-runtime-gc-retry/pressure.json` under that campaign. No universal memory bound or OS-termination claim follows.

Historical Arabic PDF raster/text checks preserved shaping, reference tokens, continuation and metadata; receipts are `standard-retry/pdf-review.json` and `pdf-text-review.json` in that directory. They predate current fields/A5 design. Current selected PDF assertions passed in workflow 07; complete preset/layout and human print acceptance remain separate.

## Creation/sharing baseline — 2026-11-10, preview 0.0.7

Historical **123 JVM passes/flavor; 75 standard device passes + one credential-dependent skip; one live Telegram case passed**. Build/unit/lint had zero errors/warnings. Two isolated profiles used one physical phone. Owned messages/pin/staged credentials were removed. This predates backup/restore and current source. Original build/summary/device/live logs: local-only `../local/verification/evidence/create-share/`.

## Shared workstation paths — 2026-11-14

`/opt/android-shared/AGENTS.md` and `/opt/android-shared/README.md` own machine paths. Five projects use pinned wrappers, the common Gradle home, JDK 21 and registered future work directories; helpers keep portable CI defaults. Aqaqeer's Gradle 9.8.2 distribution was downloaded with authorization and SHA-256 verified without invoking Gradle.

Authorized cache consolidation verified 4,977 dependency files and four distribution trees against active copies, then removed redundant/regeneratable data (97,856 readable files, 11,478,857,070 bytes). Resolver metadata was not merged. Private keys/configuration/original APK/evidence were preserved under `maintenance/organization-20261114/retained-consolidation/preserved`; its administrator receipt owns remaining old-account daemon cleanup status. `.idea`, existing build outputs, exports and counters were preserved. These were source/storage operations, not local build/sync/runtime verification; removed regeneratable caches are not available for byte-for-byte rollback.

## Evidence custody and remaining limits

Original passes, failures/interruption records and unique artifacts remain in the manifest; this documentation cleanup deletes none. [Operations](operations.md#verification-tools) explains lookup, and [plan](plan.md) keeps pending work. Physical/live campaigns and local lint remain stopped. Production signing/update continuity, unselected recovery/functional cases, default memory eligibility and runtime portability remain unverified. Extra Telegram-resource campaigns, broader devices/providers and human visual/print/spoken TalkBack acceptance are excluded from required delivery, with outcomes unverified. OS low-memory termination, power loss and benchmark improvement are not established.
