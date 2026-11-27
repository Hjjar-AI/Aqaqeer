import os
from pathlib import Path
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest


ROOT = Path(__file__).resolve().parents[3]
CHECKER = ROOT / '.github/scripts/check-repository.py'
FIXTURE = 'app/src/androidTest/assets/m3-import.aqz'


class RepositoryIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(self.enterContext(TemporaryDirectory()))
        self.environment = os.environ.copy()
        self.environment.pop('GITHUB_STEP_SUMMARY', None)
        self.command('git', 'init', '--quiet')
        for name in ('.gitignore', 'gradle/libs.versions.toml', 'build.gradle.kts', 'gradlew', 'gradlew.bat'):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)

    def command(self, *args, expected=0):
        result = subprocess.run(args, cwd=self.root, env=self.environment, capture_output=True, text=True)
        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
        return result.stdout

    def check(self, *args, expected=0):
        return self.command(sys.executable, str(CHECKER), *args, expected=expected)

    def test_ignored_backup_is_reported_only_after_force_adding_it(self):
        backup = 'sample.aqbak'
        marker = 'Synthetic backup contents'
        (self.root / backup).write_text(marker)
        self.command('git', 'check-ignore', '--quiet', '--', backup)
        self.check('--source-only')
        self.command('git', 'add', '--force', '--', backup)
        report = self.check('--source-only', expected=1)
        self.assertIn(f'{backup}: possible private signing/credential/connection/backup file (contents not read)', report)
        self.assertNotIn(marker, report)

    def test_full_check_requires_the_public_fixture_to_be_tracked_and_present(self):
        fixture = self.root / FIXTURE
        fixture.parent.mkdir(parents=True)
        fixture.write_bytes(b'\0Synthetic public fixture')
        self.command('git', 'check-ignore', '--quiet', '--', FIXTURE)
        self.assertIn(f'{FIXTURE}: allowlisted public fixture is not tracked', self.check(expected=1))
        self.command('git', 'add', '--force', '--', FIXTURE)
        self.check()
        fixture.unlink()
        self.assertIn(f'{FIXTURE}: tracked public fixture is missing', self.check(expected=1))

    def test_source_only_check_allows_an_absent_public_test_fixture(self):
        self.check('--source-only')
