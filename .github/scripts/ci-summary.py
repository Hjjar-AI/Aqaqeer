"""Write public CI metadata without collecting environment or private state."""

import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=('build', 'lint', 'workflows', 'codeql', 'verification', 'crypto', 'emulator', 'dependencies', 'release'))
    args = parser.parse_args()
    directory = Path('ci-output') / args.kind
    directory.mkdir(parents=True, exist_ok=True)
    lines = [f'## {args.kind.title()} results', '']
    # Use fixed metadata keys only. Never dump contexts, tokens or environments.
    for label, key in (
        ('Source commit', 'GITHUB_SHA'),
        ('Run ID', 'GITHUB_RUN_ID'),
        ('Attempt', 'GITHUB_RUN_ATTEMPT'),
        ('Job', 'GITHUB_JOB'),
        ('Job result before summary', 'CI_JOB_STATUS'),
        ('Main command outcome', 'CI_COMMAND_OUTCOME'),
        ('Runner OS', 'RUNNER_OS'),
        ('Runner architecture', 'RUNNER_ARCH'),
        ('Hosted image version', 'ImageVersion'),
    ):
        lines.append(f'- {label}: `{os.environ.get(key) or "unavailable / not executed"}`')
    if args.kind != 'workflows':
        try:
            version = subprocess.run(
                ['java', '-version'], capture_output=True, text=True, timeout=10,
            )
            first_line = (version.stderr or version.stdout).splitlines()
            if first_line:
                lines.append(f'- Java runtime: `{first_line[0]}`')
        except (OSError, subprocess.TimeoutExpired):
            lines.append('- Java runtime: unavailable')
    lines.extend(['', 'Artifacts upload automatically and expire after one day, subject to GitHub storage quota.',
                  'If an upload is quota-blocked, inspect step logs and summaries.',
                  'Standard public runners only; no-payment-method quota blocking is managed by GitHub.'])
    invalid_unit_results = False
    if args.kind == 'verification':
        lines.extend([
            '', f'Check: `{os.environ.get("CI_CHECK_NAME", "unavailable")}`',
            f'Tasks: `{os.environ.get("CI_GRADLE_TASKS", "unavailable")}`',
            '', 'No device tests are executed. Compilation alone is not runtime acceptance.',
        ])
        if os.environ.get('CI_CHECK_KIND') == 'unit':
            reports = sorted(Path('app/build/test-results').glob('test*UnitTest/TEST-*.xml'))
            totals = dict(tests=0, failures=0, errors=0, skipped=0)
            lines.extend(['', '### JVM test results', ''])
            for path in reports:
                try:
                    suite = ET.parse(path).getroot()
                    counts = {key: int(suite.get(key, '0')) for key in totals}
                    if any(value < 0 for value in counts.values()) or counts['skipped'] > counts['tests']:
                        raise ValueError('Invalid report counts')
                    for key, count in counts.items():
                        totals[key] += count
                    lines.append(f'- `{path.name}`: ' + ', '.join(f'{k}: {v}' for k, v in counts.items()))
                except (ET.ParseError, OSError, ValueError):
                    invalid_unit_results = True
                    lines.append(f'- `{path.name}`: unreadable/incomplete report; inspect logs.')
            lines.extend(['', 'Totals: ' + ', '.join(f'{k}: {v}' for k, v in totals.items()),
                          '', 'Counts describe reported test cases, not measured code coverage.'])
            executed = totals['tests'] - totals['skipped']
            lines.append(f'Executed (not skipped): {executed}')
            invalid_unit_results |= (not reports or executed <= 0 or totals['failures'] > 0
                                     or totals['errors'] > 0)
            invalid_unit_results |= any(count < 0 for count in totals.values())
            if invalid_unit_results:
                lines.append('Unit verification failed or lacks complete XML evidence of executed tests.')
    if args.kind == 'build':
        flavor = os.environ.get('CI_BUILD_FLAVOR')
        if flavor not in ('standard', 'demo'):
            raise ValueError('Build summary requires an explicit standard/demo flavor')
        lines.extend(['', f'Selected flavor: `{flavor}`'])
        files = sorted((Path('app/build/outputs/apk') / flavor / 'debug').glob('*.apk'))
        checksums = []
        lines.extend(['', '### APK SHA-256 checksums', ''])
        for path in files:
            with path.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            checksums.append(f'{digest}  {path.as_posix()}')
            lines.append(f'- `{path.as_posix()}`: `{digest}`')
        if checksums:
            (directory / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n')
        else:
            lines.append('No debug APKs were produced in this job.')
    if args.kind == 'lint':
        reports = sorted(Path('app/build/reports').glob('lint-results-*.xml'))
        lines.extend(['', '### Android lint findings', ''])
        for path in reports:
            try:
                root = ET.parse(path).getroot()
                counts = {}
                for issue in root.findall('issue'):
                    severity = issue.get('severity', 'Unknown')
                    counts[severity] = counts.get(severity, 0) + 1
                count_text = ', '.join(f'{key}: {counts[key]}' for key in sorted(counts))
                lines.append(f'- `{path.name}`: {count_text or "0 findings"}')
            except (ET.ParseError, OSError):
                lines.append(f'- `{path.name}`: unreadable/incomplete report; inspect logs.')
        if not reports:
            lines.append('No XML reports were produced; lint was not verified by this summary.')
        lines.extend(['', 'Finding counts are reported issues, not measured code coverage.'])
    text = '\n'.join(lines) + '\n'
    (directory / 'summary.md').write_text(text)
    if summary_path := os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(summary_path).open('a') as stream:
            stream.write(text)
    if invalid_unit_results:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
