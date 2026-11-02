#!/usr/bin/env python3
"""Run the installed Android smoke suite and fail closed on partial/crashed runs."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


def validate_report(output, returncode=0):
    if returncode:
        raise ValueError(f"ADB failed with exit code {returncode}")
    cases = re.findall(r"^(?:INSTRUMENTATION_STATUS: stream=)?(PASS|FAIL) (.+)$", output, re.M)
    if any(status == "FAIL" for status, _ in cases):
        raise ValueError("Device assertion failed")
    if "INSTRUMENTATION_FAILED" in output or "Process crashed" in output:
        raise ValueError("Instrumentation did not complete")
    codes = re.findall(r"INSTRUMENTATION_CODE:\s*(-?\d+)", output)
    if any(code != "-1" for code in codes):
        raise ValueError("Instrumentation returned a failure result")
    summaries = re.findall(r"DEVICE_TESTS_RESULT: (PASS|FAIL) cases=(\d+)", output)
    if len(summaries) != 1 or summaries[0][0] != "PASS":
        raise ValueError("Missing or unsuccessful final device-test summary")
    count = int(summaries[0][1])
    if count == 0 or len({name for status, name in cases if status == "PASS"}) != count:
        raise ValueError("Incomplete device-test case results")
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    parser.add_argument("--adb", default=str(Path(sdk) / "platform-tools/adb") if sdk else "adb")
    parser.add_argument("--serial", help="Explicit test device serial")
    args = parser.parse_args()
    command = [args.adb] + (["-s", args.serial] if args.serial else [])
    command += ["shell", "am", "instrument", "-w", "com.pranjal.liveprogress.test/com.pranjal.liveprogress.DeviceSmokeInstrumentation"]
    try:
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
        print(result.stdout, end="")
        count = validate_report(result.stdout, result.returncode)
        print(f"Device suite passed: {count} cases")
    except (OSError, subprocess.TimeoutExpired, ValueError) as error:
        print(f"Device suite failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
