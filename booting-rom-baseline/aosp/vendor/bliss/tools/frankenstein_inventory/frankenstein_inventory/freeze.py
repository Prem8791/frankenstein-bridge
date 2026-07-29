"""Final reconciliation and deterministic canonical-CBOR inventory packaging."""

from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
from typing import Any


def _head(major: int, value: int) -> bytes:
    if value < 24:
        return bytes([(major << 5) | value])
    if value <= 0xFF:
        return bytes([(major << 5) | 24, value])
    if value <= 0xFFFF:
        return bytes([(major << 5) | 25]) + value.to_bytes(2, "big")
    if value <= 0xFFFFFFFF:
        return bytes([(major << 5) | 26]) + value.to_bytes(4, "big")
    return bytes([(major << 5) | 27]) + value.to_bytes(8, "big")


def canonical_cbor(value: Any) -> bytes:
    if value is None:
        return b"\xf6"
    if value is False:
        return b"\xf4"
    if value is True:
        return b"\xf5"
    if isinstance(value, int):
        return _head(0, value) if value >= 0 else _head(1, -1 - value)
    if isinstance(value, bytes):
        return _head(2, len(value)) + value
    if isinstance(value, str):
        encoded = value.encode("utf-8")
        return _head(3, len(encoded)) + encoded
    if isinstance(value, list):
        return _head(4, len(value)) + b"".join(canonical_cbor(item) for item in value)
    if isinstance(value, dict):
        pairs = [(canonical_cbor(key), canonical_cbor(item)) for key, item in value.items()]
        pairs.sort(key=lambda pair: (len(pair[0]), pair[0]))
        return _head(5, len(pairs)) + b"".join(key + item for key, item in pairs)
    raise TypeError(f"unsupported CBOR type: {type(value).__name__}")


def reconcile(static_path: Path, runtime_path: Path, decisions_path: Path) -> dict[str, Any]:
    static = json.loads(static_path.read_text(encoding="utf-8"))
    runtime = json.loads(runtime_path.read_text(encoding="utf-8"))
    decisions = json.loads(decisions_path.read_text(encoding="utf-8"))
    decision_rows = decisions.get("decisions", [])
    by_id = {row["stable_id"]: row for row in decision_rows}
    records = static.get("records", [])
    missing = sorted(record["stable_id"] for record in records
                     if record["stable_id"] not in by_id)
    orphaned = sorted(set(by_id) - {record["stable_id"] for record in records})
    incomplete_a = sorted(row["stable_id"] for row in decision_rows
                          if row["bucket"] == "A"
                          and not all(row.get(field) for field in (
                              "provider_id", "provider_owner", "dependency_contract",
                              "failure_contract", "test_id")))
    if missing or orphaned or incomplete_a:
        raise ValueError(
            f"reconciliation failed missing={missing} orphaned={orphaned} "
            f"incomplete_a={incomplete_a}"
        )
    return {
        "format_version": 1,
        "static_inventory_sha256": sha256(static_path.read_bytes()).hexdigest(),
        "runtime_inventory_sha256": sha256(runtime_path.read_bytes()).hexdigest(),
        "decision_manifest_sha256": sha256(decisions_path.read_bytes()).hexdigest(),
        "records": [
            {**record, "decision": by_id[record["stable_id"]]}
            for record in sorted(records, key=lambda row: row["stable_id"])
        ],
        "runtime": runtime,
    }


def package_inventory(reconciled: dict[str, Any], output: Path) -> str:
    encoded = canonical_cbor(reconciled)
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.write_bytes(encoded)
    temporary.replace(output)
    return sha256(encoded).hexdigest()
