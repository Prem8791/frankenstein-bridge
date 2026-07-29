import json
import unittest
from dataclasses import replace

from frankenstein_inventory.model import (
    CoverageEntry,
    EvidenceRef,
    SurfaceRecord,
    canonical_coverage,
    canonical_json,
    canonical_records,
)


class ModelTest(unittest.TestCase):
    def test_record_id_ignores_runtime_classification_and_source_line(self):
        base = SurfaceRecord(
            kind="BINDER_SERVICE",
            name="activity",
            evidence=(EvidenceRef("SOURCE", "Owner.java", 10, "a" * 64),),
        )
        changed = replace(
            base,
            runtime_present="YES",
            bucket="A",
            rationale="framework private",
            evidence=(EvidenceRef("SOURCE", "Owner.java", 11, "b" * 64),),
        )
        self.assertEqual(base.record_id(), changed.record_id())

    def test_record_id_changes_with_surface_identity(self):
        a = SurfaceRecord(kind="BINDER_SERVICE", name="activity")
        b = SurfaceRecord(kind="BINDER_SERVICE", name="window")
        self.assertNotEqual(a.record_id(), b.record_id())

    def test_canonical_records_merges_evidence_and_runtime_presence(self):
        records = canonical_records(
            [
                SurfaceRecord(
                    kind="BINDER_SERVICE",
                    name="window",
                    evidence=(EvidenceRef("SOURCE", "Wms.java", 1, "a" * 64),),
                ),
                SurfaceRecord(kind="BINDER_SERVICE", name="activity"),
                SurfaceRecord(
                    kind="BINDER_SERVICE",
                    name="window",
                    runtime_present="YES",
                    evidence=(EvidenceRef("RUNTIME", "service-list", 0, "b" * 64),),
                ),
            ]
        )
        self.assertEqual(["activity", "window"], [record.name for record in records])
        self.assertEqual("YES", records[1].runtime_present)
        self.assertEqual(2, len(records[1].evidence))

    def test_canonical_records_rejects_conflicting_metadata(self):
        with self.assertRaises(ValueError):
            canonical_records(
                [
                    SurfaceRecord(kind="INIT_SERVICE", name="x", partition="system"),
                    SurfaceRecord(kind="INIT_SERVICE", name="x", partition="vendor"),
                ]
            )

    def test_canonical_json_is_stable_and_valid(self):
        document = {"schemaVersion": 1, "records": [{"name": "activity"}]}
        encoded = canonical_json(document)
        self.assertEqual(encoded, canonical_json(document))
        self.assertTrue(encoded.endswith(b"\n"))
        self.assertEqual(document, json.loads(encoded))

    def test_coverage_keeps_absence_visible(self):
        entries = canonical_coverage(
            [CoverageEntry("APEX", "system/apex", "ABSENT", 0, 0, "not in product")]
        )
        self.assertEqual("ABSENT", entries[0].status)


if __name__ == "__main__":
    unittest.main()
