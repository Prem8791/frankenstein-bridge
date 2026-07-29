"""Run compiled broker/ordinary APKs and capture value-free reachability evidence."""

from __future__ import annotations

import csv
import json
from pathlib import Path
import subprocess
import time
import uuid


PACKAGES = {
    "broker": "com.frankenbridge.assistant",
    "ordinary": "com.frankenbridge.reachability.ordinary",
}
ACTIVITY = "com.frankenbridge.reachability.ProbeActivity"
KIND_MAP = {
    "BINDER_SERVICE": "binder",
    "RUNTIME_BINDER_SERVICE": "binder",
    "SOURCE_PROTECTED_PATH": "path",
    "DEVICE_NODE": "node",
    "UNIX_SOCKET": "socket",
}


def _adb(adb: str, serial: str, *arguments: str, check: bool = True) -> str:
    result = subprocess.run(
        [adb, "-s", serial, *arguments],
        text=True,
        capture_output=True,
        check=check,
    )
    return result.stdout


def run_probe(
    worksheet: Path,
    output: Path,
    apk: Path,
    identity: str,
    adb: str,
    serial: str,
) -> None:
    package = PACKAGES[identity]
    _adb(adb, serial, "install", "-r", "-t", str(apk))
    _adb(adb, serial, "logcat", "-c")
    results: dict[str, str] = {}
    rows = list(csv.DictReader(worksheet.open(encoding="utf-8", newline="")))
    for row in rows:
        stable_id = row["stable_id"]
        kind = KIND_MAP.get(row.get("kind", ""), "")
        target = row.get("name", "")
        if not kind or not target:
            results[stable_id] = "NOT_APPLICABLE"
            continue
        request_id = uuid.uuid4().hex
        component = f"{package}/{ACTIVITY}"
        _adb(
            adb, serial, "shell", "am", "start", "-W", "-n", component,
            "--es", "request_id", request_id,
            "--es", "kind", kind,
            "--es", "target", target,
        )
        deadline = time.monotonic() + 5
        measured = ""
        while time.monotonic() < deadline and not measured:
            log = _adb(adb, serial, "logcat", "-d", "-s", "FrankensteinProbe:I", "*:S")
            for line in reversed(log.splitlines()):
                marker = line.find("{")
                if marker < 0:
                    continue
                try:
                    payload = json.loads(line[marker:])
                except json.JSONDecodeError:
                    continue
                if payload.get("request_id") == request_id:
                    measured = payload.get("result", "PARTIAL")
                    break
            if not measured:
                time.sleep(0.1)
        results[stable_id] = measured or "PARTIAL"
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.write_text(
        json.dumps(results, sort_keys=True, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    temporary.replace(output)
