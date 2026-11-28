# Verification scope

[Validation](validation.md) owns executed evidence; [plan](plan.md) owns remaining checks. A source inventory or selected class/method is not proof of execution or measured branch coverage.

## Evidence by revision and environment

Use [validation](validation.md) for run outcomes and [build](build.md#hosted-emulator-screenshots-and-recordings) for current workflow selections and commands. The [backup/restore audit](validation.md#backuprestore-requirement-to-evidence-audit--2026-11-22) maps retained cases to specific acceptance boundaries.

## Android 6 preview

The JVM comparison uses independent wire keys, including an exact Unicode passphrase, against both PBKDF2 implementations. The Android provider already reads the public vectors and rejects a wrong password. The shared fixture key derivation follows the application's API floor. The splash assertions retain their raster comparisons using an API 23 device screenshot cropped to the measured node bounds.

Workflow 07 `android6_checks=true` (default) selects 13 Demo cases on API 23 (11 methods) and 13 Standard cases (11 methods) across the initial API 23, API 35 exchange, and final API 23 exchange legs. The 26 selections span 20 distinct methods. The PIN case has three instrumentation phases (arm, fresh process, and reboot), for 28 instrumentation invocations when the selection completes. The existing API 29/35 profiles still select 289 cases across 267 methods when both are enabled.

The focused profile checks native splash, theme and Arabic text, search and duplicates, timezone city/ID, leap-day and early-date handling, keyboard, PDF/XLSX, active network changes, Keystore/SQLCipher reopen, wrong passwords and same-API backup. The Standard exchange transfers freshly exported synthetic archives containing PIN verifiers in both directions, with byte hashes, correct/wrong PIN checks and restored medication identity. The PIN boundary records process absence, changed boot ID and unchanged failure count. Physical device, low-memory, visual acceptance and live Telegram evidence remain separate. [Exact hosted inputs](build.md#hosted-emulator-screenshots-and-recordings)

## Pre-production risk regressions — 2026-11-27

Five added Standard Android methods cover both outcomes of an uncertain publication in a restored archive, separately exercise a higher minimum app version and unsupported schema with successful backup export and restore, and verify an over-limit PDF fails before touching an existing document. The restore fixtures exercise encrypted backups and offline authenticated read-back, compare exact pending and history changes, and assert upload and write counts before review and after separate publication. The PDF fixture measures forced-line spacing to establish the 1,000-page precondition and checks the real document provider's previous bytes.

After an authorized workflow **03 - Checks - Tests and compilation** run with `scope=all` and `coverage=false`, select **07 - Verify - Emulator with visual evidence** on the default branch containing the tests. Set `android6_checks=false` for this targeted retry, then set `demo_checks=false`, `standard_functional=true` and this exact `retry_methods` value. This selects four storage methods and one Standard report method on API 35. The earlier Home and branding retry remains separately selectable below.

```text
BackupRestoreInstrumentedTest#restoreAcknowledgesAnAcceptedUncertainPinPublicationWithoutReplayingIt,BackupRestoreInstrumentedTest#restoreArchivesAnUnacceptedAttemptAndDropsItsPendingPinBeforeSeparatePublication,BackupReleaseRegressionInstrumentedTest#higherRequiredAppVersionBlocksEditsAndPublicationButKeepsBackupExport,BackupReleaseRegressionInstrumentedTest#unsupportedRequiredSchemaBlocksEditsAndPublicationButKeepsBackupExport,ReportExportsInstrumentedTest#pdfOverPageCapLeavesTheUserSelectedDestinationUntouched
```

An eligible release artifact pair and an owned emulator are required for the separate [installed-update rehearsal](build.md#installed-update-rehearsal).

## Repository, Home and branding regressions — 2026-11-27

One JVM method checks that rebinding the search index after a stock-only edit updates Reader results, section markers, and stock sort order. Three offline Python methods cover Git ignoring backups, redacted rejection after force-add, and the full versus source-only public fixture contract. Four existing Android methods now check query and cursor restoration through activity recreation, the default and saved accent mappings with rendered tabs and artwork, the PDF and Excel stock palettes, and both generated logo drawables. The existing Android selectors and counts are unchanged.

For compilation, the JVM suites, and the offline helpers, run **03 - Checks - Tests and compilation** with `scope=all` and `coverage=false`. For the targeted Android batch, use **07 - Verify - Emulator with visual evidence** on the default branch containing the changes. Set `android6_checks=false`, `demo_checks=true` and `standard_functional=true`. The two Demo methods run on both API 29 and 35, and the three Standard methods run on API 35.

`demo_retry_methods`

```text
HomeUiTest#searchIsReachableDoesNotAutofocusAndClearRestoresBrowsing,LaunchSplashUiTest#readyDestinationTakesOverDuringSplashWithCurrentAnimationSetting
```

`retry_methods`

```text
SectionThemeUiTest#retainedAccentKeysAndSectionIndicatorUseTheNewPalette,ReportExportsInstrumentedTest#sectionColumnsRetainDistinctStatusesColorsAndUniqueHeaders,LaunchSplashUiTest#readyDestinationTakesOverDuringSplashWithCurrentAnimationSetting
```

The illustration palette retry passed on Standard API 35 at `f08bebb`. Workflow **07 - Verify - Emulator with visual evidence** ran with `demo_checks=false`, `standard_functional=true`, blank `demo_retry_methods`, and `retry_methods=SectionThemeUiTest#retainedAccentKeysAndSectionIndicatorUseTheNewPalette`. [Hosted evidence](validation.md#repository-home-and-branding-follow-up--2026-11-27)

## Focused Android follow-up — 2026-11-27

Workflow 07 selects 11 new Standard methods and retains the expanded backup reminder and splash assertions. The provider fixture delivers part of a real CSV stream before failing. The Home cases exercise an active IME at 2× RTL text and a deliberately retained stale projection. Backup coverage follows the live PIN across restore review and a separate publication, and challenges reuse of a maintenance receipt from another operation. Report picker results pass through the actual activity-result contract; the test inspects the new A5 PDF and workbook for the selected rows.

The Reader cases use the real WorkManager scheduler with an offline snapshot transport. The emulator runner records a disconnected network, Battery Saver, the admitted attempt and killed PID, and the boot IDs around reboot. The retry test allows the normal five-to-seven-minute delay. Splash assertions check Compose frames and retain separate cold and warm native launch recordings with animations off and at normal scale. The recorded handoffs reached Demo Home and Standard setup. The verification record describes the native frame review.

The focused batch has scoped passes for 32 Standard API 35 invocations and four Demo splash invocations across APIs 29 and 35. The final retry passed the Home keyboard section-switching and grouped stale-selection cases.

Workflow 08 with `expected_certificate_sha256` blank passed the merged WorkManager manifest, runtime notice inspection and release startup on the published production revision. [Verification status](validation.md#focused-android-follow-up--2026-11-27)

## Reader refresh scheduling — 2026-11-25

Twelve JVM cases exercise the Reader coordinator with in-memory preferences, a mocked WorkManager boundary and controlled transport. They cover foreground reopens before the deadline, rescheduling after unchanged data, the manual cooldown after coordinator recreation, offline and unvalidated networks, the single short retry, persisted Telegram 429 delays, shared foreground/worker requests, missing pins, revoked credentials, loss of Reader eligibility and required updates. They also verify that a cached 429 leaves the last request time alone and a verified check from another path replaces a pending retry. Three JVM transport cases check admission ordering and confirm that rejected admission and a cached Telegram cooldown send zero requests. Two JVM presentation cases verify that a newer verified check clears a foreground refresh error, unrelated state retains it and a different pharmacy never displays it. Three Python cases cover packaged permission policy with synthetic manifests. One Android case commits a verified snapshot between browse sessions and checks marker retention in the two subsequent sessions.

Workflow 03 passed both JVM suites for this batch. Workflow 07 passed the fetched-marker case and the focused Android follow-up established real WorkManager execution under the tested emulator conditions. [Hosted results](validation.md#focused-android-follow-up--2026-11-27)

## Report accordion, advanced export and fixed A5 — 2026-11-25

Two new JVM methods cover custom field ordering with both availability levels and the three opening summary groups. Existing JVM cases now cover serialization of the new choices and stale capture when the user changes a current presentation option. Five new Android methods cover forward and backward accordion navigation through Preview, shared step header disclosure, reorder and reset with busy controls, ordered workbook headers and status values, and ascending and descending expiry order with undated items last. Updated Android assertions cover A5 PDF dimensions and opening margins, A5 XLSX print settings, preservation of advanced preferences, the blue theme name, and the distinction between the Telegram progress heading and its disabled action.

The scoped workflow 07 runs used these selectors for the report batch and the Arabic PDF continuation case.

```text
ReportExportsInstrumentedTest#workbookHasSummaryFilterableRowsRtlFrozenHeadersAndPrintSettings,ReportExportsInstrumentedTest#workbookPreservesCustomOrderWithOverallAndSectionStatusesTogether,ReportExportsInstrumentedTest#expirySortChangesDirectionAndKeepsUndatedLastAndScientificTiesStable,ReportExportsInstrumentedTest#pdfPaperOrientationAndDensityOptionsRemainRenderable,ReportPreferencesInstrumentedTest#everyPresentationOptionAndHintTogglePersistForANewReader,ReportPreferencesInstrumentedTest#invalidScientificFieldSelectionRejectsWithoutOverwritingSavedDefaults,ReportControlsUiTest#layoutChoicesChangeOnlyPresentationAndBusyStateDisablesEdits,ReportControlsUiTest#combinedAvailabilityAndFieldReorderingRespectRequiredFieldsAndBusyState,ReportControlsUiTest#sharedStepHeaderDisclosesProgressWithoutChangingTheCurrentStep,ViewModelInstrumentedTest#reportAccordionRevealsOnlyTheCurrentStepActions,ViewModelInstrumentedTest#adminChecklistOpensSetupGuideAndBackReturnsToTools,SectionThemeUiTest#retainedAccentKeysAndSectionIndicatorUseTheNewPalette,CsvReportsInstrumentedTest#arabicPdfRendersMultiplePagesAndHasNoBlankContinuationPages
```

The PDF checks cover page dimensions, visible opening content and margin placement. Exact font-size appearance, Arabic shaping and printed pagination still need visual review.

## Focused risk regressions — 2026-11-25

One JVM method covers authorization lost after the picker result queues a report writer. Four standard Android methods cover a concurrent edit after backup capture, authorization revoked during upload followed by explicit retry, restore-picker result and cancellation at large RTL text, and splash handoff. The picker test controls the system contract result through the activity result registry. The splash method exercises the static handoff with hosted animations disabled and checks the pulse with animations enabled at normal speed. Native launch appearance remains a separate check.

For these new Android methods, use workflow 07 with `demo_checks=false`, `standard_functional=true` and this exact `retry_methods` value on the default branch containing the changes.

```text
BackupReleaseRegressionInstrumentedTest#editDuringDestinationOpenDoesNotMixCapturedCatalogHistoryOrPendingWork,PharmacySharingInstrumentedTest#authorizationLostDuringUploadRetainsAttemptAndRetryWritesOnce,ViewModelInstrumentedTest#restorePickerRequiresExactPharmacyAndAcknowledgmentAtLargeRtlText,LaunchSplashUiTest#readyDestinationTakesOverDuringSplashWithCurrentAnimationSetting
```

## Admin tools, accents and section switching — 2026-11-25

Four standard Android methods cover Reader, shared, maintenance, update and restore actions in the admin hub, the checklist’s setup destination and Back, accent key compatibility and the rendered fill and underline, and Add/search bounds on the first selected frame and after idle in both bar placements. The palette assertions cover Copper and Cobalt values and Green/Rose luminance separation. Visual motion, keyboard interaction during section switching and human color-vision acceptance remain separate checks.

Use workflow 07 on the default branch containing the changes with `demo_checks=false`, `standard_functional=true` and this exact `retry_methods` value.

```text
HomeBarPlacementUiTest#sectionSwitchKeepsAdminAddAndSearchBoundsStableInBothPlacements,SectionThemeUiTest#retainedAccentKeysAndSectionIndicatorUseTheNewPalette,ViewModelInstrumentedTest#adminHubKeepsOneLeaveActionAndShowsOnlyToolsPermittedByState,ViewModelInstrumentedTest#adminChecklistOpensSetupGuideAndBackReturnsToTools
```

## Hosted completion batch — 2026-11-24

Added one JVM merge case and 12 Android backup/restore, maintenance and database lease/reset cases. Follow-up BackupScreen coverage addressed Reader access, Unicode and busy export. The historical workflow 07 selection was 223 Standard methods and 16 Demo invocations per API. Workflow 16 counts semantic scenarios separately from phase invocations. [Executed evidence](validation.md#hosted-completion-batch--2026-11-24)

## Production-readiness test extension — 2026-11-24

At `25c7ee2`, added **35 JVM methods** for Home projection, refresh ownership, snapshot-read safety and merge handling, and **11 Android methods** for adaptive grid thresholds, card/badge states and overflow at larger text. These are authoring counts. The [dated checkpoint](validation.md#hosted-completion-batch--2026-11-24) records execution separately.

## Report and hint test batch — 2026-11-23

At `ddf0350`, added **39 JVM and 23 Android methods** covering report selection/snapshot guards, PDF and workbook output, writer/provider failures, Home hints and preferences. The report writer also gained a cleanup fix to preserve the original exception. [Original failures and retries](validation.md#user-run-report-workflows--2026-11-23)

## Earlier domain/demo authored batch — 2026-11-23

Added **47 JVM and seven Android methods** for the three-state availability contract, Demo isolation and exchange, real report output and PIN replacement/recovery. Standard HTTP coverage and Demo pre-connection refusal remain flavor-specific.

<a id="recoveryresource-extension"></a>

<a id="current-executed-jvm-coverage--2026-11-23"></a>
<a id="warning-fix-follow-up--hosted-results"></a>

## Earlier hosted coverage and retries — 2026-11-23

JVM coverage, flavor skips and Android compilation at `b49eb35`, and the subsequent warning, Home fixture and CSV retry results, are recorded by revision in [validation](validation.md#current-hosted-result-and-warning-review--2026-11-23). JVM coverage does not establish Android execution.

## Recovery evidence

See the [backup/restore audit](validation.md#backuprestore-requirement-to-evidence-audit--2026-11-22) for open boundaries and the [hosted checkpoint](validation.md#hosted-completion-batch--2026-11-24) for later maintenance and resource results. SIGKILL, controlled heap pressure, OS low-memory termination and power loss are distinct.

## Resume protocol

**Physical testing and local lint remain stopped.** Hosted configuration/results do not authorize local/device execution. Existing selected hosted emulator scope is documented in [build](build.md#manual-quality-and-security-workflows); no new broader-device or human-review campaign is required.

On an authorized resume, verify current constructor/result/cleanup contracts before fixture development, finish the authorized files before their first run, and reconcile fresh per-case results with original failures/interruption evidence. Inspect or modify test source only with explicit authorization. Retain source/artifact/device/run provenance; stale reports, missing instrumentation and interrupted hosts are not passes.

Physical helpers require the authorized serial and verified package/signer/install/run ownership. Use exact retries for unfinished cases; recheck ownership before delayed cleanup. See [operations](operations.md#verification-tools) for protected credentials and integration effects, and [build](build.md) for commands.

## Limits

User-reported completion is distinguished from independently inspected per-case evidence. Additional Android/device/provider and human visual/print/spoken TalkBack acceptance are excluded from delivery gates, while their outcomes remain unverified. Extra-resource token-revocation/second-destination campaigns are not awaited. Production signing/update continuity remains open. No new execution results follow from this document cleanup.
