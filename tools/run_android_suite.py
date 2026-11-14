#!/usr/bin/env python3
"""One initial unit/device batch, or an explicitly supplied failed-part retry; never runs lint."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import shutil
import time
import xml.etree.ElementTree as ET
import subprocess
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
ADB = '/opt/android-shared/Sdk/platform-tools/adb'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--defer-live', action='store_true', help='Keep live test as an explicit missing-prerequisite failure; run independent checks.')
    parser.add_argument('--retry', nargs=argparse.REMAINDER, help='Exact failed Gradle task(s) and filters; omits initial batch.')
    args = parser.parse_args()
    run = ROOT / 'local/verification/runs' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    run.mkdir(parents=True, exist_ok=False)
    secret = ROOT / 'local/verification/credentials/telegram-test-config.local.json'
    if not args.defer_live and stat.S_IMODE(secret.stat().st_mode) != 0o600:
        raise RuntimeError('Credential file must have mode 0600')
    config = {} if args.defer_live else json.loads(secret.read_bytes())
    marker = uuid.uuid4().hex
    remote_secret = f'/data/local/tmp/aqaqeer-suite-{marker}.json'
    remote_owned = remote_secret + '.owned'

    def adb(*values, **kwargs):
        return subprocess.run([ADB, '-s', args.serial, *values], check=True, **kwargs)

    def api(method, values=None):
        request = urllib.request.Request('https://api.telegram.org/bot' + config['botToken'] + '/' + method,
                                         data=urllib.parse.urlencode(values or {}).encode())
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                value = json.load(response)
            if not value.get('ok'):
                raise RuntimeError('API rejected request')
            return value['result']
        except Exception:
            raise RuntimeError('Telegram operation failed: ' + method) from None

    device = adb('shell', 'getprop', 'ro.kernel.qemu', capture_output=True, text=True).stdout.strip()
    if device == '1' or args.serial.startswith('emulator-'):
        raise RuntimeError('A physical phone is required')
    if not args.defer_live:
        bot = api('getMe')
        chat = api('getChat', {'chat_id': config['chatId']})
        member = api('getChatMember', {'chat_id': config['chatId'], 'user_id': bot['id']})
        if not bot.get('is_bot') or chat.get('username') or chat.get('type') not in ('group', 'supergroup'):
            raise RuntimeError('Dedicated private test group required')
        if chat.get('pinned_message'):
            raise RuntimeError('Dedicated test group must have an empty pin; existing pin left untouched')
        if member.get('status') != 'administrator' or not member.get('can_pin_messages'):
            raise RuntimeError('Test bot requires administrator pin permissions')
    provenance = {'serial': args.serial, 'api': adb('shell','getprop','ro.build.version.sdk',capture_output=True,text=True).stdout.strip(),
                  'commit': subprocess.run(['git','rev-parse','HEAD'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.strip(),
                  'dirty': True, 'initial': not bool(args.retry), 'lintRun': False}
    (run / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    counter = ROOT / 'local/versioning/last-code.txt'
    code_before = counter.read_bytes() if counter.exists() else None
    source_paths = [p for folder in ('app/src', 'gradle') for p in (ROOT / folder).rglob('*')
                    if p.is_file() and p.suffix in {'.kt', '.java', '.xml', '.toml'}]
    source_paths += [ROOT / p for p in ('app/build.gradle.kts', 'build.gradle.kts', 'gradle.properties')]
    (run / 'source-sha256.json').write_text(json.dumps({str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                                                      for p in sorted(source_paths)}, indent=2) + '\n')
    exit_code = 1
    started_ns = time.time_ns()
    try:
        if not args.defer_live:
            adb('push', str(secret), remote_secret, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            adb('shell', 'chmod', '600', remote_secret, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        command = ['./gradlew', '--gradle-user-home', '/opt/android-shared/aqaqeer/gradle-home',
                   '-I', 'local/verification/tools/verification.init.gradle', '--console=plain', '--continue', '--offline']
        if not args.defer_live:
            command += ['-Pandroid.testInstrumentationRunnerArguments.suiteSecretPath=' + remote_secret]
        command += args.retry or ['test', 'connectedAndroidTest']
        (run / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        env = dict(os.environ, ANDROID_SERIAL=args.serial)
        started_ns = time.time_ns()
        with (run / 'gradle.log').open('w') as log:
            result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        exit_code = result.returncode
        for folder in ('test-results', 'outputs/androidTest-results/connected'):
            source = ROOT / 'app/build' / folder
            if source.exists():
                for report in source.rglob('TEST-*.xml'):
                    if report.stat().st_mtime_ns >= started_ns:
                        destination = run / folder / report.relative_to(source)
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copyfile(report, destination)
        totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
        for report in run.rglob('TEST-*.xml'):
            suite = ET.parse(report).getroot()
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
        (run / 'counts.json').write_text(json.dumps(totals, indent=2) + '\n')
        print(json.dumps(totals), flush=True)
    finally:
        # Preserve fresh per-case logs even when instrumentation never emits XML.
        source = ROOT / 'app/build/outputs/androidTest-results/connected'
        if source.exists():
            for report in source.rglob('logcat-*'):
                if report.stat().st_mtime_ns >= started_ns:
                    destination = run / 'device-partial' / report.relative_to(source)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    content = report.read_text(errors='replace')
                    if config.get('botToken'):
                        content = content.replace(config['botToken'], '[REDACTED]')
                    destination.write_text(content)
        # Runner.finish may never execute after process death. Recover only the
        # test-owned record from the isolated application, using shell exit status.
        owned_ids = set()
        for package in ('com.aqaqeer.app.verification.creation.debug',
                        'com.aqaqeer.app.demo.verification.creation.debug'):
            installed = subprocess.run([ADB, '-s', args.serial, 'shell', 'pm', 'path', package], capture_output=True, text=True)
            if 'package:' not in installed.stdout:
                continue
            adb('shell', 'run-as', package, 'rm', '-f',
                'no_backup/telegram-test-config.local.json', stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            record = subprocess.run([ADB, '-s', args.serial, 'shell', 'run-as', package,
                                     'cat', 'no_backup/telegram-test-owned-message-ids.json'], capture_output=True, text=True)
            if record.returncode == 0 and record.stdout.strip():
                values = json.loads(record.stdout)
                if not isinstance(values, list) or not all(type(n) is int and n > 0 for n in values):
                    raise RuntimeError('Invalid app ownership record; cleanup requires review')
                owned_ids.update(values)
            elif 'No such file or directory' not in record.stderr + record.stdout:
                raise RuntimeError('Cannot read app ownership record; cleanup requires review')
        owned_result = subprocess.run([ADB,'-s',args.serial,'shell','cat',remote_owned],capture_output=True,text=True)
        if owned_result.returncode == 0 and owned_result.stdout.strip():
            owned = json.loads(owned_result.stdout)
            if not isinstance(owned,list) or not all(isinstance(n,int) and n > 0 for n in owned):
                raise RuntimeError('Invalid ownership record; cleanup requires review')
            owned_ids.update(owned)
        adb('shell', 'rm', '-f', remote_secret, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        if owned_ids:
            owned = sorted(owned_ids)
            (run / 'owned-message-ids.json').write_text(json.dumps(owned) + '\n')
            chat = api('getChat', {'chat_id':config['chatId']})
            pin = chat.get('pinned_message',{}).get('message_id')
            if pin in owned:
                api('unpinChatMessage', {'chat_id':config['chatId'],'message_id':pin})
            for message in owned:
                api('deleteMessage', {'chat_id':config['chatId'],'message_id':message})
        for package in ('com.aqaqeer.app.verification.creation.debug',
                        'com.aqaqeer.app.demo.verification.creation.debug'):
            installed = subprocess.run([ADB, '-s', args.serial, 'shell', 'pm', 'path', package], capture_output=True, text=True)
            if 'package:' in installed.stdout:
                adb('shell', 'run-as', package, 'rm', '-f',
                    'no_backup/telegram-test-config.local.json', 'no_backup/telegram-test-owned-message-ids.json',
                    stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        (run / 'cleanup.json').write_text(json.dumps({'ownedMessagesRemoved': len(owned_ids), 'phoneCredentialsRemoved': True}) + '\n')
        adb('shell','rm','-f',remote_secret,remote_owned,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        if (counter.read_bytes() if counter.exists() else None) != code_before:
            raise RuntimeError('Version counter changed unexpectedly')
        (run / 'result.json').write_text(json.dumps({'gradleExit':exit_code,'lintRun':False}) + '\n')
        print('Evidence: ' + str(run), flush=True)
    return exit_code


if __name__ == '__main__':
    raise SystemExit(main())
