"""Launch a throwaway-signed release only on this workflow's owned ephemeral emulator."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

from android_config import build_tools

PACKAGE = 'com.aqaqeer.app'
SERIAL = 'emulator-5554'
AVD = 'aqaqeer-release-smoke'
OUTPUT = Path('ci-output/release-smoke')


def run(arguments, binary=False):
    result = subprocess.run(arguments, capture_output=True, text=not binary, timeout=60)
    if result.returncode:
        raise ValueError('Smoke command failed; no startup pass claimed')
    return result.stdout


def adb(*arguments, binary=False):
    return run(['adb', '-s', SERIAL, *arguments], binary=binary)


def emulator_identity():
    if (os.environ.get('GITHUB_ACTIONS') != 'true' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted'
            or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1'
            or adb('emu', 'avd', 'name').splitlines()[0] != AVD):
        raise ValueError('Owned GitHub-hosted emulator required; physical/local devices forbidden')


def installed_identity():
    paths = adb('shell', 'pm', 'path', PACKAGE).strip().splitlines()
    if len(paths) != 1 or not paths[0].startswith('package:/data/app/'):
        raise ValueError('Unexpected installed APK path/set')
    uid = adb('shell', 'cmd', 'package', 'list', 'packages', '-U', PACKAGE).strip()
    if not re.fullmatch(r'package:com\.aqaqeer\.app uid:[0-9]+', uid):
        raise ValueError('Unexpected package UID identity')
    return dict(path=paths[0], uid=uid, sha256=hashlib.sha256(adb('exec-out', 'cat', paths[0][8:], binary=True)).hexdigest())


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    emulator_identity()
    if adb('shell', 'pm', 'path', PACKAGE).strip():
        raise ValueError('Existing installation must be preserved')
    candidates = list(Path('smoke-apks').glob('*universal*.apk'))
    if len(candidates) != 1:
        raise ValueError('Expected one universal release APK')
    source = candidates[0]
    tools = build_tools()
    receipt = dict(serial=SERIAL, avd=AVD, package=PACKAGE, sourceCommit=os.environ['GITHUB_SHA'],
                   run=os.environ['GITHUB_RUN_ID'], attempt=os.environ['GITHUB_RUN_ATTEMPT'], startup='unexecuted', cleanup='unexecuted')
    installed = None
    with tempfile.TemporaryDirectory(dir=os.environ['RUNNER_TEMP'], prefix='release-smoke-') as temporary:
        key = Path(temporary) / 'smoke.keystore'
        signed = Path(temporary) / 'smoke.apk'
        environment = dict(os.environ, AQAQEER_SMOKE_PASSWORD='throwaway-ephemeral-only')
        generated = subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-alias', 'smoke',
                                    '-storepass:env', 'AQAQEER_SMOKE_PASSWORD', '-keypass:env', 'AQAQEER_SMOKE_PASSWORD',
                                    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=Aqaqeer CI smoke'],
                                   env=environment, capture_output=True, timeout=60)
        if generated.returncode:
            raise ValueError('Throwaway key generation failed')
        result = subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', 'smoke',
                                 '--ks-pass', 'env:AQAQEER_SMOKE_PASSWORD', '--key-pass', 'env:AQAQEER_SMOKE_PASSWORD',
                                 '--v4-signing-enabled', 'false', '--out', str(signed), str(source)],
                                env=environment, capture_output=True, timeout=60)
        if result.returncode:
            raise ValueError('Throwaway signing failed')
        signer = run([str(tools / 'apksigner'), 'verify', '--print-certs', str(signed)])
        receipt['certificateSha256'] = re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]+)', signer)
        receipt['artifactSha256'] = hashlib.sha256(signed.read_bytes()).hexdigest()
        badging = run([str(tools / 'aapt'), 'dump', 'badging', str(signed)])
        activity = re.search(r"launchable-activity: name='([^']+)'", badging)
        if not badging.startswith(f"package: name='{PACKAGE}' ") or 'application-debuggable' in badging or not activity:
            raise ValueError('Expected launchable production release')
        version = re.search(r"versionCode='([0-9]+)' versionName='([^']+)'", badging)
        if not version or len(receipt['certificateSha256']) != 1:
            raise ValueError('Missing artifact version/certificate identity')
        receipt.update(versionCode=int(version[1]), versionName=version[2])
        observed_pid = None
        # Persist the artifact/signer/run identity before installing anything.
        (OUTPUT / 'ownership.json').write_text(json.dumps(receipt, indent=2) + '\n')
        try:
            receipt['cleanup'] = 'installation attempted; ownership verification pending'
            adb('install', str(signed))
            installed = installed_identity()
            if installed['sha256'] != receipt['artifactSha256']:
                raise ValueError('Installed bytes differ from owned artifact')
            receipt['installation'] = installed
            (OUTPUT / 'ownership.json').write_text(json.dumps(receipt, indent=2) + '\n')
            launch = adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/' + activity[1])
            (OUTPUT / 'launch.txt').write_text(launch)
            if 'Status: ok' not in launch or 'Error' in launch:
                raise ValueError('Launch did not succeed')
            pid = adb('shell', 'pidof', PACKAGE).strip()
            if not re.fullmatch(r'[0-9]+', pid):
                raise ValueError('No single application process')
            observed_pid = pid
            for _ in range(30):
                time.sleep(1)
                if adb('shell', 'pidof', PACKAGE).strip() != pid:
                    raise ValueError('Application exited/restarted during startup observation')
            logs = adb('logcat', '-d', '--pid=' + pid, '-v', 'threadtime')
            (OUTPUT / 'startup.log').write_text(logs)
            (OUTPUT / 'startup.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            if 'FATAL EXCEPTION' in logs:
                raise ValueError('Startup crash observed')
            receipt['startup'] = 'passed: launch and 30-second process survival only'
        except Exception:
            receipt['startup'] = 'failed/incomplete'
            raise
        finally:
            # Capture failure evidence too, without clearing the shared log buffer.
            try:
                current = observed_pid or adb('shell', 'pidof', PACKAGE).strip()
                if re.fullmatch(r'[0-9]+', current):
                    (OUTPUT / 'startup.log').write_text(adb('logcat', '-d', '--pid=' + current, '-v', 'threadtime'))
                if not (OUTPUT / 'startup.png').exists():
                    (OUTPUT / 'startup.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            except Exception:
                receipt['failureCapture'] = 'unavailable'
            if installed is not None:
                try:
                    emulator_identity()
                    if installed_identity() != installed or installed['sha256'] != receipt['artifactSha256']:
                        receipt['cleanup'] = 'refused: installation ownership differs'
                    else:
                        adb('uninstall', PACKAGE)
                        receipt['cleanup'] = 'confirmed absent' if not adb('shell', 'pm', 'path', PACKAGE).strip() else 'failed: still installed'
                except Exception:
                    receipt['cleanup'] = 'failed/incomplete: preserve installation identity evidence'
            (OUTPUT / 'results.json').write_text(json.dumps(receipt, indent=2) + '\n')
            if installed is not None and receipt['cleanup'] != 'confirmed absent':
                raise ValueError('Owned installation cleanup incomplete')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        sys.exit('Release startup/cleanup failed or incomplete. Inspect retained evidence; no broader runtime pass claimed.')
