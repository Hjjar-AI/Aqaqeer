"""Offline recovery selection contracts; importing the campaign never executes it."""
import importlib.util
from pathlib import Path
import unittest

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

