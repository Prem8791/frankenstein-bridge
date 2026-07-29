"""Secret-safe runtime inventory over an explicitly selected adb device."""

from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
from pathlib import Path
import re
import subprocess
from typing import Callable

from .model import (
    CollectionResult,
    CoverageEntry,
    EvidenceRef,
    SurfaceRecord,
    canonical_coverage,
    canonical_records,
    canonical_json,
    normalized_sha256,
)


class InventoryError(RuntimeError):
    pass


@dataclass(frozen=True)
class CommandEvidence:
    command_id: str
    redacted_argv: tuple[str, ...]
    exit_code: int
    stdout_sha256: str
    stderr_sha256: str


@dataclass(frozen=True)
class DeviceIdentity:
    product_device: str
    build_fingerprint: str
    build_id: str
    build_incremental: str
    build_date_utc: str
    build_description: str
    product_system_name: str
    build_type: str
    build_tags: str
    bliss_device: str

    def to_target(self) -> dict[str, str]:
        return {
            "product": self.product_device,
            "buildFingerprint": self.build_fingerprint,
            "buildId": self.build_id,
            "buildIncremental": self.build_incremental,
            "buildDateUtc": self.build_date_utc,
            "buildDescription": self.build_description,
            "productSystemName": self.product_system_name,
            "buildType": self.build_type,
            "buildTags": self.build_tags,
            "blissDevice": self.bliss_device,
        }


IDENTITY_PROPERTIES = (
    ("product_device", "ro.product.device"),
    ("build_fingerprint", "ro.build.fingerprint"),
    ("build_id", "ro.build.id"),
    ("build_incremental", "ro.build.version.incremental"),
    ("build_date_utc", "ro.build.date.utc"),
    ("build_description", "ro.build.description"),
    ("product_system_name", "ro.product.system.name"),
    ("build_type", "ro.build.type"),
    ("build_tags", "ro.build.tags"),
    ("bliss_device", "ro.bliss.device"),
)


def adb(serial: str, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["adb", "-s", serial, *args],
        text=True,
        capture_output=True,
        timeout=30,
    )


def _load_properties(product_out: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for relative in (
        "system/build.prop",
        "system_ext/build.prop",
        "vendor/build.prop",
        "odm/build.prop",
        "product/build.prop",
    ):
        path = product_out / relative
        if not path.exists():
            continue
        for raw in path.read_text(encoding="utf-8").splitlines():
            if "=" not in raw or raw.lstrip().startswith("#"):
                continue
            key, value = raw.split("=", 1)
            values.setdefault(key, value)
    if "ro.product.device" not in values:
        for partition in ("product", "odm", "vendor", "system_ext", "system"):
            candidate = values.get(f"ro.product.{partition}.device", "")
            if candidate:
                values["ro.product.device"] = candidate
                break
    return values


def require_matching_device(serial: str, product_out: Path) -> DeviceIdentity:
    state = adb(serial, "get-state")
    if state.returncode != 0 or state.stdout.strip() != "device":
        raise InventoryError("selected adb target is not in device state")
    expected = _load_properties(product_out)
    actual: dict[str, str] = {}
    for field, prop in IDENTITY_PROPERTIES:
        result = adb(serial, "shell", "getprop", prop)
        if result.returncode != 0:
            raise InventoryError(f"identity property unavailable: {prop}")
        value = result.stdout.strip()
        wanted = expected.get(prop, "")
        if not value or not wanted or value != wanted:
            raise InventoryError(f"build identity mismatch: {prop}")
        actual[field] = value
    if actual["product_device"] != "I001D":
        raise InventoryError("selected device is not I001D")
    if actual["build_id"].startswith("RKQ") or actual["bliss_device"] != "I001D":
        raise InventoryError("preserved vendor fingerprint lacks matching Bliss/BP4A identity")
    return DeviceIdentity(**actual)


def _runtime_evidence(command_id: str, line: str) -> tuple[EvidenceRef, ...]:
    return (EvidenceRef("RUNTIME", command_id, 0, normalized_sha256(line)),)


def parse_service_list(output: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        match = re.match(r"\s*\d+\s+([^:\s]+):\s+\[([^\]]*)\]", raw)
        if match:
            records.append(
                SurfaceRecord(
                    kind="BINDER_SERVICE",
                    name=match.group(1),
                    runtime_present="YES",
                    interface_descriptor=match.group(2),
                    evidence=_runtime_evidence("service-list", f"{match.group(1)}:{match.group(2)}"),
                )
            )
    return records


def _simple_lines(output: str, kind: str, command_id: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        name = raw.strip()
        if not name or name.startswith(("Currently running", "Found ")):
            continue
        records.append(
            SurfaceRecord(
                kind=kind,
                name=name,
                runtime_present="YES",
                evidence=_runtime_evidence(command_id, name),
            )
        )
    return records


def parse_dumpsys_list(output: str) -> list[SurfaceRecord]:
    return _simple_lines(output, "DUMPSYS_SERVICE", "dumpsys-list")


def parse_cmd_list(output: str) -> list[SurfaceRecord]:
    return _simple_lines(output, "CMD_SERVICE", "cmd-list")


def parse_lshal(output: str) -> list[SurfaceRecord]:
    records = []
    pattern = re.compile(r"(?P<pkg>[A-Za-z0-9_.]+)@(?P<version>[0-9.]+)::(?P<intf>[A-Za-z0-9_]+)/(?P<instance>[^\s]+)")
    for raw in output.splitlines():
        match = pattern.search(raw)
        if not match:
            continue
        normalized = match.group(0)
        records.append(
            SurfaceRecord(
                kind="VINTF_HIDL_INSTANCE",
                name=f"{match.group('pkg')}::{match.group('intf')}",
                instance=match.group("instance"),
                interface_version=match.group("version"),
                runtime_present="YES",
                evidence=_runtime_evidence("lshal", normalized),
            )
        )
    return records


def parse_features(output: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        if not raw.startswith("feature:"):
            continue
        name = raw.partition(":")[2].strip()
        records.append(
            SurfaceRecord(
                kind="SYSTEM_FEATURE",
                name=name,
                runtime_present="YES",
                evidence=_runtime_evidence("pm-features", name),
            )
        )
    return records


def parse_apex(output: str) -> list[SurfaceRecord]:
    modules: list[tuple[str, str]] = []
    current = ""
    active = ""
    for raw in output.splitlines():
        match = re.search(r"(?:moduleName|modulePath|name)\s*[:=]\s*([A-Za-z0-9_.-]+)", raw)
        if match and ("moduleName" in raw or not current):
            if current:
                modules.append((current, active))
            current, active = match.group(1), ""
        active_match = re.search(r"isActive\s*[:=]\s*(true|false)", raw, re.I)
        if active_match:
            active = active_match.group(1).lower()
    if current:
        modules.append((current, active))
    if not modules:
        for raw in output.splitlines():
            match = re.search(r"\b(com\.[A-Za-z0-9_.-]+)\b", raw)
            if match:
                modules.append((match.group(1), "true"))
    return [
        SurfaceRecord(
            kind="APEX_MODULE",
            name=name,
            runtime_present="YES" if active != "false" else "NO",
            evidence=_runtime_evidence("apex-active", f"{name}:{active}"),
        )
        for name, active in modules
    ]


def parse_overlay(output: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        match = re.match(r"\s*\[([ xX-])\]\s+([A-Za-z0-9_.:]+)", raw)
        if not match:
            continue
        name = match.group(2).split(":", 1)[0]
        records.append(
            SurfaceRecord(
                kind="OVERLAY_PACKAGE",
                name=name,
                runtime_present="YES" if match.group(1).lower() == "x" else "NO",
                evidence=_runtime_evidence("overlay-list", f"{match.group(1)}:{name}"),
            )
        )
    return records


GETPROP_LINE = re.compile(r"^\[([^\]]+)\]:\s*\[(.*)\]$")


def parse_getprop_names(output: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        match = GETPROP_LINE.match(raw)
        if not match:
            continue
        name = match.group(1)
        records.append(
            SurfaceRecord(
                kind="PROPERTY_NAME",
                name=name,
                runtime_present="YES",
                evidence=_runtime_evidence("getprop-names", name),
            )
        )
    return records


def parse_init_states(output: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        match = GETPROP_LINE.match(raw)
        if not match or not match.group(1).startswith("init.svc."):
            continue
        name = match.group(1)[len("init.svc."):]
        if name.startswith(("debug_pid.", "updatable_crashing.")):
            continue
        state = match.group(2)
        records.append(
            SurfaceRecord(
                kind="INIT_RUNTIME_STATE",
                name=name,
                runtime_present="YES",
                availability_condition=state,
                evidence=_runtime_evidence("init-states", f"{name}:{state}"),
            )
        )
    return records


def parse_processes(output: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        parts = raw.split()
        if len(parts) < 3 or parts[0].upper() in {"LABEL", "CONTEXT"}:
            continue
        label, user, name = parts[0], parts[1], parts[2]
        if not label.startswith("u:r:"):
            continue
        records.append(
            SurfaceRecord(
                kind="PROCESS_DOMAIN",
                name=name,
                instance=user,
                selinux_context=label,
                runtime_present="YES",
                evidence=_runtime_evidence("ps-domains", f"{label} {user} {name}"),
            )
        )
    return records


def parse_device_nodes(output: str) -> list[SurfaceRecord]:
    return [
        SurfaceRecord(
            kind="DEVICE_NODE",
            name=raw.strip(),
            runtime_present="YES",
            evidence=_runtime_evidence("device-nodes", raw.strip()),
        )
        for raw in output.splitlines()
        if raw.strip().startswith("/dev/")
    ]


def parse_unix_sockets(output: str) -> list[SurfaceRecord]:
    records = []
    for raw in output.splitlines():
        parts = raw.split()
        if not parts:
            continue
        name = parts[-1]
        if not (name.startswith("/") or name.startswith("@")):
            continue
        records.append(
            SurfaceRecord(
                kind="UNIX_SOCKET",
                name=name,
                runtime_present="YES",
                evidence=_runtime_evidence("unix-sockets", name),
            )
        )
    return records


def _parse_selinux(output: str) -> list[SurfaceRecord]:
    state = output.strip()
    return [
        SurfaceRecord(
            kind="SELINUX_STATE",
            name="kernel",
            runtime_present="YES",
            availability_condition=state,
            evidence=_runtime_evidence("getenforce", state),
        )
    ]


RUNTIME_COMMANDS: tuple[
    tuple[str, tuple[str, ...], str, Callable[[str], list[SurfaceRecord]]], ...
] = (
    ("service-list", ("shell", "service", "list"), "RUNTIME_BINDER", parse_service_list),
    ("dumpsys-list", ("shell", "dumpsys", "-l"), "RUNTIME_DUMPSYS_CMD", parse_dumpsys_list),
    ("cmd-list", ("shell", "cmd", "-l"), "RUNTIME_DUMPSYS_CMD", parse_cmd_list),
    ("lshal", ("shell", "lshal", "--neat"), "RUNTIME_HALS", parse_lshal),
    ("pm-features", ("shell", "pm", "list", "features"), "RUNTIME_FEATURES", parse_features),
    ("apex-active", ("shell", "cmd", "apexservice", "getActivePackages"), "RUNTIME_APEX", parse_apex),
    ("overlay-list", ("shell", "cmd", "overlay", "list", "--user", "0"), "RUNTIME_OVERLAYS", parse_overlay),
    ("getprop", ("shell", "getprop"), "RUNTIME_PROPERTIES", lambda value: parse_getprop_names(value) + parse_init_states(value)),
    ("getenforce", ("shell", "getenforce"), "RUNTIME_SELINUX", _parse_selinux),
    ("ps-domains", ("shell", "ps", "-AZ", "-o", "LABEL,USER,NAME"), "RUNTIME_PROCESSES", parse_processes),
    ("device-nodes", ("shell", "find", "/dev", "-maxdepth", "3", "(", "-type", "c", "-o", "-type", "b", ")"), "RUNTIME_DEVICE_NODES", parse_device_nodes),
    ("unix-sockets", ("shell", "cat", "/proc/net/unix"), "RUNTIME_SOCKETS", parse_unix_sockets),
)


def collect_runtime(
    serial: str,
) -> tuple[CollectionResult, tuple[CommandEvidence, ...]]:
    records: list[SurfaceRecord] = []
    coverage: list[CoverageEntry] = []
    commands: list[CommandEvidence] = []
    for command_id, argv, family, parser in RUNTIME_COMMANDS:
        result = adb(serial, *argv)
        redacted_stderr = result.stderr.replace(serial, "SERIAL")
        parsed = parser(result.stdout) if result.returncode == 0 else []
        sanitized = canonical_json({"records": [item.to_dict() for item in canonical_records(parsed)]})
        evidence = CommandEvidence(
            command_id,
            ("adb", "-s", "SERIAL", *argv),
            result.returncode,
            sha256(sanitized).hexdigest(),
            sha256(redacted_stderr.encode("utf-8")).hexdigest(),
        )
        commands.append(evidence)
        if result.returncode != 0:
            raise InventoryError(
                f"runtime command failed: {command_id} exit={result.returncode} "
                f"stdoutSha256={evidence.stdout_sha256} stderrSha256={evidence.stderr_sha256}"
            )
        records.extend(parsed)
        coverage.append(CoverageEntry(family, command_id, "COLLECTED", 1, len(parsed)))
    return (
        CollectionResult(tuple(canonical_records(records)), tuple(canonical_coverage(coverage))),
        tuple(commands),
    )
