"""Offline mocked checks for retry boundaries; never start Gradle or access the network."""

import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('gradle_ci', Path(__file__).resolve().parents[1] / 'run-gradle-ci.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

RATE_LIMIT = """Execution failed for task ':app:mergeManifest'.
> Could not resolve all files for configuration ':app:runtimeClasspath'.
   > Could not GET 'https://repo.maven.apache.org/maven2/public-fixture.pom'.
      > Received status code 429 from server: Too Many Requests
"""
COMPILER = """e: file:///example.kt:1:1 Unresolved reference.
Execution failed for task ':app:compileKotlin'.
> Kotlin compiler error
"""


class FakeProcess:
    def __init__(self, output, status):
        self.stdout = io.StringIO(output)
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.stdout.close()

    def wait(self):
        return self.status


class GradleCiTest(unittest.TestCase):
    def invoke(self, attempts):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            previous = Path.cwd()
            try:
                os.chdir(root)
                with patch.object(runner, 'ROOT', root), \
                     patch.object(runner.sys, 'argv', ['run-gradle-ci.py', '--log', 'ci-output/probe/gradle.log', '--', '--no-daemon', ':app:testDemoDebugUnitTest']), \
                     patch.object(runner.subprocess, 'Popen', side_effect=[FakeProcess(*pair) for pair in attempts]) as process, \
                     patch.object(runner.time, 'sleep') as sleep, \
                     patch.object(runner.sys, 'stdout', io.StringIO()):
                    status = runner.main()
                    calls = process.call_args_list
                    delays = [call.args[0] for call in sleep.call_args_list]
                files = {p.name: p.read_text() for p in (root / 'ci-output/probe').iterdir()}
                return status, calls, delays, files
            finally:
                os.chdir(previous)

    def test_rate_limited_dependency_can_recover_without_changing_tasks(self):
        status, calls, delays, logs = self.invoke([(RATE_LIMIT, 1), ('BUILD SUCCESSFUL\n', 0)])
        self.assertEqual(0, status)
        self.assertEqual(2, len(calls))
        self.assertEqual(calls[0].args, calls[1].args)
        self.assertEqual(['--no-daemon', ':app:testDemoDebugUnitTest'], calls[0].args[0][1:])
        self.assertEqual([20], delays)
        self.assertIn(RATE_LIMIT, logs['gradle.log'])
        self.assertIn('BUILD SUCCESSFUL', logs['gradle.log'])
        self.assertEqual(RATE_LIMIT, logs['gradle.attempt-1.log'])

    def test_persistent_rate_limit_has_three_attempts_and_retains_failure(self):
        status, calls, delays, logs = self.invoke([(RATE_LIMIT, 1)] * 3)
        self.assertEqual(1, status)
        self.assertEqual(3, len(calls))
        self.assertEqual([20, 40], delays)
        self.assertIn('gradle.attempt-3.log', logs)

    def test_compiler_failure_after_download_retry_stops_immediately(self):
        status, calls, delays, _ = self.invoke([(RATE_LIMIT, 1), (COMPILER, 2)])
        self.assertEqual(2, status)
        self.assertEqual(2, len(calls))
        self.assertEqual([20], delays)

    def test_assertion_failure_is_not_retried_even_with_429_in_test_output(self):
        output = RATE_LIMIT + "Execution failed for task ':app:testDemoDebugUnitTest'.\n> There were failing tests\n2 tests completed, 1 failed\n"
        status, calls, delays, _ = self.invoke([(output, 1)])
        self.assertEqual(1, status)
        self.assertEqual(1, len(calls))
        self.assertEqual([], delays)

    def test_non429_and_mixed_dependency_failures_are_not_retried(self):
        for output in [RATE_LIMIT.replace('429', '401'), RATE_LIMIT + RATE_LIMIT.replace('429', '403')]:
            with self.subTest(output=output):
                status, calls, delays, _ = self.invoke([(output, 1)])
                self.assertEqual(1, status)
                self.assertEqual(1, len(calls))
                self.assertEqual([], delays)

    def test_signal_termination_never_retries(self):
        status, calls, delays, _ = self.invoke([(RATE_LIMIT, -15)])
        self.assertEqual(143, status)
        self.assertEqual(1, len(calls))
        self.assertEqual([], delays)


if __name__ == '__main__':
    unittest.main()
