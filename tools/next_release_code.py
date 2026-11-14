#!/usr/bin/env python3
"""Read-only suggestion; does not reserve a version or alter local state."""
import argparse
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.github/scripts'))
sys.path.insert(0, str(ROOT / 'gradle'))
from reserve_release_registry import remote_codes
from build_version import read_code, MAX_CODE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--distributed-maximum', type=int, default=0)
    args = parser.parse_args()
    if not 0 <= args.distributed_maximum <= MAX_CODE:
        raise ValueError('Invalid distributed maximum')
    result = subprocess.run(['gh', 'repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner'],
                            cwd=ROOT, text=True, capture_output=True, timeout=30, check=True)
    code = max(read_code(ROOT / 'local/versioning/last-code.txt'), args.distributed_maximum,
               max(remote_codes(result.stdout.strip()), default=0)) + 1
    if code > MAX_CODE:
        raise ValueError('Version sequence exhausted; do not reset it')
    print(f'Next suggested code: {code}. This is not a reservation.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError):
        sys.exit('Cannot determine the next code. Check GitHub access and preserve existing counters.')
