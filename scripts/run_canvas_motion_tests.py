#!/usr/bin/env python3
"""Run real-device canvas checks in the isolated QA package (ADB on PATH)."""

import argparse
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
parser.add_argument("--class-name", default="dev.vetki.CanvasMotionTest")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
command = [
    args.adb, "shell", "am", "instrument", "--user", "0", "-w",
    "-e", "class", args.class_name,
    "-e", "canvasHostLaunch", "true",
    "dev.vetki.qa.test/androidx.test.runner.AndroidJUnitRunner",
]

with (args.output / "instrumentation.log").open("w") as log:
    process = subprocess.Popen(
        command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1,
    )
    launched = set()
    try:
        for line in process.stdout:
            log.write(line)
            log.flush()
            match = re.search(r"CANVAS_READY (qa-motion-[a-z0-9-]+)", line)
            if match and match[1] not in launched:
                launched.add(match[1])
                # MIUI can suppress an Activity launched by background
                # instrumentation. Launch the QA Activity from ADB instead.
                result = subprocess.run(
                    [
                        args.adb, "shell", "am", "start", "--user", "0", "-W",
                        "--activity-single-top", "-n",
                        "dev.vetki.qa/dev.vetki.MainActivity",
                        "--es", "boardId", match[1],
                    ],
                    capture_output=True, text=True, check=True,
                )
                log.write(result.stdout)
                log.flush()
        code = process.wait()
    finally:
        if process.poll() is None:
            process.terminate()
            subprocess.run(
                [args.adb, "shell", "am", "force-stop", "dev.vetki.qa"],
                check=False,
            )
            process.wait()

text = (args.output / "instrumentation.log").read_text()
for label in ["zoom15", "zoom46"]:
    result = subprocess.run(
        [args.adb, "exec-out", "run-as", "dev.vetki.qa", "cat",
         f"files/qa-canvas-{label}-gfxinfo.txt"],
        capture_output=True,
    )
    if result.returncode == 0:
        (args.output / f"{label}-gfxinfo.txt").write_bytes(result.stdout)

print(text)
if (
    code
    or "FAILURES!!!" in text
    or "INSTRUMENTATION_FAILED" in text
    or not re.search(r"OK \(\d+ tests?\)", text)
):
    raise SystemExit(code or 1)
