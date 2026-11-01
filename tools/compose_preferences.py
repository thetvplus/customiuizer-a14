"""Generate lazy, typed preference models for the Compose settings renderer."""

from __future__ import annotations

import json
import xml.etree.ElementTree as ET
from pathlib import Path

try:
    from tools.settings_schema import ANDROID_NS, AUTO_NS
except ModuleNotFoundError:
    from settings_schema import ANDROID_NS, AUTO_NS

A = f"{{{ANDROID_NS}}}"
U = f"{{{AUTO_NS}}}"


def literal(value: str) -> str:
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")


def text(value: str, *, as_string: bool = False) -> str:
    getter = "getString" if as_string else "getText"
    if value.startswith("@string/"):
        return f"context.{getter}(R.string.{value.split('/', 1)[1]})"
    if value.startswith("@android:string/"):
        return f"context.{getter}(android.R.string.{value.split('/', 1)[1]})"
    return literal(value)


def _page_body(root: ET.Element) -> list[str]:
    lines = ["        val root = manager.createPreferenceScreen(context)"]
    dependencies = []
    serial = 0

    def visit(element: ET.Element, parent: str | None = None) -> None:
        nonlocal serial
        attributes = dict(element.attrib)
        kind = element.tag.rsplit(".", 1)[-1]
        if parent is None:
            variable = "root"
        else:
            serial += 1
            variable = f"row{serial}"
            constructor = f"{kind}(context, null)" if kind.endswith("Ex") or kind == "SeekBarPreference" else f"{kind}(context)"
            lines.append(f"        val {variable} = {constructor}.apply {{")
        indent = "            " if parent else "        "
        prefix = "" if parent else "root."
        for name in ("key", "title", "summary", "dialogMessage"):
            value = attributes.pop(A + name, None)
            if value is not None:
                expression = literal(value) if name == "key" else text(value)
                lines.append(f"{indent}{prefix}{name} = {expression}")
        for name, property_name in (("selectable", "isSelectable"), ("persistent", "isPersistent"),
                                    ("disableDependentsState", "disableDependentsState"),
                                    ("iconSpaceReserved", "isIconSpaceReserved")):
            value = attributes.pop(A + name, None)
            if value is not None:
                lines.append(f"{indent}{prefix}{property_name} = {value}")
        for name in ("entries", "entryValues"):
            value = attributes.pop(A + name, None)
            if value is not None:
                if not value.startswith("@array/"):
                    raise ValueError(f"Expected array resource for {name}: {value}")
                lines.append(f"{indent}{prefix}{name} = context.resources.getTextArray(R.array.{value[7:]})")
        value = attributes.pop(U + "isPreferenceVisible", None)
        if value is not None:
            lines.append(f"{indent}{prefix}isVisible = {value}")
        simple = attributes.pop(U + "useSimpleSummaryProvider", None)
        if simple == "true":
            lines.append(f"{indent}{prefix}summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()")
        icon_space = attributes.pop(U + "iconSpaceReserved", None)
        if icon_space is not None:
            lines.append(f"{indent}{prefix}isIconSpaceReserved = {icon_space}")
        default = attributes.pop(A + "defaultValue", None)
        if kind == "SeekBarPreference":
            has_divider = U + "displayDividerValue" in attributes
            numbers = [attributes.pop(U + name, fallback) for name, fallback in (
                ("minValue", "0"), ("maxValue", "10"), ("stepValue", "1"),
                ("negativeShift", "0"), ("displayDividerValue", "1"))]
            strings = [attributes.pop(U + name, None) for name in ("format", "offtext", "note")]
            show_plus = attributes.pop(U + "showplus", "false")
            arguments = [*numbers[:3], default or "0", *numbers[3:], str(has_divider).lower(),
                         *("null" if value is None else text(value, as_string=True) for value in strings), show_plus]
            lines.append(f"{indent}configureComposeValues({', '.join(arguments)})")
        elif default is not None:
            if kind == "CheckBoxPreferenceEx":
                expression = default
            elif kind == "ColorPreferenceEx":
                expression = f"android.graphics.Color.parseColor({literal(default)})" if default.startswith("#") else default
                lines.append(f'{indent}extras.putInt("ui.defaultColor", {expression})')
            else:
                expression = literal(default)
            lines.append(f"{indent}{prefix}setDefaultValue({expression})")
        dependency = attributes.pop(A + "dependency", None)
        if dependency is not None:
            dependencies.append(f"        {variable}.dependency = {literal(dependency)}")
        for name in ("dynamic", "warning", "countAsSummary", "valueAsSummary", "longClickable"):
            value = attributes.pop(U + name, None)
            if value is not None:
                lines.append(f'{indent}{prefix}extras.putBoolean("ui.{name}", {value})')
        for name in ("indentLevel", "state"):
            value = attributes.pop(U + name, None)
            if value is not None:
                lines.append(f'{indent}{prefix}extras.putInt("ui.{name}", {value})')
        # Compose owns row geometry and disclosure widgets, not resource layouts.
        for name in (A + "layout_width", A + "layout_height", A + "widgetLayout"):
            attributes.pop(name, None)
        if attributes:
            raise ValueError(f"Untranslated Compose setting attributes on {kind}: {attributes}")
        if parent:
            lines.extend(["        }", f"        {parent}.addPreference({variable})"])
        for child in element:
            visit(child, variable)

    visit(root)
    if dependencies:
        # setDependency binds immediately through the manager's current root. Stage the complete
        # hierarchy first, then detach those cold bindings so normal onAttached installs once.
        lines += ["        manager.setPreferences(root)", *dependencies, "        root.onDetached()"]
    return lines + ["        return root"]


def write_pages(output: Path, pages: dict[str, ET.Element]) -> None:
    directory = output / "tv/withaibuild/customiuizer/prefs"
    directory.mkdir(parents=True, exist_ok=True)
    imports = ["package tv.withaibuild.customiuizer.prefs", "", "import android.content.Context",
               "import androidx.preference.*", "import tv.withaibuild.customiuizer.R", ""]
    lines = imports + ["/** Generated from app/settings. No XML inflation or eagerly created pages. */",
                      "internal object ComposeSettingsPages {",
                      "    fun create(id: Int, context: Context, manager: PreferenceManager): PreferenceScreen = when (id) {"]
    for name in sorted(pages):
        lines.append(f"        R.id.{name} -> {name}(context, manager)")
    lines.extend(['        else -> error("Unknown settings page: $id")', "    }"])
    for name, root in sorted(pages.items()):
        lines.append(f"    private fun {name}(context: Context, manager: PreferenceManager): PreferenceScreen {{")
        lines.extend(_page_body(root))
        lines.append("    }")
    lines.extend(["}", ""])
    (directory / "ComposeSettingsPages.kt").write_text("\n".join(lines), encoding="utf-8", newline="\n")
