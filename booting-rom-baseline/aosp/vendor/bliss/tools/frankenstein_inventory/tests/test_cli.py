import csv
import json
import tempfile
import unittest
from pathlib import Path

from frankenstein_inventory.model import CoverageEntry, EvidenceRef, SurfaceRecord
from frankenstein_inventory.render import (
    CLASSIFICATION_FIELDS,
    InventoryError,
    verify_output,
    write_evidence,
)


class CliRenderTest(unittest.TestCase):
    def test_write_and_verify_five_outputs(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "inventory"
            record = SurfaceRecord(
                kind="BINDER_SERVICE",
                name="activity",
                runtime_present="YES",
                evidence=(EvidenceRef("RUNTIME", "service-list", 0, "a" * 64),),
            )
            coverage = [CoverageEntry("RUNTIME_BINDER", "service-list", "COLLECTED", 1, 1)]
            write_evidence(
                output,
                target={"product": "I001D"},
                static_records=[record],
                runtime_records=[record],
                coverage=coverage,
                commands=[],
                revisions="manifestSha256=" + "b" * 64 + "\n",
            )
            self.assertEqual(
                {
                    "static-inventory.json",
                    "runtime-inventory.json",
                    "classification.csv",
                    "source-revisions.txt",
                    "collection-report.md",
                },
                {path.name for path in output.iterdir()},
            )
            verify_output(output, require_complete_coverage=False)
            with (output / "classification.csv").open(newline="") as stream:
                reader = csv.DictReader(stream)
                self.assertEqual(CLASSIFICATION_FIELDS, reader.fieldnames)
                row = next(reader)
                self.assertEqual("UNCLASSIFIED", row["bucket"])
                self.assertEqual("UNTESTED", row["future_app_reachability"])
            self.assertEqual(1, json.loads((output / "static-inventory.json").read_text())["schemaVersion"])
            with self.assertRaises(InventoryError):
                write_evidence(
                    output,
                    target={},
                    static_records=[],
                    runtime_records=[],
                    coverage=[],
                    commands=[],
                    revisions="",
                )


if __name__ == "__main__":
    unittest.main()
