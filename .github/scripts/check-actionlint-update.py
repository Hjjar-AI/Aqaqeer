"""Report a stable upstream actionlint update as a patch; never execute or apply it."""
import difflib
import json
from pathlib import Path
import re
import urllib.request


def main():
    path = Path('.github/ci-tools.json')
    original = path.read_text()
    pins = json.loads(original)
    with urllib.request.urlopen('https://api.github.com/repos/rhysd/actionlint/releases/latest', timeout=30) as response:
        release = json.load(response)
    version = release['tag_name'].removeprefix('v')
    if release['prerelease'] or release['draft'] or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise ValueError('Stable actionlint release required')
    asset = next(a for a in release['assets'] if a['name'] == f'actionlint_{version}_linux_amd64.tar.gz')
    digest = asset.get('digest', '')
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', digest):
        raise ValueError('No official asset SHA-256 available; verify upstream manually')
    pins['actionlint'] = dict(version=version, sha256=digest.removeprefix('sha256:'))
    directory = Path('ci-output/actionlint-update')
    directory.mkdir(parents=True, exist_ok=True)
    patch = ''.join(difflib.unified_diff(original.splitlines(True), (json.dumps(pins, indent=2)+'\n').splitlines(True),
                                       fromfile='a/.github/ci-tools.json', tofile='b/.github/ci-tools.json'))
    (directory / 'proposed-update.patch').write_text(patch)
    (directory / 'results.md').write_text(f'Current pin: {json.loads(original)["actionlint"]["version"]}; upstream stable: {version}.\n'
                                        'Review the proposed patch; nothing was applied or executed.\n')
    print((directory / 'results.md').read_text())


if __name__ == '__main__':
    main()
