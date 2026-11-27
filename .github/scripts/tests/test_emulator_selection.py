"""Offline selector contracts; no SDK, emulator, network, Gradle or real package mutation."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / '.github/scripts'))
spec = importlib.util.spec_from_file_location('emulator_selection', ROOT / '.github/scripts/run-emulator-checks.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class EmulatorSelectionTest(unittest.TestCase):
    def test_default_selection_preserves_all_four_disjoint_standard_batches(self):
        combined = []
        for name, cases in runner.STANDARD_BATCHES.items():
            self.assertEqual(cases, runner.select_cases(name))
            self.assertTrue(cases)
            combined.extend(case[1] for case in cases)
        self.assertEqual(248, len(combined))
        self.assertEqual(len(combined), len(set(combined)))

    def test_exact_retry_keeps_only_requested_methods_in_their_original_batch(self):
        first = runner.STORAGE_CASES[0][1]
        second = runner.UI_CASES[-1][1]
        requested = first + ',' + second
        self.assertEqual([first], [case[1] for case in runner.select_cases('storage', requested)])
        self.assertEqual([second], [case[1] for case in runner.select_cases('ui', requested)])
        self.assertFalse(runner.select_cases('standard', requested))

    def test_unknown_malformed_duplicate_and_shell_like_values_are_rejected(self):
        valid = runner.STORAGE_CASES[0][1]
        for methods in ('Missing#method', valid + ',' + valid, valid + ',', ',' + valid,
                        ' ' + valid, valid + '\n', valid + ';echo unsafe', '*', valid + ', Missing#method'):
            with self.subTest(methods=methods), self.assertRaises(ValueError):
                runner.select_cases('storage', methods)

    def test_live_and_dedicated_recovery_campaigns_are_absent_from_ordinary_batches(self):
        selected = [case[1] for cases in runner.STANDARD_BATCHES.values() for case in cases]
        self.assertFalse(any('realTelegram' in method or '#phase' in method or
                             method.startswith('AcceptanceResource') for method in selected))

    def test_reader_lifecycle_scenarios_select_only_the_standard_sharing_batch(self):
        for method in runner.READER_LIFECYCLE_CASES:
            with self.subTest(method=method):
                self.assertEqual([method], [case[1] for case in runner.select_cases('sharing', method)])
                self.assertFalse(runner.select_cases('ui', method))
                with self.assertRaises(ValueError):
                    runner.select_cases('demo', method)

    def test_demo_selection_retains_repeated_theme_invocations(self):
        cases = runner.select_cases('demo')
        self.assertEqual(18, len(cases))
        self.assertEqual(16, len({case[1] for case in cases}))
        self.assertEqual(3, sum(case[1].startswith('CiPresentationUiTest#') for case in cases))

    def test_demo_retry_selects_exact_methods_and_preserves_theme_variants(self):
        search = 'HomeUiTest#searchIsReachableDoesNotAutofocusAndClearRestoresBrowsing'
        self.assertEqual([search], [case[1] for case in runner.select_cases('demo', search)])
        theme = 'CiPresentationUiTest#arabicLargeTextThemeAndRecreationRemainUsable'
        self.assertEqual(3, len(runner.select_cases('demo', theme)))
        with self.assertRaises(ValueError):
            runner.select_cases('demo', runner.STORAGE_CASES[0][1])
        with self.assertRaises(ValueError):
            runner.select_cases('storage', search)

    def test_demo_retry_environment_reaches_selection_before_device_access(self):
        method = runner.DEMO_CASES[0][1]
        with patch.dict(os.environ, {'CI_DEMO_RETRY_METHODS': method}), \
             patch.object(sys, 'argv', ['runner']), \
             patch.object(runner, 'select_cases', return_value=()) as select, \
             tempfile.TemporaryDirectory() as directory, patch.object(runner, 'OUTPUT', Path(directory)), \
             patch.object(runner, 'adb') as adb:
            runner.main()
            select.assert_called_once_with('demo', method)
            adb.assert_not_called()

    def test_selection_only_reports_empty_and_matching_batches_without_device_or_gradle(self):
        method = runner.STORAGE_CASES[0][1]
        with tempfile.TemporaryDirectory() as directory, patch.object(runner, 'OUTPUT', Path(directory)), \
             patch.object(runner, 'adb') as adb, patch.object(runner.subprocess, 'run') as command:
            output = Path(directory) / 'github-output'
            for suite, expected in (('storage', 'true'), ('ui', 'false')):
                with patch.dict(os.environ, {'CI_RETRY_METHODS': method, 'GITHUB_OUTPUT': str(output)}), \
                     patch.object(sys, 'argv', ['runner', '--suite', suite, '--selection-only']):
                    runner.main()
                self.assertEqual('has_checks=' + expected, output.read_text().splitlines()[-1])
            adb.assert_not_called(); command.assert_not_called()

    def test_nonmatching_retry_batch_records_zero_selected_without_adb_calls(self):
        method = runner.STORAGE_CASES[0][1]
        with tempfile.TemporaryDirectory() as directory, patch.object(runner, 'OUTPUT', Path(directory)), \
             patch.dict(os.environ, {'CI_RETRY_METHODS': method}), \
             patch.object(sys, 'argv', ['runner', '--suite', 'ui']), patch.object(runner, 'adb') as adb:
            runner.main()
            result = json.loads((Path(directory) / 'results.json').read_text())
            self.assertEqual(0, result['selected'])
            self.assertEqual([], result['cases'])
            adb.assert_not_called()


if __name__ == '__main__':
    unittest.main()
