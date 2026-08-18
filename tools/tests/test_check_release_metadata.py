import unittest

from tools import check_release_metadata as c


class ReleaseMetadataTests(unittest.TestCase):
    def test_current_tree_passes(self):
        errors = c.check(require_tag=False)
        self.assertEqual(errors, [], errors)

    def test_gradle_version_is_r14(self):
        code, name = c.parse_gradle_version()
        self.assertGreaterEqual(code, 199)
        self.assertTrue(name.startswith("r14."))
