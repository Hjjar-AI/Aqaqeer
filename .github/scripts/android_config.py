"""Read literal SDK declarations without invoking Gradle or changing app versions."""

import argparse
import json
import os
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]


def android_config():
    source = (ROOT / 'app/build.gradle.kts').read_text()
    # This intentionally supports the current literal Kotlin DSL only. A DSL change
    # must update this reader rather than silently selecting a stale SDK fallback.
    source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)

    def integer(name):
        declarations = re.findall(r'\b' + name + r'\s*=', source)
        values = re.findall(r'^\s*' + name + r'\s*=\s*(\d+)\s*$', source, re.M)
        if len(declarations) != 1 or len(values) != 1:
            raise ValueError(f'Expected one literal {name} declaration')
        return int(values[0])

    compile_blocks = re.findall(r'\bcompileSdk\s*\{\s*version\s*=\s*release\((\d+)\)\s*\{\s*minorApiLevel\s*=\s*(\d+)\s*\}\s*\}', source)
    if len(re.findall(r'\bcompileSdk\b', source)) != 1 or len(compile_blocks) != 1:
        raise ValueError('Unsupported or ambiguous compileSdk declaration')
    major, minor = map(int, compile_blocks[0])
    minimum, target = integer('minSdk'), integer('targetSdk')
    if not 1 <= minimum <= target <= major or minor < 0:
        raise ValueError('Invalid Android SDK bounds')
    tools = json.loads((ROOT / '.github/android-toolchain.json').read_text())['buildTools']
    if not isinstance(tools, str) or not re.fullmatch(r'\d+\.\d+\.\d+', tools):
        raise ValueError('Invalid build-tools version')
    return {'minimum': minimum, 'target': target, 'compile': major, 'minor': minor,
            'platform': f'android-{major}.{minor}', 'buildTools': tools}


def build_tools():
    return Path(os.environ['ANDROID_HOME']) / 'build-tools' / android_config()['buildTools']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--github-env', action='store_true')
    args = parser.parse_args()
    config = android_config()
    if args.github_env:
        with Path(os.environ['GITHUB_ENV']).open('a') as stream:
            stream.write(f"CI_ANDROID_PLATFORM={config['platform']}\n")
            stream.write(f"CI_ANDROID_BUILD_TOOLS={config['buildTools']}\n")
    else:
        print(json.dumps(config, indent=2))


if __name__ == '__main__':
    main()
