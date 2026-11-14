#!/usr/bin/env python3
"""Build selected debug flavors independently through the normal version-reserving wrapper."""

import argparse
import os
from pathlib import Path
import shlex
import subprocess


ROOT = Path(__file__).resolve().parents[1]
TASKS = {"standard": ":app:assembleStandardDebug", "demo": ":app:assembleDemoDebug"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("flavor", choices=(*TASKS, "both"))
    parser.add_argument("--offline", action="store_true", help="Use existing Gradle dependencies only")
    parser.add_argument("--no-export", action="store_true", help="Do not copy the APK to Desktop")
    parser.add_argument("--print-command", action="store_true", help="Show the command without executing or reserving a version")
    args = parser.parse_args()
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(wrapper), "--no-daemon", "--console=plain"]
    if args.offline:
        command += ["--offline", "--no-configuration-cache"]
    if args.no_export:
        command.append("-Paqaqeer.exportApks=false")
    flavors = tuple(TASKS) if args.flavor == "both" else (args.flavor,)
    outcome = 0
    for flavor in flavors:
        selected = command + [TASKS[flavor]]
        print(subprocess.list2cmdline(selected) if os.name == "nt" else shlex.join(selected), flush=True)
        if args.print_command:
            continue
        try:
            result = subprocess.run(selected, cwd=ROOT, check=False).returncode
        except KeyboardInterrupt:
            return 130
        # Preserve failure status while still attempting the other requested flavor.
        if result:
            outcome = result
    return outcome


if __name__ == "__main__":
    raise SystemExit(main())
