"""Manual hosted check; never run merely to inspect this source."""
import os
from pathlib import Path
import subprocess
import sys


def main():
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Use the manual hosted workflow; local Gradle remains separately authorized')
    counter = Path('local/versioning/last-code.txt')
    before = counter.read_bytes() if counter.exists() else None
    output = Path('ci-output/build-tooling')
    output.mkdir(parents=True, exist_ok=True)
    retry = [sys.executable, '.github/scripts/run-gradle-ci.py', '--timeout', '600']
    try:
        for attempt in (1, 2):
            result = subprocess.run([*retry, '--log', str(output / f'configuration-{attempt}.log'), '--',
                                     '--no-daemon', '--console=plain', '--configuration-cache', 'help'],
                                    capture_output=True, text=True, timeout=630)
            if result.returncode or attempt == 2 and 'Reusing configuration cache' not in result.stdout:
                raise ValueError('Configuration-cache execution/reuse not established')
        result = subprocess.run([*retry, '--log', str(output / 'packaging-dry-run.log'), '--',
                                 '--no-daemon', '--console=plain', '--dry-run',
                                 ':app:assembleStandardDebug'], capture_output=True, text=True, timeout=630)
        if result.returncode:
            raise ValueError('Packaging dry-run failed')
    finally:
        after = counter.read_bytes() if counter.exists() else None
        if before != after:
            raise ValueError('Non-packaging work changed the counter; preserve and investigate')
    (output / 'results.md').write_text('Two non-packaging invocations established configuration-cache reuse; packaging dry-run succeeded; counter unchanged.\n'
                                      'Windows, concurrent local locking and actual packaging are not covered.\n')


if __name__ == '__main__':
    main()
