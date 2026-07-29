"""Atomic rendering and verification of inventory evidence."""

from __future__ import annotations

import csv
from dataclasses import asdict
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
from typing import Mapping, Sequence

from .model import (
    SCHEMA_VERSION,
    CoverageEntry,
    SurfaceRecord,
    canonical_coverage,
    canonical_json,
    canonical_records,
)
from .runtime import CommandEvidence, InventoryError

OUTPUT_NAMES = (
    "static-inventory.json",
    "runtime-inventory.json",
    "classification.csv",
    "source-revisions.txt",
    "collection-report.md",
)
CLASSIFICATION_FIELDS = [
    "record_id", "kind", "name", "instance", "partition", "owner",
    "runtime_present", "interface_descriptor", "interface_version",
    "interface_hash", "selinux_context", "availability_condition",
    "evidence_count", "evidence_refs_json", "future_app_reachability",
    "bucket", "later_access_path", "rationale", "provider_id", "test_id",
]


def _digest(data: bytes) -> str:
    return sha256(data).hexdigest()


def _json_document(
    target: Mapping[str, object],
    records: Sequence[SurfaceRecord],
    coverage: Sequence[CoverageEntry],
) -> bytes:
    return canonical_json(
        {
            "schemaVersion": SCHEMA_VERSION,
            "target": dict(target),
            "records": [record.to_dict() for record in canonical_records(records)],
            "coverage": [entry.to_dict() for entry in canonical_coverage(coverage)],
        }
    )


def _classification_bytes(records: Sequence[SurfaceRecord]) -> bytes:
    import io

    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=CLASSIFICATION_FIELDS, lineterminator="\n")
    writer.writeheader()
    for record in canonical_records(records):
        writer.writerow(
            {
                "record_id": record.record_id(),
                "kind": record.kind,
                "name": record.name,
                "instance": record.instance,
                "partition": record.partition,
                "owner": record.owner,
                "runtime_present": record.runtime_present,
                "interface_descriptor": record.interface_descriptor,
                "interface_version": record.interface_version,
                "interface_hash": record.interface_hash,
                "selinux_context": record.selinux_context,
                "availability_condition": record.availability_condition,
                "evidence_count": len(record.evidence),
                "evidence_refs_json": json.dumps(
                    [item.to_dict() for item in sorted(set(record.evidence))],
                    sort_keys=True,
                    separators=(",", ":"),
                ),
                "future_app_reachability": record.future_app_reachability,
                "bucket": record.bucket,
                "later_access_path": record.later_access_path,
                "rationale": record.rationale,
                "provider_id": record.provider_id,
                "test_id": record.test_id,
            }
        )
    return stream.getvalue().encode("utf-8")


def _report_bytes(
    static_count: int,
    runtime_count: int,
    worksheet_count: int,
    coverage: Sequence[CoverageEntry],
    commands: Sequence[CommandEvidence],
    hashes: Mapping[str, str],
) -> bytes:
    lines = [
        "# Frankenstein Stage 1 Collection Report",
        "",
        f"- Schema version: {SCHEMA_VERSION}",
        f"- Static records: {static_count}",
        f"- Runtime records: {runtime_count}",
        f"- Classification rows: {worksheet_count}",
        "",
        "## Coverage",
        "",
        "| Family | Location | Status | Inputs | Records | Limitation |",
        "|---|---|---:|---:|---:|---|",
    ]
    for entry in canonical_coverage(coverage):
        limitation = entry.limitation.replace("|", "\\|")
        lines.append(
            f"| `{entry.family}` | `{entry.location}` | {entry.status} | "
            f"{entry.scanned_inputs} | {entry.record_count} | {limitation} |"
        )
    lines.extend(["", "## Runtime Commands", "", "| Command | Exit | stdout SHA-256 | stderr SHA-256 |", "|---|---:|---|---|"])
    for command in commands:
        lines.append(
            f"| `{command.command_id}` | {command.exit_code} | "
            f"`{command.stdout_sha256}` | `{command.stderr_sha256}` |"
        )
    lines.extend(["", "## Evidence SHA-256", ""])
    for name in sorted(hashes):
        lines.append(f"- `{name}`: `{hashes[name]}`")
    lines.extend(
        [
            "",
            "## Limitations",
            "",
            "- Runtime evidence is a bounded shell-domain observation of one matching boot.",
            "- Classification and future-app reachability remain intentionally unassigned.",
            "- The report excludes its own hash to avoid a self-referential digest.",
            "",
        ]
    )
    return "\n".join(lines).encode("utf-8")


def _write_fsync(path: Path, data: bytes) -> None:
    with path.open("xb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def write_evidence(
    output: Path,
    *,
    target: Mapping[str, object],
    static_records: Sequence[SurfaceRecord],
    runtime_records: Sequence[SurfaceRecord],
    coverage: Sequence[CoverageEntry],
    commands: Sequence[CommandEvidence],
    revisions: str,
) -> None:
    if output.exists() and any(output.iterdir()):
        raise InventoryError(f"refusing non-empty output directory: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.rmdir()
    temporary = Path(tempfile.mkdtemp(prefix=f".{output.name}.", dir=output.parent))
    try:
        static_coverage = [entry for entry in coverage if not entry.family.startswith("RUNTIME_")]
        runtime_coverage = [entry for entry in coverage if entry.family.startswith("RUNTIME_")]
        static_bytes = _json_document(target, static_records, static_coverage)
        runtime_bytes = _json_document(target, runtime_records, runtime_coverage)
        worksheet = canonical_records([*static_records, *runtime_records])
        csv_bytes = _classification_bytes(worksheet)
        revision_bytes = revisions.encode("utf-8")
        bodies = {
            "static-inventory.json": static_bytes,
            "runtime-inventory.json": runtime_bytes,
            "classification.csv": csv_bytes,
            "source-revisions.txt": revision_bytes,
        }
        hashes = {name: _digest(body) for name, body in bodies.items()}
        bodies["collection-report.md"] = _report_bytes(
            len(canonical_records(static_records)),
            len(canonical_records(runtime_records)),
            len(worksheet),
            coverage,
            commands,
            hashes,
        )
        for name, body in bodies.items():
            _write_fsync(temporary / name, body)
        directory_fd = os.open(temporary, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
        temporary.rename(output)
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise


HASH_LINE = re.compile(r"^- `([^`]+)`: `([0-9a-f]{64})`$", re.M)


def verify_output(output: Path, require_complete_coverage: bool = True) -> None:
    names = {path.name for path in output.iterdir() if path.is_file()}
    if names != set(OUTPUT_NAMES):
        raise InventoryError(f"unexpected evidence files: {sorted(names)}")
    documents = []
    for name in ("static-inventory.json", "runtime-inventory.json"):
        raw = (output / name).read_bytes()
        document = json.loads(raw)
        if canonical_json(document) != raw:
            raise InventoryError(f"non-canonical JSON: {name}")
        if document.get("schemaVersion") != SCHEMA_VERSION:
            raise InventoryError(f"schema mismatch: {name}")
        documents.append(document)
    report = (output / "collection-report.md").read_text(encoding="utf-8")
    expected_hashes = dict(HASH_LINE.findall(report))
    for name in OUTPUT_NAMES[:4]:
        if expected_hashes.get(name) != _digest((output / name).read_bytes()):
            raise InventoryError(f"evidence hash mismatch: {name}")
    with (output / "classification.csv").open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != CLASSIFICATION_FIELDS:
            raise InventoryError("classification header mismatch")
        for row in reader:
            if row["bucket"] != "UNCLASSIFIED" or row["future_app_reachability"] != "UNTESTED":
                raise InventoryError("premature classification")
    if require_complete_coverage:
        coverage = [entry for document in documents for entry in document["coverage"]]
        if any(entry["status"] == "UNAVAILABLE" for entry in coverage):
            raise InventoryError("incomplete coverage")
