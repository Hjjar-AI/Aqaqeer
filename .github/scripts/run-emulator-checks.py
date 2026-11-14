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
    ('read-only-details', 'ReadOnlyUiTest#medicationDetailsRemainReadOnlyAndNavigateBack', True),
    ('light-large-arabic', 'CiPresentationUiTest#arabicLargeTextThemeAndRecreationRemainUsable', True),
    ('dark-large-arabic', 'CiPresentationUiTest#arabicLargeTextThemeAndRecreationRemainUsable', True),
    ('amoled-large-arabic', 'CiPresentationUiTest#arabicLargeTextThemeAndRecreationRemainUsable', True),
    ('public-vectors', 'EnvelopeInstrumentedTest#androidProviderReadsAllPublicVectorsIncludingExactUnicodePassphrase', False),
    ('authentication-rejection', 'EnvelopeInstrumentedTest#androidAuthenticationFailureLeavesNoStagingFileOrPayload', False),
)

STANDARD_CASES = (
    ('CsvReports-contentProviderImportCommitsValidRowsAndRetainsFailedRowResults', 'CsvReportsInstrumentedTest#contentProviderImportCommitsValidRowsAndRetainsFailedRowResults', False),
    ('CsvReports-staleUpdateRollsBackAndTaxonomyRequiresExplicitConsent', 'CsvReportsInstrumentedTest#staleUpdateRollsBackAndTaxonomyRequiresExplicitConsent', False),
    ('CsvReports-cancellationAfterChunkCallbackRetainsAllCommittedRows', 'CsvReportsInstrumentedTest#cancellationAfterChunkCallbackRetainsAllCommittedRows', False),
    ('CsvReports-reportsDefaultToPublishedAndOutNeverIncludesUnknown', 'CsvReportsInstrumentedTest#reportsDefaultToPublishedAndOutNeverIncludesUnknown', False),
    ('CsvReports-draftsRequireOptInAndExpiryPresetExcludesExpired', 'CsvReportsInstrumentedTest#draftsRequireOptInAndExpiryPresetExcludesExpired', False),
    ('CsvReports-arabicPdfRendersMultiplePagesAndHasNoBlankContinuationPages', 'CsvReportsInstrumentedTest#arabicPdfRendersMultiplePagesAndHasNoBlankContinuationPages', False),
    ('CsvReports-authorizationLossBlocksExternalOutputAndImportBeforeFileAccess', 'CsvReportsInstrumentedTest#authorizationLossBlocksExternalOutputAndImportBeforeFileAccess', False),
    ('CsvReports-fiftyOneRowsReportCommittedChunksInOrder', 'CsvReportsInstrumentedTest#fiftyOneRowsReportCommittedChunksInOrder', False),
    ('CsvReports-cancellationAfterFirstFiftyRowsDoesNotStartSecondChunk', 'CsvReportsInstrumentedTest#cancellationAfterFirstFiftyRowsDoesNotStartSecondChunk', False),
    ('CsvReports-failedStorageWriteRollsBackWholeChunkAndReportsNoSuccesses', 'CsvReportsInstrumentedTest#failedStorageWriteRollsBackWholeChunkAndReportsNoSuccesses', False),
    ('CsvReports-semicolonSectionAliasImportPersistsExplicitAndDefaultStatuses', 'CsvReportsInstrumentedTest#semicolonSectionAliasImportPersistsExplicitAndDefaultStatuses', False),
    ('CsvReports-duplicateChoiceUpdatesExactlyOneExistingMedication', 'CsvReportsInstrumentedTest#duplicateChoiceUpdatesExactlyOneExistingMedication', False),
    ('CsvReports-staleAvailabilityFailsImportWithoutChangingMedicationFields', 'CsvReportsInstrumentedTest#staleAvailabilityFailsImportWithoutChangingMedicationFields', False),
    ('CsvReports-allSectionShortagesExcludeInactiveOnlyAndUnknownMedication', 'CsvReportsInstrumentedTest#allSectionShortagesExcludeInactiveOnlyAndUnknownMedication', False),
    ('CsvReports-emptyA5ReportStillProducesRenderablePage', 'CsvReportsInstrumentedTest#emptyA5ReportStillProducesRenderablePage', False),
    ('BulkMedicationRegression-statusAndFormCommitTogetherAndUndoRestoresBoth', 'BulkMedicationRegressionInstrumentedTest#statusAndFormCommitTogetherAndUndoRestoresBoth', False),
    ('BulkMedicationRegression-unchangedRequestedValuesCreateNoReceiptHistoryOrPending', 'BulkMedicationRegressionInstrumentedTest#unchangedRequestedValuesCreateNoReceiptHistoryOrPending', False),
    ('BulkMedicationRegression-staleSelectedFieldRollsBackEveryMedication', 'BulkMedicationRegressionInstrumentedTest#staleSelectedFieldRollsBackEveryMedication', False),
    ('BulkMedicationRegression-changedUncheckedFieldDoesNotRejectOrOverwriteStatusEdit', 'BulkMedicationRegressionInstrumentedTest#changedUncheckedFieldDoesNotRejectOrOverwriteStatusEdit', False),
    ('BulkMedicationRegression-deletedSelectedTargetCannotResurrectDuringBulkEdit', 'BulkMedicationRegressionInstrumentedTest#deletedSelectedTargetCannotResurrectDuringBulkEdit', False),
    ('BulkMedicationRegression-undoRejectsStaleSelectedFieldAtomically', 'BulkMedicationRegressionInstrumentedTest#undoRejectsStaleSelectedFieldAtomically', False),
    ('BulkMedicationRegression-undoPreservesConcurrentUncheckedFields', 'BulkMedicationRegressionInstrumentedTest#undoPreservesConcurrentUncheckedFields', False),
    ('BulkMedicationRegression-undoRestoresMissingLegacyPairAsAbsence', 'BulkMedicationRegressionInstrumentedTest#undoRestoresMissingLegacyPairAsAbsence', False),
    ('BulkMedicationRegression-wrongPharmacyRejectsBeforeAnyMutation', 'BulkMedicationRegressionInstrumentedTest#wrongPharmacyRejectsBeforeAnyMutation', False),
    ('BulkMedicationRegression-expectedKeysMustExactlyMatchSelectedIds', 'BulkMedicationRegressionInstrumentedTest#expectedKeysMustExactlyMatchSelectedIds', False),
    ('BulkMedicationRegression-staleRequiredExistingEditorDoesNotRecreateDeletedMedication', 'BulkMedicationRegressionInstrumentedTest#staleRequiredExistingEditorDoesNotRecreateDeletedMedication', False),
    ('BulkMedicationRegression-samePinInDifferentDigitScriptRejectsWithoutPendingWork', 'BulkMedicationRegressionInstrumentedTest#samePinInDifferentDigitScriptRejectsWithoutPendingWork', False),
    ('BulkMedicationRegression-fourDigitPinChangeCreatesBoundOperationAndWipesInputs', 'BulkMedicationRegressionInstrumentedTest#fourDigitPinChangeCreatesBoundOperationAndWipesInputs', False),
    ('BulkMedicationRegression-historyTypeFilterSeparatesAvailabilityFromMedication', 'BulkMedicationRegressionInstrumentedTest#historyTypeFilterSeparatesAvailabilityFromMedication', False),
    ('BulkMedicationRegression-taxonomyReassignmentPreservesUntouchedAvailabilityAndMedication', 'BulkMedicationRegressionInstrumentedTest#taxonomyReassignmentPreservesUntouchedAvailabilityAndMedication', False),
)


def adb(*args, timeout=30, check=True):
    return subprocess.run(['adb', '-s', SERIAL, *args], capture_output=True, timeout=timeout, check=check)


def installation_identity(package):
    details = adb('shell', 'dumpsys', 'package', package).stdout.decode(errors='replace')
    identity = {}
    for field in ('userId', 'codePath', 'firstInstallTime', 'lastUpdateTime'):
        match = re.search(r'^\s*' + field + r'=([^\r\n]+)', details, re.M)
        if match is None:
            raise RuntimeError(f'Missing {field} ownership evidence for {package}')
        identity[field] = match.group(1).strip()
    return identity, details


def require_absent(package):
    probe = adb('shell', 'pm', 'path', package, check=False)
    if probe.returncode not in (0, 1) or probe.stdout.strip() or probe.stderr.strip():
        raise RuntimeError(f'Package must be absent on the fresh AVD: {package}')


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
    OUTPUT.mkdir(parents=True, exist_ok=True)
    results = []
    installations = {}
    capture_errors = []
    provenance = {'serial': SERIAL, 'package': PACKAGE, 'commit': os.environ.get('GITHUB_SHA'),
                  'suite': args.suite, 'runId': os.environ.get('GITHUB_RUN_ID'),
                  'attempt': os.environ.get('GITHUB_RUN_ATTEMPT'), 'job': os.environ.get('GITHUB_JOB'),
                  'api': adb('shell', 'getprop', 'ro.build.version.sdk').stdout.decode().strip(), 'apks': {}}
    try:
        for label, pattern in (
            ('application', f'app/build/outputs/apk/{args.suite}/debug/*.apk'),
            ('instrumentation', f'app/build/outputs/apk/androidTest/{args.suite}/debug/*.apk'),
        ):
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
            adb('install', '-t', str(path), timeout=90)
            identity, details = installation_identity(package)
            installations[package] = identity
            provenance[label + 'Identity'] = identity
            provenance[label + 'Installation'] = details
            (OUTPUT / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
        for name, method, record in cases:
            recorder = None
            recorder_log = None
            remote = f'/sdcard/aqaqeer-ci-{name}.mp4'
            try:
                large_text = method.startswith('CiPresentationUiTest#')
                adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3' if large_text else '1.0')
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
        (OUTPUT / 'results.json').write_text(json.dumps({'suite': args.suite, 'selected': len(cases), 'cases': results, 'captureErrors': capture_errors}, indent=2) + '\n')
        lines = [f'### {args.suite.title()} emulator results', '', *[f'- {r["case"]}: {r["status"]}' for r in results],
                 *[f'- Capture limitation: {message}' for message in capture_errors], '',
                 'Download emulator-evidence for MP4/PNG/logs within one day; GitHub storage quota can block uploads.',
                 'Clips cover at most the first 45 seconds of each UI case. ui-screenshots holds live-activity captures; host failure PNGs are post-instrumentation fallbacks.']
        (OUTPUT / 'results.md').write_text('\n'.join(lines) + '\n')
    if len(results) != len(cases) or any(r['status'] != 'passed' for r in results) or capture_errors:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
