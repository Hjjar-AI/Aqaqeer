#!/usr/bin/env python3
"""Sign one hosted debug APK with the permanent, separate debug identity."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile

from sign_release import command
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.github/scripts'))
from android_config import build_tools


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('flavor', choices=('standard', 'demo'))
    args = parser.parse_args()
    if os.environ['GITHUB_REF'] != 'refs/heads/' + os.environ['CI_DEFAULT_BRANCH']:
        raise ValueError('Default branch required')
    expected = os.environ['AQAQEER_DEBUG_CERT_SHA256'].replace(':', '').strip().lower()
    if not re.fullmatch(r'[0-9a-f]{64}', expected):
        raise ValueError('Invalid expected debug certificate')
    package = 'com.aqaqeer.app' + ('.demo' if args.flavor == 'demo' else '') + '.debug'
    source = ROOT / 'unsigned-debug'
    metadata = json.loads((source / 'output-metadata.json').read_text())
    if metadata['applicationId'] != package or len(metadata['elements']) != 1:
        raise ValueError('Unexpected debug package/output set')
    item = metadata['elements'][0]
    code = int(os.environ['CI_BUILD_CODE'])
    version_name = f'0.0.{code}' + ('-demo' if args.flavor == 'demo' else '')
    if item['versionCode'] != code or item.get('versionName') != version_name or item.get('filters'):
        raise ValueError('Debug version/architecture differs from reservation')
    apk = (source / item['outputFile']).resolve()
    if apk.parent != source.resolve() or not apk.is_file():
        raise ValueError('Invalid APK path')
    destination = ROOT / 'release-files' / args.flavor
    destination.mkdir(parents=True, exist_ok=False)
    tools = build_tools()
    with tempfile.TemporaryDirectory(dir=os.environ['RUNNER_TEMP'], prefix='aqaqeer-debug-') as temporary:
        key = Path(temporary) / 'debug.keystore'
        with key.open('xb') as stream:
            os.chmod(key, 0o600)
            stream.write(base64.b64decode(''.join(os.environ['AQAQEER_DEBUG_KEYSTORE_BASE64'].split()), validate=True))
        signed = Path(temporary) / f'Aqaqeer-{args.flavor}-v0.0.{code}-debug.apk'
        command([str(tools / 'apksigner'), 'sign', '--ks', str(key),
                 '--ks-key-alias', os.environ['AQAQEER_DEBUG_KEY_ALIAS'],
                 '--ks-pass', 'env:AQAQEER_DEBUG_STORE_PASSWORD', '--key-pass', 'env:AQAQEER_DEBUG_KEY_PASSWORD',
                 '--v4-signing-enabled', 'false', '--out', str(signed), str(apk)])
        signer = command([str(tools / 'apksigner'), 'verify', '--print-certs', str(signed)])
        fingerprints = re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]+)', signer)
        badging = command([str(tools / 'aapt'), 'dump', 'badging', str(signed)])
        if fingerprints != [expected] or not badging.startswith(f"package: name='{package}' "):
            raise ValueError('Debug signer/package mismatch')
        if f"versionCode='{code}'" not in badging or f"versionName='{version_name}'" not in badging or 'application-debuggable' not in badging:
            raise ValueError('Unexpected debug version/build type')
        command([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(signed)])
        shutil.copyfile(signed, destination / signed.name)
        receipt = dict(flavor=args.flavor, package=package, versionCode=code, versionName=version_name, certificateSha256=expected,
                       apk=signed.name, sourceCommit=os.environ['GITHUB_SHA'], workflowRun=os.environ['GITHUB_RUN_ID'],
                       workflowAttempt=os.environ['GITHUB_RUN_ATTEMPT'])
        (destination / f'{args.flavor}-debug-info.json').write_text(json.dumps(receipt, indent=2) + '\n')
        (destination / f'{args.flavor}-SHA256SUMS.txt').write_text(''.join(
            f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n'
            for path in sorted(destination.iterdir()) if path.is_file()))


if __name__ == '__main__':
    try:
        main()
    except Exception:
        sys.exit('Debug signing failed; check configuration and preserve existing installations. No private diagnostics printed.')
