"""Offline recovery selection contracts; importing the campaign never executes it."""
import importlib.util
from pathlib import Path
import unittest
import os
import sys
import tempfile
from unittest.mock import patch, Mock

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('prepare_recovery', ROOT / '.github/scripts/prepare-recovery.py')
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)


class RecoverySelectionTest(unittest.TestCase):
    def test_default_includes_restore_export_and_remaining_maintenance_boundaries(self):
        names = selection.selected_scenarios('')
        self.assertEqual(len(names), len(set(names)))
        for name in ('empty:none', 'configured:repeat_activation', 'export:export_after_close',
                     'maintenance:maintenance_preparing', 'maintenance:maintenance_after_active',
                     'maintenance:maintenance_during_cleanup'):
            self.assertIn(name, names)

    def test_exact_failed_scenario_retry_does_not_select_other_cases(self):
        name = 'maintenance:maintenance_during_cleanup'
        self.assertEqual([name], selection.selected_scenarios(name))

    def test_unknown_duplicate_whitespace_and_shell_text_are_rejected(self):
        for value in ('unknown', 'empty:none,empty:none', ' empty:none', 'empty:none,', 'empty:none;echo'):
            with self.assertRaises(ValueError):
                selection.selected_scenarios(value)


    def test_resource_only_retry_omits_recovery_scenarios_and_retains_exact_method(self):
        self.assertEqual([], selection.selected_scenarios('none'))
        method = 'measuredHistoryRoundTripsBelowBackupCap'
        self.assertEqual([method], selection.selected_resources(method))
        self.assertEqual(list(selection.campaign.RESOURCE_METHODS), selection.selected_resources(''))
        for value in ('unknown', method + ',' + method, ' ' + method, method + ',', method + ';echo'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                selection.selected_resources(value)

    def test_resource_only_preparation_and_execution_keep_exact_retry_without_device_effects(self):
        method = 'measuredHistoryRoundTripsBelowBackupCap'
        environment = {'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'github-hosted',
                       'CI_RECOVERY_SCENARIOS': 'none', 'CI_RECOVERY_RESOURCES': 'true',
                       'CI_RECOVERY_RESOURCE_METHODS': method}
        with tempfile.TemporaryDirectory() as directory, patch.object(selection, 'ROOT', Path(directory)), \
             patch.dict(os.environ, environment), patch.object(sys, 'argv', ['prepare-recovery']):
            selection.main()
            with patch.object(sys, 'argv', ['prepare-recovery', '--run']), \
                 patch.object(selection.subprocess, 'run', return_value=Mock(returncode=0)) as run, \
                 self.assertRaises(SystemExit) as result:
                selection.main()
            self.assertEqual(0, result.exception.code)
            command = run.call_args.args[0]
            self.assertIn('--resources-only', command)
            self.assertNotIn('--only', command)
            self.assertEqual(method, command[command.index('--resource-method') + 1])
            self.assertIn('--hosted-emulator', command)
            with patch.dict(os.environ, {'CI_RECOVERY_RESOURCES': 'false'}), self.assertRaises(ValueError):
                selection.main()
