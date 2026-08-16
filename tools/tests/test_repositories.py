import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SETTINGS = REPO_ROOT / "settings.gradle.kts"
CI = REPO_ROOT / ".github" / "workflows" / "ci.yml"

CHINA_HOSTS = (
    "maven.aliyun.com",
    "mirrors.huaweicloud.com",
)

OFFICIAL_REPOS = (
    "google()",
    "mavenCentral()",
    "gradlePluginPortal()",
)


def find_block_after(text: str, marker: str, start: int = 0) -> str:
    """Return the text inside the first {} block that follows a marker string.

    The marker string itself may end with the opening brace (e.g. '} else {');
    the search begins one character before the end of the marker so that the
    brace is found consistently.
    """
    idx = text.find(marker, start)
    if idx == -1:
        return ""
    brace_idx = text.find("{", idx + max(1, len(marker) - 1))
    if brace_idx == -1:
        return ""
    depth = 1
    end = brace_idx + 1
    while end < len(text) and depth > 0:
        if text[end] == "{":
            depth += 1
        elif text[end] == "}":
            depth -= 1
        end += 1
    return text[brace_idx + 1:end - 1]


class RepositoryPolicyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.assertTrue(SETTINGS.is_file(), f"{SETTINGS} is missing")
        self.settings_text = SETTINGS.read_text(encoding="utf-8")
        self.ci_text = CI.read_text(encoding="utf-8") if CI.is_file() else ""

    def test_ci_does_not_enable_china_mirrors(self):
        if not self.ci_text:
            return
        self.assertNotIn("useChinaMirrors", self.ci_text,
                         "CI workflow must not pass -PuseChinaMirrors")

    def test_china_mirrors_are_conditional(self):
        self.assertIn("useChinaMirrors", self.settings_text,
                      "settings.gradle.kts must read the useChinaMirrors property")

        first_if = find_block_after(self.settings_text, "if (useChinaMirrors)", 0)
        second_if = find_block_after(self.settings_text, "if (useChinaMirrors)",
                                     self.settings_text.find("if (useChinaMirrors)", 0) + 1)
        if_blocks = [first_if, second_if]

        else_markers = [m.start() for m in re.finditer(r"\}\s*else\s*\{", self.settings_text)]
        self.assertEqual(2, len(else_markers), "expected two if/else blocks")

        else_blocks = [
            find_block_after(self.settings_text, "} else {", else_markers[0]),
            find_block_after(self.settings_text, "} else {", else_markers[1]),
        ]

        for i, (if_block, else_block) in enumerate(zip(if_blocks, else_blocks), start=1):
            self.assertTrue(if_block, f"if block #{i} could not be parsed")
            self.assertTrue(else_block, f"else block #{i} could not be parsed")

            for host in CHINA_HOSTS:
                self.assertIn(host, if_block,
                              f"China mirror {host} must be inside the conditional block #{i}")
                self.assertNotIn(host, else_block,
                                 f"China mirror {host} must not be in the default/else block #{i}")

        for repo in OFFICIAL_REPOS:
            self.assertIn(repo, "".join(else_blocks),
                          f"Official repository {repo} must be present in at least one default/else block")

    def test_china_mirrors_have_content_filters(self):
        text = self.settings_text
        for _ in range(2):
            if_block = find_block_after(text, "if (useChinaMirrors)", 0)
            advance = text.find("if (useChinaMirrors)", 0) + 1
            text = text[advance:]
            if not if_block:
                continue
            china_maven_count = sum(h in if_block for h in CHINA_HOSTS)
            content_blocks = if_block.count("content {")
            self.assertGreaterEqual(
                content_blocks, china_maven_count,
                "every China mirror must declare a content filter block"
            )


if __name__ == "__main__":
    unittest.main()
