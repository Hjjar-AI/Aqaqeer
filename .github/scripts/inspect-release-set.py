"""Inspect each selected distribution output without signing."""
import json
import os
from pathlib import Path
import subprocess
import sys


def main():
    source = Path('app/build/outputs/apk/standard/release').resolve()
    metadata = json.loads((source / 'output-metadata.json').read_text())
    if metadata.get('applicationId') != 'com.aqaqeer.app':
        raise ValueError('Expected production metadata')
    codes = {item['versionCode'] for item in metadata['elements']}
    if len(codes) != 1 or type(next(iter(codes))) is not int:
        raise ValueError('Distribution metadata version mismatch')
    code = next(iter(codes))
    if not 1 <= code <= 2_099_999_999 or any(item['versionName'] != f'0.0.{code}' for item in metadata['elements']):
        raise ValueError('Distribution metadata name/code mismatch')
    seen = set()
    for item in metadata['elements']:
        filters = item.get('filters', [])
        if filters and (len(filters) != 1 or filters[0]['filterType'] != 'ABI'):
            raise ValueError('Unexpected APK filter')
        abi = filters[0]['value'] if filters else 'universal'
        if abi not in ('universal', 'armeabi-v7a', 'arm64-v8a'):
            continue
        apk = (source / item['outputFile']).resolve()
        if abi in seen or apk.parent != source or not apk.is_file():
            raise ValueError('Duplicate architecture/invalid APK path')
        seen.add(abi)
        environment = dict(os.environ, CI_RELEASE_APK_PATH=str(apk), CI_RELEASE_ABI=abi,
                           CI_RELEASE_INSPECTION_DIRECTORY=f'ci-output/release/{abi}',
                           AQAQEER_EXPECTED_RELEASE_CODE=str(code))
        subprocess.run([sys.executable, '.github/scripts/check-release.py'], env=environment, check=True)
    if seen != {'universal', 'armeabi-v7a', 'arm64-v8a'}:
        raise ValueError('Distribution APK set incomplete')


if __name__ == '__main__':
    main()
