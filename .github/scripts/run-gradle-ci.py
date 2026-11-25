"""Run an existing CI Gradle command with bounded retries for known resolution failures.

No task selection, cache replacement, version reservation or workflow dispatch occurs here.
Keep every attempt in the diagnostics directory; compiler/test failures stop immediately.
"""

import argparse
from pathlib import Path
import re
import subprocess
import sys
import time
import threading


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


def compose_plugin_resolution_failure(output):
    # A cold runner failed before tasks when resolving this marker, while the
    # same pin resolved in the other jobs. The diagnostic has no HTTP status.
    if len(re.findall(r'^FAILURE:', output, re.M)) != 1 or re.search(r'^> Task |^e: ', output, re.M):
        return False
    causes = re.findall(r'^\* What went wrong:\n(.*?)\n\n\* Try:', output, re.M | re.S)
    if len(causes) != 1:
        return False
    return re.fullmatch(
        r"Plugin \[id: 'org\.jetbrains\.kotlin\.plugin\.compose', version: '(\d+\.\d+\.\d+)', apply: false\]"
        r" was not found in any of the following sources:\n\n"
        r"- Gradle Core Plugins [^\n]+\n"
        r"- Included Builds [^\n]+\n"
        r"- Plugin Repositories \(could not resolve plugin artifact "
        r"'org\.jetbrains\.kotlin\.plugin\.compose:org\.jetbrains\.kotlin\.plugin\.compose\.gradle\.plugin:\1'\)\n"
        r"  Searched in the following repositories:(?:\n    [^\n]+)+",
        causes[0],
    ) is not None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log', required=True)
    parser.add_argument('--command', action='store_true', help='Retry the supplied command; used after wrapper version reservation')
    parser.add_argument('--timeout', type=int, help='Total seconds allowed, including retry waits')
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
    if args.timeout is not None and args.timeout <= 0:
        parser.error('--timeout must be positive')
    command = tasks if args.command else [str(ROOT / 'gradlew'), *tasks]
    deadline = time.monotonic() + args.timeout if args.timeout else None
    with log.open('w', encoding='utf-8') as combined:
        for attempt in range(1, 4):
            attempt_log = log.with_name(f'{log.stem}.attempt-{attempt}{log.suffix}')
            with attempt_log.open('w', encoding='utf-8') as transcript:
                with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                      text=True, encoding='utf-8', errors='replace') as process:
                    timer = threading.Timer(max(0, deadline - time.monotonic()), process.kill) if deadline else None
                    if timer:
                        timer.start()
                    try:
                        for line in process.stdout:
                            sys.stdout.write(line)
                            sys.stdout.flush()
                            combined.write(line)
                            transcript.write(line)
                        status = process.wait()
                    finally:
                        if timer:
                            timer.cancel()
            combined.flush()
            if status == 0:
                return 0
            if status < 0 or attempt == 3:
                return status if status > 0 else 128 - status
            output = attempt_log.read_text(encoding='utf-8')
            if dependency_rate_limit(output):
                delay = 120 * attempt
                reason = 'dependency HTTP 429'
            elif attempt == 1 and compose_plugin_resolution_failure(output):
                delay = 30
                reason = 'Kotlin Compose plugin resolution failed before tasks'
            else:
                return status
            if deadline and time.monotonic() + delay >= deadline:
                return status
            message = f'CI: {reason}; retry {attempt + 1}/3 after {delay}s.\n'
            print(message, end='', flush=True)
            combined.write(message)
            combined.flush()
            time.sleep(delay)
    return 1


if __name__ == '__main__':
    sys.exit(main())
