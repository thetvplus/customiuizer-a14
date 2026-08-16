import unittest

from tools import check_goal_constitution as c


class GoalConstitutionTests(unittest.TestCase):

    def test_required_items_present(self):
        errors = c.check()
        for required in ["tomthenpc/customiuizer-a14", "devin/a14-rom-intelligence-audit",
                         "ANDROID_14_ACTIVE_STABLE_REFERENCE", "LONG_HORIZON_CONSTITUTION.md"]:
            self.assertFalse(any(required in e for e in errors),
                             f"Missing required item: {required}; errors={errors}")


if __name__ == "__main__":
    unittest.main()
