import unittest
from pathlib import Path
from unittest.mock import patch

from tools import check_document_contracts as c


class DocumentContractTests(unittest.TestCase):

    def test_allowed_kinds(self):
        self.assertIn("CURRENT", c.ALLOWED_KINDS)
        self.assertIn("SNAPSHOT", c.ALLOWED_KINDS)

    def test_parse_metadata_basic(self):
        text = "# Title\n\n```text\nDocumentKind: CURRENT\nBranch: devin/a14-rom-intelligence-audit\n```\n\nbody"
        meta = c.parse_metadata(text)
        self.assertEqual(meta, {"DocumentKind": "CURRENT", "Branch": "devin/a14-rom-intelligence-audit"})

    def test_parse_metadata_missing(self):
        self.assertIsNone(c.parse_metadata("# Title\n\nNo block"))

    def test_validate_evidence_commit(self):
        ok, classification = c.validate_evidence_commit("HEAD")
        self.assertTrue(ok)
        self.assertEqual("OK", classification)

        ok, classification = c.validate_evidence_commit("0000000000000000000000000000000000000000")
        self.assertFalse(ok)
        self.assertEqual("HISTORY_UNAVAILABLE", classification)

        # A valid 40-hex object that is unlikely to exist should also be unavailable.
        ok, classification = c.validate_evidence_commit("1111111111111111111111111111111111111111")
        self.assertFalse(ok)
        self.assertIn(classification, {"HISTORY_UNAVAILABLE", "INVALID_COMMIT", "NOT_ANCESTOR"})


if __name__ == "__main__":
    unittest.main()
