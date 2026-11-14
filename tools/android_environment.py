"""Portable prerequisite discovery; no downloads, cache replacement or device commands."""
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def add_options(parser):
    parser.add_argument('--sdk-root', type=Path, help='SDK root; otherwise ANDROID_HOME/ANDROID_SDK_ROOT')
    parser.add_argument('--adb', type=Path, help='Explicit adb executable; otherwise SDK/PATH')
    parser.add_argument('--gradle-user-home', type=Path, help='Existing Gradle home; otherwise environment/shared wrapper defaults')
    parser.add_argument('--verification-init', type=Path, default=ROOT / 'local/verification/tools/verification.init.gradle',
                        help='Required campaign-specific init script; not supplied by an ordinary checkout')


def prepare(args):
    sdk = args.sdk_root or next((Path(os.environ[key]) for key in ('ANDROID_HOME', 'ANDROID_SDK_ROOT') if os.environ.get(key)), None)
    if sdk is None:
        raise ValueError('Set ANDROID_HOME or --sdk-root to an existing SDK')
    sdk = sdk.expanduser().resolve()
    if not sdk.is_dir():
        raise ValueError('Configured SDK is absent; investigate ownership/prerequisites without replacing it')
    os.environ['ANDROID_HOME'] = str(sdk)
    candidate = args.adb or (sdk / 'platform-tools/adb')
    if args.adb and not candidate.is_file():
        raise ValueError('Explicit adb path is missing; no fallback selected')
    adb = str(candidate) if candidate.is_file() else shutil.which('adb')
    if not adb or not os.access(adb, os.X_OK):
        raise ValueError('adb is missing or inaccessible; no download attempted')
    init = args.verification_init.expanduser().resolve()
    if not init.is_file():
        raise ValueError('Campaign init script missing. Supply --verification-init; no device/build work started')
    home = args.gradle_user_home or (Path(os.environ['GRADLE_USER_HOME']) if os.environ.get('GRADLE_USER_HOME') else None)
    gradle_args = []
    if home:
        home = home.expanduser().resolve()
        if not home.is_dir() or not os.access(home, os.R_OK | os.W_OK | os.X_OK):
            raise ValueError('Configured Gradle home missing/inaccessible; preserve it and diagnose access')
        gradle_args = ['--gradle-user-home', str(home)]
    return sdk, adb, init, gradle_args
