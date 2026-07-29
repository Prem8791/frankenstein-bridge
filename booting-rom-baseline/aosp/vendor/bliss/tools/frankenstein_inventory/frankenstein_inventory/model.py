"""Canonical, versioned inventory data model."""

from __future__ import annotations

from dataclasses import asdict, dataclass, fields, replace
from hashlib import sha256
import json
import re
from typing import Iterable

SCHEMA_VERSION = 1
UNCLASSIFIED = "UNCLASSIFIED"
UNTESTED = "UNTESTED"

KINDS = frozenset(
    """
SYSTEM_SERVER_SERVICE LOCAL_SERVICE_PRODUCER LOCAL_SERVICE_CONSUMER
LOCAL_MANAGER_PRODUCER LOCAL_MANAGER_CONSUMER INIT_SERVICE BINDER_SERVICE
DUMPSYS_SERVICE CMD_SERVICE VINTF_AIDL_INSTANCE VINTF_HIDL_INSTANCE
SERVICE_CONTEXT HWSERVICE_CONTEXT VNDSERVICE_CONTEXT FILE_CONTEXT
GENFS_CONTEXT PROPERTY_CONTEXT SEAPP_CONTEXT SELINUX_STATE SYSTEM_FEATURE
SYSCONFIG_ENTRY PRIVAPP_PERMISSION_GRANT APEX_MODULE OVERLAY_PACKAGE
PROPERTY_NAME INIT_RUNTIME_STATE PROCESS_DOMAIN DEVICE_NODE UNIX_SOCKET
SOURCE_PROTECTED_PATH
""".split()
)
COVERAGE_STATES = frozenset({"COLLECTED", "ABSENT", "UNAVAILABLE"})
RUNTIME_STATES = frozenset({"YES", "NO", "UNKNOWN"})
REACHABILITY_STATES = frozenset(
    {"UNTESTED", "REACHABLE", "DENIED", "PARTIAL", "NOT_APPLICABLE"}
)
BUCKETS = frozenset({"UNCLASSIFIED", "A", "B", "C", "D"})
EVIDENCE_KINDS = frozenset({"SOURCE", "ASSEMBLED", "RUNTIME"})
HEX_64 = re.compile(r"^[0-9a-f]{64}$")


def _validate_text(value: str, label: str) -> None:
    if "\x00" in value:
        raise ValueError(f"{label} contains NUL")


@dataclass(frozen=True, order=True)
class EvidenceRef:
    evidence_kind: str
    location: str
    line: int = 0
    sha256: str = ""

    def __post_init__(self) -> None:
        if self.evidence_kind not in EVIDENCE_KINDS:
            raise ValueError(f"invalid evidence kind: {self.evidence_kind}")
        _validate_text(self.location, "evidence location")
        if self.line < 0:
            raise ValueError("negative evidence line")
        if self.sha256 and not HEX_64.fullmatch(self.sha256):
            raise ValueError("invalid evidence SHA-256")

    def to_dict(self) -> dict[str, object]:
        return {
            "evidenceKind": self.evidence_kind,
            "location": self.location,
            "line": self.line,
            "sha256": self.sha256,
        }


@dataclass(frozen=True, order=True)
class CoverageEntry:
    family: str
    location: str
    status: str
    scanned_inputs: int
    record_count: int
    limitation: str = ""

    def __post_init__(self) -> None:
        if self.status not in COVERAGE_STATES:
            raise ValueError(f"invalid coverage state: {self.status}")
        if self.scanned_inputs < 0 or self.record_count < 0:
            raise ValueError("negative coverage count")
        for label, value in (
            ("coverage family", self.family),
            ("coverage location", self.location),
            ("coverage limitation", self.limitation),
        ):
            _validate_text(value, label)

    def to_dict(self) -> dict[str, object]:
        return {
            "family": self.family,
            "location": self.location,
            "status": self.status,
            "scannedInputs": self.scanned_inputs,
            "recordCount": self.record_count,
            "limitation": self.limitation,
        }


@dataclass(frozen=True, order=True)
class SurfaceRecord:
    kind: str
    name: str
    instance: str = ""
    partition: str = ""
    owner: str = ""
    runtime_present: str = "UNKNOWN"
    interface_descriptor: str = ""
    interface_version: str = ""
    interface_hash: str = ""
    selinux_context: str = ""
    availability_condition: str = ""
    evidence: tuple[EvidenceRef, ...] = ()
    future_app_reachability: str = UNTESTED
    bucket: str = UNCLASSIFIED
    later_access_path: str = ""
    rationale: str = ""
    provider_id: str = ""
    test_id: str = ""

    def __post_init__(self) -> None:
        if self.kind not in KINDS:
            raise ValueError(f"invalid surface kind: {self.kind}")
        if not self.name:
            raise ValueError("surface name is empty")
        if self.runtime_present not in RUNTIME_STATES:
            raise ValueError(f"invalid runtime state: {self.runtime_present}")
        if self.future_app_reachability not in REACHABILITY_STATES:
            raise ValueError("invalid reachability state")
        if self.bucket not in BUCKETS:
            raise ValueError("invalid bucket")
        for field in fields(self):
            value = getattr(self, field.name)
            if isinstance(value, str):
                _validate_text(value, field.name)

    def stable_key(self) -> tuple[str, ...]:
        return (self.kind, self.name, self.instance)

    def record_id(self) -> str:
        encoded = b"\x00".join(part.encode("utf-8") for part in self.stable_key())
        return "fs1:" + sha256(encoded).hexdigest()

    def to_dict(self) -> dict[str, object]:
        return {
            "recordId": self.record_id(),
            "kind": self.kind,
            "name": self.name,
            "instance": self.instance,
            "partition": self.partition,
            "owner": self.owner,
            "runtimePresent": self.runtime_present,
            "interfaceDescriptor": self.interface_descriptor,
            "interfaceVersion": self.interface_version,
            "interfaceHash": self.interface_hash,
            "selinuxContext": self.selinux_context,
            "availabilityCondition": self.availability_condition,
            "evidence": [item.to_dict() for item in sorted(set(self.evidence))],
            "futureAppReachability": self.future_app_reachability,
            "bucket": self.bucket,
            "laterAccessPath": self.later_access_path,
            "rationale": self.rationale,
            "providerId": self.provider_id,
            "testId": self.test_id,
        }


@dataclass(frozen=True)
class CollectionResult:
    records: tuple[SurfaceRecord, ...]
    coverage: tuple[CoverageEntry, ...]


def _merge_text(left: str, right: str, label: str) -> str:
    if left and right and left != right:
        raise ValueError(f"conflicting {label}: {left!r} != {right!r}")
    return left or right


def _merge_record(left: SurfaceRecord, right: SurfaceRecord) -> SurfaceRecord:
    if left.stable_key() != right.stable_key():
        raise ValueError("cannot merge different surfaces")
    runtime = left.runtime_present
    if right.runtime_present == "YES" or runtime == "UNKNOWN":
        runtime = right.runtime_present
    return replace(
        left,
        partition=_merge_text(left.partition, right.partition, "partition"),
        owner=_merge_text(left.owner, right.owner, "owner"),
        interface_descriptor=_merge_text(
            left.interface_descriptor, right.interface_descriptor, "interface descriptor"
        ),
        interface_version=_merge_text(
            left.interface_version, right.interface_version, "interface version"
        ),
        interface_hash=_merge_text(left.interface_hash, right.interface_hash, "interface hash"),
        selinux_context=_merge_text(
            left.selinux_context, right.selinux_context, "SELinux context"
        ),
        availability_condition=_merge_text(
            left.availability_condition,
            right.availability_condition,
            "availability condition",
        ),
        runtime_present=runtime,
        evidence=tuple(sorted(set(left.evidence + right.evidence))),
    )


def canonical_records(records: Iterable[SurfaceRecord]) -> list[SurfaceRecord]:
    merged: dict[tuple[str, ...], SurfaceRecord] = {}
    for record in records:
        key = record.stable_key()
        merged[key] = _merge_record(merged[key], record) if key in merged else record
    return sorted(merged.values(), key=lambda record: record.stable_key())


def canonical_coverage(entries: Iterable[CoverageEntry]) -> list[CoverageEntry]:
    return sorted(set(entries))


def canonical_json(document: dict[str, object]) -> bytes:
    return (
        json.dumps(
            document,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )
        + "\n"
    ).encode("utf-8")


def normalized_sha256(value: str) -> str:
    normalized = " ".join(value.replace("\r\n", "\n").replace("\r", "\n").split())
    return sha256(normalized.encode("utf-8")).hexdigest()
