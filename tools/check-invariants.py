#!/usr/bin/env python3
"""Static gate for the invariants that keep this module from bricking a device.

Usage:
    python tools/check-invariants.py            # check the whole module source
    python tools/check-invariants.py --staged   # check only files staged in git

Exit code 0 means every invariant holds. Any other exit code means at least one
rule in AGENTS.md was violated and the change must not be committed.

Each rule below exists because the exact defect it detects was found in this
repository, in code that compiled, passed lint and passed the unit tests. The
build cannot catch them: they are runtime contracts with the Android framework
and with libxposed, not type errors.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"

# Files that are allowed to break a rule, with the reason. Keep this list short;
# every entry is a place where the invariant is enforced rather than consumed.
ALLOWED = {
    "no-raw-register-receiver": {
        "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt",
        "tv/withaibuild/customiuizer/mods/utils/ReceiverRegistry.kt",
    },
    "no-direct-hook-installation": {
        # The wrappers are the only places that may call the underlying Xposed
        # helpers directly. Everyone else must go through ModuleHelper so that
        # HookDiagnostics can record every install attempt.
        "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt",
        "tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt",
    },
    "guard-framework-callbacks": {
        # The settings app is the module's own process. A throw there shows a
        # normal app crash dialog; it cannot take a system process down.
        "tv/withaibuild/customiuizer/MainApplication.kt",
        "tv/withaibuild/customiuizer/tasker/UnlockReceiver.kt",
    },
}

REFLECTION = re.compile(r"XposedHelpers\.|\bcallMethod\(|\bgetObjectField\(|\bsetObjectField\(")

LINE_COMMENT = re.compile(r"//[^\n]*")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)


def strip_comments(text: str) -> str:
    """Blanks out comments, preserving every newline so line numbers stay correct."""

    def blank(match: re.Match[str]) -> str:
        return re.sub(r"[^\n]", " ", match.group(0))

    return LINE_COMMENT.sub(blank, BLOCK_COMMENT.sub(blank, text))


class Finding:
    def __init__(self, rule: str, path: Path, line: int, detail: str) -> None:
        self.rule = rule
        self.path = path
        self.line = line
        self.detail = detail

    def __str__(self) -> str:
        rel = self.path.relative_to(REPO_ROOT).as_posix()
        return f"{rel}:{self.line}: [{self.rule}] {self.detail}"


def rel_posix(path: Path) -> str:
    return path.relative_to(SOURCE_ROOT).as_posix()


def is_allowed(rule: str, path: Path) -> bool:
    return rel_posix(path) in ALLOWED.get(rule, set())


def block_at(text: str, search_from: int) -> tuple[str, int]:
    """Returns the brace-balanced block starting at the first '{' at or after search_from."""
    start = text.index("{", search_from)
    depth = 0
    index = start
    while index < len(text):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                break
        index += 1
    return text[start : index + 1], start


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


# --- rules -----------------------------------------------------------------

CALLBACK_SIGNATURES = (
    r"override fun handleMessage\(",
    r"override fun onReceive\(",
    r"override fun onChange\(",
    r"override fun run\(\)",
)


def check_guard_framework_callbacks(path: Path, text: str) -> list[Finding]:
    """Framework-invoked callbacks run outside the MethodHook try/catch.

    A reflective miss on a ROM that renamed a field then propagates out of the
    module and kills system_server, SystemUI or Launcher. Wrap the body in
    ModuleHelper.guarded, or catch inside it.

    PreferenceObserver.onChange is exempt: ModuleHelper.handlePreferenceChanged
    already isolates every observer it dispatches to.
    """
    if is_allowed("guard-framework-callbacks", path):
        return []
    findings = []
    for signature in CALLBACK_SIGNATURES:
        for match in re.finditer(signature, text):
            body, start = block_at(text, match.end() - 1)
            header = text[match.start() : start]
            if "guarded" in header or "guarded" in body or "try" in body:
                continue
            if not REFLECTION.search(body):
                continue
            if ": ModuleHelper.PreferenceObserver" in text[max(0, match.start() - 400) : match.start()]:
                continue
            findings.append(
                Finding(
                    "guard-framework-callbacks",
                    path,
                    line_of(text, match.start()),
                    "callback performs reflection but is not wrapped in ModuleHelper.guarded",
                )
            )
    return findings


DEFERRED_CALLBACKS = (
    r"\bRunnable\s*\(?\s*\{",
    r"\b(?:post|postDelayed|postAtTime|postOnAnimation|runOnUiThread)\s*\(\s*\{",
    r"\bThread\s*\(\s*\{",
    r"\bset(?:On\w+Listener)\s*\{",
    r"\b(?:withEndAction|doOnLayout|addUpdateListener|postFrameCallback)\s*\(?\s*\{",
)


def check_guard_deferred_callbacks(path: Path, text: str) -> list[Finding]:
    """Lambdas that run later are outside the hook's try/catch, exactly like named callbacks.

    The round-one rule only matched `override fun run()` and friends, so
    `postDelayed(Runnable { ... })` slipped through — including two bodies posted
    to the PhoneWindowManager handler inside system_server, where an uncaught
    throw reboots the device rather than restarting an app.

    Anything deferred from `mods/` must be wrapped in ModuleHelper.guarded.
    """
    if "customiuizer/mods/" not in path.as_posix():
        return []
    findings = []
    for pattern in DEFERRED_CALLBACKS:
        for match in re.finditer(pattern, text):
            body, start = block_at(text, match.end() - 1)
            if "guarded" in body or "try" in body or "runCatching" in body:
                continue
            # An empty lambda cannot throw; it is a deliberate no-op replacement.
            if not body.strip("{} \n\t"):
                continue
            findings.append(
                Finding(
                    "guard-deferred-callbacks",
                    path,
                    line_of(text, match.start()),
                    "deferred body runs outside the hook try/catch; wrap it in ModuleHelper.guarded",
                )
            )
    return findings


def check_coroutine_scopes_handle_failure(path: Path, text: str) -> list[Finding]:
    """A SupervisorJob does not swallow failures, it only stops them cascading.

    An uncaught exception in `launch` still reaches the thread's default handler,
    which inside SystemUI or Launcher kills the process. Every scope the module
    runs in a host process must carry ModuleHelper.coroutineFailureHandler, so a
    coroutine added later cannot forget it.
    """
    if "customiuizer/mods/" not in path.as_posix():
        return []
    findings = []
    for match in re.finditer(r"CoroutineScope\(", text):
        end = text.find("\n", match.start())
        statement = text[match.start() : end if end != -1 else len(text)]
        if "coroutineFailureHandler" in statement:
            continue
        findings.append(
            Finding(
                "coroutine-scopes-handle-failure",
                path,
                line_of(text, match.start()),
                "add + ModuleHelper.coroutineFailureHandler to this scope",
            )
        )
    return findings


def check_no_raw_register_receiver(path: Path, text: str) -> list[Finding]:
    """Receivers registered straight on a Context outlive their hook target.

    Cleanup keyed on the hooked instance cannot see the registration a previous
    instance made, so every recreation of the target leaves another live
    receiver behind. Use ModuleHelper.registerModuleReceiver (one per key) or
    registerOwnedReceiver (one per live owner).

    A raw registration is accepted only when the same file unregisters that
    exact receiver, which is how the screen-state, weather and step-counter
    controllers manage their own paired lifetime.
    """
    if is_allowed("no-raw-register-receiver", path):
        return []
    if "customiuizer/mods/" not in path.as_posix():
        return []
    findings = []
    for match in re.finditer(r"\.registerReceiver\(\s*([^,\n]*)", text):
        receiver = match.group(1).strip()
        # A null receiver is a synchronous sticky-broadcast read, not a registration.
        if receiver.startswith("null"):
            continue
        # Anonymous receivers can never be unregistered; they always need the registry.
        if not re.fullmatch(r"[\w.]+", receiver):
            findings.append(
                Finding(
                    "no-raw-register-receiver",
                    path,
                    line_of(text, match.start()),
                    "anonymous receiver cannot be unregistered; "
                    "use ModuleHelper.registerModuleReceiver / registerOwnedReceiver",
                )
            )
            continue
        if f"unregisterReceiver({receiver}" in text:
            continue
        # A declared field plus an unregister path in the same file is a managed
        # lifetime, even when the unregister call goes through a local alias.
        declared_field = re.search(rf"^\s*(private )?(var|val) {re.escape(receiver)}\b", text, re.MULTILINE)
        if declared_field and "unregisterReceiver(" in text:
            continue
        findings.append(
            Finding(
                "no-raw-register-receiver",
                path,
                line_of(text, match.start()),
                "use ModuleHelper.registerModuleReceiver / registerOwnedReceiver, "
                "or unregister this exact receiver in the same file",
            )
        )
    return findings


def check_no_looperless_handler(path: Path, text: str) -> list[Finding]:
    """Handler() with no Looper binds to whichever thread ran the hook.

    In a hook that is not guaranteed to run on a Looper thread it throws
    outright. Always pass an explicit Looper.
    """
    findings = []
    for match in re.finditer(r"\bHandler\(\s*\)", text):
        findings.append(
            Finding(
                "no-looperless-handler",
                path,
                line_of(text, match.start()),
                "pass an explicit Looper, e.g. Handler(context.mainLooper)",
            )
        )
    return findings


def check_no_redundant_arg_marshalling(path: Path, text: str) -> list[Finding]:
    """getArgsArray + proceed(args) is only for hooks that rewrite arguments.

    It allocates the argument list and a copy of it on every invocation, and
    makes the framework re-marshal every argument on proceed. Hooks that only
    read arguments must use Chain.getArg(i) / Chain.getArgs() and Chain.proceed().
    """
    findings = []
    for match in re.finditer(r"override fun intercept\(", text):
        body, start = block_at(text, match.end() - 1)
        if "getArgsArray" not in body:
            continue
        if re.search(r"\bargs\w*\[\s*[^\]]+\]\s*=[^=]", body):
            continue
        findings.append(
            Finding(
                "no-redundant-arg-marshalling",
                path,
                line_of(text, match.start()),
                "hook does not rewrite arguments; use Chain.getArg(i) and Chain.proceed()",
            )
        )
    return findings


def check_no_direct_hook_installation(path: Path, text: str) -> list[Finding]:
    """Hook installation must go through ModuleHelper so diagnostics are recorded."""
    if is_allowed("no-direct-hook-installation", path):
        return []
    findings = []
    for match in re.finditer(r"XposedHelpers\.(findAndHookMethod|findAndHookConstructor|hookAllMethods|hookAllConstructors)\s*\(", text):
        findings.append(
            Finding(
                "no-direct-hook-installation",
                path,
                line_of(text, match.start()),
                "hook installation bypasses ModuleHelper; route through ModuleHelper and add allowlist if truly unavoidable",
            )
        )
    return findings


def check_no_legacy_xposed(path: Path, text: str) -> list[Finding]:
    """The module runs on libxposed API 101/102 only."""
    findings = []
    for match in re.finditer(r"de\.robv\.android\.xposed", text):
        findings.append(
            Finding(
                "no-legacy-xposed",
                path,
                line_of(text, match.start()),
                "legacy Xposed API is not available at runtime",
            )
        )
    return findings


def check_no_regex_split_on_literal(path: Path, text: str) -> list[Finding]:
    """split("x".toRegex()) compiles a Pattern on every call.

    Java's String.split takes a single-character fast path that does not touch
    the regex engine; the mechanical Kotlin translation loses it.

    Only single-character delimiters are flagged; a genuine pattern such as
    "\\s+" has to stay a Regex.
    """
    findings = []
    for match in re.finditer(r'split\(\s*"(?:\\\\)?[^"\\+*?\[\]{}()^$]"\.toRegex\(\)', text):
        findings.append(
            Finding(
                "no-regex-split-on-literal",
                path,
                line_of(text, match.start()),
                "split on a literal delimiter, not a compiled Regex",
            )
        )
    return findings


SET_ID_ALLOWED = {
    "tv/withaibuild/customiuizer/mods/utils/Api102HookBridge.kt",
}

API_VERSION_ALLOWED = {
    "tv/withaibuild/customiuizer/MainModule.java",
    "tv/withaibuild/customiuizer/mods/utils/XposedApiCapabilities.kt",
}


def check_api102_isolation(path: Path, text: str) -> list[Finding]:
    """API 102 hook features are isolated behind a capability gate.

    - setId may only be called from Api102HookBridge.
    - replaceHook is not enabled in production.
    - HotReloadingParam / HotReloadedParam are not used.
    - getApiVersion may only be read from the module entry cold path.
    """
    rel = rel_posix(path)
    findings: list[Finding] = []

    for match in re.finditer(r"\bsetId\s*\(", text):
        if rel not in SET_ID_ALLOWED:
            findings.append(
                Finding(
                    "api102-isolation",
                    path,
                    line_of(text, match.start()),
                    "setId may only be called from Api102HookBridge",
                )
            )

    for match in re.finditer(r"\breplaceHook\s*\(", text):
        findings.append(
            Finding(
                "api102-isolation",
                path,
                line_of(text, match.start()),
                "replaceHook is not enabled",
            )
        )

    for match in re.finditer(r"\bHotReload(?:ing|ed)Param\b", text):
        findings.append(
            Finding(
                "api102-isolation",
                path,
                line_of(text, match.start()),
                "hot reload parameters are not enabled",
            )
        )

    for match in re.finditer(r"\bgetApiVersion\s*\(\s*\)", text):
        if rel not in API_VERSION_ALLOWED:
            findings.append(
                Finding(
                    "api102-isolation",
                    path,
                    line_of(text, match.start()),
                    "getApiVersion may only be read from the module entry cold path",
                )
            )

    return findings


FEATURE_INSTALL_REGISTRY = "tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"
FEATURE_DEFINITION_ROOT = "tv/withaibuild/customiuizer/mods/utils/feature/"
SYSTEM_SERVER_INSTALLER = "tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt"
DEVICE_INFO_MONITOR = "tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt"
HOOKER_CLASS_HELPER = "tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt"
MODULE_HELPER = "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt"
SYSTEM_LOCK_SCREEN_HOOKS = "tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt"
LOCK_SCREEN_ALBUM_ART_CONTROLLER = "tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt"
HOOK_UTILS = "tv/withaibuild/customiuizer/utils/HookUtils.kt"
CONTROLS = "tv/withaibuild/customiuizer/mods/Controls.kt"
BATTERY_INDICATOR = "tv/withaibuild/customiuizer/utils/BatteryIndicator.kt"
XPOSED_SERVICE_MANAGER = "tv/withaibuild/customiuizer/utils/XposedServiceManager.kt"
CHECKBOX_PREFERENCE = "tv/withaibuild/customiuizer/prefs/CheckBoxPreferenceEx.kt"


def check_feature_install_oom_cleanup(path: Path, text: str) -> list[Finding]:
    """Feature install OOM must set FAILED_TRANSIENT before rethrowing."""
    if rel_posix(path) != FEATURE_INSTALL_REGISTRY:
        return []
    findings = []
    for match in re.finditer(r"catch\s*\(\s*oom\s*:\s*OutOfMemoryError\s*\)\s*\{", text):
        body, _ = block_at(text, match.start())
        if "FeatureInstallState.set" not in body or "FAILED_TRANSIENT" not in body:
            findings.append(
                Finding(
                    "feature-install-oom-cleanup",
                    path,
                    line_of(text, match.start()),
                    "install OOM catch must set FeatureInstallState to FAILED_TRANSIENT",
                )
            )
        if "throw oom" not in body:
            findings.append(
                Finding(
                    "feature-install-oom-cleanup",
                    path,
                    line_of(text, match.start()),
                    "install OOM catch must rethrow the OutOfMemoryError",
                )
            )
    return findings


def check_feature_install_boundary(path: Path, text: str) -> list[Finding]:
    """Feature definitions must delegate Throwable isolation to FeatureInstallRegistry.

    A local catch(Throwable) hides OutOfMemoryError from the registry's fatal boundary and also
    loses the feature id/name diagnostics recorded by that boundary.
    """
    rel = rel_posix(path)
    if not rel.startswith(FEATURE_DEFINITION_ROOT) and rel != SYSTEM_SERVER_INSTALLER:
        return []

    return [
        Finding(
            "feature-install-boundary",
            path,
            line_of(text, match.start()),
            "feature installer must not catch Throwable; let FeatureInstallRegistry isolate and record it",
        )
        for match in re.finditer(r"catch\s*\([^)]*\bThrowable\b[^)]*\)", text)
    ]


def check_device_info_monitor_hot_path(path: Path, text: str) -> list[Finding]:
    """The two-second device monitor must avoid Formatter churn and preserve OOM propagation."""
    if rel_posix(path) != DEVICE_INFO_MONITOR:
        return []
    findings = []
    for match in re.finditer(r"\bString\.format\s*\(", text):
        findings.append(
            Finding(
                "device-info-monitor-hot-path",
                path,
                line_of(text, match.start()),
                "two-second monitor path must use the cached fixed-decimal formatter",
            )
        )

    for generic in re.finditer(r"catch\s*\(\s*[_A-Za-z]\w*\s*:\s*Throwable\s*\)", text):
        preceding = None
        for oom in re.finditer(
            r"catch\s*\(\s*([A-Za-z]\w*)\s*:\s*OutOfMemoryError\s*\)",
            text[:generic.start()],
        ):
            preceding = oom
        safe = False
        if preceding is not None:
            oom_body, oom_body_start = block_at(text, preceding.start())
            between = text[oom_body_start + len(oom_body):generic.start()]
            safe = not between.strip() and re.search(
                rf"\bthrow\s+{re.escape(preceding.group(1))}\b",
                oom_body,
            ) is not None
        if not safe:
            findings.append(
                Finding(
                    "device-info-monitor-hot-path",
                    path,
                    line_of(text, generic.start()),
                    "device monitor catch(Throwable) must be preceded by an OOM rethrow catch",
                )
            )
    return findings


def check_method_hook_fatal_boundary(path: Path, text: str) -> list[Finding]:
    """The shared before/after adapters must not turn fatal errors into a logged success."""
    if rel_posix(path) != HOOKER_CLASS_HELPER:
        return []
    findings = []
    fatal_types = ("OutOfMemoryError", "VirtualMachineError", "ThreadDeath")
    for callback_name in ("beforeHook", "afterHook"):
        match = re.search(rf"override\s+fun\s+{callback_name}\s*\(", text)
        if match is None:
            findings.append(
                Finding(
                    "method-hook-fatal-boundary",
                    path,
                    1,
                    f"shared {callback_name} callback is missing",
                )
            )
            continue
        body, _ = block_at(text, match.start())
        generic = re.search(r"catch\s*\(\s*[A-Za-z]\w*\s*:\s*Throwable\s*\)", body)
        missing = []
        for fatal in fatal_types:
            fatal_match = re.search(
                rf"catch\s*\(\s*([A-Za-z]\w*)\s*:\s*{fatal}\s*\)\s*\{{\s*throw\s+\1\s*\}}",
                body,
            )
            if fatal_match is None or (generic is not None and fatal_match.start() > generic.start()):
                missing.append(fatal)
        if missing:
            findings.append(
                Finding(
                    "method-hook-fatal-boundary",
                    path,
                    line_of(text, match.start()),
                    f"{callback_name} must rethrow {', '.join(missing)} before catch(Throwable)",
                )
            )
    return findings


def _catch_body_starts_with_fatal_guard(body: str, variable: str) -> bool:
    """Return true if the catch body begins by rethrowing or unwrapping fatal errors.

    Accepts either an unconditional `throw variable` or a call to
    FatalErrors.unwrapAndRethrowIfFatal(variable) as the first statement, optionally
    wrapped in a `val` assignment.  The surrounding OOM catch no longer satisfies this
    rule because each generic catch must handle its own fatal boundary.
    """
    if not body:
        return False
    # The body starts with '{'.  Strip it and leading whitespace; what follows must be
    # either `throw variable` or a statement whose first expression is the fatal guard.
    return re.search(
        rf"^\{{\s*(?:throw\s+{re.escape(variable)}\b"
        rf"|(?:(?:val|var)\s+\w+\s*=\s*)?FatalErrors\.unwrapAndRethrowIfFatal\s*\(\s*{re.escape(variable)}\s*\))",
        body,
        re.DOTALL,
    ) is not None


def check_module_helper_fatal_boundaries(path: Path, text: str) -> list[Finding]:
    """Shared runtime helpers may isolate ordinary failures but must propagate fatal errors.

    Every catch(Throwable) must itself call FatalErrors.unwrapAndRethrowIfFatal(caught) or
    unconditionally throw the caught exception before any other statement. A preceding
    catch(OutOfMemoryError) does not protect a later generic catch; each catch body is
    responsible for its own fatal boundary.
    """
    if rel_posix(path) != MODULE_HELPER:
        return []
    findings = []
    for generic in re.finditer(r"catch\s*\(\s*([_A-Za-z]\w*)\s*:\s*Throwable\s*\)", text):
        body, _ = block_at(text, generic.start())
        variable = generic.group(1)
        if variable == "_":
            findings.append(
                Finding(
                    "module-helper-fatal-boundary",
                    path,
                    line_of(text, generic.start()),
                    "ModuleHelper catch(Throwable) must not use '_' as the caught variable",
                )
            )
            continue
        if _catch_body_starts_with_fatal_guard(body, variable):
            continue
        findings.append(
            Finding(
                "module-helper-fatal-boundary",
                path,
                line_of(text, generic.start()),
                f"ModuleHelper catch(Throwable) '{variable}' must start with throw {variable} or FatalErrors.unwrapAndRethrowIfFatal({variable})",
            )
        )
    return findings


def check_charging_info_hot_path(path: Path, text: str) -> list[Finding]:
    """Charging hint updates must skip disabled detail I/O and avoid Formatter churn."""
    if rel_posix(path) != SYSTEM_LOCK_SCREEN_HOOKS:
        return []
    method = re.search(r"fun\s+buildChargingInfoDetails\s*\(", text)
    if method is None:
        return [Finding("charging-info-hot-path", path, 1, "buildChargingInfoDetails is missing")]
    body, _ = block_at(text, method.start())
    findings = []
    for match in re.finditer(r"\bString\.format\s*\(", body):
        findings.append(
            Finding(
                "charging-info-hot-path",
                path,
                line_of(text, method.start() + match.start()),
                "charging hint hot path must use cached fixed-decimal formatters",
            )
        )
    disabled_return = body.find("!showCurr && !showVolt && !showWatt && !showTemp")
    is_keyguard_caller = body.find("isKeyguardCaller()")
    detail_allocation = body.find("ArrayList<String>")
    sysfs_read = body.find("batteryPropsProvider()")
    if disabled_return < 0:
        findings.append(
            Finding(
                "charging-info-hot-path",
                path,
                line_of(text, method.start()),
                "all-disabled charging details short-circuit is missing",
            )
        )
    elif is_keyguard_caller < 0 or disabled_return > is_keyguard_caller:
        findings.append(
            Finding(
                "charging-info-hot-path",
                path,
                line_of(text, method.start()),
                "all-disabled charging details must return before caller classification",
            )
        )
    elif any(
        position >= 0 and is_keyguard_caller > position
        for position in (detail_allocation, sysfs_read)
    ):
        findings.append(
            Finding(
                "charging-info-hot-path",
                path,
                line_of(text, method.start()),
                "caller classification must precede collection allocation and sysfs I/O",
            )
        )
    return findings


def check_album_art_memory_lifecycle(path: Path, text: str) -> list[Finding]:
    """Full-screen album-art frames need detach cleanup, reuse and owned-intermediate release."""
    rel = rel_posix(path)
    if rel == HOOK_UTILS:
        method = text.find("fun fastBlur(")
        if method < 0:
            return [Finding("album-art-memory-lifecycle", path, 1, "fastBlur is missing")]
        body, _ = block_at(text, method)
        radius = body.find("if (radius < 1) return null")
        bitmap_copy = body.find("sentBitmap.copy(")
        findings = []
        if radius < 0 or bitmap_copy < 0 or radius > bitmap_copy:
            findings.append(
                Finding(
                    "album-art-memory-lifecycle",
                    path,
                    line_of(text, method),
                    "fastBlur must reject an invalid radius before copying the bitmap",
                )
            )
        if "sentBitmap.config ?: Bitmap.Config.ARGB_8888" not in body:
            findings.append(
                Finding(
                    "album-art-memory-lifecycle",
                    path,
                    line_of(text, method),
                    "fastBlur must support bitmaps whose config is null",
                )
            )
        return findings

    if rel != LOCK_SCREEN_ALBUM_ART_CONTROLLER:
        return []

    findings = []
    required = (
        ("current.bitmap === bitmap", "reuse the existing BitmapDrawable for the same bitmap"),
        ("onViewDetachedFromWindow", "release the controller-owned background when the view detaches"),
        ("removeAdditionalInstanceField(view, APPLIED_DRAWABLE_FIELD)", "remove the controller-owned drawable reference"),
        ("recycleIntermediate(blurred, art, processed)", "release the blurred intermediate on every process exit"),
        ("recycleIntermediate(small, art, blurred)", "release an owned blur downsample after copying"),
        ("art.width.toLong() * art.height.toLong()", "calculate source pixels without Int overflow"),
    )
    for token, detail in required:
        if token not in text:
            findings.append(Finding("album-art-memory-lifecycle", path, 1, detail))

    for generic in re.finditer(r"catch\s*\(\s*([_A-Za-z]\w*)\s*:\s*Throwable\s*\)", text):
        body, _ = block_at(text, generic.start())
        variable = generic.group(1)
        if variable != "_" and re.search(rf"\bthrow\s+{re.escape(variable)}\b", body):
            continue
        preceding = None
        for oom in re.finditer(
            r"catch\s*\(\s*([A-Za-z]\w*)\s*:\s*OutOfMemoryError\s*\)",
            text[:generic.start()],
        ):
            preceding = oom
        safe = False
        if preceding is not None:
            oom_body, oom_body_start = block_at(text, preceding.start())
            between = text[oom_body_start + len(oom_body):generic.start()]
            safe = not between.strip() and re.search(
                rf"\bthrow\s+{re.escape(preceding.group(1))}\b",
                oom_body,
            ) is not None
        if not safe:
            findings.append(
                Finding(
                    "album-art-memory-lifecycle",
                    path,
                    line_of(text, generic.start()),
                    "album-art catch(Throwable) must rethrow it or follow an OOM rethrow catch",
                )
            )
    return findings


def check_nav_bar_dark_hot_path(path: Path, text: str) -> list[Finding]:
    """Dark-intensity animation frames must not reload unchanged navigation drawables."""
    if rel_posix(path) != CONTROLS:
        return []
    method = text.find("fun NavBarButtonsHook(")
    if method < 0:
        return [Finding("nav-bar-dark-hot-path", path, 1, "NavBarButtonsHook is missing")]
    body, _ = block_at(text, method)
    findings = []
    if "chain.getArgs()[0] as Float" in body or "chain.getArg(0) as Float" not in body:
        findings.append(
            Finding(
                "nav-bar-dark-hot-path",
                path,
                line_of(text, method),
                "dark intensity must read one argument without materializing the argument array",
            )
        )
    state = body.find("if (previousDark == isDark)")
    resources = body.find("ModuleHelper.getModuleContext(navbar.context)")
    if state < 0 or resources < 0 or state > resources:
        findings.append(
            Finding(
                "nav-bar-dark-hot-path",
                path,
                line_of(text, method),
                "unchanged dark state must return before module resources or drawables are loaded",
            )
        )
    if "setAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD, isDark)" not in body:
        findings.append(
            Finding("nav-bar-dark-hot-path", path, line_of(text, method), "dark state must be recorded after icon replacement")
        )
    if "removeAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD)" not in body:
        findings.append(
            Finding("nav-bar-dark-hot-path", path, line_of(text, method), "configuration changes must invalidate the dark-state cache")
        )
    return findings


def check_weather_data_lifecycle(path: Path, text: str) -> list[Finding]:
    """The process weather singleton must not retain rebuilt SystemUI controllers or View contexts."""
    rel = rel_posix(path)
    if rel == "tv/withaibuild/customiuizer/mods/SystemClockHooks.kt":
        findings = []
        if "val mWeatherRunnable = Runnable" in text:
            findings.append(
                Finding(
                    "weather-data-lifecycle",
                    path,
                    1,
                    "do not allocate a controller-capturing Runnable for the process singleton",
                )
            )
        if "WeatherDataController.initContext(mContext, thisObject)" not in text:
            findings.append(
                Finding(
                    "weather-data-lifecycle",
                    path,
                    1,
                    "pass the clock controller itself so WeatherDataController can retain it weakly",
                )
            )
        init_call = text.find("WeatherDataController.initContext(mContext, thisObject)")
        oom_catch = text.find("catch (oom: OutOfMemoryError)", init_call)
        generic_catch = text.find("catch (t: Throwable)", init_call)
        if init_call >= 0 and (oom_catch < 0 or generic_catch < 0 or oom_catch > generic_catch):
            findings.append(
                Finding(
                    "weather-data-lifecycle",
                    path,
                    line_of(text, init_call),
                    "weather hook initialization must rethrow OOM before isolating ordinary failures",
                )
            )
        return findings

    if rel == "tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt":
        handler = text.find("val coroutineFailureHandler: CoroutineExceptionHandler")
        if handler < 0:
            return [Finding("weather-data-lifecycle", path, 1, "coroutine failure handler is missing")]
        body, _ = block_at(text, handler)
        if "if (throwable is OutOfMemoryError) throw throwable" not in body:
            return [
                Finding(
                    "weather-data-lifecycle",
                    path,
                    line_of(text, handler),
                    "coroutine failure handler must rethrow OutOfMemoryError",
                )
            ]
        return []

    if rel != "tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt":
        return []

    findings = []
    required = (
        ("private var updateTarget: WeakReference<Any>?", "retain the clock controller through WeakReference"),
        ("val appContext = context.applicationContext", "retain only the application context"),
        ("updateTarget = WeakReference(clockController)", "replace the weak update target on controller rebuild"),
        ("@Volatile\n    var weatherInfo", "publish weather updates safely from the I/O dispatcher"),
        ("if (!queryFailureLogged)", "rate-limit repeated weather provider diagnostics"),
        ("catch (oom: OutOfMemoryError)", "rethrow query OutOfMemoryError before isolating ordinary failures"),
    )
    for token, detail in required:
        if token not in text:
            findings.append(Finding("weather-data-lifecycle", path, 1, detail))
    if "private var weakReferenceRunnable: Runnable?" in text:
        findings.append(
            Finding(
                "weather-data-lifecycle",
                path,
                1,
                "process singleton must not strongly retain a controller-capturing Runnable",
            )
        )
    return findings


def check_battery_indicator_lifecycle(path: Path, text: str) -> list[Finding]:
    """Battery indicator callbacks must avoid duplicate redraw work and follow the View lifetime."""
    if rel_posix(path) != BATTERY_INDICATOR:
        return []
    findings = []
    required = (
        ("val charging = isCharging && !isCharged", "normalize charging state before comparing it"),
        ("mIsBeingCharged == charging", "skip duplicate full-charge redraws"),
        ("if (updatePosted) return", "coalesce repeated layout/configuration updates"),
        ("if (!post(updateRunnable)) updatePosted = false", "recover when the View cannot enqueue an update"),
        ("ModuleHelper.registerOwnedReceiver(", "bind the receiver to the indicator owner"),
        ("ModuleHelper.unregisterOwnedReceiver(this, RECEIVER_KEY, broadcastReceiver)", "release the owned receiver on detach"),
        ("removeCallbacks(updateRunnable)", "release the pending View callback on detach"),
        ("Dispatchers.Main + ModuleHelper.coroutineFailureHandler", "isolate ordinary coroutine failures without swallowing OOM"),
    )
    for token, detail in required:
        if token not in text:
            findings.append(Finding("battery-indicator-lifecycle", path, 1, detail))
    forbidden = (
        ("mIsBeingCharged == isCharging && !isCharged", "full-charge events force duplicate redraws"),
        ("viewScope.launch { update() }", "layout changes allocate a coroutine job per update"),
        ("context.registerReceiver(broadcastReceiver", "receiver bypasses the owner registry"),
    )
    for token, detail in forbidden:
        if token in text:
            findings.append(Finding("battery-indicator-lifecycle", path, 1, detail))
    return findings


def check_preference_click_feedback(path: Path, text: str) -> list[Finding]:
    """Preference clicks must render before remote mirroring and switches need pressed feedback."""
    rel = rel_posix(path)
    if rel == CHECKBOX_PREFERENCE:
        findings = []
        if "android.R.id.switch_widget" not in text:
            findings.append(Finding("preference-click-feedback", path, 1, "bind the platform switch widget"))
        if "isDuplicateParentStateEnabled = true" not in text:
            findings.append(
                Finding(
                    "preference-click-feedback",
                    path,
                    1,
                    "propagate the row pressed state to the switch for immediate touch feedback",
                )
            )
        return findings

    if rel != XPOSED_SERVICE_MANAGER:
        return []

    listener_start = text.find("private val prefsChanged")
    if listener_start < 0:
        return [Finding("preference-click-feedback", path, 1, "preference change listener is missing")]
    listener, _ = block_at(text, listener_start)
    findings = []
    if "requestPreferenceWrite(sharedPreferences, key, generation)" not in listener:
        findings.append(
            Finding(
                "preference-click-feedback",
                path,
                line_of(text, listener_start),
                "enqueue the remote write instead of executing it in the SharedPreferences callback",
            )
        )
    for token in ("sharedPreferences.all", "remote.edit()", "edit.apply()"):
        if token in listener:
            findings.append(
                Finding(
                    "preference-click-feedback",
                    path,
                    line_of(text, listener_start),
                    f"'{token}' must not run inside the input-frame preference callback",
                )
            )
    required = (
        ("Dispatchers.Default.limitedParallelism(1)", "serialize mirror work on a shared background dispatcher"),
        ("mirrorScope.launch { runMirror(generation, reason) }", "run full mirror passes off the main looper"),
        ("if (throwable is OutOfMemoryError) throw throwable", "preserve OOM at the mirror worker boundary"),
    )
    for token, detail in required:
        if token not in text:
            findings.append(Finding("preference-click-feedback", path, 1, detail))
    return findings


REFLECTION_CACHE = "tv/withaibuild/customiuizer/mods/utils/ReflectionCache.kt"


def check_reflection_cache_get_declared_method_oom(path: Path, text: str) -> list[Finding]:
    """ReflectionCache.getDeclaredMethod must catch and rethrow OOM before the generic Throwable handler."""
    if rel_posix(path) != REFLECTION_CACHE:
        return []
    match = re.search(r"\bgetDeclaredMethod\s*\(", text)
    if not match:
        return []
    # Find the nearest preceding try block.
    try_match = None
    for m in re.finditer(r"\btry\s*\{", text[: match.start()]):
        try_match = m
    if try_match is None:
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "getDeclaredMethod is not inside a try block",
            )
        ]
    block, block_start = block_at(text, try_match.start())
    block_end = block_start + len(block)
    if match.start() < try_match.end() or match.end() > block_end:
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "getDeclaredMethod is not inside the nearest try block",
            )
        ]
    after = text[block_end:]
    oom_catch = re.search(r"catch\s*\(\s*oom\s*:\s*OutOfMemoryError\s*\)\s*\{\s*throw\s+oom\s*\}", after)
    t_catch = re.search(r"catch\s*\(\s*t\s*:\s*Throwable\s*\)", after)
    if not oom_catch:
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "getDeclaredMethod try block lacks catch (oom: OutOfMemoryError) { throw oom }",
            )
        ]
    if not t_catch or oom_catch.start() > t_catch.start():
        return [
            Finding(
                "reflection-cache-getdeclaredmethod-oom",
                path,
                line_of(text, match.start()),
                "catch (oom: OutOfMemoryError) must precede catch (t: Throwable)",
            )
        ]
    return []


def check_docs_zero_object_wording(docs_dir: Path | None = None) -> list[Finding]:
    """Docs must not use the old "disabled feature zero objects" wording."""
    if docs_dir is None:
        docs_dir = REPO_ROOT / "docs"
    if not docs_dir.is_dir():
        return []
    findings = []
    banned = (
        (re.compile(r"关闭功能\s*零运行对象"), "关闭功能零FeatureDefinition；零业务installer对象；零Hook对象；仅保留固定LazyFeatureSpec元数据和轻量lambda"),
        (re.compile(r"disabled\s+feature\s+(?:zero|0)\s+(?:running\s+)?objects?", re.IGNORECASE), "zero FeatureDefinition / zero installer / zero Hook wording"),
    )
    for path in docs_dir.rglob("*.md"):
        file_text = path.read_text(encoding="utf-8")
        for pattern, suggestion in banned:
            for m in pattern.finditer(file_text):
                findings.append(
                    Finding(
                        "docs-zero-object-wording",
                        path,
                        line_of(file_text, m.start()),
                        f"forbidden wording; use '{suggestion}'",
                    )
                )
    return findings


def check_gesture_hot_path_no_reflection(path: Path, text: str) -> list[Finding]:
    """The gesture state machine hot path must not perform reflection at dispatch time."""
    if "customiuizer/mods/utils/gesture/" not in path.as_posix():
        return []
    if rel_posix(path) in {
        "tv/withaibuild/customiuizer/mods/utils/gesture/StatusBarGestureDependenciesResolver.kt",
        "tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureDependenciesResolver.kt",
    }:
        return []
    findings = []
    for match in REFLECTION.finditer(text):
        findings.append(
            Finding(
                "gesture-hot-path-no-reflection",
                path,
                line_of(text, match.start()),
                "gesture hot path uses reflection or XposedHelpers",
            )
        )
    return findings


# --- gesture machine invariants -----------------------------------------------------

GESTURE_HOT_PATH_FILES = {
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureStateMachine.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureEvent.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureEventFingerprint.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureGeometry.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureCommand.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureSnapshot.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureSession.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/GestureConfigPublisher.kt",
    "tv/withaibuild/customiuizer/mods/utils/gesture/StatusBarGestureEffectExecutor.kt",
}


def check_gesture_machine_dispatch_rejects_intercept(path: Path, text: str) -> list[Finding]:
    """STATUS_BAR_INTERCEPT must never flow through the authoritative dispatch() path."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt":
        return []
    if 'require(event.entry != GestureEntry.STATUS_BAR_INTERCEPT)' not in text:
        return [
            Finding(
                "gesture-dispatch-rejects-intercept",
                path,
                1,
                "GestureMachine.dispatch() must reject STATUS_BAR_INTERCEPT; use observe()",
            )
        ]
    return []


def check_gesture_down_no_prefs(path: Path, text: str) -> list[Finding]:
    """The hot path must not read raw preferences inside ACTION_DOWN / observe."""
    if rel_posix(path) not in GESTURE_HOT_PATH_FILES:
        return []
    if "MainModule.mPrefs" in text or "mPrefs." in text:
        return [
            Finding(
                "gesture-down-no-prefs",
                path,
                1,
                "gesture hot path reads MainModule.mPrefs; use a published GestureConfig",
            )
        ]
    return []


def check_gesture_hot_path_no_cold_method_lookup(path: Path, text: str) -> list[Finding]:
    """Method handles are resolved in prepare(); the hot path must not look them up."""
    if rel_posix(path) not in GESTURE_HOT_PATH_FILES:
        return []
    if rel_posix(path) in {
        "tv/withaibuild/customiuizer/mods/utils/gesture/StatusBarGestureDependenciesResolver.kt",
        "tv/withaibuild/customiuizer/mods/utils/gesture/ControlCenterGestureDependenciesResolver.kt",
    }:
        return []
    findings = []
    for match in re.finditer(r"\.getDeclared?Method\(", text):
        findings.append(
            Finding(
                "gesture-hot-path-no-method-lookup",
                path,
                line_of(text, match.start()),
                "gesture hot path looks up a Method; resolve Method handles during prepare()",
            )
        )
    return findings


def check_gesture_dispatch_no_deps_prepare(path: Path, text: str) -> list[Finding]:
    """dispatch() and observe() must not cold-resolve dependencies on the hot path."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt":
        return []
    findings = []
    for method_match in re.finditer(r"\b(fun|internal fun)\s+(dispatch|observe)\s*\(", text):
        body, start = block_at(text, method_match.end() - 1)
        if "depsResolver" in body and ".prepare" in body:
            findings.append(
                Finding(
                    "gesture-dispatch-no-deps-prepare",
                    path,
                    line_of(text, method_match.start()),
                    f"{method_match.group(2)}() calls depsResolver.prepare(); preparation belongs in prepare()",
                )
            )
    return findings


def check_gesture_move_no_system_service(path: Path, text: str) -> list[Finding]:
    """ACTION_MOVE must not hit Context.getSystemService or any new service lookup."""
    if rel_posix(path) not in GESTURE_HOT_PATH_FILES:
        return []
    if "getSystemService" in text:
        return [
            Finding(
                "gesture-move-no-system-service",
                path,
                1,
                "gesture hot path calls getSystemService; resolve services during prepare()",
            )
        ]
    return []


def check_gesture_dispatch_no_config_resolver(path: Path, text: str) -> list[Finding]:
    """dispatch()/observe() must not call the full GestureConfigResolver.resolve() parser."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt":
        return []
    if "GestureConfigResolver.resolve" in text:
        return [
            Finding(
                "gesture-dispatch-no-config-resolver",
                path,
                1,
                "GestureMachine calls GestureConfigResolver.resolve(); publish the config outside touch callbacks",
            )
        ]
    return []


def check_gesture_shared_arbiter(path: Path, text: str) -> list[Finding]:
    """Status Bar and Control Center GestureMachine instances must share one PhysicalGestureArbiter."""
    if rel_posix(path) == "tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt":
        if "PhysicalGestureArbiter()" not in text:
            return [
                Finding(
                    "gesture-shared-arbiter",
                    path,
                    1,
                    "ControlCenterPluginRuntime must create the shared PhysicalGestureArbiter",
                )
            ]
        if "ControlCenterGestureRuntimeHolder(" not in text or "arbiter = arbiter" not in text:
            return [
                Finding(
                    "gesture-shared-arbiter",
                    path,
                    1,
                    "ControlCenterGestureRuntimeHolder must use the shared arbiter",
                )
            ]
        return []
    if rel_posix(path) == "tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt":
        if "PhysicalGestureArbiter()" in text:
            return [
                Finding(
                    "gesture-shared-arbiter",
                    path,
                    1,
                    "StatusBarGesturesHook must not create a separate PhysicalGestureArbiter; use ControlCenterPluginRuntime.arbiter()",
                )
            ]
        if "statusBarMachine" not in text or ("arbiter = arbiter" not in text and "runtime.arbiter()" not in text and "ControlCenterPluginRuntime.arbiter()" not in text):
            return [
                Finding(
                    "gesture-shared-arbiter",
                    path,
                    1,
                    "statusBarMachine must use the shared arbiter from ControlCenterPluginRuntime",
                )
            ]
        return []
    return []


def check_gesture_detach_cleanup(path: Path, text: str) -> list[Finding]:
    """View detach must clear the corresponding GestureMachine owner."""
    findings = []
    if rel_posix(path) == "tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt":
        status_ok = '"onDetachedFromWindow"' in text and "statusBarMachine.clear" in text
        if not status_ok:
            findings.append(
                Finding(
                    "gesture-detach-cleanup",
                    path,
                    1,
                    "PhoneStatusBarView.onDetachedFromWindow must call statusBarMachine.clear(ownerId)",
                )
            )
    if rel_posix(path) == "tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt":
        cc_ok = '"onDetachedFromWindow"' in text and "controlCenterMachine.clear" in text
        if not cc_ok:
            findings.append(
                Finding(
                    "gesture-detach-cleanup",
                    path,
                    1,
                    "ControlCenterWindowViewImpl.onDetachedFromWindow must call controlCenterMachine.clear(ownerId)",
                )
            )
    return findings
    if not status_ok:
        findings.append(
            Finding(
                "gesture-detach-cleanup",
                path,
                1,
                "PhoneStatusBarView.onDetachedFromWindow must call statusBarMachine.clear(ownerId)",
            )
        )
    if not cc_ok:
        findings.append(
            Finding(
                "gesture-detach-cleanup",
                path,
                1,
                "ControlCenterWindowViewImpl.onDetachedFromWindow must call controlCenterMachine.clear(ownerId)",
            )
        )
    return findings


def check_gesture_no_obsolete_hook_state(path: Path, text: str) -> list[Finding]:
    """The old all-in-one status bar gesture hook state must not be reintroduced."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt":
        return []
    findings = []
    for field in (
        "isSlidingStart",
        "isSliding",
        "tapStartX",
        "tapStartY",
        "tapStartPointers",
        "tapStartBrightness",
        "topMinimumBacklight",
        "topMaximumBacklight",
        "currentTouchX",
        "currentTouchTime",
        "currentDownTime",
        "currentDownX",
        "nextBrightNess",
    ):
        if field in text:
            index = text.find(field)
            findings.append(
                Finding(
                    "gesture-no-obsolete-hook-state",
                    path,
                    line_of(text, index),
                    f"obsolete status bar gesture hook state '{field}' found",
                )
            )
    return findings


def check_gesture_arbiter_down_only(path: Path, text: str) -> list[Finding]:
    """Tokens can only be acquired on ACTION_DOWN; no other event may grow the arbiter."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/PhysicalGestureArbiter.kt":
        return []
    if re.search(r"\bfun\s+tryAcquire\s*\(", text):
        return [
            Finding(
                "gesture-arbiter-down-only",
                path,
                1,
                "PhysicalGestureArbiter must expose only tryAcquireOnDown(), not tryAcquire()",
            )
        ]
    return []


def check_gesture_machine_uses_down_acquire(path: Path, text: str) -> list[Finding]:
    """GestureMachine must request the token via tryAcquireOnDown()."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachine.kt":
        return []
    if "tryAcquireOnDown" not in text:
        return [
            Finding(
                "gesture-machine-uses-down-acquire",
                path,
                1,
                "GestureMachine must call PhysicalGestureArbiter.tryAcquireOnDown()",
            )
        ]
    return []


def check_gesture_command_action_id(path: Path, text: str) -> list[Finding]:
    """Double-tap and long-press commands must carry the resolved action id so it is snapshotted."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/GestureCommand.kt":
        return []
    findings = []
    if "val actionId: Int" not in text:
        findings.append(
            Finding(
                "gesture-command-action-id",
                path,
                1,
                "GestureCommand.TriggerDoubleTap and TriggerLongPress must carry a snapshotted actionId",
            )
        )
    return findings


def check_gesture_executor_uses_action_launcher(path: Path, text: str) -> list[Finding]:
    """StatusBarGestureEffectExecutor must launch actions through GestureActionLauncher, not GlobalActions."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/StatusBarGestureEffectExecutor.kt":
        return []
    if "GlobalActions" in text:
        return [
            Finding(
                "gesture-executor-uses-action-launcher",
                path,
                1,
                "StatusBarGestureEffectExecutor must not call GlobalActions directly; use actionLauncher.launch()",
            )
        ]
    return []


def check_gesture_control_center_no_ishooked(path: Path, text: str) -> list[Finding]:
    """The Control Center plugin hook must rely on the runtime holder, not a stale isHooked flag."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt":
        return []
    if "isHooked" in text:
        return [
            Finding(
                "gesture-control-center-no-ishooked",
                path,
                1,
                "Control Center plugin hook must not use an isHooked flag; use ControlCenterGestureRuntimeHolder",
            )
        ]
    return []


def check_gesture_stress_no_bypass(path: Path, text: str) -> list[Finding]:
    """The behavioural stress test must not force invalid events to look like valid ones."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/GestureMachineBehavioralStressTest.kt":
        return []
    findings = []
    if re.search(r"\baction\s*=\s*GestureAction\.DOWN", text):
        findings.append(
            Finding(
                "gesture-stress-no-bypass",
                path,
                1,
                "stress test must not force every event to be a DOWN",
            )
        )
    if "random.nextFloat() * 80f" in text:
        findings.append(
            Finding(
                "gesture-stress-no-bypass",
                path,
                1,
                "stress test must not force Control Center DOWN inside the status bar",
            )
        )
    if re.search(r"heldTokens\(\)\s*\.\s*get|get\s*\(\s*ownerId\s*\).*arbiter", text):
        findings.append(
            Finding(
                "gesture-stress-no-bypass",
                path,
                1,
                "stress test should use arbiter.tokensForOwner() and arbiter.heldTokenCount(), not raw map access",
            )
        )
    return findings


def check_gesture_side_effect_gate_owner_cleanup(path: Path, text: str) -> list[Finding]:
    """The side-effect gate must model owner identity and support owner-level cleanup."""
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/gesture/GestureSideEffectGate.kt":
        return []
    findings = []
    if "data class OwnerFingerprint" not in text:
        findings.append(
            Finding(
                "gesture-side-effect-gate-owner-cleanup",
                path,
                1,
                "GestureSideEffectGate must define OwnerFingerprint(ownerId)",
            )
        )
    if "fun clearOwner" not in text:
        findings.append(
            Finding(
                "gesture-side-effect-gate-owner-cleanup",
                path,
                1,
                "GestureSideEffectGate must expose clearOwner(ownerId)",
            )
        )
    return findings


def _body_before(text: str, search_from: int) -> str:
    """Return the top-level-ish block starting at the first '{' at or after search_from.

    This is a convenience around block_at for callers that only need the body.
    """
    body, _ = block_at(text, search_from)
    return body


def check_owned_registrations_model(path: Path, text: str) -> list[Finding]:
    """OwnedRegistrations must not require a live owner to run a cleanup action.

    The cleanup action is what actually releases a system-side registration, so it must run
    even when the owner has been garbage collected. The model is therefore:

        register(owner: V, cleanup: () -> Unit): RegistrationHandle

    and the entry must hold the cleanup reference weakly enough that owner collection does
    not keep the cleanup alive, but strongly enough that the cleanup still runs after
    collection. All cleanup paths must be two-phase (snapshot, then run) and exact-once.
    """
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/OwnedRegistrations.kt":
        return []
    findings = []

    # The public register API must take a no-arg cleanup action.
    if not re.search(r"fun\s+register\s*\(\s*owner\s*:\s*V\s*,\s*cleanup\s*:\s*\(\)\s*->\s*Unit\s*\)", text):
        findings.append(
            Finding(
                "owned-registrations-model",
                path,
                1,
                "register must take a no-argument cleanup: () -> Unit",
            )
        )

    # Cleanup of the whole registry must exist.
    if "fun cleanupAll()" not in text:
        findings.append(
            Finding(
                "owned-registrations-model",
                path,
                1,
                "OwnedRegistrations must expose cleanupAll()",
            )
        )

    # cleanupWhere must take a snapshot before running any cleanup.
    if "entries.removeAll(toRemove)" not in text or "for (entry in toRemove)" not in text:
        findings.append(
            Finding(
                "owned-registrations-model",
                path,
                1,
                "cleanupWhere must snapshot stale entries before running them",
            )
        )

    # cleanupAll must snapshot before running.
    if "val toRemove = entries.toList()" not in text and "val toRemove = entries" not in text:
        findings.append(
            Finding(
                "owned-registrations-model",
                path,
                1,
                "cleanupAll must snapshot the live list before running cleanups",
            )
        )

    # The internal callback reference must be consumed (nulled) before the action runs,
    # so a fatal exception cannot lead to a second execution.
    if not re.search(r"val\s+callback\s*=\s*entry\.cleanup\n\s*entry\.cleanup\s*=\s*null", text):
        findings.append(
            Finding(
                "owned-registrations-model",
                path,
                1,
                "cleanup callback reference must be consumed before the action is invoked",
            )
        )

    # No owner-null gate may prevent the callback from running.
    if re.search(r"owner\s*!=\s*null\s*&&\s*callback\s*!=\s*null", text):
        findings.append(
            Finding(
                "owned-registrations-model",
                path,
                1,
                "cleanup action must run even when the owner has been garbage collected",
            )
        )

    return findings


def _statement_offset_in_body(body: str, needle: str) -> int | None:
    """Return the character offset of a statement token inside an already-extracted body."""
    match = re.search(needle, body)
    if match is None:
        return None
    return match.start()


def _require_after(
    findings: list[Finding],
    rule: str,
    path: Path,
    line: int,
    before: int | None,
    after: int | None,
    before_label: str,
    after_label: str,
) -> None:
    """Add a finding if a statement does not occur after another within the same body."""
    if before is None:
        return
    if after is None:
        findings.append(
            Finding(
                rule,
                path,
                line,
                f"{after_label} must follow {before_label}",
            )
        )
        return
    if after <= before:
        findings.append(
            Finding(
                rule,
                path,
                line,
                f"{after_label} must come after {before_label} in the same block",
            )
        )


def check_status_bar_display_registry_prune(path: Path, text: str) -> list[Finding]:
    """Dead per-display and pending states must release their registrations before removal.

    A state can only be dropped after every registration it owns has been released. A
    reentrant cleanup that registers a new entry must keep the state alive. Pending owners
    must be held weakly so a never-bound view can still be garbage collected.

    Inside prune():
      - bound states are iterated in a for loop over byDisplay;
      - for each dead display state, registrations.cleanupAll() runs first;
      - the generation and registration list are re-checked after cleanup;
      - the dead-display list is populated only after both re-checks;
      - byDisplay.remove() is never called before the dead list is populated;
      - pending references are expunged, the return value is kept, and every
        cleared pending state has its registrations cleaned.
    """
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt":
        return []
    findings = []

    if "WeakIdentityMap" not in text:
        findings.append(
            Finding(
                "status-bar-display-registry-prune",
                path,
                1,
                "pending state must be keyed by a WeakIdentityMap so owners are collected by identity and equal-but-distinct owners do not share state",
            )
        )

    if re.search(r"\bWeakHashMap\b", text):
        findings.append(
            Finding(
                "status-bar-display-registry-prune",
                path,
                1,
                "pending state must not use WeakHashMap: equals/hashCode would make equal owners share state",
            )
        )

    if "private val pendingByOwner" not in text:
        findings.append(
            Finding(
                "status-bar-display-registry-prune",
                path,
                1,
                "StatusBarDisplayRegistry must declare a pending owner map",
            )
        )

    prune_match = re.search(r"fun\s+prune\s*\(", text)
    if prune_match is None:
        findings.append(
            Finding(
                "status-bar-display-registry-prune",
                path,
                1,
                "StatusBarDisplayRegistry must expose prune()",
            )
        )
    else:
        prune_body, prune_body_start = block_at(text, prune_match.start())
        if not prune_body:
            findings.append(
                Finding(
                    "status-bar-display-registry-prune",
                    path,
                    line_of(text, prune_match.start()),
                    "prune() must have a brace-balanced body",
                )
            )
        else:
            # --- bound display cleanup / re-check / dead-list order ---
            bound_loop = re.search(
                r"for\s*\(\s*\(?\s*(\w+)\s*,\s*(\w+)\s*\)?\s+in\s+byDisplay\s*\)",
                prune_body,
            )
            if bound_loop is None:
                findings.append(
                    Finding(
                        "status-bar-display-registry-prune",
                        path,
                        line_of(text, prune_body_start),
                        "prune must iterate byDisplay to find dead displays",
                    )
                )
            else:
                display_var = re.escape(bound_loop.group(1))
                state_var = re.escape(bound_loop.group(2))
                loop_body, _ = block_at(prune_body, bound_loop.start())

                cleanup_pos = _statement_offset_in_body(loop_body, rf"\b{state_var}\.registrations\.cleanupAll\s*\(\s*\)")
                gen_recheck_pos = _statement_offset_in_body(loop_body, rf"\b{state_var}\.generation\?\.get\(\)\s*==\s*null")
                size_recheck_pos = _statement_offset_in_body(loop_body, rf"\b{state_var}\.registrations\.size\s*==\s*0")
                add_dead_pos = _statement_offset_in_body(loop_body, rf"\b\w+\.add\s*\(\s*{display_var}\s*\)")

                if cleanup_pos is None:
                    findings.append(
                        Finding(
                            "status-bar-display-registry-prune",
                            path,
                            line_of(text, prune_body_start),
                            f"prune loop must call {state_var}.registrations.cleanupAll() before removing a state",
                        )
                    )
                if gen_recheck_pos is None:
                    findings.append(
                        Finding(
                            "status-bar-display-registry-prune",
                            path,
                            line_of(text, prune_body_start),
                            f"prune loop must re-check {state_var}.generation?.get() == null after cleanup",
                        )
                    )
                if size_recheck_pos is None:
                    findings.append(
                        Finding(
                            "status-bar-display-registry-prune",
                            path,
                            line_of(text, prune_body_start),
                            f"prune loop must re-check {state_var}.registrations.size == 0 after cleanup",
                        )
                    )
                if add_dead_pos is None:
                    findings.append(
                        Finding(
                            "status-bar-display-registry-prune",
                            path,
                            line_of(text, prune_body_start),
                            f"prune loop must add {display_var} to a dead-display list after re-checks",
                        )
                    )

                _require_after(
                    findings,
                    "status-bar-display-registry-prune",
                    path,
                    line_of(text, prune_body_start),
                    cleanup_pos,
                    gen_recheck_pos,
                    f"{state_var}.registrations.cleanupAll()",
                    f"{state_var}.generation?.get() == null re-check",
                )
                _require_after(
                    findings,
                    "status-bar-display-registry-prune",
                    path,
                    line_of(text, prune_body_start),
                    gen_recheck_pos,
                    size_recheck_pos,
                    f"{state_var}.generation?.get() == null re-check",
                    f"{state_var}.registrations.size == 0 re-check",
                )
                _require_after(
                    findings,
                    "status-bar-display-registry-prune",
                    path,
                    line_of(text, prune_body_start),
                    size_recheck_pos,
                    add_dead_pos,
                    f"{state_var}.registrations.size == 0 re-check",
                    "dead-displays.add()",
                )

                # byDisplay.remove must not appear inside the bound loop (it runs after the dead list is built).
                if re.search(rf"\bbyDisplay\.remove\s*\(", loop_body):
                    findings.append(
                        Finding(
                            "status-bar-display-registry-prune",
                            path,
                            line_of(text, prune_body_start),
                            "byDisplay.remove() must not run inside the bound-state loop; collect ids first",
                        )
                    )

                # byDisplay.remove must appear after the dead list is populated in the rest of prune().
                if add_dead_pos is not None:
                    remaining = prune_body[bound_loop.end() :]
                    remove_match = re.search(r"\bbyDisplay\.remove\s*\(", remaining)
                    if remove_match is None:
                        findings.append(
                            Finding(
                                "status-bar-display-registry-prune",
                                path,
                                line_of(text, prune_body_start),
                                "prune must call byDisplay.remove() after the dead-display list is collected",
                            )
                        )

            # --- pending state expunge and cleanup ---
            expunge_assignment = re.search(
                r"val\s+(\w+)\s*=\s*pendingByOwner\.expunge\s*\(\s*\)",
                prune_body,
            )
            if expunge_assignment is None:
                findings.append(
                    Finding(
                        "status-bar-display-registry-prune",
                        path,
                        line_of(text, prune_body_start),
                        "prune must keep the return value of pendingByOwner.expunge() and use it",
                    )
                )
            else:
                cleared_var = re.escape(expunge_assignment.group(1))
                pending_loop = re.search(
                    rf"for\s*\(\s*(\w+)\s+in\s+{cleared_var}\s*\)",
                    prune_body,
                )
                if pending_loop is None:
                    findings.append(
                        Finding(
                            "status-bar-display-registry-prune",
                            path,
                            line_of(text, prune_body_start),
                            f"prune must iterate the expunged pending states ({expunge_assignment.group(1)})",
                        )
                    )
                else:
                    pending_state_var = re.escape(pending_loop.group(1))
                    pending_loop_body, _ = block_at(prune_body, pending_loop.start())
                    if not re.search(
                        rf"\b{pending_state_var}\.registrations\.cleanupAll\s*\(\s*\)",
                        pending_loop_body,
                    ):
                        findings.append(
                            Finding(
                                "status-bar-display-registry-prune",
                                path,
                                line_of(text, prune_body_start),
                                f"prune must call {pending_state_var}.registrations.cleanupAll() for every cleared pending state",
                            )
                        )

    bind_match = re.search(r"fun\s+bind\s*\(", text)
    if bind_match is not None:
        body = _body_before(text, bind_match.start())
        if "registrations.cleanupAll()" not in body:
            findings.append(
                Finding(
                    "status-bar-display-registry-prune",
                    path,
                    1,
                    "bind must cleanupAll the old generation for this display",
                )
            )

    if re.search(r"fun\s+detach\s*\(", text) is None:
        findings.append(
            Finding(
                "status-bar-display-registry-prune",
                path,
                1,
                "StatusBarDisplayRegistry must expose detach()",
            )
        )

    if "fun expunge" not in text and "expunge()" not in text:
        findings.append(
            Finding(
                "status-bar-display-registry-prune",
                path,
                1,
                "WeakIdentityMap must expose expunge() for reference-queue cleanup",
            )
        )

    if "allStatesSnapshot" not in text:
        findings.append(
            Finding(
                "status-bar-display-registry-prune",
                path,
                1,
                "registry must expose allStatesSnapshot() so consumers read a consistent main-thread snapshot",
            )
        )

    return findings


def _check_left_icon_handle(path: Path, text: str, findings: list[Finding]) -> None:
    """Verify the onAttachedToWindow left icon manager uses an exact-once handle.

    The method must:
      - read the previous leftIconRegistrationHandle;
      - call oldHandle.cleanupNow() in the oldHandle != null branch before addIconGroup;
      - call addIconGroup with a new manager;
      - register a new handle whose cleanup removes that same manager from the same controller;
      - save the new handle as leftIconRegistrationHandle.

    An oldHandle == null fallback that releases a staleManager directly is allowed.
    """
    method_body = None
    method_start = None
    for match in re.finditer(r"\bfun\s+(\w+)\s*\(", text):
        body, start = block_at(text, match.start())
        if "leftIconRegistrationHandle" in body:
            method_body = body
            method_start = start
            break

    if method_body is None:
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                1,
                "left icon hook must read and save leftIconRegistrationHandle in a method block",
            )
        )
        return

    # 1. Read the existing handle.
    old_handle_pattern = r"val\s+(\w+)\s*=\s*XposedHelpers\.getAdditionalInstanceField\s*\(\s*mStatusBar\s*,\s*\"leftIconRegistrationHandle\"\s*\)"
    old_handle_match = re.search(old_handle_pattern, method_body)
    if old_handle_match is None:
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                line_of(text, method_start),
                "left icon hook must read the existing leftIconRegistrationHandle",
            )
        )
        return
    old_handle_var = re.escape(old_handle_match.group(1))

    # 2. cleanupNow when oldHandle != null, before addIconGroup.
    if_match = re.search(rf"if\s*\(\s*{old_handle_var}\s*!=\s*null\s*\)", method_body)
    if if_match is None:
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                line_of(text, method_start),
                f"left icon hook must check {old_handle_match.group(1)} != null before cleanup",
            )
        )
    else:
        if_body, _ = block_at(method_body, if_match.start())
        cleanup_match = re.search(rf"\b{old_handle_var}\.cleanupNow\s*\(\s*\)", if_body)
        if cleanup_match is None:
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    line_of(text, method_start),
                    f"left icon hook must call {old_handle_match.group(1)}.cleanupNow() when an old handle exists",
                )
            )

    # 3. Find addIconGroup and the controller/manager it uses.
    add_icon_group_match = re.search(
        r'XposedHelpers\.callMethod\s*\(\s*(\w+)\s*,\s*"addIconGroup"\s*,\s*(\w+)\s*\)',
        method_body,
    )
    if add_icon_group_match is None:
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                line_of(text, method_start),
                "left icon hook must call addIconGroup before registering a new handle",
            )
        )
        return
    controller_var = re.escape(add_icon_group_match.group(1))
    manager_var = re.escape(add_icon_group_match.group(2))

    # 4. cleanupNow must appear before addIconGroup in the method body.
    if if_match is not None:
        cleanup_pos_match = re.search(rf"\b{old_handle_var}\.cleanupNow\s*\(\s*\)", method_body)
        if cleanup_pos_match is not None and cleanup_pos_match.start() >= add_icon_group_match.start():
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    line_of(text, method_start),
                    "left icon oldHandle.cleanupNow() must run before addIconGroup",
                )
            )

    # 5. New handle from state.registrations.register after addIconGroup.
    tail = method_body[add_icon_group_match.end() :]
    register_match = re.search(
        r"val\s+(\w+)\s*=\s*state\.registrations\.register\s*\(\s*mStatusBar\s*\)\s*\{",
        tail,
    )
    if register_match is None:
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                line_of(text, method_start),
                "left icon hook must register a new handle after addIconGroup",
            )
        )
        return
    new_handle_var = re.escape(register_match.group(1))

    # 6. The cleanup must removeIconGroup with the same controller/manager.
    register_body, _ = block_at(tail, register_match.start())
    cleanup_pattern = (
        rf"releaseRegistrationSilently\s*\(\s*{controller_var}\s*,\s*\"removeIconGroup\"\s*,\s*{manager_var}\s*,"
    )
    if not re.search(cleanup_pattern, register_body):
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                line_of(text, method_start),
                "left icon registration cleanup must call removeIconGroup on the same controller and manager",
            )
        )

    # 7. Save the new handle to leftIconRegistrationHandle.
    after_register = tail[register_match.start() :]
    save_pattern = (
        rf'XposedHelpers\.setAdditionalInstanceField\s*\(\s*mStatusBar\s*,\s*"leftIconRegistrationHandle"\s*,\s*{new_handle_var}\s*\)'
    )
    if not re.search(save_pattern, after_register):
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                line_of(text, method_start),
                "left icon handle must be saved as leftIconRegistrationHandle",
            )
        )


def check_status_bar_registration_cleanup(path: Path, text: str) -> list[Finding]:
    """Status bar dispatcher/controller registrations must have a release path.

    DarkIconDispatcher.addDarkReceiver and StatusBarIconController.addIconGroup make SystemUI
    singletons hold strong references to module Views. Without a matching removeDarkReceiver /
    removeIconGroup at the next status bar generation, every theme/density/fold re-inflation
    leaks the previous View tree and keeps feeding callbacks into detached views. A weak-ref
    registry alone cannot fix this because the system side keeps the views strongly reachable.

    This rule is structural, not just string counting. It checks that:
      - addDarkReceiver is paired with a per-display registration whose cleanup calls
        removeDarkReceiver with the exact same dispatcher and view arguments;
      - addIconGroup is paired with a registration whose cleanup calls removeIconGroup with
        the same controller and manager, and that the resulting handle is saved;
      - the old global generation / second-row references are gone and replaced by
        StatusBarDisplayRegistry and HookInstallStateMachine;
      - the left icon manager uses an exact-once registration handle, not a manual remove;
      - the network speed second row is saved per-display and every posted update re-verifies
        the row, the owner and the display state before touching a View;
      - registration cleanup closures do not capture the owner view.
    """
    if rel_posix(path) != "tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt":
        return []
    findings = []

    # --- old global state that must not return ---
    for pattern, detail in (
        (r"private\s+var\s+statusBarGeneration", "global statusBarGeneration is not per-display"),
        (r"private\s+val\s+statusBarRegistrations\s*=\s*OwnedRegistrations", "global OwnedRegistrations is not per-display"),
        (r"private\s+var\s+netSpeedSecondRowRef", "global netSpeedSecondRowRef is not per-display"),
        (r"private\s+var\s+netSpeedSecondRowHookInstalled", "global once flag must be replaced by HookInstallStateMachine"),
        (r"fun\s+cleanupStaleStatusBarRegistrations", "old generation cleanup function must be replaced by per-display state"),
        (r"cleanupWhere\s*\{\s*owner\s*->\s*owner\s*!==\s*current", "global cleanupWhere { owner !== current } must be per-display"),
    ):
        if re.search(pattern, text):
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    1,
                    detail,
                )
            )

    # --- required per-display / state-machine pieces ---
    for token, detail in (
        ("StatusBarDisplayRegistry", "per-display state registry"),
        ("StatusBarDisplayState", "per-display state class"),
        ("StatusBarNetworkSpeedDispatcher", "testable network speed main-thread dispatcher"),
        ("netSpeedSecondRowHookInstaller", "network speed hook once-guard installer"),
        ("statusBarViewDetachHookInstaller", "status bar view detach hook once-guard"),
        ("installStatusBarViewLifecycleHook", "status bar view lifecycle hook installer"),
        ("onDetachedFromWindow", "status bar view detach callback"),
        ("statusBarDisplayRegistry.detach", "per-display detach cleanup"),
        ("HookInstallStateMachine", "process-level once-guard state machine"),
        ("leftIconRegistrationHandle", "left icon manager exact-once handle field"),
        ("releaseRegistrationSilently", "registration release diagnostics helper"),
        ("state.secondRow", "per-display second row reference"),
    ):
        if token not in text:
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    1,
                    f"missing {detail}: {token}",
                )
            )

    # --- onFinishInflate must save the second row for this display ---
    if not re.search(r"state\.secondRow\s*=\s*WeakReference\s*\(\s*secondRight\s*\)", text):
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                1,
                "onFinishInflate must save state.secondRow = WeakReference(secondRight)",
            )
        )

    # --- the left icon group handle must be saved on the status bar view ---
    _check_left_icon_handle(path, text, findings)

    # --- no direct manual remove outside the release helper ---
    for method in ("removeIconGroup", "removeDarkReceiver"):
        for match in re.finditer(rf'ModuleHelper\.callMethodSilently\s*\([^)]*"{method}"', text):
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    line_of(text, match.start()),
                    f"manual ModuleHelper.callMethodSilently for {method}; use releaseRegistrationSilently through an exact-once handle",
                )
            )

    # --- addDarkReceiver / removeDarkReceiver pairing per call ---
    add_receiver = re.compile(
        r'XposedHelpers\.callMethod\s*\(\s*([^\),\s]+)\s*,\s*"addDarkReceiver"\s*,\s*([^\),\s]+)\s*\)'
    )
    for match in add_receiver.finditer(text):
        dispatcher = match.group(1)
        arg = match.group(2)
        tail = text[match.end():]
        cleanup = rf'releaseRegistrationSilently\s*\(\s*{re.escape(dispatcher)}\s*,\s*"removeDarkReceiver"\s*,\s*{re.escape(arg)}'
        if not re.search(cleanup, tail):
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    line_of(text, match.start()),
                    f"addDarkReceiver({dispatcher}, {arg}) has no matching releaseRegistrationSilently removeDarkReceiver",
                )
            )

    # --- addIconGroup / removeIconGroup pairing per call ---
    add_icon_group = re.compile(
        r'XposedHelpers\.callMethod\s*\(\s*([^\),\s]+)\s*,\s*"addIconGroup"\s*,\s*([^\),\s]+)\s*\)'
    )
    for match in add_icon_group.finditer(text):
        controller = match.group(1)
        manager = match.group(2)
        tail = text[match.end():]
        cleanup = rf'releaseRegistrationSilently\s*\(\s*{re.escape(controller)}\s*,\s*"removeIconGroup"\s*,\s*{re.escape(manager)}'
        if not re.search(cleanup, tail):
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    line_of(text, match.start()),
                    f"addIconGroup({controller}, {manager}) has no matching releaseRegistrationSilently removeIconGroup",
                )
            )

    # --- the apply helper must re-verify row/owner/state before touching views ---
    apply = re.search(r"fun\s+applyNetworkSpeedToRow\s*\(", text)
    if apply is not None:
        apply_body = _body_before(text, apply.start())
        for check, detail in (
            ("isAttachedToWindow", "applyNetworkSpeedToRow must re-check row.isAttachedToWindow"),
            ("state.generation?.get()", "applyNetworkSpeedToRow must re-check the display generation"),
            ("state.secondRow?.get()", "applyNetworkSpeedToRow must re-check the per-display second row"),
        ):
            if check not in apply_body:
                findings.append(
                    Finding(
                        "status-bar-registration-cleanup",
                        path,
                        1,
                        detail,
                    )
                )

    # --- the posted runnable must dispatch from the main looper with immutable payload only ---
    if "netSpeedMainHandler" not in text:
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                1,
                "network speed updates must post to a dedicated main-looper handler",
            )
        )

    post_blocks = list(re.finditer(r"netSpeedMainHandler\.post\s*\{", text))
    if not post_blocks:
        findings.append(
            Finding(
                "status-bar-registration-cleanup",
                path,
                1,
                "network speed update must post a runnable to the main looper",
            )
        )
    else:
        # The network-speed posted runnable must invoke the dispatcher with an immutable payload
        # and a sequence. Detach/prune runnables may also use the same handler, so each block is
        # checked for stale View/owner/state capture, but only the one that dispatches network
        # speed requires StatusBarNetworkSpeedDispatcher.dispatch.
        dispatch_seen = False
        for posted in post_blocks:
            posted_body = _body_before(text, posted.start())
            if "StatusBarNetworkSpeedDispatcher.dispatch" in posted_body:
                dispatch_seen = True
            for banned in ("val row", "val owner", "val state", "row.post", "allStatesSnapshot", "for ("):
                if banned in posted_body:
                    findings.append(
                        Finding(
                            "status-bar-registration-cleanup",
                            path,
                            1,
                            f"network speed posted runnable must not capture stale {banned}",
                        )
                    )
        if not dispatch_seen:
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    1,
                    "network speed main runnable must call StatusBarNetworkSpeedDispatcher.dispatch",
                )
            )

    # --- registration cleanup closures must not capture the owner view ---
    for match in re.finditer(r'state\.registrations\.register\s*\(\s*([^\s\n,)]+)\s*\)\s*(\{)', text):
        owner_expr = match.group(1)
        block, _ = block_at(text, match.start(2))
        if re.search(rf"\b{re.escape(owner_expr)}\b", block):
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    line_of(text, match.start()),
                    f"registration cleanup closure captures the owner '{owner_expr}'; capture the release target instead",
                )
            )

    # --- setNetworkSpeedIcon hook must be installed via the guarded installer ---
    if '"setNetworkSpeedIcon"' in text:
        if "installNetSpeedSecondRowHook" not in text:
            findings.append(
                Finding(
                    "status-bar-registration-cleanup",
                    path,
                    1,
                    "setNetworkSpeedIcon hook must be installed via installNetSpeedSecondRowHook",
                )
            )

    return findings


RULES = (
    check_gesture_hot_path_no_reflection,
    check_gesture_machine_dispatch_rejects_intercept,
    check_gesture_down_no_prefs,
    check_gesture_hot_path_no_cold_method_lookup,
    check_gesture_dispatch_no_deps_prepare,
    check_gesture_move_no_system_service,
    check_gesture_dispatch_no_config_resolver,
    check_gesture_shared_arbiter,
    check_gesture_detach_cleanup,
    check_gesture_no_obsolete_hook_state,
    check_gesture_arbiter_down_only,
    check_gesture_machine_uses_down_acquire,
    check_gesture_command_action_id,
    check_gesture_executor_uses_action_launcher,
    check_gesture_control_center_no_ishooked,
    check_gesture_stress_no_bypass,
    check_gesture_side_effect_gate_owner_cleanup,
    check_owned_registrations_model,
    check_status_bar_display_registry_prune,
    check_status_bar_registration_cleanup,
    check_guard_framework_callbacks,
    check_guard_framework_callbacks,
    check_guard_deferred_callbacks,
    check_coroutine_scopes_handle_failure,
    check_no_raw_register_receiver,
    check_no_looperless_handler,
    check_no_redundant_arg_marshalling,
    check_no_direct_hook_installation,
    check_no_legacy_xposed,
    check_no_regex_split_on_literal,
    check_api102_isolation,
    check_feature_install_oom_cleanup,
    check_feature_install_boundary,
    check_device_info_monitor_hot_path,
    check_method_hook_fatal_boundary,
    check_module_helper_fatal_boundaries,
    check_charging_info_hot_path,
    check_album_art_memory_lifecycle,
    check_nav_bar_dark_hot_path,
    check_weather_data_lifecycle,
    check_battery_indicator_lifecycle,
    check_preference_click_feedback,
    check_reflection_cache_get_declared_method_oom,
)


def _git_changed_files(ref: str | None = None) -> list[Path]:
    cmd = ["git", "diff", "--name-only", "--diff-filter=ACMR"]
    if ref:
        cmd.extend([ref])
    result = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True, check=True)
    files = []
    for name in result.stdout.splitlines():
        path = REPO_ROOT / name
        if path.suffix == ".kt" and path.is_file() and SOURCE_ROOT in path.parents:
            files.append(path)
    return files


def staged_kotlin_files() -> list[Path]:
    return _git_changed_files("--cached")


def changed_kotlin_files() -> list[Path]:
    """Files changed relative to HEAD (staged or unstaged)."""
    return _git_changed_files("HEAD")


MANIFEST = REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
MAIN_MODULE = REPO_ROOT / "app" / "src" / "main" / "java" / "tv" / "withaibuild" / "customiuizer" / "MainModule.java"
XPOSED_HELPERS = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "mods" / "utils" / "XposedHelpers.java"
GENERIC_APP_INSTALLER = SOURCE_ROOT / "tv" / "withaibuild" / "customiuizer" / "installers" / "GenericAppInstaller.kt"
JAVA_FATAL_BOUNDARIES = (
    MAIN_MODULE,
    XPOSED_HELPERS,
)


CONTRACTS_DIR = REPO_ROOT / "rom-contracts"
SCHEMA_FILE = CONTRACTS_DIR / "schema.json"

EXPECTED_PROCESS_PACKAGE = {
    "system_server": "android",
    "systemui": "com.android.systemui",
    "launcher": "com.miui.home",
    "securitycenter": "com.miui.securitycenter",
}

FRAMEWORK_TARGETS = {"framework"}


def check_dexkit_close_oom(path: Path | None = None) -> list[Finding]:
    """DexKit bridge cleanup must not swallow OutOfMemoryError."""
    if path is None:
        path = XPOSED_HELPERS
    text = strip_comments(path.read_text(encoding="utf-8"))
    method = text.find("public static void closeBridge()")
    if method < 0:
        return [Finding("dexkit-close-oom", path, 1, "closeBridge method is missing")]

    body, _ = block_at(text, method)
    oom = re.search(r"catch\s*\(\s*OutOfMemoryError\s+oom\s*\)", body)
    if oom is None or "throw oom;" not in body[oom.start():]:
        return [
            Finding(
                "dexkit-close-oom",
                path,
                line_of(text, method),
                "closeBridge must catch and rethrow OutOfMemoryError before generic Throwable",
            )
        ]
    return []


def check_java_fatal_boundaries(paths: tuple[Path, ...] | None = None) -> list[Finding]:
    """Java runtime boundaries may isolate Throwable only after explicitly rethrowing OOM."""
    if paths is None:
        paths = JAVA_FATAL_BOUNDARIES
    findings = []
    for path in paths:
        text = strip_comments(path.read_text(encoding="utf-8"))
        for generic in re.finditer(r"catch\s*\(\s*Throwable\s+(\w+)\s*\)", text):
            body, _ = block_at(text, generic.start())
            variable = generic.group(1)
            if re.search(rf"\bthrow\s+{re.escape(variable)}\s*;", body):
                continue

            preceding = None
            for oom in re.finditer(r"catch\s*\(\s*OutOfMemoryError\s+(\w+)\s*\)", text[:generic.start()]):
                preceding = oom
            if preceding is not None:
                oom_body, oom_body_start = block_at(text, preceding.start())
                between = text[oom_body_start + len(oom_body):generic.start()]
                oom_variable = preceding.group(1)
                if not between.strip() and re.search(
                    rf"\bthrow\s+{re.escape(oom_variable)}\s*;",
                    oom_body,
                ):
                    continue

            findings.append(
                Finding(
                    "java-fatal-boundary",
                    path,
                    line_of(text, generic.start()),
                    "catch(Throwable) must rethrow it or be immediately preceded by an OOM rethrow catch",
                )
            )
    return findings


def check_xposed_throwable_log_oom(path: Path | None = None) -> list[Finding]:
    """Throwable logging is a shared isolation boundary and must rethrow OOM before formatting."""
    if path is None:
        path = XPOSED_HELPERS
    text = strip_comments(path.read_text(encoding="utf-8"))
    methods = list(re.finditer(r"public\s+static\s+void\s+log\s*\([^)]*Throwable\s+(\w+)[^)]*\)", text))
    if len(methods) != 2:
        return [
            Finding(
                "xposed-throwable-log-oom",
                path,
                1,
                f"expected two Throwable log overloads, found {len(methods)}",
            )
        ]
    findings = []
    for method in methods:
        body, _ = block_at(text, method.start())
        variable = method.group(1)
        guard = re.search(
            rf"if\s*\(\s*{re.escape(variable)}\s+instanceof\s+OutOfMemoryError\s*\)\s*"
            rf"throw\s*\(\s*OutOfMemoryError\s*\)\s*{re.escape(variable)}\s*;",
            body,
        )
        formatter = body.find("Log.getStackTraceString")
        if guard is None or formatter < 0 or guard.start() > formatter:
            findings.append(
                Finding(
                    "xposed-throwable-log-oom",
                    path,
                    line_of(text, method.start()),
                    "Throwable log overload must rethrow OOM before formatting the stack trace",
                )
            )
    return findings


def check_generic_app_attach_transaction(path: Path | None = None) -> list[Finding]:
    """Generic app specs and registry must be short-lived inside Application.attach."""
    if path is None:
        path = GENERIC_APP_INSTALLER
    text = strip_comments(path.read_text(encoding="utf-8"))
    callback = text.find("after(")
    registry = text.find("FeatureInstallRegistry()")
    findings = []
    if callback < 0 or registry < callback:
        findings.append(
            Finding(
                "generic-app-attach-transaction",
                path,
                1,
                "generic registry must be created inside the Application.attach callback",
            )
        )
    if "GenericAppFeatures.selected(" not in text or "GenericAppFeatures.all(" in text:
        findings.append(
            Finding(
                "generic-app-attach-transaction",
                path,
                1,
                "generic installer must construct only route-selected FeatureSpec objects",
            )
        )
    return findings


def smali_to_fqcn(class_desc: str) -> str:
    return class_desc[1:-1].replace("/", ".")


def check_rom_contracts() -> list[Finding]:
    """Every ROM contract entry must be traceable to a real source file and consistent target."""
    if not CONTRACTS_DIR.is_dir():
        return []

    findings: list[Finding] = []
    for contract_path in CONTRACTS_DIR.glob("*.json"):
        if contract_path.name == "schema.json":
            continue

        try:
            contract = json.loads(contract_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            findings.append(Finding("rom-contracts", contract_path, 0, f"invalid JSON: {e}"))
            continue

        if contract.get("schemaVersion") != 1:
            findings.append(Finding("rom-contracts", contract_path, 0, "unsupported schemaVersion"))

        for target in contract.get("targets", []):
            target_name = target.get("target", "?")
            pkg = target.get("targetProcessPackage", "")

            if target_name not in FRAMEWORK_TARGETS:
                expected = EXPECTED_PROCESS_PACKAGE.get(target_name)
                if expected and pkg != expected:
                    findings.append(
                        Finding(
                            "rom-contracts",
                            contract_path,
                            0,
                            f"target '{target_name}' has targetProcessPackage '{pkg}', expected '{expected}'",
                        )
                    )

            class_desc = target.get("class", "")
            if not class_desc.startswith("L") or not class_desc.endswith(";"):
                findings.append(Finding("rom-contracts", contract_path, 0, f"class '{class_desc}' is not a smali descriptor"))
                continue

            source_file = REPO_ROOT / target.get("sourceFile", "")
            source_hook = target.get("sourceHookFunction", "")
            if not source_file.is_file():
                findings.append(Finding("rom-contracts", contract_path, 0, f"sourceFile {target.get('sourceFile')} does not exist"))
                continue

            source_text = source_file.read_text(encoding="utf-8")
            fqcn = smali_to_fqcn(class_desc)
            if fqcn not in source_text:
                findings.append(Finding("rom-contracts", contract_path, 0, f"class {fqcn} not found in {target.get('sourceFile')}"))

            for method in target.get("methods", []):
                method_name = method.get("name", "")
                if method_name and f'"{method_name}"' not in source_text:
                    findings.append(Finding("rom-contracts", contract_path, 0, f"method '{method_name}' not referenced in {target.get('sourceFile')}"))

                if method.get("required", True) and not method.get("descriptor"):
                    findings.append(Finding("rom-contracts", contract_path, 0, f"required method '{method_name}' is missing descriptor"))

            if source_hook and source_hook not in source_text:
                findings.append(Finding("rom-contracts", contract_path, 0, f"sourceHookFunction '{source_hook}' not found in {target.get('sourceFile')}"))
                continue

            # Derive expected target from the install entry / parameter type.
            if source_hook == "onSystemServerStarting" or source_hook == "onPackageReady":
                method_match = re.search(
                    rf"(?:public\s+void|fun)\s+{re.escape(source_hook)}\s*\(([^)]*)\)",
                    MAIN_MODULE.read_text(encoding="utf-8") if source_file == MAIN_MODULE else source_text,
                )
                if method_match and "SystemServerStartingParam" in method_match.group(1):
                    if target_name != "system_server" or pkg != "android":
                        findings.append(
                            Finding(
                                "rom-contracts",
                                contract_path,
                                0,
                                f"{source_hook} uses SystemServerStartingParam; process target must be 'system_server' (LSPosed scope 'system'), got '{target_name}' / '{pkg}'",
                            )
                        )
            else:
                signature_match = re.search(
                    rf"fun\s+{re.escape(source_hook)}\s*\(([^)]*)\)",
                    source_text,
                )
                if signature_match:
                    params = signature_match.group(1)
                    if "SystemServerStartingParam" in params:
                        if target_name != "system_server" or pkg != "android":
                            findings.append(
                                Finding(
                                    "rom-contracts",
                                    contract_path,
                                    0,
                                    f"{source_hook} uses SystemServerStartingParam; process target must be 'system_server' (LSPosed scope 'system'), got '{target_name}' / '{pkg}'",
                                )
                            )
                    elif "PackageReadyParam" in params:
                        if target_name == "system_server":
                            findings.append(
                                Finding(
                                    "rom-contracts",
                                    contract_path,
                                    0,
                                    f"{source_hook} uses PackageReadyParam but is declared as '{target_name}'",
                                )
                            )

    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", action="store_true", help="check only files staged in git")
    parser.add_argument("--changed", action="store_true", help="check files changed relative to HEAD (staged or unstaged)")
    args = parser.parse_args()

    if args.staged and args.changed:
        parser.error("--staged and --changed are mutually exclusive")

    if args.staged:
        files = staged_kotlin_files()
    elif args.changed:
        files = changed_kotlin_files()
    else:
        files = sorted(SOURCE_ROOT.rglob("*.kt"))
    findings: list[Finding] = []
    for path in files:
        text = strip_comments(path.read_text(encoding="utf-8"))
        for rule in RULES:
            findings.extend(rule(path, text))

    findings.extend(check_rom_contracts())
    findings.extend(check_docs_zero_object_wording())
    findings.extend(check_dexkit_close_oom())
    findings.extend(check_java_fatal_boundaries())
    findings.extend(check_xposed_throwable_log_oom())
    findings.extend(check_generic_app_attach_transaction())

    if not findings:
        print(f"check-invariants: {len(files)} files, no violations")
        return 0

    by_rule: dict[str, list[Finding]] = {}
    for finding in findings:
        by_rule.setdefault(finding.rule, []).append(finding)

    for rule, items in sorted(by_rule.items()):
        try:
            doc = next(r for r in RULES if r.__name__.replace("check_", "").replace("_", "-") == rule).__doc__
        except StopIteration:
            doc = None
        print(f"\n=== {rule} ({len(items)}) ===")
        if doc:
            print((doc or "").strip())
            print()
        for finding in items:
            print(f"  {finding}")

    print(f"\ncheck-invariants: {len(findings)} violation(s) across {len(files)} files")
    return 1


if __name__ == "__main__":
    sys.exit(main())
