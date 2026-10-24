#!/usr/bin/env python3
"""Generate/check the documentation map; never writes Android build artifacts."""
from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import generate_preference_artifacts as prefs

REPO_ROOT = Path(__file__).resolve().parent.parent
RES = Path("app/src/main/res")
JAVA = Path("app/src/main/java/tv/withaibuild/customiuizer")
ANDROID = f"{{{prefs.ANDROID_NS}}}"
AUTO = f"{{{prefs.AUTO_NS}}}"

# Click-only controller actions; these are not independent Feature registrations.
SETTINGS_ACTION_KEYS = {
    "pref_key_system_cleanshare_test", "pref_key_system_cleanopenwith_test",
    "pref_key_system_batteryindicator_test", "pref_key_various_clear_update_state",
    "pref_key_various_runningservices", "pref_key_various_batteryoptimization",
    "pref_key_various_aospnotif",
}

# Kotlin conditions cannot be inferred safely from XML. Keep only their concise
# navigation implications here; the source stamp forces review when owners change.
PAGE_NOTES = {
    "pref_key_system_cat_toasts": "位置选项只显示对应的顶部 / 居中 / 底部偏移。",
    "pref_key_system_cat_qs": "国际版隐藏“对调控制中心与通知中心”。",
    "pref_key_system_cat_island": "动态岛模式启用岛内参数；纵向偏移仅在该模式显示。应用反馈也可用于匹配状态栏高度模式。音乐 / 通知接入及允许名单仍有各自开关。",
    "pref_key_system_island_music_cat": "需动态岛模式及音乐与媒体开启；动作编辑沿用当前项键和 _action 等派生键。",
    "pref_key_system_island_feedback_cat": "需动态岛模式；各类反馈分别启用，不把旧用户迁移值当作新安装默认值。",
    "pref_key_system_detailednetspeed_cat": "隐藏 B/s、隐藏低速及阈值只在详细样式 2 / 3 可编辑；原生样式保留存储值。",
    "pref_key_system_visualizer_cat": "自定义颜色仅在颜色模式 2 可编辑，动态颜色变化间隔仅在模式 5 可编辑。",
    "pref_key_system_batteryindicator_cat": "颜色模式 3 禁用四项自定义颜色；测试项在设置端发送既有测试请求。",
    "pref_key_system_screenshot_cat": "质量仅在截图格式值 2 / 4 可编辑，自定义目录仅在保存位置值 4 可选；点击目录进入系统文件夹选择器。",
    "pref_key_system_cat_lockscreen": "设备已加密时“开机免密”不可用。",
    "pref_key_system_noscreenlock_cat": "受信任 Wi-Fi / 蓝牙仅在对应模式可编辑并检查权限；加密设备固定首次解锁要求。",
    "pref_key_launcher_cat_dock": "全宽底栏开启后隐藏圆角、水平间距与背景尺寸微调；背景开关的其他依赖仍生效。",
    "pref_key_launcher_cat_folders": "文件夹列数决定全宽和减少边距是否可编辑。",
    "pref_key_controls_cat_navbar": "输入法全面屏修复要求已隐藏手势提示线；其他依赖由 XML 决定。",
    "pref_key_controls_cat_fingerprint": "系统触感关闭时仍振动，需已选择成功振动。",
    "pref_key_controls_cat_fsg": "禁止振动需已配置侧滑停顿动作。",
    "pref_key_various_cat_general": "国际版隐藏远程检测拦截入口；旧备份不能绕过安装门禁。",
    "pref_key_various_cat_package_installer": "小米安装器缺失或停用时，“允许其更新系统应用”标为不支持。",
    "pref_key_various_cat_exclusive": "维护动作只在设置端发出请求，由既有特权桥执行；目标包 / 组件缺失时不可用。恢复原状态记录不随普通备份传输。",
}


@dataclasses.dataclass
class Page:
    path: tuple[str, ...]
    key: str
    source: str
    controller: str
    root: ET.Element
    standalone: bool = False


def cell(text: str) -> str:
    return text.replace("\\'", "'").replace("|", "\\|").replace("\n", " ").strip()


def load_strings(repo: Path) -> dict[str, str]:
    strings: dict[str, str] = {}
    for folder in ("values", "values-zh-rCN"):
        for file in sorted((repo / RES / folder).glob("*.xml")):
            for element in ET.parse(file).getroot().findall("string"):
                strings[element.attrib["name"]] = "".join(element.itertext())
    return strings


def resolve(value: str, strings: dict[str, str]) -> str:
    if value.startswith("@string/"):
        return strings[value.removeprefix("@string/")]
    if value.startswith("@"):
        raise ValueError(f"Unresolved title resource: {value}")
    return value


def collect_pages(repo: Path, strings: dict[str, str]) -> list[Page]:
    xml_dir = repo / RES / "xml"
    resolver = (repo / JAVA / "utils/PreferenceResourceResolver.kt").read_text(encoding="utf-8")
    routes = dict(re.findall(r'"(pref_key_\w+_cat_\w+)" -> R\.xml\.(\w+)', resolver))
    standalone = {row[0]: row[1:] for row in prefs.STANDALONE_PAGES}
    seen: set[str] = set()
    expected_routes: dict[str, str] = {}
    pages: list[Page] = []
    home = ET.parse(xml_dir / "prefs_main.xml").getroot()
    home_categories = [e.get(prefs.ANDROID_KEY) for e in home.iter() if e.get(prefs.ANDROID_KEY) in {c.key for c in prefs.CATEGORY_SOURCES}]
    if home_categories != [c.key for c in prefs.CATEGORY_SOURCES]:
        raise ValueError("Home categories differ from CATEGORY_SOURCES")
    for category in prefs.CATEGORY_SOURCES:
        root = ET.parse(xml_dir / category.source_name).getroot()
        # Reuse search breadcrumb/route parsing, rather than another page registry.
        search = prefs._search_entries(category, root)
        for group in root.iter(prefs.PREFERENCE_CATEGORY):
            key = group.get(prefs.ANDROID_KEY, "")
            if not key:
                continue
            resource = prefs._split_name(category, key).removesuffix(".xml")
            expected_routes[key] = resource
            path = (resolve(category.title, strings), resolve(group.attrib[prefs.ANDROID_TITLE], strings))
            controller = {"system": "System", "launcher": "Launcher", "controls": "Controls", "various": "Various"}[category.suffix]
            pages.append(Page(path, key, category.source_name, controller, group))
            for entry in search:
                if entry.get("routeSub") != key or entry["key"] not in standalone:
                    continue
                resource, owner, _dynamic = standalone[entry["key"]]
                seen.add(entry["key"])
                child = ET.parse(xml_dir / f"{resource}.xml").getroot()
                pages.append(Page(path + (resolve(entry["title"], strings),), entry["key"], f"{resource}.xml", owner, child, True))
    if routes != expected_routes:
        raise ValueError("PreferenceResourceResolver routes differ from canonical categories")
    if seen != set(standalone):
        raise ValueError(f"Unreachable standalone pages: {sorted(set(standalone) - seen)}")
    covered = {p.source for p in pages} | {"prefs_main.xml"}
    orphaned = {p.name for p in xml_dir.glob("prefs_*.xml")} - covered
    if orphaned:
        raise ValueError(f"Unmapped preference XML: {sorted(orphaned)}")
    navigation = "\n".join((repo / JAVA / "subs" / f"{owner}.kt").read_text(encoding="utf-8") for owner in ("System", "Various"))
    for page in pages:
        owner = JAVA / (f"subs/{page.controller}.kt" if page.controller != "SubFragment" else "SubFragment.kt")
        if not (repo / owner).is_file():
            raise ValueError(f"Missing page controller: {owner}")
        if page.standalone and not re.search(r"\bR\.xml\." + re.escape(Path(page.source).stem) + r"\b", navigation):
            raise ValueError(f"Missing controller navigation: {page.source}")
    return pages


def rows(root: ET.Element, strings: dict[str, str], section: str = "", dependencies: tuple[str, ...] = ()):
    dependency = root.get(ANDROID + "dependency")
    if dependency:
        dependencies += (dependency,)
    for element in root:
        if element.get(prefs.AUTO_VISIBLE, "true").lower() == "false":
            continue
        title = resolve(element.get(prefs.ANDROID_TITLE, ""), strings)
        if element.tag == prefs.PREFERENCE_CATEGORY:
            heading = section if element.get(AUTO + "state") in ("1", "2") else title or section
            yield from rows(element, strings, heading, dependencies)
            continue
        key = element.get(prefs.ANDROID_KEY)
        if not key:
            continue  # explanatory text has no independent action or setting
        label = " → ".join(filter(None, (section, title or "（无独立标题的数值滑杆）")))
        conditions = dependencies + ((element.attrib[ANDROID + "dependency"],) if ANDROID + "dependency" in element.attrib else ())
        state = "条件启用：" + "、".join(f"`{k}`" for k in dict.fromkeys(conditions)) if conditions else "可见"
        if key in SETTINGS_ACTION_KEYS:
            state += "；设置端动作"
        yield label, key, state


def contract_stamp(repo: Path) -> str:
    # Review signal for the small, manually described Kotlin/menu/editor conditions.
    paths = set((repo / JAVA / "subs").glob("*.kt")) | set((repo / JAVA / "prefs").glob("*.kt"))
    paths.update(repo / JAVA / name for name in ("MainFragment.kt", "MainActivity.kt", "SubFragment.kt", "SubFragmentWithSearch.kt", "PreferenceFragmentBase.kt", "AboutFragment.kt", "utils/PreferenceResourceResolver.kt"))
    paths.update((repo / RES / "xml").glob("prefs_*.xml"))
    paths.update((repo / RES / "layout").glob("prefs_*.xml"))
    paths.add(repo / RES / "layout/fragment_about.xml")
    paths.update((repo / JAVA / "mods/utils/feature").glob("*.kt"))
    digest = hashlib.sha256()
    for path in sorted(paths, key=lambda p: p.relative_to(repo).as_posix()):
        digest.update(path.relative_to(repo).as_posix().encode())
        digest.update(b"\0")
        digest.update(path.read_bytes().replace(b"\r\n", b"\n"))
    return digest.hexdigest()


def menu_entries(repo: Path, strings: dict[str, str]):
    def visit(group: ET.Element, parent: tuple[str, ...] = ()):
        for item in group.findall("item"):
            key = item.attrib[ANDROID + "id"].split("/")[-1]
            title = resolve(item.get(prefs.ANDROID_TITLE, ""), strings)
            title = {"search_btn": "搜索", "edit_confirm": "确认（图标按钮，无文字标题）"}.get(key, title)
            path = parent + (title,)
            yield key, path
            child = item.find("menu")
            if child is not None:
                yield from visit(child, path)
    yield from visit(ET.parse(repo / RES / "menu/menu_mods.xml").getroot())


def render_simple(repo: Path) -> str:
    strings = load_strings(repo)
    pages = collect_pages(repo, strings)
    tree: dict = {}
    def insert(path):
        node = tree
        for name in path:
            node = node.setdefault(name, {})
    for page in pages:
        insert(page.path)
    main = ET.parse(repo / RES / "xml/prefs_main.xml").getroot()
    category_keys = {c.key for c in prefs.CATEGORY_SOURCES}
    for label, key, _ in rows(main, strings):
        if key not in category_keys:
            insert(tuple(label.split(" → ")))
    for key, path in menu_entries(repo, strings):
        if key == "edit_confirm":
            continue
        insert(path if key == "search_btn" else ("更多菜单",) + path)
    lines = ["# A14 功能地图（简版）", "", "<!-- Generated by tools/generate_feature_map.py; do not edit. -->", "",
             "与[详细地图](FEATURE_MAP.md)共用真实页面来源，覆盖 4 大分类及全部业务子页。这里只列结构；功能选项、稳定键、条件入口和共用编辑器见详细地图。", "", "```text", "米客 A14"]
    def draw(node, prefix=""):
        for index, (name, children) in enumerate(node.items()):
            last = index == len(node) - 1
            lines.append(prefix + ("└─ " if last else "├─ ") + name)
            draw(children, prefix + ("   " if last else "│  "))
    draw(tree)
    return "\n".join(lines + ["```"]) + "\n"


def render(repo: Path) -> str:
    strings = load_strings(repo)
    pages = collect_pages(repo, strings)
    setting_count = sum(len(list(rows(p.root, strings))) for p in pages)
    lines = ["# A14 页面与功能地图", "", "<!-- Generated by tools/generate_feature_map.py; do not edit. -->", f"<!-- UI contract SHA-256: {contract_stamp(repo)} -->", "",
             f"当前源码含 {len(prefs.CATEGORY_SOURCES)} 个首页分类入口、{sum(not p.standalone for p in pages)} 个分类业务页、{sum(p.standalone for p in pages)} 个独立设置子页；下表覆盖 {setting_count} 个业务设置 / 动作入口。另列首页、更多菜单和共用编辑页面。数量不是实机通过数。", "",
             "快速浏览见[字符树简版](FEATURE_MAP_SIMPLE.md)。名称来自简体中文资源（缺失时回退默认资源）；页面按真实 XML 顺序，独立子页紧随父页。四类目录由 [CategorySelector](../app/src/main/java/tv/withaibuild/customiuizer/subs/CategorySelector.kt) 打开。分组标题只表示页内位置，不额外算作页面。`稳定键`保留 XML 原值，不能把导航键当作持久化值。", "",
             "“可见”表示有当前入口；“条件启用”表示依赖满足后可编辑，控制器的条件显示 / 不支持另在页下注明。“设置端动作”不虚构 Feature ID；“无独立 UI”只列实际 catalog 安装项。运行时与设备边界见[兼容矩阵](../COMPATIBILITY.md)，待验项见[路线图](maintenance/A14_REFINEMENT_ROADMAP.md)。", "",
             "生成：`python tools/generate_feature_map.py`；检查：`python tools/generate_feature_map.py --check`。它复用 [CATEGORY_SOURCES / STANDALONE_PAGES 与搜索解析](../tools/generate_preference_artifacts.py)，只显式生成本文件，不进入 APK 构建。控制器指纹变化时先复核条件说明，再生成；指纹只提示复核，不证明全部路由行为。", "",
             "按完整键搜索 [Feature catalog](../app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature) 和[进程矩阵 JSON](rom-intelligence/A14_PROCESS_MATRIX.json) 的 `preferenceKey`（去掉 `pref_key_`）。动作编辑会派生 `_action` / `_app` 等键；一个入口可关联多个 Feature，明细以源码为准。Duo、Dock、动态岛的专门约束分别见 [Duo](features/duo-status-bar.md)、[Dock](features/dock/README.md)、[动态岛](features/island/README.md)。", ""]
    category = ""
    for page in pages:
        if page.path[0] != category:
            category = page.path[0]
            category_key = next(c.key for c in prefs.CATEGORY_SOURCES if resolve(c.title, strings) == category)
            lines += [f"## {category}", "", f"首页分类入口键：`{category_key}`。", ""]
        owner = f"subs/{page.controller}.kt" if page.controller != "SubFragment" else "SubFragment.kt"
        lines += [f"### {' → '.join(page.path)}", "", f"入口键：`{page.key}`；页面源：[{page.source}](../{RES.as_posix()}/xml/{page.source})；控制器：[{page.controller}](../{JAVA.as_posix()}/{owner})。", ""]
        if page.key in PAGE_NOTES:
            lines += [PAGE_NOTES[page.key], ""]
        lines += ["| 功能（页内分组 → 选项） | 稳定键 | 入口状态 / XML 依赖 |", "| --- | --- | --- |"]
        lines += [f"| {cell(label)} | `{key}` | {state} |" for label, key, state in rows(page.root, strings)]
        lines.append("")
    lines += ["## 首页、独立菜单与贯穿页面的工具", "", "首页四类业务导航见上文；以下应用设置来自 `prefs_main.xml`。", "", "| 首页设置 | 稳定键 | 状态 |", "| --- | --- | --- |"]
    main = ET.parse(repo / RES / "xml/prefs_main.xml").getroot()
    category_keys = {c.key for c in prefs.CATEGORY_SOURCES}
    lines += [f"| {cell(label)} | `{key}` | 设置端动作 / 本应用设置 |" for label, key, _ in rows(main, strings) if key not in category_keys]
    lines += ["", "更多菜单来自 [menu_mods.xml](../app/src/main/res/menu/menu_mods.xml)，显示与执行由 [PreferenceFragmentBase](../app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt)、[MainFragment](../app/src/main/java/tv/withaibuild/customiuizer/MainFragment.kt) 和 [MainActivity](../app/src/main/java/tv/withaibuild/customiuizer/MainActivity.kt) 决定。重启组仅首页可见，其他页面按各自菜单配置；编辑页只显示确认。", "", "| 入口 | 稳定 ID | 状态 / 行为 |", "| --- | --- | --- |"]
    for key, path in menu_entries(repo, strings):
        state = "条件显示：编辑页面确认修改" if key == "edit_confirm" else "设置端动作" if key != "restart_menu" else "首页子菜单"
        lines.append(f"| {cell(' → '.join(path))} | `R.id.{key}` | {state} |")
    lines += ["", "备份与恢复分别使用系统文件创建 / 选择器；重置由 MainActivity 确认后执行。关于页显示版本、维护者及微信赞赏 / PayPal / 源码仓库入口（`about_donate_row` / `about_paypal_row` / `about_repository_row`），见 [AboutFragment](../app/src/main/java/tv/withaibuild/customiuizer/AboutFragment.kt)。首页框架状态提示由 MainFragment 的服务状态检查触发。", "",
              "### 共用编辑与选择页面", "", "这些页面由上表的设置项进入，不注册第二套业务路由；标题沿用来源项或对应中文资源。", "", "| 进入方式 | 页面 / 对话框 | 稳定键或来源 |", "| --- | --- | --- |",
              "| 手势、按键、锁屏和音乐动作 → 编辑 | [MultiAction](../app/src/main/java/tv/withaibuild/customiuizer/subs/MultiAction.kt) | 来源项键派生 `_action`、`_app`、`_app_user`、`_shortcut`、`_shortcut_intent`、`_activity`、`_toggle` 等；选项依 Actions 分类 |",
              "| 应用名单、时钟 / 日历应用、隐藏应用、应用锁、修改标题 | [AppSelector](../app/src/main/java/tv/withaibuild/customiuizer/subs/AppSelector.kt) | 原设置键；隐藏应用 / 应用锁复用宿主配置；支持搜索 / 用户身份 |",
              "| 动作编辑 → 应用 / 快捷方式 / Activity | [AppSelector](../app/src/main/java/tv/withaibuild/customiuizer/subs/AppSelector.kt)、[ShortcutSelector](../app/src/main/java/tv/withaibuild/customiuizer/subs/ShortcutSelector.kt)、[ActivitySelector](../app/src/main/java/tv/withaibuild/customiuizer/subs/ActivitySelector.kt) | 来源项派生目标键；按动作条件显示 |",
              "| 跳过应用锁定 → 已选中活动 | [SortableList](../app/src/main/java/tv/withaibuild/customiuizer/subs/SortableList.kt) → 应用 / Activity 选择 | `pref_key_system_applock_skip_activities` 与条目身份；不把内部未接入的动作分支列成当前入口 |",
              "| 禁用屏幕锁 → 受信任网络 / 设备 | [WiFiList](../app/src/main/java/tv/withaibuild/customiuizer/subs/WiFiList.kt)、[BTList](../app/src/main/java/tv/withaibuild/customiuizer/subs/BTList.kt) | `pref_key_system_noscreenlock_wifi`、`pref_key_system_noscreenlock_bt`；权限满足后进入 |",
              "| 颜色设置；滑杆数字；振动时段；列表 / 文本选项 | [ColorSelector](../app/src/main/java/tv/withaibuild/customiuizer/subs/ColorSelector.kt)、[SeekBarPreference](../app/src/main/java/tv/withaibuild/customiuizer/prefs/SeekBarPreference.kt)、[振动时段](../app/src/main/java/tv/withaibuild/customiuizer/subs/System_VibrationAmp.kt)及平台对话框 | 原设置键；滑杆按可编辑状态进入精确输入 |", "",
              "## 无独立 UI 的实际安装项", "", "以下由既有[源码进程矩阵](rom-intelligence/A14_PROCESS_MATRIX.json)筛出：主偏好不直接对应当前 XML 入口或动作派生键。它们不是额外的可见功能；`null` 表示没有独立偏好，具体安装条件仍查 catalog。", "", "| Feature ID | 主偏好（短键） | 安装目录 / 状态 |", "| --- | --- | --- |"]
    visible = {key.removeprefix("pref_key_") for page in pages for _, key, _ in rows(page.root, strings)}
    visible.update(key.removeprefix("pref_key_") for _, key, _ in rows(main, strings))
    matrix = json.loads((repo / "docs/rom-intelligence/A14_PROCESS_MATRIX.json").read_text(encoding="utf-8"))
    for feature in matrix["features"]:
        key = feature["preferenceKey"]
        if key in visible or key.endswith("_action") and key.removesuffix("_action") in visible:
            continue
        lines.append(f"| `{feature['featureIdInt']}: {feature['featureIdName']}` | `{key}` | `{feature['installer']}`；无独立 UI，有安装逻辑 |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT)
    parser.add_argument("--output", type=Path, help="generate/check only the detailed map at this path")
    parser.add_argument("--check", action="store_true", help="fail without writing if the map is stale")
    args = parser.parse_args()
    repo = args.repo_root.resolve()
    try:
        outputs = {args.output or repo / "docs/FEATURE_MAP.md": render(repo).encode("utf-8")}
        if args.output is None:
            outputs[repo / "docs/FEATURE_MAP_SIMPLE.md"] = render_simple(repo).encode("utf-8")
        if args.check:
            stale = [str(output) for output, expected in outputs.items() if not output.is_file() or output.read_bytes() != expected]
            if stale:
                print(f"feature-map: stale or missing: {', '.join(stale)}; regenerate and review", file=sys.stderr)
                return 1
        else:
            for output, expected in outputs.items():
                output.write_bytes(expected)
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"feature-map: {error}", file=sys.stderr)
        return 1
    print(f"feature-map: {'check passed' if args.check else 'generated'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
