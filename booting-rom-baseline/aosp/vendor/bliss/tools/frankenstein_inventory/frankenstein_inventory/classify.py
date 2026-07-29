"""Measured reachability merge and ROM-freeze decision closure."""

from __future__ import annotations

import csv
from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Iterable

REACHABILITY = {"REACHABLE", "DENIED", "PARTIAL", "NOT_APPLICABLE"}
BUCKETS = {"A", "B", "C", "D"}


@dataclass(frozen=True)
class Decision:
    stable_id: str
    bucket: str
    reachability_broker: str
    reachability_ordinary: str
    rationale: str
    provider_id: str
    provider_owner: str
    dependency_contract: str
    failure_contract: str
    test_id: str


def load_probe(path: Path) -> dict[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    result: dict[str, str] = {}
    for stable_id, state in data.items():
        if state not in REACHABILITY:
            raise ValueError(f"invalid reachability {state!r} for {stable_id}")
        result[str(stable_id)] = state
    return result


def load_overrides(path: Path) -> dict[str, dict[str, str]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("decision overrides must be an object keyed by stable ID")
    return {str(key): dict(value) for key, value in data.items()}


def classify(
    worksheet: Path,
    broker_probe: dict[str, str],
    ordinary_probe: dict[str, str],
    overrides: dict[str, dict[str, str]],
) -> list[Decision]:
    rows = list(csv.DictReader(worksheet.open(encoding="utf-8", newline="")))
    decisions: list[Decision] = []
    unknown_overrides = set(overrides)
    for row in rows:
        stable_id = row["stable_id"]
        override = overrides.get(stable_id, {})
        unknown_overrides.discard(stable_id)
        broker = broker_probe.get(stable_id, "NOT_APPLICABLE")
        ordinary = ordinary_probe.get(stable_id, "NOT_APPLICABLE")
        bucket = override.get("bucket", "")
        if bucket not in BUCKETS:
            raise ValueError(f"{stable_id}: explicit A/B/C/D decision required")
        decision = Decision(
            stable_id=stable_id,
            bucket=bucket,
            reachability_broker=broker,
            reachability_ordinary=ordinary,
            rationale=override.get("rationale", ""),
            provider_id=override.get("provider_id", ""),
            provider_owner=override.get("provider_owner", ""),
            dependency_contract=override.get("dependency_contract", ""),
            failure_contract=override.get("failure_contract", ""),
            test_id=override.get("test_id", ""),
        )
        validate_decision(decision)
        decisions.append(decision)
    if unknown_overrides:
        raise ValueError(f"overrides reference absent surfaces: {sorted(unknown_overrides)}")
    return sorted(decisions, key=lambda item: item.stable_id)


def validate_decision(decision: Decision) -> None:
    if not decision.rationale:
        raise ValueError(f"{decision.stable_id}: rationale required")
    if decision.bucket == "A":
        required = (
            decision.provider_id,
            decision.provider_owner,
            decision.dependency_contract,
            decision.failure_contract,
            decision.test_id,
        )
        if not all(required):
            raise ValueError(f"{decision.stable_id}: Bucket A provider closure incomplete")
    if decision.bucket in {"B", "C"}:
        expected = "REACHABLE"
        actual = (decision.reachability_broker if decision.bucket == "B"
                  else decision.reachability_ordinary)
        if actual != expected or not decision.test_id:
            raise ValueError(f"{decision.stable_id}: Bucket {decision.bucket} needs measured reachability")
    if decision.bucket == "D" and decision.provider_id:
        raise ValueError(f"{decision.stable_id}: rejected surface cannot have a provider")


def write_manifest(decisions: Iterable[Decision], output: Path) -> None:
    payload = {
        "format_version": 1,
        "decisions": [decision.__dict__ for decision in decisions],
    }
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.write_text(encoded + "\n", encoding="utf-8")
    temporary.replace(output)
