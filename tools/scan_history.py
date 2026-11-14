#!/usr/bin/env python3
"""Explicit history scan. Raw reports/logs never go to stdout or hosted artifacts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true', help='Download the proven-missing pinned archive')
    parser.add_argument('--hosted', action='store_true', help='Keep detailed findings ephemeral; print counts only')
    args = parser.parse_args()
    if args.hosted and os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Hosted mode requires GitHub Actions')
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise ValueError('This pinned scanner entrypoint currently supports Linux x86_64 only')
    pin = json.loads((ROOT / '.github/ci-tools.json').read_text())['gitleaks']
    cache = ROOT / 'local/tooling/gitleaks' / pin['version']
    archive = cache / 'gitleaks.tar.gz'
    if not archive.exists():
        if not args.install:
            raise ValueError('Pinned scanner archive missing; explicitly use --install to obtain it')
        cache.mkdir(parents=True, exist_ok=True)
        url = f'https://github.com/gitleaks/gitleaks/releases/download/v{pin["version"]}/gitleaks_{pin["version"]}_linux_x64.tar.gz'
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read(30 * 1024 * 1024 + 1)
        if len(data) > 30 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != pin['sha256']:
            raise ValueError('Scanner archive checksum/size mismatch')
        with archive.open('xb') as stream:
            stream.write(data)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != pin['sha256']:
        raise ValueError('Cached scanner checksum mismatch; preserve it for investigation')
    with tempfile.TemporaryDirectory(prefix='aqaqeer-history-', dir=os.environ.get('RUNNER_TEMP')) as temporary:
        work = Path(temporary)
        os.chmod(work, 0o700)
        with tarfile.open(archive) as bundle:
            entry = bundle.getmember('gitleaks')
            if not entry.isfile():
                raise ValueError('Expected scanner executable')
            with bundle.extractfile(entry) as source:
                executable = work / 'gitleaks'
                executable.write_bytes(source.read())
        executable.chmod(0o700)
        if args.hosted:
            output = work
        else:
            output = ROOT / 'local/verification/security' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
            output.mkdir(parents=True, mode=0o700)
        report = output / 'findings.json'
        report.touch(mode=0o600, exist_ok=False)
        log = output / 'scanner.log'
        with log.open('xb') as stream:
            os.chmod(log, 0o600)
            result = subprocess.run([str(executable), 'git', str(ROOT), '--log-opts=--all', '--redact=100',
                                     '--report-format=json', '--report-path', str(report), '--no-banner'],
                                    stdout=stream, stderr=stream, timeout=600)
        report.chmod(0o600)
        if result.returncode not in (0, 1) or not report.stat().st_size:
            raise ValueError('History scan incomplete; no pass claimed')
        findings = json.loads(report.read_text())
        if not isinstance(findings, list) or (result.returncode == 1 and not findings):
            raise ValueError('Invalid/incomplete scanner report')
        print(f'History scan completed; findings requiring private review: {len(findings)}. Values and locations withheld.')
        return 1 if findings else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        sys.exit('History scan failed/incomplete. Private diagnostics are not printed; no pass claimed.')
