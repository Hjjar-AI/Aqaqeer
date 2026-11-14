#!/usr/bin/env python3
"""Host-managed real process death; no live credentials, network requests or lint."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import shlex
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
from android_environment import add_options, prepare
PACKAGE = "com.aqaqeer.app.verification.creation.debug"
TEST_PACKAGE = PACKAGE + ".test"
RUNNER = TEST_PACKAGE + "/com.aqaqeer.app.SuiteRunner"
CLASS = "com.aqaqeer.app.RestoreProcessDeathInstrumentedTest#phase"
CHECKPOINTS = ("during_input", "before_journal", "after_journal", "before_activation",
               "after_active", "after_setup", "after_pending", "after_requirements", "before_stage_cleanup",
               "inside_sql_insert", "inside_sql_history")
EARLY = frozenset((*CHECKPOINTS[:3], "inside_sql_insert", "inside_sql_history"))
APKS = ("app/build/outputs/apk/standard/debug/app-standard-debug.apk",
        "app/build/outputs/apk/androidTest/standard/debug/app-standard-debug-androidTest.apk")


def sources(init_script):
    paths = [p for p in (ROOT / "app/src").rglob("*") if p.is_file()]
    paths += [ROOT / n for n in ("app/build.gradle.kts", "build.gradle.kts", "gradle.properties",
                                "gradle/libs.versions.toml", "tools/run_restore_process_death.py")]
    paths.append(init_script)
    return {str(p.relative_to(ROOT) if p.is_relative_to(ROOT) else p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}


def scenarios():
    controls = [{"target": t, "checkpoint": "none", "repeat": False} for t in ("empty", "configured")]
    cases = [{"target": t, "checkpoint": c, "repeat": False}
             for t in ("empty", "configured") for c in CHECKPOINTS]
    cases += [{"target": t, "checkpoint": "after_active", "repeat": True} for t in ("empty", "configured")]
    for case in controls + cases:
        case["name"] = case["target"] + ":" + ("repeat_activation" if case["repeat"] else case["checkpoint"])
    recovery = []
    for target, checkpoints in {
        "export": ("none", "export_during_write", "export_after_close"),
        "maintenance": ("none", "maintenance_preparing", "maintenance_source_ready", "maintenance_backed_up",
                        "maintenance_write_started", "maintenance_after_remote_write", "maintenance_before_active",
                        "maintenance_after_active", "maintenance_during_cleanup"),
    }.items():
        recovery += [{"name": target + ":" + point, "target": target, "checkpoint": point, "repeat": False,
                      "class": "com.aqaqeer.app.AcceptanceProcessDeathInstrumentedTest#phase"} for point in checkpoints]
    return controls + cases + recovery


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    add_options(parser)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--retry-build", action="store_true", help="Rebuild only the affected test APK after a test compilation failure.")
    parser.add_argument("--build-only", action="store_true", help="Compile the complete harness while a physical phone is unavailable.")
    parser.add_argument("--only", action="append", choices=[c["name"] for c in scenarios()],
                        help="Run only failed or previously unexecuted scenarios.")
    parser.add_argument("--build-from", type=Path, help="Reuse a prior successful build only if source/APK hashes match.")
    args = parser.parse_args()
    sdk, adb_path, init_script, gradle_args = prepare(args)
    run_id = uuid.uuid4().hex
    evidence = ROOT / "local/verification/runs" / ("restore-death-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    evidence.mkdir(parents=True)
    chosen = [c for c in scenarios() if (c["name"] in args.only if args.only else "class" not in c)]
    state = {"runId": run_id, "serial": args.serial, "lintRun": False, "externalNetworkUsed": False,
             "scenarios": [], "planned": [c["name"] for c in chosen]}
    (evidence / "source-sha256.json").write_text(json.dumps(sources(init_script), indent=2) + "\n")

    def save():
        (evidence / "results.json").write_text(json.dumps(state, indent=2) + "\n")

    def adb(*values, check=True, timeout=30, input=None):
        return subprocess.run([adb_path, "-s", args.serial, *values], capture_output=True, text=True, check=check, timeout=timeout, input=input)

    def owned_file(name):
        if not re.fullmatch(r"(marker|outcome)-[a-f0-9]{32}-[a-f0-9]{32}\.json", name):
            raise RuntimeError("Invalid owned marker name")
        result = adb("shell", "run-as", PACKAGE, "cat", "no_backup/restore-death/" + run_id + "/" + name, check=False)
        if result.returncode:
            if "No such file or directory" in result.stdout + result.stderr:
                return None
            raise RuntimeError("Unable to read phase marker")
        return json.loads(result.stdout)

    def identity(pid):
        status = adb("shell", "run-as", PACKAGE, "cat", "/proc/" + str(pid) + "/status").stdout
        uid = int(re.search(r"^Uid:\s+(\d+)", status, re.MULTILINE).group(1))
        cmd = adb("shell", "run-as", PACKAGE, "cat", "/proc/" + str(pid) + "/cmdline").stdout.split("\0")[0]
        tick = adb("shell", "run-as", PACKAGE, "cat", "/proc/" + str(pid) + "/stat").stdout.rsplit(")", 1)[1].split()[19]
        expected_uid = int(adb("shell", "run-as", PACKAGE, "id", "-u").stdout.strip())
        if cmd != PACKAGE or uid != expected_uid:
            raise RuntimeError("PID does not belong to the isolated verification package")
        return {"pid": pid, "uid": uid, "startTicks": tick, "package": cmd}

    def phase(case, folder, operation, checkpoint="none", cold="first", kill=False):
        event = uuid.uuid4().hex
        adb("shell", "am", "force-stop", PACKAGE)
        values = {"class": case.get("class", CLASS), "crashRun": run_id, "crashCase": case["id"], "crashEvent": event,
                  "crashScenarioCheckpoint": case["checkpoint"], "crashPhase": operation, "crashTarget": case["target"], "crashCheckpoint": checkpoint,
                  "crashRestored": str(case["checkpoint"] not in EARLY).lower(), "crashCold": cold}
        command = [adb_path, "-s", args.serial, "shell", "am", "instrument", "-w", "-r"]
        for name, value in values.items():
            command += ["-e", name, value]
        command += [RUNNER]
        stem = operation + "-" + event
        (folder / (stem + "-command.json")).write_text(json.dumps(command, indent=2) + "\n")
        marker_name = "marker-" + case["id"] + "-" + event + ".json"
        outcome_name = "outcome-" + case["id"] + "-" + event + ".json"
        proc = None
        try:
            with (folder / (stem + ".log")).open("w") as log:
                proc = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
                if kill:
                    deadline = time.monotonic() + 300
                    marker = None
                    while time.monotonic() < deadline:
                        marker = owned_file(marker_name)
                        if marker:
                            break
                        if proc.poll() is not None:
                            raise RuntimeError("Instrumentation ended before checkpoint " + checkpoint)
                        time.sleep(0.5)
                    if not marker or marker.get("event") != event or marker.get("checkpoint") != checkpoint:
                        raise RuntimeError("Checkpoint handshake failed")
                    if checkpoint.startswith("inside_sql_") and not (marker.get("transactionOpen") and marker.get("nativeSqlCallback")):
                        raise RuntimeError("Missing native SQL transaction evidence")
                    before = identity(int(marker["pid"]))
                    if before["uid"] != marker["uid"]:
                        raise RuntimeError("Checkpoint UID mismatch")
                    if identity(before["pid"]) != before:
                        raise RuntimeError("PID changed before SIGKILL")
                    # SELinux blocks cross-process run-as signals on this phone.
                    # Authorize the test gate to SIGKILL itself after the same
                    # host PID/UID/start-time checks. Atomic rename prevents torn requests.
                    relative = "no_backup/restore-death/" + run_id + "/" + marker_name.replace("marker-", "kill-")
                    request = json.dumps({"event": event, "pid": before["pid"], "uid": before["uid"]})
                    script = ("umask 077; cat > " + shlex.quote(relative + ".tmp") +
                              " && mv " + shlex.quote(relative + ".tmp") + " " + shlex.quote(relative))
                    adb("shell", "run-as " + PACKAGE + " /system/bin/sh -c " + shlex.quote(script), input=request)
                    deadline = time.monotonic() + 15
                    while time.monotonic() < deadline:
                        probe = adb("shell", "run-as", PACKAGE, "cat", "/proc/" + str(before["pid"]) + "/stat", check=False)
                        if probe.returncode != 0:
                            break
                        tail = probe.stdout.rsplit(")", 1)[1].split()
                        if tail[0] == "Z" or tail[19] != before["startTicks"]:
                            break
                        time.sleep(0.1)
                    else:
                        raise RuntimeError("Killed process did not terminate")
                    proc.wait(timeout=30)
                    death_output = (folder / (stem + ".log")).read_text()
                    if ("Process crashed" not in death_output or "FAILURES!!!" in death_output or
                            "OK (1 test)" in death_output or owned_file(outcome_name) is not None):
                        raise RuntimeError("Invocation ended normally instead of the requested process death")
                    record = {"checkpoint": marker, "identity": before, "signal": "SIGKILL",
                              "processTerminationVerified": True, "instrumentationExit": proc.returncode,
                              "mechanism": "Host-validated request; target gate calls Process.killProcess under its own UID",
                              "note": "Expected interrupted invocation; subsequent verifier determines pass."}
                    (folder / (stem + "-death.json")).write_text(json.dumps(record, indent=2) + "\n")
                    return record
                proc.wait(timeout=300)
            output = (folder / (stem + ".log")).read_text()
            outcome = owned_file(outcome_name)
            if proc.returncode or "OK (1 test)" not in output or "FAILURES!!!" in output or not outcome or not outcome.get("passed"):
                raise RuntimeError("Instrumentation phase failed: " + operation)
            (folder / (stem + "-outcome.json")).write_text(json.dumps(outcome, indent=2) + "\n")
            return outcome
        finally:
            if proc is not None and proc.poll() is None:
                adb("shell", "am", "force-stop", PACKAGE, check=False)
                proc.terminate()
                try:
                    proc.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=10)

    print("Evidence: " + str(evidence), flush=True)
    provenance = {"commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                  "dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True)),
                  "serial": args.serial, "deviceVerified": False, "buildOnly": args.build_only}
    if not args.build_only:
        adb("get-state")
        if args.serial.startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu").stdout.strip() == "1":
            raise RuntimeError("Physical phone required")
        for package in (PACKAGE, TEST_PACKAGE):
            if "package:" in adb("shell", "pm", "path", package, check=False).stdout:
                raise RuntimeError("Verification namespace already installed; existing data left untouched")
        provenance.update({"deviceVerified": True,
                           "model": adb("shell", "getprop", "ro.product.model").stdout.strip(),
                           "api": adb("shell", "getprop", "ro.build.version.sdk").stdout.strip()})
    (evidence / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    counter = ROOT / "local/versioning/last-code.txt"
    version_before = counter.read_bytes()
    owned_packages = []
    last_case = None
    try:
        if args.build_from:
            previous = json.loads((args.build_from / "source-sha256.json").read_text())
            relevant = lambda values: {k: v for k, v in values.items() if k != "tools/run_restore_process_death.py"}
            if relevant(previous) != relevant(sources(init_script)):
                raise RuntimeError("Build source hashes differ; compile changed tests")
            for item in json.loads((args.build_from / "artifacts.json").read_text()):
                if hashlib.sha256((ROOT / item["path"]).read_bytes()).hexdigest() != item["sha256"]:
                    raise RuntimeError("Cached APK differs from the verified build")
        else:
            command = ["./gradlew", *gradle_args, "-I", str(init_script), "--offline", "--no-configuration-cache", "--console=plain", "--continue",
                       *([":app:packageStandardDebugAndroidTest"] if args.retry_build else [":app:packageStandardDebug", ":app:packageStandardDebugAndroidTest"])]
            (evidence / "build-command.json").write_text(json.dumps(command, indent=2) + "\n")
            with (evidence / "build.log").open("w") as log:
                built = subprocess.run(command, cwd=ROOT,
                               env=os.environ.copy(),
                               stdout=log, stderr=subprocess.STDOUT)
            state["buildExit"] = built.returncode
            save()
            if built.returncode:
                return built.returncode
        if counter.read_bytes() != version_before:
            raise RuntimeError("Version counter changed")
        aapt = sdk / "build-tools" / json.loads((ROOT / ".github/android-toolchain.json").read_text())["buildTools"] / "aapt2"
        artifacts = []
        for name, package in zip(APKS, (PACKAGE, TEST_PACKAGE)):
            apk = ROOT / name
            metadata = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
            if "package: name='" + package + "'" not in metadata:
                raise RuntimeError("APK namespace mismatch")
            artifacts.append({"path": name, "bytes": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest()})
        (evidence / "artifacts.json").write_text(json.dumps(artifacts, indent=2) + "\n")
        if args.build_only:
            state["buildOnly"] = True
            return 0
        for name, package in zip(APKS, (PACKAGE, TEST_PACKAGE)):
            adb("install", "-t", str(ROOT / name), timeout=120)
            owned_packages.append(package)
        instrumentation = adb("shell", "pm", "list", "instrumentation").stdout
        if RUNNER + " (target=" + PACKAGE + ")" not in instrumentation:
            raise RuntimeError("Instrumentation target mismatch")
        for entry in chosen:
            case = dict(entry, id=uuid.uuid4().hex)
            folder = evidence / case["name"].replace(":", "-")
            folder.mkdir()
            last_case = case
            print("Running " + case["name"], flush=True)
            failed_prepare = False
            try:
                try:
                    phase(case, folder, "prepare")
                except Exception:
                    failed_prepare = True
                    raise
                death = phase(case, folder, "interrupt", checkpoint=case["checkpoint"], kill=case["checkpoint"] != "none")
                if case["repeat"]:
                    phase(case, folder, "verify", checkpoint="after_pending", kill=True)
                first = phase(case, folder, "verify")
                second = phase(case, folder, "verify", cold="second")
                if first["pid"] == second["pid"]:
                    raise RuntimeError("Cold verifications reused a process")
                case["status"] = "passed"
            except KeyboardInterrupt:
                case["status"] = "interrupted"
                case["error"] = "Host interrupted the active scenario; partial phase evidence retained"
                state["scenarios"].append(case)
                save()
                raise
            except Exception as error:
                case["status"] = "failed"
                case["error"] = str(error)
            finally:
                try:
                    phase(case, folder, "cleanup")
                    case["cleanup"] = "passed"
                except Exception as error:
                    case["cleanup"] = "failed"
                    case["cleanupError"] = str(error)
                    case["status"] = "failed"
            state["scenarios"].append(case)
            save()
            print(case["name"] + ": " + case["status"], flush=True)
            if failed_prepare:
                break
    finally:
        if last_case and TEST_PACKAGE in owned_packages:
            try:
                folder = evidence / "final-cleanup"
                folder.mkdir(exist_ok=True)
                phase(last_case, folder, "cleanupRun")
                state["profileCleanup"] = "passed"
            except Exception as error:
                state["profileCleanup"] = "failed"
                state["profileCleanupError"] = str(error)
        cleanup = []
        for package in reversed(owned_packages):
            try:
                adb("get-state")
                adb("shell", "am", "force-stop", package, check=False)
                removed = adb("uninstall", package, check=False, timeout=120)
                adb("get-state")
                absent = "package:" not in adb("shell", "pm", "path", package, check=False).stdout
                cleanup.append({"package": package, "uninstallExit": removed.returncode, "absent": absent})
            except (subprocess.SubprocessError, OSError):
                cleanup.append({"package": package, "absent": False, "error": "Device unavailable; cleanup not verified"})
        state["packageCleanup"] = cleanup
        state["versionCounterUnchanged"] = counter.read_bytes() == version_before
        state["unexecuted"] = [c["name"] for c in chosen if c["name"] not in {s["name"] for s in state["scenarios"]}]
        save()
    return 0 if (not state["unexecuted"] and all(c["status"] == "passed" for c in state["scenarios"])
                 and all(c["absent"] for c in state["packageCleanup"]) and state["versionCounterUnchanged"]
                 and state.get("profileCleanup") == "passed") else 1


if __name__ == "__main__":
    raise SystemExit(main())
