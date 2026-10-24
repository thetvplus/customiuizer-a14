"""Exercise the real UI sources plus drift, route and Markdown edge cases."""
from __future__ import annotations

import shutil
import re
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import check_docs
import generate_feature_map as feature_map
import generate_preference_artifacts as prefs


class FeatureMapTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        for directory in (feature_map.RES / "xml", feature_map.RES / "values", feature_map.RES / "values-zh-rCN", feature_map.RES / "layout", feature_map.RES / "menu", feature_map.JAVA):
            shutil.copytree(ROOT / directory, self.repo / directory)
        matrix = Path("docs/rom-intelligence/A14_PROCESS_MATRIX.json")
        (self.repo / matrix).parent.mkdir(parents=True)
        shutil.copyfile(ROOT / matrix, self.repo / matrix)

    def test_every_page_and_visible_key_in_real_inputs_is_mapped(self):
        strings = feature_map.load_strings(self.repo)
        pages = feature_map.collect_pages(self.repo, strings)
        self.assertEqual(34, sum(not p.standalone for p in pages))
        self.assertEqual({p[0] for p in prefs.STANDALONE_PAGES}, {p.key for p in pages if p.standalone})
        rendered = feature_map.render(self.repo)
        for path in (self.repo / feature_map.RES / "xml").glob("prefs_*.xml"):
            for element in ET.parse(path).getroot().iter():
                key = element.get(prefs.ANDROID_KEY)
                if key and element.tag not in ("PreferenceScreen", prefs.PREFERENCE_CATEGORY) and element.get(prefs.AUTO_VISIBLE) != "false":
                    with self.subTest(path=path.name, key=key):
                        self.assertTrue(f"`{key}`" in rendered, f"map missing {path.name}: {key}")
        self.assertIn("系统 → 状态栏 → Duo 三合一图标", rendered)
        self.assertIn("系统 → 动态岛 → 音乐手势", rendered)
        self.assertIn("桌面 → Dock", rendered)
        self.assertNotIn("system_betterpopups_nohide", rendered)
        self.assertNotIn("状态刷新", rendered)

    def test_document_matches_and_generation_is_deterministic(self):
        first = feature_map.render(ROOT).encode("utf-8")
        self.assertEqual(first, feature_map.render(ROOT).encode("utf-8"))
        self.assertEqual(first, (ROOT / "docs/FEATURE_MAP.md").read_bytes())
        self.assertEqual(first, feature_map.render(self.repo).encode("utf-8"))
        simple = feature_map.render_simple(ROOT).encode("utf-8")
        self.assertEqual(simple, feature_map.render_simple(ROOT).encode("utf-8"))
        self.assertEqual(simple, (ROOT / "docs/FEATURE_MAP_SIMPLE.md").read_bytes())

    def test_simple_tree_has_exact_business_page_structure_and_order(self):
        pages = feature_map.collect_pages(self.repo, feature_map.load_strings(self.repo))
        categories = {page.path[0] for page in pages}
        stack, actual = [], []
        for line in feature_map.render_simple(self.repo).splitlines():
            match = re.match(r"^((?:│  |   )*)(?:├─ |└─ )(.+)$", line)
            if match:
                depth = len(match[1]) // 3
                stack[depth:] = [match[2]]
                if len(stack) >= 2 and stack[0] in categories:
                    actual.append(tuple(stack))
        self.assertEqual([page.path for page in pages], actual)

    def test_default_cli_checks_both_maps_and_writes_only_documentation(self):
        before = {p.relative_to(self.repo): p.read_bytes() for p in self.repo.rglob("*") if p.is_file()}
        self.assertEqual(0, self.cli().returncode)
        after = {p.relative_to(self.repo): p.read_bytes() for p in self.repo.rglob("*") if p.is_file()}
        self.assertEqual({Path("docs/FEATURE_MAP.md"), Path("docs/FEATURE_MAP_SIMPLE.md")}, set(after) - set(before))
        for name, content in before.items():
            self.assertTrue(after[name] == content, f"generation modified input: {name}")
        self.assertEqual(0, self.cli().returncode)
        self.assertTrue(all((self.repo / name).read_bytes() == content for name, content in after.items()), "second generation changed output")
        self.assertEqual(0, self.cli("--check").returncode)
        simple = self.repo / "docs/FEATURE_MAP_SIMPLE.md"
        simple.write_text("stale", encoding="utf-8")
        self.assertEqual(1, self.cli("--check").returncode)
        self.assertEqual("stale", simple.read_text(encoding="utf-8"))

    def cli(self, *arguments):
        return subprocess.run([sys.executable, str(ROOT / "tools/generate_feature_map.py"), "--repo-root", str(self.repo), *arguments], capture_output=True, text=True)

    def test_check_fails_on_missing_or_stale_document_without_writing(self):
        output = self.repo / "map.md"
        self.assertEqual(1, self.cli("--output", str(output), "--check").returncode)
        self.assertFalse(output.exists())
        self.assertEqual(0, self.cli("--output", str(output)).returncode)
        original = output.read_bytes()
        self.assertEqual(0, self.cli("--output", str(output), "--check").returncode)
        source = self.repo / feature_map.RES / "values-zh-rCN/strings.xml"
        text = source.read_text(encoding="utf-8")
        self.assertIn('name="system_mods"', text)
        source.write_text(text.replace('name="system_mods">系统<', 'name="system_mods">系统测试<'), encoding="utf-8")
        self.assertNotEqual(original, feature_map.render(self.repo).encode())
        self.assertEqual(1, self.cli("--output", str(output), "--check").returncode)
        self.assertEqual(original, output.read_bytes())

    def test_new_literal_title_dependencies_hidden_rows_and_controller_drift(self):
        source = self.repo / feature_map.RES / "xml/prefs_system_autobrightness.xml"
        root = ET.parse(source).getroot()
        ET.SubElement(root, prefs.PREFERENCE_ITEM, {prefs.ANDROID_KEY: "pref_key_map_fixture", prefs.ANDROID_TITLE: "边界|选项", feature_map.ANDROID + "dependency": "pref_key_system_autobrightness"})
        ET.SubElement(root, prefs.PREFERENCE_ITEM, {prefs.ANDROID_KEY: "pref_key_map_hidden", prefs.ANDROID_TITLE: "不可见", prefs.AUTO_VISIBLE: "false"})
        ET.ElementTree(root).write(source, encoding="utf-8")
        rendered = feature_map.render(self.repo)
        self.assertIn("边界\\|选项", rendered)
        self.assertIn("`pref_key_map_fixture` | 条件启用：`pref_key_system_autobrightness`", rendered)
        self.assertNotIn("pref_key_map_hidden", rendered)
        owner = self.repo / feature_map.JAVA / "subs/System.kt"
        owner.write_text(owner.read_text(encoding="utf-8") + "\n// changed controller condition\n", encoding="utf-8")
        self.assertNotEqual(rendered, feature_map.render(self.repo))

    def test_missing_resource_route_and_orphan_page_fail_closed(self):
        strings = feature_map.load_strings(self.repo)
        with self.assertRaises(KeyError):
            feature_map.resolve("@string/not_present", strings)
        resolver = self.repo / feature_map.JAVA / "utils/PreferenceResourceResolver.kt"
        old = resolver.read_text(encoding="utf-8")
        resolver.write_text(old.replace("R.xml.prefs_system_screen", "R.xml.prefs_missing"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Resolver routes"):
            feature_map.collect_pages(self.repo, strings)
        resolver.write_text(old, encoding="utf-8")
        (self.repo / feature_map.RES / "xml/prefs_orphan.xml").write_text("<PreferenceScreen/>")
        with self.assertRaisesRegex(ValueError, "Unmapped preference XML"):
            feature_map.collect_pages(self.repo, strings)

    def test_home_menu_shared_pages_and_runtime_only_features(self):
        rendered = feature_map.render(self.repo)
        menu = ET.parse(self.repo / feature_map.RES / "menu/menu_mods.xml").getroot()
        for item in menu.iter("item"):
            key = item.attrib[feature_map.ANDROID + "id"].split("/")[-1]
            self.assertIn(f"`R.id.{key}`", rendered)
        for owner in ("MultiAction", "AppSelector", "ActivitySelector", "ShortcutSelector", "SortableList", "WiFiList", "BTList", "ColorSelector"):
            self.assertIn(f"{owner}.kt", rendered)
        self.assertIn("93: force_clock_use_system_fonts", rendered)
        self.assertNotIn("206: settings_miuizer_icon", rendered)
        self.assertIn("国际版隐藏", rendered)
        self.assertIn("全宽底栏开启后隐藏", rendered)
        for key in feature_map.SETTINGS_ACTION_KEYS:
            self.assertRegex(rendered, re.escape(f"`{key}`") + r" \| [^\n]*设置端动作")


class MarkdownLinksTest(unittest.TestCase):
    def test_links_images_references_unicode_duplicate_anchors_and_code(self):
        with tempfile.TemporaryDirectory() as folder:
            repo = Path(folder)
            target = repo / "page (test).md"
            target.write_text("# 中文 / API 102\n\n## 重复\n\n## 重复\n", encoding="utf-8")
            (repo / "image.png").write_bytes(b"fixture")
            source = repo / "README.md"
            source.write_text('[正文](<page (test).md#中文--api-102>)\n[第二次](page%20%28test%29.md#重复-1)\n[引用][target]\n[target]: <page (test).md>\n<img src="image.png">\n![图](image.png "标题")\n```md\n[代码不检查](missing.md)\n```\n[外链](https://example.test/a(b)#x)\n', encoding="utf-8")
            self.assertEqual([], check_docs.check_file(repo, source, {}))
            source.write_text("[坏链接](missing.md)\n[坏锚点](<page (test).md#missing>)\n<img src='missing.png'>", encoding="utf-8")
            errors = check_docs.check_file(repo, source, {})
            self.assertEqual(3, len(errors))
            self.assertTrue(any("missing anchor" in error for error in errors))

    def test_invalid_reference_and_unclosed_link_fail(self):
        for text in ("[text][undefined]", "[text](missing.md"):
            with self.assertRaises(ValueError):
                list(check_docs.destinations(text))

    def test_all_repository_markdown_links_and_existing_skills(self):
        cache = {}
        for path in check_docs.markdown_files(ROOT):
            with self.subTest(path=path):
                self.assertEqual([], check_docs.check_file(ROOT, path, cache))
        for skill in ("a14-safe-implementation", "a14-independent-review"):
            source = (ROOT / f".agents/skills/{skill}/SKILL.md").read_text(encoding="utf-8")
            self.assertTrue(source.startswith("---\nname:"))
            self.assertIn("description:", source)
        for stem in ("DUO" + "_STATUS_BAR", "DYNAMIC" + "_ISLAND", "features/" + "README"):
            self.assertFalse((ROOT / "docs" / f"{stem}.md").exists())


if __name__ == "__main__":
    unittest.main()
