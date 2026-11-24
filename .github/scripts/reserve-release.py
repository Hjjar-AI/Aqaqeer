"""Reserve a shared debug/production version using a new durable Git tag before packaging."""

import json
import os
import re
import subprocess
import sys

from reserve_release_registry import remote_codes


def api(path, payload=None):
    arguments = ['gh', 'api', path]
    if payload is not None:
        arguments += ['--method', 'POST', '--input', '-']
    else:
        arguments += ['--paginate', '--slurp']
    result = subprocess.run(arguments, input=json.dumps(payload) if payload is not None else None,
                            text=True, capture_output=True, timeout=60)
    if result.returncode:
        status = re.search(r'\(HTTP (\d{3})\)', result.stderr)
        code = status.group(1) if status else None
        if code in ('401', '403'):
            reason = f'permission/authentication denied (HTTP {code}); inspect token permissions and repository rules'
        elif code in ('409', '422'):
            reason = f'reservation conflict or rejected request (HTTP {code}); inspect existing reservations and the requested code'
        else:
            reason = 'request failed; inspect repository access and service availability'
        raise ValueError(f'Version registry API: {reason}. No reservation is retried automatically.')
    return json.loads(result.stdout)


def main():
    value = os.environ.get('CI_RELEASE_CODE', '')
    if os.environ['GITHUB_RUN_ATTEMPT'] != '1':
        raise ValueError('Start a new workflow dispatch with a new code instead of rerunning a version reservation.')
    if os.environ['GITHUB_REF'] != 'refs/heads/' + os.environ['CI_DEFAULT_BRANCH']:
        raise ValueError('APK distribution requires the default branch.')
    repository = os.environ['GITHUB_REPOSITORY']
    codes = remote_codes(repository)
    previous = max(codes, default=0)
    automatic = not value
    if not value:
        value = str(previous + 1)
    if not re.fullmatch(r'[1-9][0-9]*', value) or not 1 <= int(value) <= 2_099_999_999:
        raise ValueError('Choose a positive version code below 2100000000.')
    if codes and int(value) <= max(codes):
        raise ValueError('Version must exceed every android-initial-build-N reservation, including failed or unpublished attempts.')
    api(f'repos/{repository}/git/refs', {'ref': f'refs/tags/android-initial-build-{value}',
                                       'sha': os.environ['GITHUB_SHA']})
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'code={value}\n')
    summary_path = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary_path:
        previous_label = f'0.0.{previous} (code {previous})' if previous else 'None — initial sequence'
        with open(summary_path, 'a', encoding='utf-8') as summary:
            summary.write('### APK version\n\n'
                          '| Item | Value |\n| --- | --- |\n'
                          f'| Previous shared reservation | {previous_label} |\n'
                          f'| This build | **0.0.{value} (code {value})** |\n'
                          f'| Selection | {"Automatic" if automatic else "Manual override"} |\n'
                          f'| Source commit | `{os.environ["GITHUB_SHA"]}` |\n\n'
                          'The version is selected from the live registry when this reservation job runs. '
                          'Later pushes do not change this run’s source commit. '
                          'A reservation remains consumed if a later build or publication step fails.\n')
    print(f'Reserved android-initial-build-{value}. Preserve this tag even if packaging/publication fails.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
