"""Shared read-only access to the durable version registry."""
import json
import re
import subprocess


def remote_codes(repository):
    if not re.fullmatch(r'[\w.-]+/[\w.-]+', repository):
        raise ValueError('Invalid repository')
    result = subprocess.run(['gh', 'api', '--paginate', '--slurp',
                             f'repos/{repository}/git/matching-refs/tags/android-build-'],
                            capture_output=True, text=True, timeout=60, check=True)
    return [int(match[1]) for page in json.loads(result.stdout) for ref in page
            if (match := re.fullmatch(r'refs/tags/android-build-([1-9][0-9]*)', ref['ref']))]
