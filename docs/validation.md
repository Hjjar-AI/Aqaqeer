# Validation

[Requirements](requirements.md) define acceptance; [plan](plan.md) owns remaining work. Source review, compilation, runtime and human acceptance are separate. The evidence manifest (local-only: `../local/verification/manifest.json`) retains provenance and original results; ignored evidence may be absent in fresh checkouts. Documentation cleanup deletes no raw evidence, credentials, keys, counters or artifacts.

## Repository/release source maintenance — 2026-11-14

Implemented source/configuration changes for the selected repository/release plan. Wrapper/shared-counter integration, default-branch distributed builds, isolated permanent debug signing, three selected production APK outputs, retained mappings/receipts, four new manual workflows, policy checks, portable prerequisite discovery, notices/templates/documentation and the internal publishing/KDF/bitmap changes are source work only. Deferred test coverage/inventory review was not performed.

The [public settings summary](evidence/repository-review-20261114.md) distinguishes observed GitHub controls from proposed unapplied settings. Gitleaks 8.30.1 Linux x86_64 was pinned from its official release asset digest; no scanner binary was downloaded/executed. Cached original runtime-family license/NOTICE texts were preserved and deduplicated; exact resolved shipping-graph reconciliation remains pending.

Source verification passed: source-only repository integrity (240 text files and 213 local links, zero problems), policy across 15 workflows (zero problems), AST parsing of 17 changed Python files, syntax checks of 63 workflow shell bodies plus the POSIX wrapper/actionlint installer, Windows CRLF preservation and scoped whitespace. These are source checks, not compilation, tests or lint. No Gradle/build, compilation, packaging, counter reservation, key creation/signing, tests, lint, scanner run, workflow dispatch, publication, cleanup execution, device command or remote-setting mutation accompanied these edits. Earlier results below do not validate this source revision.

## User-reported completion — 2026-11-13

The user marked current standard/demo builds and the listed release-packaging check, hosted dependency resolution/cache reuse/security scans/selected emulator checks/workflow-cleanup confirmation, fresh/recognized/empty-demo startup with standard-data preservation, and Arabic search/highlights/sorting/grouping/saved filters/section/latest card layout **done**. These items are closed as user-reported completion. No new source/artifact identities, run IDs or per-case results accompanied the report; the assistant did not rerun them. Production signing/update continuity, unrelated functional checks and benchmark results are not established by that report.

<a id="design-critique-follow-up--2026-11-13"></a>
<a id="demo-content-and-flavor-selection--2026-11-13"></a>
<a id="sharingadmin-review--2026-11-13"></a>
<a id="verified-review-and-initial-contract--2026-11-13"></a>

## Source checks

Release guide (2026-11-13): expanded the canonical build document with an opening compact summary and a complete command-line/local/hosted release walkthrough. It explains optional Android Studio, one-time key creation and its prompts, public certificate versus APK hashes, coordinated version codes, workflow 06/08/10/11 choices, protected environment setup, outputs, distribution/update identity and troubleshooting. README and the general Actions tutorial link to the same canonical guide. Commands/environment names/defaults were compared with current production wrappers, signing/reservation helpers and workflows; changed-document local link/anchor integrity and whitespace checks passed. No command examples were executed, and no keys/counters/secrets, SDK/cache contents or remote state were changed by this documentation work.

Historical publisher configuration (2026-11-13): production workflow 11 passed source policy across 11 workflows, five Python AST parses, seven inline shell syntax checks, links and whitespace; earlier debug workflow 10 passed policy across 10 workflows, Python parsing, six inline Bash checks, links and whitespace. Neither checkpoint executed signing, publishing, installed updates or hosted artifact transfer. Those historical configurations are superseded by the current source maintenance described above. User-visible changes belong in the [changelog](../CHANGELOG.md).

Demo catalog expansion (2026-11-13): the demo-only seed now has 40 stable-key entries across 24 substances and 12 populated categories plus protected Uncategorized, including eight side-effect/supportive-care categories. Source inventory confirmed 40 unique record keys, references to all 12 categories and complete name/strength coverage in the [reference table](demo-data.md); new ingredient/strength/form combinations were reviewed against DailyMed labels. Seed marker 3 uses the existing guarded transactional refresh; its identity/key/work-preservation guards remain unchanged. No app version, schema or standard seed was changed. Source/call-site review, scoped whitespace and documentation link integrity checks cover this work; compilation, initialization/refresh execution and rendered acceptance remain unperformed. Earlier demo runtime reports cover the previous dataset only.

Home presentation follow-up (2026-11-13): source/call-site review covered adjacent-section horizontal gestures and busy/modal/bulk guards, the 320 dp drawer cap with initially expanded categories, category grouping enabled by default, the saved two-column Settings toggle (default on), removal of category/form search matching and the Home quick-status row, reversed search/filter order, removal of UNKNOWN from standard/demo filter choices and restored selections, 12 dp inline accent dots from actual theme primary colors with the corrected أرجواني label, and faint diagonal OUT-card backgrounds with opaque content. Existing explicit grouping/card-style preferences remain authoritative. Android string XML parsing, 27 local links/anchors across the updated product/implementation/plan/guide/branding documents, and scoped whitespace checks passed. The validation document has 18 pre-existing absent ignored evidence targets, confirmed unchanged against HEAD; this follow-up introduced no broken links. No compilation, packaging, tests, lint, device run or rendered visual acceptance was performed for this follow-up; earlier completion reports do not validate it.

Production review covered sharing/merge/PIN guards, database ownership, current initial file/data contracts, demo initialization, saved navigation/drafts, search/grouping and presentation. Mechanisms and decisions are maintained in architecture/requirements rather than a completed-review ledger. Reference provenance remains in [demo data](demo-data.md), [fonts](../assets/fonts/README.md), the source receipt (local-only: `../local/verification/runs/current/review-20261113-source/receipt.json`) and demo receipt (local-only: `../local/verification/runs/current/demo-flavors-20261113/receipt.json`).

Local source/resource/whitespace/link checks passed for those changes. Status-edge contrast against ordinary card surfaces was calculated at **3.10–12.58:1**, outline contrast **4.71–6.32:1** across base/crimson light/dark and AMOLED. These calculations and automated semantics do not establish rendered or spoken accessibility. Current user-reported browsing completion is recorded above; excluded human/broader-device checks are limits, not delivery prerequisites.

## Review preview delivery — 2026-11-13

Last independently recorded local delivery: **standard preview 0.0.31/code 31**, `com.aqaqeer.app.preview.debug`, Huawei PPA-LX2/API 29, serial `CNXNU21315200207`, fresh UID **10503**. Offline assembly emitted no compiler/Gradle warnings; signer and installed bytes matched. Wrapper reservation was **30→31 once**. No app launch, tests or lint accompanied delivery. This artifact predates later production changes; subsequent build completion is user-reported.

APK (local-only: `../local/verification/runs/current/delivery-20261113-review/Aqaqeer-preview-0.0.31.apk`), artifact/signer (local-only: `../local/verification/runs/current/delivery-20261113-review/artifact.json`), installation (local-only: `../local/verification/runs/current/delivery-20261113-review/installation.json`), source (local-only: `../local/verification/runs/current/delivery-20261113-review/source-before-packaging.json`), schema receipt (local-only: `../local/verification/runs/current/delivery-20261113-review/schema-review.json`), original compile failure (local-only: `../local/verification/runs/current/delivery-20261113-review/compile.log`), retry (local-only: `../local/verification/runs/current/delivery-20261113-review/compile-retry.log`), final assembly (local-only: `../local/verification/runs/current/delivery-20261113-review/build.log`). Original failures remain retained; the generated export is not database-runtime acceptance.

<a id="ci-downloads-without-a-payment-method--2026-11-13"></a>
<a id="ci-tests-and-compilation--2026-11-13"></a>

## CI quality, security and diagnostics — 2026-11-13

Current commands/policy are in [build](build.md#manual-quality-and-security-workflows) and [CI controls](ci-free-tier.md). User-reported completion above supersedes pending labels for the listed CI checks. Essential original failure evidence remains:

| Boundary | Retained evidence and result |
| --- | --- |
| Dependency scan, source `9d669f7` | **341 resolved coordinates, eight flagged build-tool/processor versions, no suppressions**; original scan failed. Results (local-only: `../local/verification/runs/current/ci-remediation-20261113/dependency-run-43416996490/results.md`), remediation receipt (local-only: `../local/verification/runs/current/ci-remediation-20261113/receipt.json`). |
| Debug build, source `1542bf5` | Both Kotlin flavors failed on a missed restore callback rename, subsequently corrected. Original run log (local-only: `../local/verification/runs/current/ci-remediation-20261113/build-run-43419825227.log`). |
| Manual cleanup, source `f123ce9` | Nine removals confirmed before an immediate absence check failed; the disputed tenth run later returned 404. Bounded GET-only confirmation replaced that check. Original log (local-only: `../local/verification/runs/current/ci-remediation-20261113/cleanup-run-43418932890.log`). |
| Local production lint | Exit 1 after **6m 37s**, internal `AsyncExecutionService.getService must not return null`; copied older reports were stale, not passes. Owned wrapper/daemon were absent at stop; counter unchanged. Original log (local-only: `../local/verification/runs/current/ci-quality-20261113T062213Z/production-lint.log`), corrected receipt (local-only: `../local/verification/runs/current/ci-quality-20261113T062213Z/receipt.json`). No local retry is recorded. |

Run links/logs/artifacts on GitHub may expire under the selected cleanup policy. Locally retained originals and the manifest remain authoritative for those historical failures. Later user-reported success does not turn them into passes or establish production signing/update continuity.

## Restore process-death extension — 2026-11-11

**22/22 semantic scenarios passed**, **22 verified SIGKILL deaths / 44 cold starts**: nine checkpoints × empty/configured targets, two controls and two repeated-death cases. Encrypted staging/replacement/activation preserved data/history/pending/uncertain work and local identity, revoked admin and paused publication; cold verifiers observed no extra protected writes/transport calls. No live credentials/network or version advance. SIGKILL is not OS low-memory termination or power loss.

Reconciled results (local-only: `../local/verification/runs/history/restore-death-20261111T111227384466Z/reconciled-results.json`), results/cleanup (local-only: `../local/verification/runs/history/restore-death-20261111T111227384466Z/results.json`), source (local-only: `../local/verification/runs/history/restore-death-20261111T111227384466Z/source-sha256.json`), artifacts (local-only: `../local/verification/runs/history/restore-death-20261111T111227384466Z/artifacts.json`).

## Recovery extension — 2026-11-12

| Campaign | Observed result / limits |
| --- | --- |
| Native replacement SQL | **4 cases, 4 deaths, 8 cold starts**; native markers proved an open transaction/callback. Rollback preserved state with no backup_cells residue. Results (local-only: `../local/verification/runs/history/restore-death-20261112T041246317346Z/results.json`). |
| Export | **3 cases, 2 deaths, 6 cold starts**: control, death during copy, death after close. Partial output failed authentication; closed output authenticated without false local success. Results (local-only: `../local/verification/runs/history/restore-death-20261112T044141746048Z/results.json`). |
| Simulated maintenance | **6 passed cases** through before-active-switch; no duplicate upload/write. PREPARING retry, post-active verifier and logical-cleanup interruption remain incomplete. Original results (local-only: `../local/verification/runs/history/restore-death-20261112T044945651757Z/results.json`), stop checkpoint (local-only: `../local/verification/runs/history/acceptance-20261112T034315Z/stopped-checkpoint.json`). |

Host ownership/termination observations and fresh-process verifiers establish those passes. Simulated state is not actual token revocation. Logical staging cleanup retains the encrypted database; stopped profile cleanup is unconfirmed, while package absence was independently confirmed. Missing instrumentation is neither an app failure nor a recovery pass.

## Focused backup/resource/PDF evidence — 2026-11-12

**Seven focused methods passed**: history-heavy round trips, controlled pressure/retry, three-preset PDF export and four ordinary-I/O regressions. Six original fixture failures and two interrupted host attempts remain retained. Reconciliation (local-only: `../local/verification/runs/history/acceptance-20261112T034315Z/standard-reconciled.json`), source/artifacts (local-only: `../local/verification/runs/history/acceptance-20261112T034315Z/final-apks/artifacts.json`).

History-heavy envelopes were **21,039,046 / 56,093,446 bytes**, with all synthetic history retained and sampled peak PSS **158,081 / 191,253 KiB** on a 384 MiB heap (samples (local-only: `../local/verification/runs/history/acceptance-20261112T034315Z/standard/roundtrips.json`)). Controlled headroom of **50,318,304 bytes** produced RESOURCE_LIMIT while preserving data/work/identity/authorization/reminders; observed allocation release increased headroom to **399,039,720 bytes** and retry succeeded (original pressure/retry (local-only: `../local/verification/runs/history/acceptance-20261112T034315Z/standard-pressure-runtime-gc-retry/pressure.json`)). The OS low-memory flag stayed false; this is no universal memory bound or OS-termination result.

Synthetic Arabic PDF checks reconciled complete names/reference tokens, shaping, continuation and metadata (raster review (local-only: `../local/verification/runs/history/acceptance-20261112T034315Z/standard-retry/pdf-review.json`), text completeness (local-only: `../local/verification/runs/history/acceptance-20261112T034315Z/standard-retry/pdf-text-review.json`)). Outputs predate current data fields and the planned redesign; no user/print acceptance is implied.

## Creation/sharing baseline — 2026-11-10, preview 0.0.7

Historical baseline: **123 JVM passes/flavor; 75 standard device passes + one credential-dependent skip; one separate live Telegram case passed**. Final build/unit/lint had zero errors/warnings. Two isolated profiles used **one physical phone**. Owned messages/pin/staged credentials were removed. This predates backup/restore and current source.

Build (local-only: `../local/verification/evidence/create-share/build-checks.log`), summary (local-only: `../local/verification/evidence/create-share/summary.json`), device (local-only: `../local/verification/evidence/create-share/device.log`), live (local-only: `../local/verification/evidence/create-share/telegram.log`).

<a id="arabic-search-and-optional-grouping--2026-11-12"></a>
<a id="phone-feedback-corrections--2026-11-12"></a>
<a id="ui-lint-and-preview-delivery--2026-11-12"></a>
<a id="historical-functional-results--2026-11-1112"></a>

## Evidence custody and remaining limits

Superseded preview-by-preview narration, completed-review lists and intermediate CI/source-check diaries are removed. Original per-case results, valid passes, failure/interruption records, public hashes and provenance remain in the manifest; [operations](operations.md#verification-tools) explains evidence lookup. No evidence files were removed during this cleanup. Reusable storage/tooling rules remain in build/operations.

Physical/live campaigns and local lint remain stopped. Retained functional/recovery checks and production signing/update continuity remain open in the plan. Extra Telegram-resource campaigns, additional Android/device/provider coverage and human visual/print/spoken TalkBack acceptance are unverified but excluded from required delivery. OS low-memory termination and power-loss behavior are not established. User-reported completion does not supply independent current-source logs or benchmark results.
