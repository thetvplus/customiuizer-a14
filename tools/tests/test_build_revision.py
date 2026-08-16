#!/usr/bin/env python3
"""Unit tests for build-revision helpers."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import build_revision


class ValidateRevisionTest(unittest.TestCase):
    def test_valid_lowercase_sha(self) -> None:
        self.assertEqual("50a0ad4f", build_revision.validate_revision("50a0ad4f"))

    def test_valid_uppercase_sha_normalizes_to_lowercase(self) -> None:
        self.assertEqual("50a0ad4f", build_revision.validate_revision("50A0AD4F"))

    def test_none_rejected(self) -> None:
        with self.assertRaises(ValueError):
            build_revision.validate_revision(None)

    def test_unknown_rejected(self) -> None:
        with self.assertRaises(ValueError):
            build_revision.validate_revision("unknown")

    def test_short_sha_rejected(self) -> None:
        with self.assertRaises(ValueError):
            build_revision.validate_revision("50a0ad4")

    def test_long_sha_rejected(self) -> None:
        with self.assertRaises(ValueError):
            build_revision.validate_revision("50a0ad4f21b3604b")

    def test_non_hex_sha_rejected(self) -> None:
        with self.assertRaises(ValueError):
            build_revision.validate_revision("50a0ad4g")


class NormalizeResolvedTypeTest(unittest.TestCase):
    def test_negative_one_is_none(self) -> None:
        self.assertIsNone(build_revision.normalize_resolved_type(-1))

    def test_zero_is_zero(self) -> None:
        self.assertEqual(0, build_revision.normalize_resolved_type(0))

    def test_one_is_one(self) -> None:
        self.assertEqual(1, build_revision.normalize_resolved_type(1))

    def test_one_twenty_eight_is_one_twenty_eight(self) -> None:
        self.assertEqual(128, build_revision.normalize_resolved_type(128))

    def test_none_stays_none(self) -> None:
        self.assertIsNone(build_revision.normalize_resolved_type(None))


class AllowedUntrackedTest(unittest.TestCase):
    def test_self_eval_not_allowed(self) -> None:
        self.assertFalse(build_revision.is_allowed_untracked("?? .self-eval-scores.jsonl"))

    def test_allowed_dir(self) -> None:
        self.assertTrue(build_revision.is_allowed_untracked("?? DEVIN_LOCAL_A14_SKILLS_V2/"))

    def test_allowed_dir_subfile(self) -> None:
        self.assertTrue(
            build_revision.is_allowed_untracked(
                "?? DEVIN_LOCAL_A14_SKILLS_V2/foo/bar.txt"
            )
        )

    def test_disallowed_untracked(self) -> None:
        self.assertFalse(build_revision.is_allowed_untracked("?? random-file.txt"))

    def test_tracked_change_not_allowed(self) -> None:
        self.assertFalse(build_revision.is_allowed_untracked(" M app/build.gradle.kts"))


if __name__ == "__main__":
    unittest.main()
