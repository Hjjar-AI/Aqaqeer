#!/usr/bin/env python3
"""Sign and inspect an already-built standard release APK, locally or on CI."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.github/scripts'))
from android_config import build_tools


def command(arguments, environment=None):
    # Signing diagnostics can contain private paths/aliases. Never forward them.
    result = subprocess.run(arguments, cwd=ROOT, env=environment, capture_output=True,
                            text=True, timeout=180)
    if result.returncode:
        raise ValueError('Signing/inspection command failed; check the key, passwords, certificate and release prerequisites.')
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-directory', type=Path, default=ROOT / 'local/release')
    args = parser.parse_args()
    os.chdir(ROOT)
    required = ('AQAQEER_RELEASE_KEY_ALIAS', 'AQAQEER_RELEASE_STORE_PASSWORD',
                'AQAQEER_RELEASE_KEY_PASSWORD', 'AQAQEER_RELEASE_CERT_SHA256')
    if any(not os.environ.get(name) for name in required):
        raise ValueError('Missing release signing configuration; see docs/build.md.')
    expected = os.environ['AQAQEER_RELEASE_CERT_SHA256'].replace(':', '').strip().lower()
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        raise ValueError('Expected certificate must be a public SHA-256 certificate fingerprint.')
    source = ROOT / 'app/build/outputs/apk/standard/release'
    metadata = json.loads((source / 'output-metadata.json').read_text())
    elements = metadata['elements']
    if metadata['applicationId'] != 'com.aqaqeer.app' or len(elements) != 1:
        raise ValueError('Expected one standard production release APK.')
    element = elements[0]
    code = element['versionCode']
    if type(code) is not int or not 1 <= code <= 2_099_999_999:
        raise ValueError('Invalid packaged version code.')
    apk = (source / element['outputFile']).resolve()
    if apk.parent != source.resolve() or not apk.is_file():
        raise ValueError('Invalid release APK path.')
    requested = os.environ.get('AQAQEER_EXPECTED_RELEASE_CODE', '')
    if requested and requested != str(code):
        raise ValueError('Release metadata differs from the reserved version code.')
    local_key = os.environ.get('AQAQEER_RELEASE_KEYSTORE', '')
    encoded_key = os.environ.get('AQAQEER_RELEASE_KEYSTORE_BASE64', '')
    if bool(local_key) == bool(encoded_key):
        raise ValueError('Provide exactly one local keystore path or Base64 keystore secret.')
    tools = build_tools()
    args.output_directory.mkdir(parents=True, exist_ok=True)
    destination = args.output_directory / f'v0.0.{code}'
    if destination.exists():
        raise ValueError('Release output already exists; preserve it and choose a new output directory.')
    with tempfile.TemporaryDirectory(prefix='aqaqeer-release-', dir=os.environ.get('RUNNER_TEMP')) as temporary:
        workspace = Path(temporary)
        if encoded_key:
            key = workspace / 'release.keystore'
            with key.open('xb') as handle:
                os.chmod(key, 0o600)
                handle.write(base64.b64decode(''.join(encoded_key.split()), validate=True))
        else:
            key = Path(local_key).expanduser().resolve()
            if not key.is_file():
                raise ValueError('Local release keystore is missing.')
        signed = workspace / f'Aqaqeer-v0.0.{code}-release.apk'
        command([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)])
        command([str(tools / 'apksigner'), 'sign', '--ks', str(key),
                 '--ks-key-alias', os.environ['AQAQEER_RELEASE_KEY_ALIAS'],
                 '--ks-pass', 'env:AQAQEER_RELEASE_STORE_PASSWORD',
                 '--key-pass', 'env:AQAQEER_RELEASE_KEY_PASSWORD',
                 '--v4-signing-enabled', 'false', '--out', str(signed), str(apk)])
        environment = {name: value for name, value in os.environ.items()
                       if not name.startswith('AQAQEER_RELEASE_')}
        environment.update(CI_RELEASE_APK_PATH=str(signed), CI_RELEASE_CERT_SHA256=expected,
                           AQAQEER_EXPECTED_RELEASE_CODE=str(code))
        command([sys.executable, str(ROOT / '.github/scripts/check-release.py')], environment)
        inspection = ROOT / 'ci-output/release/inspection.json'
        report = json.loads(inspection.read_text())
        if report.get('certificateSha256') != [expected] or report.get('versionCode') != code:
            raise ValueError('Signed output does not match the expected certificate/version.')
        report['apk'] = signed.name
        report['sourceCommit'] = os.environ.get('GITHUB_SHA', '')
        report['workflowRun'] = os.environ.get('GITHUB_RUN_ID', '')
        destination.mkdir()
        shutil.copyfile(signed, destination / signed.name)
        (destination / 'release-info.json').write_text(json.dumps(report, indent=2) + '\n')
        files = sorted(destination.iterdir())
        (destination / 'SHA256SUMS.txt').write_text(''.join(
            f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in files))
    print(f'Signed release and certificate/version checks completed: {destination}')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.SubprocessError):
        print('Release signing failed. Check signing settings and ci-output/release/ inspection evidence; no secrets are printed.', file=sys.stderr)
        raise SystemExit(1)
