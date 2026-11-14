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
        raise ValueError('Version registry API failed; inspect repository permissions/rules. No reservation is retried automatically.')
    return json.loads(result.stdout)


def main():
    value = os.environ.get('CI_RELEASE_CODE', '')
    if os.environ['GITHUB_RUN_ATTEMPT'] != '1':
        raise ValueError('Start a new workflow dispatch with a new code instead of rerunning a version reservation.')
    if os.environ['GITHUB_REF'] != 'refs/heads/' + os.environ['CI_DEFAULT_BRANCH']:
        raise ValueError('APK distribution requires the default branch.')
    repository = os.environ['GITHUB_REPOSITORY']
    codes = remote_codes(repository)
    if not value:
        value = str(max(codes, default=0) + 1)
    if not re.fullmatch(r'[1-9][0-9]*', value) or not 1 <= int(value) <= 2_099_999_999:
        raise ValueError('Choose a positive version code below 2100000000.')
    if codes and int(value) <= max(codes):
        raise ValueError('Version must exceed every android-build-N reservation, including failed or unpublished attempts.')
    api(f'repos/{repository}/git/refs', {'ref': f'refs/tags/android-build-{value}',
                                       'sha': os.environ['GITHUB_SHA']})
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'code={value}\n')
    print(f'Reserved android-build-{value}. Preserve this tag even if packaging/publication fails.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
