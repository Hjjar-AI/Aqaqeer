"""Inspect the newly shrunk standard release APK; never sign or publish it."""

import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile
import xml.etree.ElementTree as ET

from android_config import android_config, build_tools


MACHINES = {'armeabi-v7a': 40, 'arm64-v8a': 183, 'x86': 3, 'x86_64': 62}
ANDROID = '{http://schemas.android.com/apk/res/android}'
PUBLIC_PERMISSIONS = {'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'}
RECEIVER_PERMISSION = 'com.aqaqeer.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'


def inspect_manifest(text):
    manifest = ET.fromstring(text)
    if manifest.tag != 'manifest' or manifest.get('package') != 'com.aqaqeer.app':
        raise ValueError('Unexpected packaged manifest identity')
    requested = [node.get(ANDROID + 'name') for node in manifest
                 if node.tag in ('uses-permission', 'uses-permission-sdk-23', 'uses-permission-sdk-m')]
    if len(requested) != len(set(requested)) or set(requested) - {RECEIVER_PERMISSION} != PUBLIC_PERMISSIONS:
        raise ValueError('Unexpected release permission set')
    declarations = manifest.findall('permission')
    if any(node.get(ANDROID + 'name') != RECEIVER_PERMISSION for node in declarations) or len(declarations) > 1:
        raise ValueError('Unexpected custom permission declaration')
    if RECEIVER_PERMISSION in requested or declarations:
        if len(declarations) != 1:
            raise ValueError('AndroidX receiver permission lacks its signature declaration')
        protection = declarations[0].get(ANDROID + 'protectionLevel', '')
        if protection != 'signature':
            try:
                signature_only = int(protection, 0) == 2
            except ValueError:
                signature_only = False
            if not signature_only:
                raise ValueError('AndroidX receiver permission must be signature-protected')
    applications = manifest.findall('application')
    if len(applications) != 1:
        raise ValueError('Expected exactly one packaged application')
    for attribute in ('allowBackup', 'usesCleartextTraffic'):
        if applications[0].get(ANDROID + attribute) != 'false':
            raise ValueError(f'Release must explicitly disable {attribute}')
    return sorted(requested)


def inspect_elf(data, abi):
    if len(data) < 64 or data[:4] != b'\x7fELF' or data[5] != 1 or data[4] not in (1, 2):
        raise ValueError('Unsupported or invalid native ELF')
    if struct.unpack_from('<H', data, 18)[0] != MACHINES[abi]:
        raise ValueError('Native machine does not match APK ABI directory')
    wide = data[4] == 2
    if wide != (abi in ('arm64-v8a', 'x86_64')):
        raise ValueError('Native ELF bitness does not match APK ABI directory')
    offset = struct.unpack_from('<Q' if wide else '<I', data, 32 if wide else 28)[0]
    size, count = struct.unpack_from('<HH', data, 54 if wide else 42)
    if size < (56 if wide else 32) or count == 0:
        raise ValueError('Invalid ELF program header table')
    alignments = []
    for index in range(count):
        start = offset + index * size
        if start + size > len(data):
            raise ValueError('Truncated ELF program header')
        if struct.unpack_from('<I', data, start)[0] == 1:  # PT_LOAD
            alignment = struct.unpack_from('<Q' if wide else '<I', data, start + (48 if wide else 28))[0]
            alignments.append(alignment)
    if not alignments or (wide and any(value < 16384 for value in alignments)):
        raise ValueError('64-bit native load segments must support 16 KB pages')
    return alignments


def main():
    directory = Path(os.environ.get('CI_RELEASE_INSPECTION_DIRECTORY', 'ci-output/release'))
    directory.mkdir(parents=True, exist_ok=True)
    lines = ['## Release APK inspection', '']
    result = {}
    try:
        selected = os.environ.get('CI_RELEASE_APK_PATH')
        paths = [Path(selected)] if selected else list(Path('app/build/outputs/apk/standard/release').glob('*.apk'))
        if len(paths) != 1:
            raise ValueError('Expected exactly one fresh standard release APK')
        path = paths[0]
        result['apk'] = str(path)
        result['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
        config = android_config()
        tools = build_tools()
        badging = subprocess.run([str(tools / 'aapt'), 'dump', 'badging', str(path)], capture_output=True, text=True, check=True, timeout=30).stdout
        (directory / 'apk-badging.txt').write_text(badging)
        if not badging.startswith("package: name='com.aqaqeer.app' ") or 'application-debuggable' in badging:
            raise ValueError('Release package identity/debuggable validation failed')
        version = re.search(r"versionCode='([0-9]+)' versionName='([^']+)'", badging)
        if not version:
            raise ValueError('Missing packaged release version')
        result['versionCode'] = int(version[1])
        result['versionName'] = version[2]
        requested = os.environ.get('AQAQEER_EXPECTED_RELEASE_CODE', '')
        if requested and (not requested.isdecimal() or int(requested) != result['versionCode']):
            raise ValueError('Packaged release version does not match the reserved code')
        if (f"sdkVersion:'{config['minimum']}'" not in badging
                or f"targetSdkVersion:'{config['target']}'" not in badging):
            raise ValueError('Unexpected minimum/target SDK in release APK')
        analyzer = Path(os.environ['ANDROID_HOME']) / 'cmdline-tools/latest/bin/apkanalyzer'
        manifest = subprocess.run([str(analyzer), 'manifest', 'print', str(path)],
                                  capture_output=True, text=True, check=True, timeout=30).stdout
        (directory / 'packaged-manifest.xml').write_text(manifest)
        result['permissions'] = inspect_manifest(manifest)
        subprocess.run([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(path)], check=True, timeout=30)
        mapping = Path('app/build/outputs/mapping/standardRelease')
        for filename in ('mapping.txt', 'resources.txt'):
            if not (mapping / filename).is_file() or (mapping / filename).stat().st_size == 0:
                raise ValueError(f'Missing fresh R8/resource-shrinker evidence: {filename}')
        libraries = {}
        with zipfile.ZipFile(path) as apk:
            if corrupt := apk.testzip():
                raise ValueError('Corrupt APK entry: ' + corrupt)
            names = set(apk.namelist())
            required = {'AndroidManifest.xml', 'classes.dex', 'resources.arsc',
                        'assets/licenses/NotoSansArabic-OFL.txt', 'assets/SQLCipher-Android-LICENSE.txt',
                        'assets/Apache-2.0.txt', 'assets/THIRD-PARTY-NOTICES.txt', 'assets/runtime-components.txt'}
            if not required <= names:
                raise ValueError('Release is missing required code/resources/license entries')
            if any('telegram-test-config' in name or name.endswith('/v1-vectors.json') for name in names):
                raise ValueError('Verification-only fixture/credential file in release APK')
            for name in sorted(names):
                if name.startswith('lib/') and name.endswith('.so'):
                    abi = name.split('/')[1]
                    if abi not in MACHINES:
                        raise ValueError('Unexpected packaged native ABI: ' + abi)
                    libraries[name] = inspect_elf(apk.read(name), abi)
            expected_abi = os.environ.get('CI_RELEASE_ABI', 'universal')
            required_abis = tuple(MACHINES) if expected_abi == 'universal' else (expected_abi,)
            if any(abi not in MACHINES for abi in required_abis):
                raise ValueError('Invalid expected ABI')
            actual_abis = {name.split('/')[1] for name in libraries}
            if actual_abis != set(required_abis):
                raise ValueError('Native ABI set differs from the selected output')
            result['architecture'] = expected_abi
            for abi in required_abis:
                if f'lib/{abi}/libsqlcipher.so' not in libraries:
                    raise ValueError('SQLCipher is missing a required runtime ABI: ' + abi)
        result['nativeLoadAlignments'] = libraries
        signer = subprocess.run([str(tools / 'apksigner'), 'verify', '--print-certs', str(path)], capture_output=True, text=True, timeout=30)
        expected = os.environ.get('CI_RELEASE_CERT_SHA256', '').replace(':', '').lower().strip()
        if expected and not re.fullmatch(r'[a-f0-9]{64}', expected):
            raise ValueError('Expected release certificate SHA-256 must be 64 hex characters')
        if signer.returncode == 0:
            fingerprints = re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]+)', signer.stdout)
            result['certificateSha256'] = fingerprints
            if 'Android Debug' in signer.stdout or not expected or fingerprints != [expected]:
                raise ValueError('Signed release requires the original expected non-debug signing certificate')
            lines.append('APK signature verified and certificate matches CI_RELEASE_CERT_SHA256.')
        else:
            if 'DOES NOT VERIFY' not in signer.stderr + signer.stdout:
                raise ValueError('Signature inspection failed unexpectedly')
            result['signing'] = 'unsigned; production signing/update continuity unverified'
            if expected:
                raise ValueError('Release is unsigned; expected signing identity cannot be verified')
            print('::warning::Release APK is unsigned. Sign with the original private release key and verify its certificate before distribution.')
            lines.append('**Unsigned verification APK.** Production signing/update continuity remains a manual prerequisite; this artifact is not ready to install/distribute.')
        lines += ['', f'APK SHA-256: `{result["sha256"]}`', f'Native libraries inspected: {len(libraries)}',
                  'Packaged permissions, backup/cleartext policy, R8/resource evidence, APK resources/licenses, ABI/ELF/64-bit 16 KB and ZIP alignment checked.',
                  'Runtime behavior and update installation are not verified by this inspection.']
    except Exception:
        lines.append('Release inspection failed/incomplete; inspect the failed step. No release-readiness pass is claimed.')
        raise
    finally:
        (directory / 'inspection.json').write_text(json.dumps(result, indent=2) + '\n')
        text = '\n'.join(lines) + '\n'
        (directory / 'results.md').write_text(text)
        if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
            with Path(summary).open('a') as stream:
                stream.write(text)


if __name__ == '__main__':
    main()
