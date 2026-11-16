"""Offline reservation checks. Never contacts GitHub or changes the real checkout counter."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / '.github/scripts'))
import reserve_release_registry as registry


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


reserve = load('initial_reserve_release', ROOT / '.github/scripts/reserve-release.py')
versions = load('initial_build_version', ROOT / 'gradle/build_version.py')


class InitialReservationsTest(unittest.TestCase):
    def test_registry_ignores_old_tags_and_reads_only_initial_namespace(self):
        refs = [[{'ref': 'refs/tags/android-build-999'},
                 {'ref': 'refs/tags/android-initial-build-2'},
                 {'ref': 'refs/tags/android-initial-build-01'}],
                [{'ref': 'refs/tags/android-initial-build-4'}]]
        with patch.object(registry.subprocess, 'run', return_value=SimpleNamespace(stdout=json.dumps(refs))) as call:
            self.assertEqual([2, 4], registry.remote_codes('owner/repo'))
        self.assertEqual('repos/owner/repo/git/matching-refs/tags/android-initial-build-', call.call_args.args[0][-1])

    def test_empty_initial_registry_reserves_one_without_touching_old_tags(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'output'
            env = {'CI_RELEASE_CODE': '', 'GITHUB_RUN_ATTEMPT': '1', 'GITHUB_REF': 'refs/heads/Final',
                   'CI_DEFAULT_BRANCH': 'Final', 'GITHUB_REPOSITORY': 'owner/repo',
                   'GITHUB_SHA': 'a' * 40, 'GITHUB_OUTPUT': str(output)}
            with patch.dict(os.environ, env, clear=True), patch.object(reserve, 'remote_codes', return_value=[]), \
                    patch.object(reserve, 'api', return_value={}) as api:
                reserve.main()
            self.assertEqual('code=1\n', output.read_text())
            api.assert_called_once_with('repos/owner/repo/git/refs',
                                        {'ref': 'refs/tags/android-initial-build-1', 'sha': 'a' * 40})

    def test_consumed_initial_code_is_rejected_without_remote_write(self):
        env = {'CI_RELEASE_CODE': '2', 'GITHUB_RUN_ATTEMPT': '1', 'GITHUB_REF': 'refs/heads/Final',
               'CI_DEFAULT_BRANCH': 'Final', 'GITHUB_REPOSITORY': 'owner/repo'}
        with patch.dict(os.environ, env, clear=True), patch.object(reserve, 'remote_codes', return_value=[2]), \
                patch.object(reserve, 'api') as api:
            with self.assertRaises(ValueError):
                reserve.main()
            api.assert_not_called()

    def test_fresh_local_counter_reserves_one_then_two_and_preserves_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(versions, 'ROOT', root), patch.object(versions.Path, 'cwd', return_value=root), \
                    patch.dict(os.environ, {}, clear=True):
                self.assertEqual(1, versions.build_code(['assembleStandardDebug']))
                lock = root / 'local/versioning/counter.lock'
                self.assertTrue(lock.exists())
                identity = lock.stat().st_ino
                self.assertEqual(2, versions.build_code(['assembleStandardDebug']))
                self.assertEqual(identity, lock.stat().st_ino)
                self.assertEqual('2\n', (root / 'local/versioning/last-code.txt').read_text())


if __name__ == '__main__':
    unittest.main()
