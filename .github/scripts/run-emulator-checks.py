"""Selected offline checks on an ephemeral GitHub-hosted emulator only."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

from android_config import build_tools


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'ci-output/emulator'
SERIAL = 'emulator-5554'
PACKAGE = 'com.aqaqeer.app.demo.debug'
DEMO_CASES = (
    ('demo-pdf-reports', 'DemoParityInstrumentedTest#demoPdfExportsAllPresetsThroughRealProviderWithRenderableA5Pages', False),
    ('demo-excel-reports', 'DemoParityInstrumentedTest#demoExcelExportsEveryCapturedMedicationAndSummaryThroughRealProvider', False),
    ('other-section-settings', 'BottomPlacementSettingsUiTest#otherSectionHintsSwitchPersistsAcrossRecreationAndCanBeDisabled', False),
    ('read-only-details', 'ReadOnlyUiTest#medicationDetailsRemainReadOnlyAndNavigateBack', True),
    ('light-large-arabic', 'CiPresentationUiTest#arabicLargeTextThemeAndRecreationRemainUsable', True),
    ('dark-large-arabic', 'CiPresentationUiTest#arabicLargeTextThemeAndRecreationRemainUsable', True),
    ('amoled-large-arabic', 'CiPresentationUiTest#arabicLargeTextThemeAndRecreationRemainUsable', True),
    ('public-vectors', 'EnvelopeInstrumentedTest#androidProviderReadsAllPublicVectorsIncludingExactUnicodePassphrase', False),
    ('authentication-rejection', 'EnvelopeInstrumentedTest#androidAuthenticationFailureLeavesNoStagingFileOrPayload', False),
    ('bottom-layout-settings', 'BottomPlacementSettingsUiTest#independentSwitchesPersistAcrossActivityRecreationAndNewPreferenceReader', True),
)

STANDARD_CASES = (
    ('Reports-ReportExportsInstrumented-manySectionColumnsAndLongHeadingsKeepFullScopeInSummary', 'ReportExportsInstrumentedTest#manySectionColumnsAndLongHeadingsKeepFullScopeInSummary', False),
    ('Reports-ReportExportsInstrumented-publishedAndLocalSourcesNeverFallbackAndRejectForeignSelections', 'ReportExportsInstrumentedTest#publishedAndLocalSourcesNeverFallbackAndRejectForeignSelections', False),
    ('Reports-ReportExportsInstrumented-filteredSnapshotDrivesBothFormatsWithoutChangingCatalogOrPendingWork', 'ReportExportsInstrumentedTest#filteredSnapshotDrivesBothFormatsWithoutChangingCatalogOrPendingWork', False),
    ('Reports-ReportExportsInstrumented-workbookHasSummaryFilterableRowsRtlFrozenHeadersAndPrintSettings', 'ReportExportsInstrumentedTest#workbookHasSummaryFilterableRowsRtlFrozenHeadersAndPrintSettings', False),
    ('Reports-ReportExportsInstrumented-workbookContainsOnlySelectedFieldsAndPreservesFormulaLikeText', 'ReportExportsInstrumentedTest#workbookContainsOnlySelectedFieldsAndPreservesFormulaLikeText', False),
    ('Reports-ReportExportsInstrumented-longNotesPreserveEveryCharacterAcrossIdentifiedContinuationRows', 'ReportExportsInstrumentedTest#longNotesPreserveEveryCharacterAcrossIdentifiedContinuationRows', False),
    ('Reports-ReportExportsInstrumented-longScientificIdentityUsesExplicitMedicationReferencesWithoutTruncation', 'ReportExportsInstrumentedTest#longScientificIdentityUsesExplicitMedicationReferencesWithoutTruncation', False),
    ('Reports-ReportExportsInstrumented-sectionColumnsRetainDistinctStatusesColorsAndUniqueHeaders', 'ReportExportsInstrumentedTest#sectionColumnsRetainDistinctStatusesColorsAndUniqueHeaders', False),
    ('Reports-ReportExportsInstrumented-categoryGroupingPreservesOrdinaryRowsAndAlternatingStyles', 'ReportExportsInstrumentedTest#categoryGroupingPreservesOrdinaryRowsAndAlternatingStyles', False),
    ('Reports-ReportExportsInstrumented-emptyWorkbookHasAValidFilterTableAndZeroCount', 'ReportExportsInstrumentedTest#emptyWorkbookHasAValidFilterTableAndZeroCount', False),
    ('Reports-ReportExportsInstrumented-writerLeavesDestinationOpenAndPropagatesWriteFailures', 'ReportExportsInstrumentedTest#writerLeavesDestinationOpenAndPropagatesWriteFailures', False),
    ('Reports-ReportExportsInstrumented-cancelledWriterStopsBeforeSuccessAndNeverClosesProvider', 'ReportExportsInstrumentedTest#cancelledWriterStopsBeforeSuccessAndNeverClosesProvider', False),
    ('Reports-ReportExportsInstrumented-pdfPaperOrientationAndDensityOptionsRemainRenderable', 'ReportExportsInstrumentedTest#pdfPaperOrientationAndDensityOptionsRemainRenderable', False),
    ('Reports-ReportExportsInstrumented-compactPdfUsesNoMorePagesThanComfortableForTheSameRecords', 'ReportExportsInstrumentedTest#compactPdfUsesNoMorePagesThanComfortableForTheSameRecords', False),
    ('Reports-ReportExportsInstrumented-cancelledPdfRenderingPropagatesBeforeAnyDestinationIsOpened', 'ReportExportsInstrumentedTest#cancelledPdfRenderingPropagatesBeforeAnyDestinationIsOpened', False),
    ('Reports-ReportPreferencesInstrumented-everyPresentationOptionAndHintTogglePersistForANewReader', 'ReportPreferencesInstrumentedTest#everyPresentationOptionAndHintTogglePersistForANewReader', False),
    ('Reports-ReportPreferencesInstrumented-invalidScientificFieldSelectionRejectsWithoutOverwritingSavedDefaults', 'ReportPreferencesInstrumentedTest#invalidScientificFieldSelectionRejectsWithoutOverwritingSavedDefaults', False),
    ('Reports-ReportControlsUi-requiredScientificIdentityAndOptionalNotesRemainUsableAtLargeArabicText', 'ReportControlsUiTest#requiredScientificIdentityAndOptionalNotesRemainUsableAtLargeArabicText', False),
    ('Reports-ReportControlsUi-layoutChoicesChangeOnlyPresentationAndBusyStateDisablesEdits', 'ReportControlsUiTest#layoutChoicesChangeOnlyPresentationAndBusyStateDisablesEdits', False),
    ('Reports-ReportControlsUi-medicationPickerSearchSelectAllAndClearRetainExplicitSelectionMeaning', 'ReportControlsUiTest#medicationPickerSearchSelectAllAndClearRetainExplicitSelectionMeaning', False),
    ('Reports-HomeFixtureUi-narrowRtlRowsAllowLongCategoriesAndSectionChangeRemovesOutTip', 'HomeFixtureUiTest#narrowRtlRowsAllowLongCategoriesAndSectionChangeRemovesOutTip', False),
    ('Reports-HomeFixtureUi-hintsDefaultOffAndToggleAsPlainTextInBothCardLayouts', 'HomeFixtureUiTest#hintsDefaultOffAndToggleAsPlainTextInBothCardLayouts', False),
    ('Reports-HomeFixtureUi-expiryRecomputesAtPharmacyMidnightWithoutChangingAvailability', 'HomeFixtureUiTest#expiryRecomputesAtPharmacyMidnightWithoutChangingAvailability', False),
    ('Reports-HomeFixtureUi-futureCompatibilityNoticeKeepsCachedMedicationAndBrowsingAvailable', 'HomeFixtureUiTest#futureCompatibilityNoticeKeepsCachedMedicationAndBrowsingAvailable', False),
    ('CsvReports-contentProviderImportCommitsValidRowsAndRetainsFailedRowResults', 'CsvReportsInstrumentedTest#contentProviderImportCommitsValidRowsAndRetainsFailedRowResults', False),
    ('CsvReports-staleUpdateRollsBackAndTaxonomyRequiresExplicitConsent', 'CsvReportsInstrumentedTest#staleUpdateRollsBackAndTaxonomyRequiresExplicitConsent', False),
    ('CsvReports-cancellationAfterChunkCallbackRetainsAllCommittedRows', 'CsvReportsInstrumentedTest#cancellationAfterChunkCallbackRetainsAllCommittedRows', False),
    ('CsvReports-reportsDefaultToPublishedAndOutIncludesMissingAvailability', 'CsvReportsInstrumentedTest#reportsDefaultToPublishedAndOutIncludesMissingAvailability', False),
    ('CsvReports-draftsRequireOptInAndExpiryPresetExcludesExpired', 'CsvReportsInstrumentedTest#draftsRequireOptInAndExpiryPresetExcludesExpired', False),
    ('CsvReports-arabicPdfRendersMultiplePagesAndHasNoBlankContinuationPages', 'CsvReportsInstrumentedTest#arabicPdfRendersMultiplePagesAndHasNoBlankContinuationPages', False),
    ('CsvReports-authorizationLossBlocksExternalOutputAndImportBeforeFileAccess', 'CsvReportsInstrumentedTest#authorizationLossBlocksExternalOutputAndImportBeforeFileAccess', False),
    ('CsvReports-fiftyOneRowsReportCommittedChunksInOrder', 'CsvReportsInstrumentedTest#fiftyOneRowsReportCommittedChunksInOrder', False),
    ('CsvReports-cancellationAfterFirstFiftyRowsDoesNotStartSecondChunk', 'CsvReportsInstrumentedTest#cancellationAfterFirstFiftyRowsDoesNotStartSecondChunk', False),
    ('CsvReports-failedStorageWriteRollsBackWholeChunkAndReportsNoSuccesses', 'CsvReportsInstrumentedTest#failedStorageWriteRollsBackWholeChunkAndReportsNoSuccesses', False),
    ('CsvReports-semicolonSectionAliasImportPersistsExplicitAndDefaultStatuses', 'CsvReportsInstrumentedTest#semicolonSectionAliasImportPersistsExplicitAndDefaultStatuses', False),
    ('CsvReports-duplicateChoiceUpdatesExactlyOneExistingMedication', 'CsvReportsInstrumentedTest#duplicateChoiceUpdatesExactlyOneExistingMedication', False),
    ('CsvReports-staleAvailabilityFailsImportWithoutChangingMedicationFields', 'CsvReportsInstrumentedTest#staleAvailabilityFailsImportWithoutChangingMedicationFields', False),
    ('CsvReports-allSectionShortagesIncludeMissingAvailabilityButExcludeInactiveOnly', 'CsvReportsInstrumentedTest#allSectionShortagesIncludeMissingAvailabilityButExcludeInactiveOnly', False),
    ('CsvReports-emptyA5ReportStillProducesRenderablePage', 'CsvReportsInstrumentedTest#emptyA5ReportStillProducesRenderablePage', False),
    ('BulkMedicationRegression-statusAndFormCommitTogetherAndUndoRestoresBoth', 'BulkMedicationRegressionInstrumentedTest#statusAndFormCommitTogetherAndUndoRestoresBoth', False),
    ('BulkMedicationRegression-unchangedRequestedValuesCreateNoReceiptHistoryOrPending', 'BulkMedicationRegressionInstrumentedTest#unchangedRequestedValuesCreateNoReceiptHistoryOrPending', False),
    ('BulkMedicationRegression-staleSelectedFieldRollsBackEveryMedication', 'BulkMedicationRegressionInstrumentedTest#staleSelectedFieldRollsBackEveryMedication', False),
    ('BulkMedicationRegression-changedUncheckedFieldDoesNotRejectOrOverwriteStatusEdit', 'BulkMedicationRegressionInstrumentedTest#changedUncheckedFieldDoesNotRejectOrOverwriteStatusEdit', False),
    ('BulkMedicationRegression-deletedSelectedTargetCannotResurrectDuringBulkEdit', 'BulkMedicationRegressionInstrumentedTest#deletedSelectedTargetCannotResurrectDuringBulkEdit', False),
    ('BulkMedicationRegression-undoRejectsStaleSelectedFieldAtomically', 'BulkMedicationRegressionInstrumentedTest#undoRejectsStaleSelectedFieldAtomically', False),
    ('BulkMedicationRegression-undoPreservesConcurrentUncheckedFields', 'BulkMedicationRegressionInstrumentedTest#undoPreservesConcurrentUncheckedFields', False),
    ('BulkMedicationRegression-undoRestoresMissingPairAsAbsenceWithOutFallback', 'BulkMedicationRegressionInstrumentedTest#undoRestoresMissingPairAsAbsenceWithOutFallback', False),
    ('BulkMedicationRegression-wrongPharmacyRejectsBeforeAnyMutation', 'BulkMedicationRegressionInstrumentedTest#wrongPharmacyRejectsBeforeAnyMutation', False),
    ('BulkMedicationRegression-expectedKeysMustExactlyMatchSelectedIds', 'BulkMedicationRegressionInstrumentedTest#expectedKeysMustExactlyMatchSelectedIds', False),
    ('BulkMedicationRegression-staleRequiredExistingEditorDoesNotRecreateDeletedMedication', 'BulkMedicationRegressionInstrumentedTest#staleRequiredExistingEditorDoesNotRecreateDeletedMedication', False),
    ('BulkMedicationRegression-samePinInDifferentDigitScriptRejectsWithoutPendingWork', 'BulkMedicationRegressionInstrumentedTest#samePinInDifferentDigitScriptRejectsWithoutPendingWork', False),
    ('BulkMedicationRegression-fourDigitPinChangeCreatesBoundOperationAndWipesInputs', 'BulkMedicationRegressionInstrumentedTest#fourDigitPinChangeCreatesBoundOperationAndWipesInputs', False),
    ('BulkMedicationRegression-historyTypeFilterSeparatesAvailabilityFromMedication', 'BulkMedicationRegressionInstrumentedTest#historyTypeFilterSeparatesAvailabilityFromMedication', False),
    ('BulkMedicationRegression-taxonomyReassignmentPreservesUntouchedAvailabilityAndMedication', 'BulkMedicationRegressionInstrumentedTest#taxonomyReassignmentPreservesUntouchedAvailabilityAndMedication', False),
    ('Review-SnapshotRefresh-branchesWrongParentsAndRegressionAreRetainedWithoutReplacingTheBase', 'SnapshotRefreshInstrumentedTest#branchesWrongParentsAndRegressionAreRetainedWithoutReplacingTheBase', False),
    ('Review-SnapshotRefresh-cleanReaderCatchesUpAcrossGapAndContinuesWithNextPublication', 'SnapshotRefreshInstrumentedTest#cleanReaderCatchesUpAcrossGapAndContinuesWithNextPublication', False),
    ('Review-SnapshotRefresh-gapWithReusedPublicationIdNeverFastForwards', 'SnapshotRefreshInstrumentedTest#gapWithReusedPublicationIdNeverFastForwards', False),
    ('Review-SnapshotRefresh-gapWithPendingEditsRetainsTheOriginalBaseAndLocalWork', 'SnapshotRefreshInstrumentedTest#gapWithPendingEditsRetainsTheOriginalBaseAndLocalWork', False),
    ('Review-SnapshotRefresh-attemptCreatedDuringDownloadBlocksGapCatchUpTransactionally', 'SnapshotRefreshInstrumentedTest#attemptCreatedDuringDownloadBlocksGapCatchUpTransactionally', False),
    ('Review-SnapshotRefresh-divergentVisibleCatalogWithoutPendingWorkCannotUseGapCatchUp', 'SnapshotRefreshInstrumentedTest#divergentVisibleCatalogWithoutPendingWorkCannotUseGapCatchUp', False),
    ('Review-AdminHistory-missingDeletionTargetsReturnSpecificErrorsWithoutCreatingAnyWork', 'AdminHistoryInstrumentedTest#missingDeletionTargetsReturnSpecificErrorsWithoutCreatingAnyWork', False),
    ('Review-PinRecovery-missingPinWithNoPendingEditsRequiresReviewThenPublishesAndPinsNextRevision', 'PinRecoveryInstrumentedTest#missingPinWithNoPendingEditsRequiresReviewThenPublishesAndPinsNextRevision', False),
    ('Review-PinRecovery-unrelatedPinRecoveryPublishesReviewedPendingEditsAndAcknowledgesOnlyAfterReadback', 'PinRecoveryInstrumentedTest#unrelatedPinRecoveryPublishesReviewedPendingEditsAndAcknowledgesOnlyAfterReadback', False),
    ('Review-PinRecovery-changedPinInvalidatesEarlierRecoveryConfirmationWithoutUploading', 'PinRecoveryInstrumentedTest#changedPinInvalidatesEarlierRecoveryConfirmationWithoutUploading', False),
    ('Review-PinRecovery-pinChangedDuringUploadBlocksMutationAndRetainsDurableAttempt', 'PinRecoveryInstrumentedTest#pinChangedDuringUploadBlocksMutationAndRetainsDurableAttempt', False),
    ('Review-PinRecovery-networkCredentialsAndDamagedPayloadNeverEnableForcedRecovery', 'PinRecoveryInstrumentedTest#networkCredentialsAndDamagedPayloadNeverEnableForcedRecovery', False),
    ('Review-PinRecovery-lostPinResponseIsAcknowledgedAfterRecreationWithoutAnotherUpload', 'PinRecoveryInstrumentedTest#lostPinResponseIsAcknowledgedAfterRecreationWithoutAnotherUpload', False),
    ('Review-PinRecovery-hiddenWriteStartedAttemptNeedsSeparateReconciliationBeforeReplacement', 'PinRecoveryInstrumentedTest#hiddenWriteStartedAttemptNeedsSeparateReconciliationBeforeReplacement', False),
    ('Review-PinRecovery-validLiveSnapshotRefusesForcedRepublication', 'PinRecoveryInstrumentedTest#validLiveSnapshotRefusesForcedRepublication', False),
    ('Review-PinRecovery-multipleStoredAttemptsBlockBeforeNetworkAndPreserveBothCandidates', 'PinRecoveryInstrumentedTest#multipleStoredAttemptsBlockBeforeNetworkAndPreserveBothCandidates', False),
    ('Review-HomeBarPlacement-bothBarsDefaultToBottomWithSectionsBottommost', 'HomeBarPlacementUiTest#bothBarsDefaultToBottomWithSectionsBottommost', False),
    ('Review-HomeBarPlacement-sectionsCanBeBottomWhileSearchStaysAtTop', 'HomeBarPlacementUiTest#sectionsCanBeBottomWhileSearchStaysAtTop', False),
    ('Review-HomeBarPlacement-searchCanBeBottomWhileSectionsStayAtTop', 'HomeBarPlacementUiTest#searchCanBeBottomWhileSectionsStayAtTop', False),
    ('Review-HomeBarPlacement-bothBarsCanReturnToTheirOriginalTopOrder', 'HomeBarPlacementUiTest#bothBarsCanReturnToTheirOriginalTopOrder', False),
    ('Review-HomeBarPlacement-changingPlacementPreservesQueryFiltersAndSelectedSection', 'HomeBarPlacementUiTest#changingPlacementPreservesQueryFiltersAndSelectedSection', False),
    ('Review-HomeBarPlacement-keyboardHidesBottomSectionsAndDismissalRestoresSelectionAndQuery', 'HomeBarPlacementUiTest#keyboardHidesBottomSectionsAndDismissalRestoresSelectionAndQuery', False),
    ('Review-HomeBarPlacement-compactBottomSectionsRemainReachableAfterScrollingTheCatalog', 'HomeBarPlacementUiTest#compactBottomSectionsRemainReachableAfterScrollingTheCatalog', False),
    ('Review-HomeBarPlacement-compactTopSectionsStayPinnedWhileTheGridScrolls', 'HomeBarPlacementUiTest#compactTopSectionsStayPinnedWhileTheGridScrolls', False),
    ('Review-ViewModel-bottomPlacementPreferencesPersistIndependentlyWithoutCatalogOrPendingChanges', 'ViewModelInstrumentedTest#bottomPlacementPreferencesPersistIndependentlyWithoutCatalogOrPendingChanges', False),
    ('Review-ViewModel-taxonomyDeleteConfirmationDoesNotCarryAcrossRowsTypesOrNew', 'ViewModelInstrumentedTest#taxonomyDeleteConfirmationDoesNotCarryAcrossRowsTypesOrNew', False),
    ('Review-ViewModel-blankSectionsDefaultActiveAfterTypeChangeNewAndSuccessfulSave', 'ViewModelInstrumentedTest#blankSectionsDefaultActiveAfterTypeChangeNewAndSuccessfulSave', False),
    ('Review-ViewModel-sectionTypedNameClearsOnSelectionAndSuccessfulDeleteResetsForm', 'ViewModelInstrumentedTest#sectionTypedNameClearsOnSelectionAndSuccessfulDeleteResetsForm', False),
    ('Initial-Home-largeText', 'HomeBarPlacementUiTest#largeTextKeepsAllFourBarPlacementsPinnedAndSearchVisible', False),
    ('Initial-Fetch-commit-failure-nochange', 'SnapshotRefreshInstrumentedTest#committedFetchMarksRemovedAvailabilityAndFailureOrNoChangeRetainsMarkers', False),
    ('Initial-Markers-accumulatedMarkersSurviveNextSessionAndRetireBeforeThird', 'FetchedChangesInstrumentedTest#accumulatedMarkersSurviveNextSessionAndRetireBeforeThird', False),
    ('Initial-Markers-changingAgainRenewsOnlyThatMedicationLifetime', 'FetchedChangesInstrumentedTest#changingAgainRenewsOnlyThatMedicationLifetime', False),
    ('Initial-Markers-repeatedActivityStartAndNoChangeDoNotAdvanceOrClearSession', 'FetchedChangesInstrumentedTest#repeatedActivityStartAndNoChangeDoNotAdvanceOrClearSession', False),
    ('Initial-Markers-pharmacyMarkersRemainIndependentAndReopenFromPersistedState', 'FetchedChangesInstrumentedTest#pharmacyMarkersRemainIndependentAndReopenFromPersistedState', False),
)


def adb(*args, timeout=30, check=True):
    return subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, timeout=timeout, check=check)


def installation_identity(package, observation='current'):
    details = adb('shell', 'dumpsys', 'package', package).stdout.decode(errors='replace')
    # Keep the raw response even when parsing fails; never relax ownership to proceed.
    (OUTPUT / f'{package}-{observation}-package-dump.txt').write_text(details)
    # Android 15 renamed the package's userId field to appId. Fresh AVDs use user 0.
    identifiers = set(re.findall(r'^\s*(?:userId|appId)=([0-9]+)[ \t]*\r?$', details, re.M))
    if len(identifiers) != 1 or int(next(iter(identifiers))) < 10000:
        raise RuntimeError(f'Missing or ambiguous application UID ownership evidence for {package}')
    identity = {'userId': next(iter(identifiers))}
    for field in ('codePath', 'firstInstallTime', 'lastUpdateTime'):
        values = {value.strip() for value in re.findall(r'^\s*' + field + r'=([^\r\n]+)', details, re.M)}
        if len(values) != 1 or not next(iter(values)):
            raise RuntimeError(f'Missing or ambiguous {field} ownership evidence for {package}')
        identity[field] = next(iter(values))
    if not identity['codePath'].startswith('/data/app/'):
        raise RuntimeError(f'Unexpected installed code path for {package}')
    return identity, details


def require_absent(package):
    probe = adb('shell', 'pm', 'path', package, check=False)
    if probe.returncode not in (0, 1) or probe.stdout.strip() or probe.stderr.strip():
        raise RuntimeError(f'Package must be absent on the fresh AVD: {package}')


def save_results(suite, cases, results, capture_errors):
    finished = {item['case'] for item in results}
    payload = {'suite': suite, 'selected': len(cases), 'completedInvocations': len(results),
               'notExecuted': [method for name, method, _ in cases if name not in finished],
               'cases': results, 'captureErrors': capture_errors}
    temporary = OUTPUT / 'results.json.tmp'
    temporary.write_text(json.dumps(payload, indent=2) + '\n')
    temporary.replace(OUTPUT / 'results.json')


def main():
    global PACKAGE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', choices=('demo', 'standard'), default='demo')
    args = parser.parse_args()
    cases = STANDARD_CASES if args.suite == 'standard' else DEMO_CASES
    PACKAGE = 'com.aqaqeer.app.debug' if args.suite == 'standard' else 'com.aqaqeer.app.demo.debug'
    if os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise SystemExit('This helper only runs on ephemeral GitHub-hosted runners.')
    if adb('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != b'1':
        raise SystemExit('Expected the owned emulator; refusing another device.')
    if adb('shell', 'am', 'get-current-user').stdout.strip() != b'0':
        raise SystemExit('Expected user 0 on the owned fresh AVD.')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    results = []
    installations = {}
    capture_errors = []
    provenance = {'serial': SERIAL, 'package': PACKAGE, 'commit': os.environ.get('GITHUB_SHA'),
                  'suite': args.suite, 'runId': os.environ.get('GITHUB_RUN_ID'),
                  'attempt': os.environ.get('GITHUB_RUN_ATTEMPT'), 'job': os.environ.get('GITHUB_JOB'),
                  'selectedMethods': [method for _, method, _ in cases],
                  'api': adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip(),
                  'user': 0, 'phase': 'APK inspection', 'apks': {}}
    save_results(args.suite, cases, results, capture_errors)
    try:
        for label, pattern in (
            ('application', f'app/build/outputs/apk/{args.suite}/debug/*.apk'),
            ('instrumentation', f'app/build/outputs/apk/androidTest/{args.suite}/debug/*.apk'),
        ):
            provenance['phase'] = label + ': APK inspection'
            paths = list(ROOT.glob(pattern))
            if len(paths) != 1:
                raise RuntimeError(f'Expected exactly one fresh {label} APK.')
            path = paths[0]
            provenance['apks'][label] = {'path': str(path.relative_to(ROOT)),
                                       'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
            # A new AVD must not have either package installed. No replacement/uninstall.
            package = PACKAGE if label == 'application' else PACKAGE + '.test'
            tools = build_tools()
            badging = subprocess.run([str(tools / 'aapt'), 'dump', 'badging', str(path)],
                                     capture_output=True, text=True, timeout=30, check=True).stdout
            if not badging.startswith(f"package: name='{package}' "):
                raise RuntimeError(f'Unexpected APK package for {label}')
            signer = subprocess.run([str(tools / 'apksigner'), 'verify', '--print-certs', str(path)],
                                    capture_output=True, text=True, timeout=30, check=True).stdout
            provenance['apks'][label]['signer'] = signer
            require_absent(package)
            provenance[label + 'BeforeInstall'] = 'absent on fresh owned AVD'
            (OUTPUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
            provenance['phase'] = label + ': installation'
            (OUTPUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
            adb('install', '-t', str(path), timeout=90)
            provenance['phase'] = label + ': ownership evidence'
            identity, details = installation_identity(package, observation='installed')
            installations[package] = identity
            provenance[label + 'Identity'] = identity
            provenance[label + 'Installation'] = details
            (OUTPUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
        for name, method, record in cases:
            provenance['phase'] = 'instrumentation: ' + method
            recorder = None
            recorder_log = None
            remote = f'/sdcard/aqaqeer-ci-{name}.mp4'
            try:
                large_text = method.startswith('CiPresentationUiTest#')
                adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3' if large_text else '1.0')
                if method == 'HomeBarPlacementUiTest#keyboardHidesBottomSectionsAndDismissalRestoresSelectionAndQuery':
                    # This helper already verified its fresh, owned emulator and user above.
                    adb('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
                    if adb('shell', 'settings', 'get', 'secure', 'show_ime_with_hard_keyboard').stdout.strip() != b'1':
                        raise RuntimeError('Soft-keyboard prerequisite could not be enabled on the owned AVD.')
                    provenance['showImeWithHardKeyboard'] = '1 (read back)'
                    (OUTPUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
                if record:
                    recorder_log = (OUTPUT / f'{name}-recorder.log').open('wb')
                    recorder = subprocess.Popen(
                        ['adb', '-s', SERIAL, 'shell', 'screenrecord', '--size', '360x640',
                         '--bit-rate', '1000000', '--time-limit', '45', remote],
                        stdout=recorder_log, stderr=subprocess.STDOUT,
                    )
                command = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                           'com.aqaqeer.app.' + method, '-e', 'ciEvidence', 'true']
                if large_text:
                    command += ['-e', 'ciTheme', name.split('-')[0].upper()]
                command += [PACKAGE + '.test/com.aqaqeer.app.SuiteRunner']
                try:
                    run = adb(*command, timeout=180 if args.suite == 'standard' else 120, check=False)
                    transcript = run.stdout + run.stderr
                    passed = run.returncode == 0 and bool(re.search(rb'OK \(1 tests?\)', transcript))
                    passed &= b'INSTRUMENTATION_CODE: -1' in transcript
                    passed &= not bool(re.search(rb'INSTRUMENTATION_STATUS_CODE: -(?:3|4)\b', transcript))
                    expected_class, expected_method = method.split('#')
                    passed &= ('INSTRUMENTATION_STATUS: class=com.aqaqeer.app.' + expected_class).encode() in transcript
                    passed &= ('INSTRUMENTATION_STATUS: test=' + expected_method).encode() in transcript
                    status = 'passed' if passed else 'failed / incomplete instrumentation'
                except subprocess.TimeoutExpired as error:
                    transcript = (error.stdout or b'') + (error.stderr or b'')
                    status = 'timed out'
                    passed = False
                (OUTPUT / f'{name}-instrumentation.log').write_bytes(transcript)
                results.append({'case': name, 'method': method, 'status': status,
                                'instrumentationExitCode': run.returncode if status != 'timed out' else None})
                save_results(args.suite, cases, results, capture_errors)
                if not passed:
                    try:
                        screenshot = adb('exec-out', 'screencap', '-p').stdout
                        if not screenshot.startswith(b'\x89PNG\r\n\x1a\n'):
                            raise RuntimeError('Invalid screenshot')
                        (OUTPUT / f'{name}-failure.png').write_bytes(screenshot)
                    except (OSError, RuntimeError, subprocess.SubprocessError):
                        capture_errors.append(name + ': failure screenshot unavailable')
                    try:
                        logcat = adb('logcat', '-d', '-t', '2000', timeout=20, check=False)
                        (OUTPUT / f'{name}-logcat.txt').write_bytes(logcat.stdout + logcat.stderr)
                    except (OSError, subprocess.SubprocessError):
                        capture_errors.append(name + ': logcat unavailable')
            finally:
                if recorder is not None:
                    try:
                        # Stop only the recorder whose command line contains our unique path.
                        pids = adb('shell', 'pidof', 'screenrecord', check=False).stdout.decode().split()
                        for pid in pids:
                            if pid.isdigit():
                                cmdline = adb('shell', 'cat', f'/proc/{pid}/cmdline', check=False).stdout
                                if remote.encode() in cmdline and b'screenrecord' in cmdline:
                                    adb('shell', 'kill', '-INT', pid, check=False)
                        recorder.wait(timeout=10)
                        adb('pull', remote, str(OUTPUT / f'{name}.mp4'))
                        if (OUTPUT / f'{name}.mp4').stat().st_size == 0:
                            raise RuntimeError('Empty recording')
                    except (OSError, RuntimeError, subprocess.SubprocessError):
                        capture_errors.append(name + ': recording unavailable/incomplete')
                    finally:
                        if recorder.poll() is None:
                            recorder.terminate()
                            try:
                                recorder.wait(timeout=5)
                            except subprocess.TimeoutExpired:
                                recorder.kill()
                                recorder.wait(timeout=5)
                        recorder_log.close()
                        adb('shell', 'rm', '-f', remote, check=False)
                # A timed-out instrumentation must not overlap the next case.
                current, _ = installation_identity(PACKAGE)
                if current != installations[PACKAGE]:
                    raise RuntimeError('Installation ownership changed; refusing delayed force-stop.')
                adb('shell', 'am', 'force-stop', PACKAGE)
        provenance['phase'] = 'all selected invocations completed'
    except Exception as error:
        provenance['failure'] = {'type': type(error).__name__, 'message': str(error)}
        raise
    finally:
        # Assertion-time captures are made inside the live Compose activity. Keep the
        # host screenshot as a fallback for setup crashes/timeouts that bypass JUnit.
        try:
            screenshots = adb('shell', 'test', '-d', f'/sdcard/Android/data/{PACKAGE}/files/ci-visuals', check=False)
            if screenshots.returncode == 0:
                pulled = adb('pull', f'/sdcard/Android/data/{PACKAGE}/files/ci-visuals', str(OUTPUT / 'ui-screenshots'), check=False)
                if pulled.returncode != 0:
                    capture_errors.append('Assertion/success screenshots could not be pulled')
            for theme in ('light', 'dark', 'amoled'):
                case = next((item for item in results if item['case'] == theme + '-large-arabic'), None)
                if case and case['status'] == 'passed':
                    for screen in ('home', 'detail'):
                        png = OUTPUT / 'ui-screenshots' / f'{theme}-large-arabic-{screen}.png'
                        if not png.is_file() or not png.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
                            capture_errors.append(f'{theme}: {screen} screenshot missing/invalid')
            adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0', check=False)
        except (OSError, subprocess.SubprocessError):
            capture_errors.append('Final screenshot collection/font-scale cleanup incomplete')
        (OUTPUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
        save_results(args.suite, cases, results, capture_errors)
        lines = [f'### {args.suite.title()} emulator results', '',
                 'Last phase: ' + provenance['phase'],
                 *(['Runner/setup failure: ' + provenance['failure']['message']] if 'failure' in provenance else []),
                 *[f'- {r["case"]}: {r["status"]}' for r in results],
                 *[f'- Capture limitation: {message}' for message in capture_errors], '',
                 f'Download {"emulator-standard-functional" if args.suite == "standard" else "emulator-evidence"} artifacts within one day; GitHub storage quota can block uploads.',
                 ('Standard cases retain individual instrumentation logs and post-instrumentation failure PNG/logcat fallbacks; no recordings or human visual acceptance.' if args.suite == 'standard' else 'Clips cover at most the first 45 seconds of each UI case. ui-screenshots holds live-activity captures; host failure PNGs are post-instrumentation fallbacks.')]
        (OUTPUT / 'results.md').write_text('\n'.join(lines) + '\n')
    if len(results) != len(cases) or any(r['status'] != 'passed' for r in results) or capture_errors:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
