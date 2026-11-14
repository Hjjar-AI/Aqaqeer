"""Supply one version to Gradle before configuration-cache lookup (Python 3)."""

import contextlib
import os
from pathlib import Path
import re
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
MAX_CODE = 2_099_999_999


def packaging_requested(arguments):
    """Inspect full lifecycle names, skipping Gradle options' separate values."""
    value_options = {
        "-p", "--project-dir", "-g", "--gradle-user-home", "-I", "--init-script",
        "-x", "--exclude-task", "--include-build", "--project-cache-dir",
        "--console", "--warning-mode", "--max-workers", "--priority",
        "--configuration-cache-problems", "--dependency-verification",
        "--write-verification-metadata", "-M", "-D", "-P",
    }
    no_execution = {"--dry-run", "-m", "--help", "-h", "--version", "-v"}
    packaging = False
    skip_value = False
    directory = Path.cwd()
    directory_value = False
    property_value = False
    for argument in arguments:
        if skip_value:
            if directory_value:
                directory = Path(argument)
            if property_value and argument.split("=", 1)[0] in {
                "aqaqeer.buildCode", "org.gradle.project.aqaqeer.buildCode",
            }:
                raise ValueError("aqaqeer.buildCode is reserved for the wrapper.")
            skip_value = False
            directory_value = False
            property_value = False
            continue
        if argument in no_execution:
            return False
        if argument.startswith(("-Paqaqeer.buildCode", "-Dorg.gradle.project.aqaqeer.buildCode")):
            raise ValueError("aqaqeer.buildCode is reserved for the wrapper.")
        if argument in value_options:
            skip_value = True
            directory_value = argument in {"-p", "--project-dir"}
            property_value = argument in {"-P", "-D"}
            continue
        if argument.startswith("--project-dir="):
            directory = Path(argument.split("=", 1)[1])
        elif argument.startswith("-p") and len(argument) > 2:
            directory = Path(argument[2:])
        elif not argument.startswith("-"):
            task = argument.rsplit(":", 1)[-1]
            packaging |= task.lower().startswith(("assemble", "bundle", "install")) or task in {
                "build", "buildNeeded", "buildDependents",
            }
    if packaging and ROOT not in (directory.resolve(), *directory.resolve().parents):
        raise ValueError("Use the Aqaqeer wrapper inside this checkout or with -p pointing to it.")
    return packaging


def read_code(counter):
    if not counter.exists():
        return 0
    value = counter.read_text(encoding="utf-8").strip()
    if not re.fullmatch(r"[0-9]+", value) or not 0 <= int(value) <= MAX_CODE:
        raise ValueError("Invalid version counter; preserve it and investigate instead of resetting it.")
    return int(value)


@contextlib.contextmanager
def counter_lock(path):
    with path.open("a+b") as handle:
        if os.name == "nt":
            import msvcrt

            # Windows locks one existing byte; POSIX locks the entire same file.
            if handle.seek(0, os.SEEK_END) == 0:
                handle.write(b"\0")
                handle.flush()
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_LOCK, 1)
            try:
                yield
            finally:
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl

            # lockf uses POSIX record locks, matching the previous Java FileChannel lock.
            fcntl.lockf(handle, fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.lockf(handle, fcntl.LOCK_UN)


def build_code(arguments):
    directory = ROOT / "local/versioning"
    counter = directory / "last-code.txt"
    if not packaging_requested(arguments):
        return max(1, read_code(counter))
    directory.mkdir(parents=True, exist_ok=True)
    with counter_lock(directory / "counter.lock"):
        last = read_code(counter)
        if last == MAX_CODE:
            raise ValueError("Version counter exhausted; do not reset it.")
        minimum = os.environ.get("AQAQEER_MIN_BUILD_CODE", "1")
        if not re.fullmatch(r"[0-9]+", minimum) or not 1 <= int(minimum) <= MAX_CODE:
            raise ValueError("AQAQEER_MIN_BUILD_CODE must be a valid positive build code.")
        code = max(last + 1, int(minimum))
        descriptor, name = tempfile.mkstemp(prefix="next-code-", suffix=".tmp", dir=directory)
        temporary = Path(name)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
                output.write(f"{code}\n")
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, counter)
        finally:
            temporary.unlink(missing_ok=True)
    print(f"Aqaqeer build version: 0.0.{code} (code {code})", file=sys.stderr)
    return code


if __name__ == "__main__":
    try:
        print(build_code(sys.argv[1:]))
    except (OSError, ValueError) as error:
        print(f"Aqaqeer version reservation failed: {error}", file=sys.stderr)
        sys.exit(1)
