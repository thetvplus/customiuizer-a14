"""Extraction evidence must prove Kotlin analysis, not only a green workflow."""
import unittest

from tools.check_codeql_extraction import validate


def report(files, *, lines=23, successful=True):
    return {"runs": [{
        "invocations": [{
            "executionSuccessful": successful,
            "toolExecutionNotifications": [{
                "descriptor": {"id": "java/diagnostics/successfully-extracted-files"},
                "locations": [{"physicalLocation": {"artifactLocation": {"uri": path}}}],
            } for path in files],
        }],
        "properties": {"metricResults": [{
            "ruleId": "java/summary/lines-of-code-kotlin", "value": lines,
        }]},
        "results": [{"ruleId": "java/security-finding"}],
    }]}


class CodeqlExtractionTest(unittest.TestCase):
    expected = {"app/src/main/A.kt", "app/src/main/B.kt", "app/src/main/C.java"}

    def test_complete_sources_with_generated_extra_and_real_finding(self):
        data = report(self.expected | {"app/build/generated/Catalog.kt"})
        self.assertEqual({"production_kotlin_files": 2, "production_java_files": 1,
                          "kotlin_lines": 23, "findings": 1}, validate(data, self.expected))

    def test_java_only_and_partly_missing_kotlin_fail(self):
        for files in ({"app/src/main/C.java"}, self.expected - {"app/src/main/B.kt"}):
            with self.subTest(files=files), self.assertRaisesRegex(ValueError, "extraction missing"):
                validate(report(files), self.expected)

    def test_file_metadata_without_positive_kotlin_analysis_fails(self):
        for lines in (None, 0, -1, True, "23", float("nan"), float("inf")):
            with self.subTest(lines=lines), self.assertRaisesRegex(ValueError, "Kotlin analysis metric"):
                validate(report(self.expected, lines=lines), self.expected)

    def test_execution_failure_is_not_a_security_finding(self):
        with self.assertRaisesRegex(ValueError, "execution failed"):
            validate(report(self.expected, successful=False), self.expected)

    def test_missing_run_or_kotlin_inventory_fails(self):
        with self.assertRaisesRegex(ValueError, "one Java/Kotlin"):
            validate({"runs": []}, self.expected)
        with self.assertRaisesRegex(ValueError, "No production Kotlin"):
            validate(report([]), set())


if __name__ == "__main__":
    unittest.main()
