import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RULES = ROOT / "app" / "proguard-rules.pro"


class R8KeepRulesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.rules = RULES.read_text(encoding="utf-8")

    def test_xposed_runtime_entry_rules_remain_explicit(self) -> None:
        self.assertRegex(
            self.rules,
            re.compile(
                r"-keep,allowoptimization,allowobfuscation public class \* "
                r"extends io\.github\.libxposed\.api\.XposedModule \{\s*"
                r"public <init>\(\);\s*\}",
                re.DOTALL,
            ),
        )
        self.assertRegex(
            self.rules,
            re.compile(
                r"-keep,allowobfuscation class \* implements "
                r"io\.github\.libxposed\.api\.XposedInterface\$Hooker \{ \*; \}",
            ),
        )

    def test_mods_members_are_not_globally_kept(self) -> None:
        self.assertNotRegex(
            self.rules,
            re.compile(
                r"-keepclassmembers\s+class\s+"
                r"tv\.withaibuild\.customiuizer\.mods\.\*\*\s*\{",
            ),
        )


if __name__ == "__main__":
    unittest.main()
