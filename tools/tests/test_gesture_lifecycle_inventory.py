#!/usr/bin/env python3
"""In-memory source-derived gesture lifecycle owner/release invariant checks.

This test replaces the previous Markdown-backed inventory.  It reads the A14
Kotlin sources directly, builds an in-memory picture of the attach/bind/acquire
-> detach/unbind/release pairs, and verifies that each owner has a matching
cleanup path.  No long-lived output is written; all state stays in memory.
"""

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent


def source(rel: str) -> str:
    path = REPO_ROOT / rel
    if not path.is_file():
        raise FileNotFoundError(f"Missing source: {path}")
    return path.read_text(encoding="utf-8", errors="replace")


def assert_contains(test: unittest.TestCase, haystack: str, needle: str, label: str) -> None:
    if needle not in haystack:
        test.fail(f"{label}: missing '{needle}'")


def find_hook_call(text: str, class_name: str, method_name: str) -> bool:
    """Return True if the file hooks the given class+method with findAndHookMethod."""
    pattern = re.compile(
        r"findAndHookMethod\s*\(\s*"
        + re.escape(f'"{class_name}"')
        + r"\s*,(?:[^\"]|\"[^\"]*\")*?"
        + re.escape(f'"{method_name}"'),
        re.DOTALL,
    )
    return bool(pattern.search(text))


class GestureLifecycleInventoryTest(unittest.TestCase):
    """Static source invariants for gesture owner lifecycle."""

    def test_source_files_exist(self) -> None:
        for rel in [
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt",
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt",
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt",
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt",
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt",
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt",
        ]:
            self.assertTrue((REPO_ROOT / rel).is_file(), f"missing {rel}")

    def test_phone_status_bar_view_attach_has_detach(self) -> None:
        """Status-bar gesture hooks attach and detach PhoneStatusBarView."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")
        self.assertTrue(
            find_hook_call(text, "com.android.systemui.statusbar.phone.PhoneStatusBarView", "onAttachedToWindow"),
            "PhoneStatusBarView onAttachedToWindow hook missing",
        )
        self.assertTrue(
            find_hook_call(text, "com.android.systemui.statusbar.phone.PhoneStatusBarView", "onDetachedFromWindow"),
            "PhoneStatusBarView onDetachedFromWindow hook missing",
        )

    def test_status_bar_machine_prepare_has_corresponding_clear(self) -> None:
        """statusBarMachine.prepare(ownerId, view) is paired with statusBarMachine.clear(ownerId)."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")
        owner_expr = r"System\.identityHashCode\s*\(\s*thisObject\s*\)"

        prepare = re.search(
            r"statusBarMachine\.prepare\s*\(\s*" + owner_expr + r"\s*,\s*thisObject\s*\)",
            text,
        )
        clear = re.search(
            r"statusBarMachine\.clear\s*\(\s*" + owner_expr + r"\s*\)",
            text,
        )

        self.assertIsNotNone(prepare, "statusBarMachine.prepare(System.identityHashCode(thisObject), thisObject) missing")
        self.assertIsNotNone(clear, "statusBarMachine.clear(System.identityHashCode(thisObject)) missing")

    def test_control_center_runtime_bind_has_unbind(self) -> None:
        """The Control Center runtime holder has a bind and a symmetric unbind."""
        text = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt"
        )

        self.assertRegex(text, r"fun\s+bind\s*\(\s*classLoader:\s*ClassLoader\s*\)", "bind(ClassLoader) missing")
        self.assertRegex(text, r"fun\s+unbind\s*\(\s*\)", "unbind() missing")

        # bind clears an existing runtime before creating the new one.
        self.assertIn("existing?.machine?.clear()", text)

        # unbind clears the active machine and drops the reference.
        unbind = re.search(r"fun\s+unbind\s*\(\s*\).*?(?=fun\s+|class\s+|\Z)", text, re.DOTALL)
        self.assertIsNotNone(unbind, "could not locate unbind body")
        unbind_body = unbind.group(0)
        self.assertIn("activeRuntime?.machine?.clear()", unbind_body)
        self.assertIn("activeRuntime = null", unbind_body)

    def test_control_center_plugin_runtime_bind_has_clear(self) -> None:
        """ControlCenterPluginRuntime.bind has a symmetric clear() that releases all state."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt")

        self.assertRegex(text, r"fun\s+bind\s*\(\s*loader:\s*ClassLoader\s*\)", "bind(loader) missing")
        self.assertRegex(text, r"fun\s+clear\s*\(\s*\)", "clear() missing")

        # New loader path invalidates old lease and runs clearInternal before binding.
        self.assertIn("activeLease?.invalidate()", text)
        self.assertIn("clearInternal()", text)
        self.assertIn("runtimeHolder.bind(loader)", text)

        # clearInternal() tears down the current runtime and arbiter state.
        clear_internal = re.search(r"private\s+fun\s+clearInternal\s*\(\s*\).*?(?=private\s+|fun\s+|\Z)", text, re.DOTALL)
        self.assertIsNotNone(clear_internal, "clearInternal missing")
        ci_body = clear_internal.group(0)
        self.assertIn("runtimeHolder.unbind()", ci_body)
        self.assertIn("arbiter.releaseAll()", ci_body)
        self.assertIn("activeLoader = null", ci_body)

    def test_control_center_machine_prepare_has_corresponding_clear(self) -> None:
        """controlCenterMachine.prepare(ownerId, view) is paired with clear(ownerId) on detach."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt")

        owner_expr = r"System\.identityHashCode\s*\(\s*thisObject\s*\)"

        self.assertTrue(
            find_hook_call(text, "miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", "onAttachedToWindow"),
            "ControlCenterWindowViewImpl onAttachedToWindow hook missing",
        )
        self.assertTrue(
            find_hook_call(text, "miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", "onDetachedFromWindow"),
            "ControlCenterWindowViewImpl onDetachedFromWindow hook missing",
        )

        prepare = re.search(
            r"controlCenterMachine\.prepare\s*\(\s*" + owner_expr + r"\s*,\s*thisObject\s*\)",
            text,
        )
        clear = re.search(
            r"controlCenterMachine\.clear\s*\(\s*" + owner_expr + r"\s*\)",
            text,
        )

        self.assertIsNotNone(prepare, "controlCenterMachine.prepare(...) missing")
        self.assertIsNotNone(clear, "controlCenterMachine.clear(...) missing")

        # Detach is guarded by the per-bind lease so stale callbacks are no-ops.
        self.assertIn("if (!lease.active) return", text)

    def test_gesture_machine_owner_state_has_clear(self) -> None:
        """GestureMachine exposes owner-scoped clear(ownerId) that drops all per-owner state."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt")

        clear_owner = re.search(
            r"fun\s+clear\s*\(\s*ownerId:\s*Int\s*\).*?(?=fun\s+|class\s+|\Z)",
            text,
            re.DOTALL,
        )
        self.assertIsNotNone(clear_owner, "GestureMachine.clear(ownerId: Int) missing")
        body = clear_owner.group(0)

        for call in [
            "snapshots.remove(ownerId)",
            "dependencies.remove(ownerId)",
            "configs.remove(ownerId)",
            "gate.clearOwner(ownerId)",
            "arbiter?.releaseOwner(ownerId)",
        ]:
            self.assertIn(call, body)

        # Whole-machine clear also exists for ClassLoader teardown.
        self.assertIn("fun clear()", text)

    def test_physical_gesture_arbiter_acquire_has_release_family(self) -> None:
        """PhysicalGestureArbiter.tryAcquireOnDown has release/releaseOwner/releaseAll."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt")

        self.assertRegex(text, r"fun\s+tryAcquireOnDown\s*\(", "acquire method missing")
        self.assertRegex(text, r"fun\s+release\s*\(\s*ownerId:\s*Int\s*,\s*event:\s*GestureEvent\s*\)", "release missing")
        self.assertRegex(text, r"fun\s+releaseOwner\s*\(\s*ownerId:\s*Int\s*\)", "releaseOwner missing")
        self.assertRegex(text, r"fun\s+releaseAll\s*\(\s*\)", "releaseAll missing")

        release_all = re.search(
            r"fun\s+releaseAll\s*\(\s*\).*?(?=fun\s+|class\s+|\Z)",
            text,
            re.DOTALL,
        )
        self.assertIsNotNone(release_all, "could not locate releaseAll body")
        self.assertIn("owners.clear()", release_all.group(0))

    def test_gesture_side_effect_gate_has_clear_owner(self) -> None:
        """GestureSideEffectGate exposes clearOwner(ownerId) scoped to one owner."""
        text = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt")

        clear_owner = re.search(
            r"fun\s+clearOwner\s*\(\s*ownerId:\s*Int\s*\).*?(?=fun\s+|class\s+|\Z)",
            text,
            re.DOTALL,
        )
        self.assertIsNotNone(clear_owner, "clearOwner(ownerId) missing")
        body = clear_owner.group(0)
        self.assertIn("order.removeIf { it.ownerId == ownerId }", body)
        self.assertIn("seen.removeIf { it.ownerId == ownerId }", body)

    def test_classloader_replacement_cleans_old_runtime(self) -> None:
        """Binding a new ClassLoader invalidates the old lease and clears the old machine/arbiter."""
        holder = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt"
        )
        plugin = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt")

        # Holder: a different loader causes existing.machine.clear() before the new runtime is installed.
        self.assertIn("if (existing?.classLoader === classLoader)", holder)
        self.assertIn("existing?.machine?.clear()", holder)

        # Plugin: new loader invalidates old lease, runs clearInternal, then binds a fresh machine.
        bind = re.search(r"fun\s+bind\s*\(\s*loader:\s*ClassLoader\s*\).*?(?=fun\s+|class\s+|\Z)", plugin, re.DOTALL)
        self.assertIsNotNone(bind, "ControlCenterPluginRuntime.bind missing")
        bind_body = bind.group(0)
        self.assertIn("activeLease?.invalidate()", bind_body)
        self.assertIn("clearInternal()", bind_body)
        self.assertIn("runtimeHolder.bind(loader)", bind_body)

        # Whole-machine clear releases all owner state.
        machine = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt")
        whole_clear = re.search(r"fun\s+clear\s*\(\s*\).*?(?=fun\s+|class\s+|\Z)", machine, re.DOTALL)
        self.assertIsNotNone(whole_clear, "GestureMachine.clear() missing")
        whole_body = whole_clear.group(0)
        self.assertIn("snapshots.clear()", whole_body)
        self.assertIn("dependencies.clear()", whole_body)
        self.assertIn("configs.clear()", whole_body)
        self.assertIn("gate.clear()", whole_body)
        self.assertIn("arbiter?.releaseAll()", whole_body)

    def test_stale_old_loader_detach_does_not_clear_new_runtime(self) -> None:
        """A detach callback from an old ClassLoader cannot wipe the new runtime."""
        plugin = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt")

        # Detach guards are checked before any machine state is touched.
        self.assertIn("if (!lease.active) return", plugin)

        # Both prepare and clear use the same per-view identity hash, so a stale old view
        # can only ever clear its own ownerId.
        control_center = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt")
        owner_expr = r"System\.identityHashCode\s*\(\s*thisObject\s*\)"
        prepare = re.search(
            r"controlCenterMachine\.prepare\s*\(\s*" + owner_expr + r"\s*,\s*thisObject\s*\)",
            control_center,
        )
        clear = re.search(
            r"controlCenterMachine\.clear\s*\(\s*" + owner_expr + r"\s*\)",
            control_center,
        )
        self.assertIsNotNone(prepare, "control center prepare with identityHashCode missing")
        self.assertIsNotNone(clear, "control center clear with identityHashCode missing")

        status_bar = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt")
        sb_prepare = re.search(
            r"statusBarMachine\.prepare\s*\(\s*" + owner_expr + r"\s*,\s*thisObject\s*\)",
            status_bar,
        )
        sb_clear = re.search(
            r"statusBarMachine\.clear\s*\(\s*" + owner_expr + r"\s*\)",
            status_bar,
        )
        self.assertIsNotNone(sb_prepare, "status bar prepare with identityHashCode missing")
        self.assertIsNotNone(sb_clear, "status bar clear with identityHashCode missing")

        # Replacement path invalidates the old lease; new hooks are created with a new lease.
        plugin_bind = re.search(r"fun\s+bind\s*\(\s*loader:\s*ClassLoader\s*\).*?(?=fun\s+|class\s+|\Z)", plugin, re.DOTALL)
        self.assertIsNotNone(plugin_bind)
        self.assertIn("activeLease?.invalidate()", plugin_bind.group(0))

    def test_repeated_loader_replacement_does_not_grow_unbounded_state(self) -> None:
        """Only one active runtime is kept; in-memory collections are bounded."""
        holder = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureRuntimeHolder.kt"
        )

        # Holder keeps a single nullable activeRuntime, not a list/map.
        self.assertRegex(holder, r"private\s+var\s+activeRuntime\s*:\s*ControlCenterGestureRuntime\?\s*=\s*null")
        self.assertRegex(holder, r"private\s+var\s+nextRuntimeId\s*=\s*0L")
        for bad in ("MutableList", "MutableMap", "MutableSet", "ArrayList", "HashMap"):
            self.assertNotIn(bad, holder, f"Holder should not accumulate runtimes in a {bad}")

        # Plugin runtime also keeps single references.
        plugin = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt")
        self.assertRegex(plugin, r"private\s+var\s+activeLoader\s*:\s*ClassLoader\?\s*=\s*null")
        self.assertRegex(plugin, r"private\s+var\s+activeLease\s*:\s*RuntimeLease\?\s*=\s*null")

        # Underlying mutable maps have explicit bounds.
        arbiter = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt")
        self.assertIn("const val MAX_HELD_TOKENS = 16", arbiter)
        self.assertIn("if (owners.size >= maxHeldTokens)", arbiter)

        gate = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt")
        self.assertIn("private val maxFingerprints: Int = 32", gate)
        self.assertIn("if (order.size >= maxFingerprints)", gate)

        # Bind replaces the active runtime; it does not append.
        bind = re.search(r"fun\s+bind\s*\(\s*classLoader:\s*ClassLoader\s*\).*?(?=fun\s+|class\s+|\Z)", holder, re.DOTALL)
        self.assertIsNotNone(bind, "could not locate holder.bind body")
        bind_body = bind.group(0)
        self.assertIn("activeRuntime = runtime", bind_body)


if __name__ == "__main__":
    unittest.main()
