"""Run an existing CI Gradle command, retrying only dependency-resolution HTTP 429.

No task selection, cache replacement, version reservation or workflow dispatch occurs here.
Keep every attempt in the diagnostics directory; compiler/test failures stop immediately.
"""

import argparse
from pathlib import Path
import re
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[2]


def dependency_rate_limit(output):
    if not re.search(r'Received status code 429 from server:', output):
        return False
    # --continue may expose more than one failure. Every failure must be a
    # dependency-resolution failure; never retry an assertion/compiler failure.
    failures = re.split(r'Execution failed for task [^\n]+\n', output)[1:]
    if not failures:
        return False
    if any(not re.search(r'^> Could not resolve (?:all (?:files|artifacts)|[^\n]+)', block.lstrip(), re.M)
           or 'Received status code 429 from server:' not in block for block in failures):
        return False
    if re.search(r'(^e: |Kotlin compiler error|There were failing tests|\d+ tests completed)', output, re.M):
        return False
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log', required=True)
    parser.add_argument('gradle_args', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    tasks = args.gradle_args
    if tasks[:1] == ['--']:
        tasks = tasks[1:]
    if not tasks:
        parser.error('Supply the original Gradle arguments after --')
    log = (ROOT / args.log).resolve()
    if not log.is_relative_to(ROOT / 'ci-output') or Path.cwd().resolve() != ROOT:
        parser.error('Run from the repository root with a log under ci-output/')
    log.parent.mkdir(parents=True, exist_ok=True)
    command = [str(ROOT / 'gradlew'), *tasks]
    with log.open('w', encoding='utf-8') as combined:
        for attempt in range(1, 4):
            attempt_log = log.with_name(f'{log.stem}.attempt-{attempt}{log.suffix}')
            with attempt_log.open('w', encoding='utf-8') as transcript:
                with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                      text=True, encoding='utf-8', errors='replace') as process:
                    for line in process.stdout:
                        sys.stdout.write(line)
                        sys.stdout.flush()
                        combined.write(line)
                        transcript.write(line)
                    status = process.wait()
            combined.flush()
            if status == 0:
                return 0
            if status < 0 or attempt == 3 or not dependency_rate_limit(attempt_log.read_text(encoding='utf-8')):
                return status if status > 0 else 128 - status
            delay = 20 * attempt
            message = f'CI: dependency HTTP 429; retry {attempt + 1}/3 after {delay}s.\n'
            print(message, end='', flush=True)
            combined.write(message)
            combined.flush()
            time.sleep(delay)
    return 1


if __name__ == '__main__':
    sys.exit(main())
