"""Shared read-only access to the durable version registry."""
import json
import re
import subprocess


def remote_codes(repository):
    if not re.fullmatch(r'[\w.-]+/[\w.-]+', repository):
        raise ValueError('Invalid repository')
    result = subprocess.run(['gh', 'api', '--paginate',
                             f'repos/{repository}/git/matching-refs/tags/android-initial-build-'],
                            capture_output=True, text=True, timeout=60, check=True)
    # --paginate emits successive JSON pages. Parse them without requiring the newer
    # --slurp flag, so the read-only workstation helper also supports older gh clients.
    remaining = result.stdout.lstrip()
    pages = []
    decoder = json.JSONDecoder()
    while remaining:
        page, end = decoder.raw_decode(remaining)
        if not isinstance(page, list) or any(not isinstance(ref, dict) or not isinstance(ref.get('ref'), str) for ref in page):
            raise ValueError('Invalid version registry response')
        pages.append(page)
        remaining = remaining[end:].lstrip()
    if not pages:
        raise ValueError('Missing version registry response')
    return [int(match[1]) for page in pages for ref in page
            if (match := re.fullmatch(r'refs/tags/android-initial-build-([1-9][0-9]*)', ref['ref']))]
