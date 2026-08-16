# C6-B1 — Notification Auto-Expand Architecture C Production Implementation

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**C6-A0 freeze / B1 base SHA:** `173b422fd6b98302039587e32684b026d8b28904`
**Scope:** `SystemNotificationHooks.ExpandNotificationsHook` → `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow.setFeedbackIcon` → notification auto-expand
**Type:** B1 production implementation — resolver, ABI, snapshot, runtime state, effect, hook, tests, and documentation.

---

## 0. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `173b422fd6b98302039587e32684b026d8b28904` | `git rev-parse HEAD` at start gate |
| Remote HEAD | `173b422fd6b98302039587e32684b026d8b28904` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` at start gate |
| Merge-base (HEAD, origin/HEAD) | `173b422fd6b98302039587e32684b026d8b28904` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` at start gate |
| Worktree at start gate | clean | `git status --short` empty |
| C6-A0 preflight changed | `false` | no edits to `C6_NOTIFICATION_AUTO_EXPAND_A0_PREFLIGHT.md` |
| C6 target-selection changed | `false` | no edits to `C6_TARGET_SELECTION.md` |
| C1-C5 changed | `false` | no edits |

START PASS.
`C6_A0 = PASS`, `C6_B1 = AUTHORIZED`, `C6_B2 = NOT AUTHORIZED`.

---

## 1. CHANGED FILES

### 1.1 Production

```text
app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandAbi.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandResolver.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandSnapshot.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandRuntimeState.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandEffect.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandHook.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt
```

### 1.2 Tests

```text
app/src/test/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandFixtures.java
app/src/test/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandResolverTest.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandRuntimeStateTest.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandEffectTest.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandHookTest.kt
```

### 1.3 Documentation

```text
docs/architecture-c/C6_NOTIFICATION_AUTO_EXPAND_B1.md
```

---

## 2. ARCHITECTURE FLOW

```text
Cold Resolve
    NotificationAutoExpandHook.install(classLoader)
    → fail-safe XposedHelpers.findClassIfExists("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", classLoader)
    → if root found: NotificationAutoExpandResolver.resolve(resolutionRootClass)
    → primitive-boolean mOnKeyguard, zero-argument getEntry, one-argument setSystemExpanded(Boolean.class)
    → NotificationAutoExpandAbi

Frozen ABI
    NotificationAutoExpandAbi(resolutionRootClass, mOnKeyguardField, getEntryMethod, setSystemExpandedMethod)

Immutable Snapshot
    NotificationAutoExpandRuntimeState
    → one MainModule.mPrefs.getAll() generation per refresh
    → NotificationAutoExpandSnapshot(modeRaw, copy-owned Set<String>)

Process Runtime State
    → process-scoped singleton
    → one ModuleHelper.PreferenceObserver registration
    → AtomicReference<NotificationAutoExpandSnapshot?>

Effect
    NotificationAutoExpandEffect(abi, snapshotRef)
    → FAST: exact root, Field.getBoolean, Method.invoke, legacy mSbn/getPackageName helpers, chain.proceed
    → COMPLETE_LEGACY: legacy XposedHelpers path, chain.proceed

Thin Hook
    NotificationAutoExpandHook.install(classLoader)
    → ModuleHelper.hookAllMethods(... "setFeedbackIcon", hook)

Hot Execute
    MethodHook.intercept(chain) → effect.intercept(chain) → XposedHelpers.throwOrReturn(throwable, result)
```

---

## 3. ABI STRATEGY

**Strategy name:** `CONSERVATIVE_PARTIAL_FAST`

| Member | FAST | Resolution |
|---|---|---|
| `resolutionRootClass` | class identity only | `XposedHelpers.findClassIfExists` (findClassIfExists-compatible) probe |
| `mOnKeyguard` | `Field.getBoolean(thisObject)` | `XposedHelpers.findField` + `type == Boolean.TYPE` |
| `getEntry` | `Method.invoke(thisObject)` | `XposedHelpers.findMethodBestMatch(... "getEntry")` |
| `setSystemExpanded` | `Method.invoke(thisObject, true)` | `XposedHelpers.findMethodBestMatch(... "setSystemExpanded", java.lang.Boolean::class.java)` |
| `mSbn` | LEGACY only | `XposedHelpers.getObjectField(entry, "mSbn")` at hot-time |
| `getPackageName` | LEGACY only | `XposedHelpers.callMethod(notification, "getPackageName")` at hot-time |

- `mOnKeyguard` accepts only primitive `Boolean.TYPE`; wrapper `Boolean.class` is rejected.
- `setSystemExpanded` is resolved with the boxed `java.lang.Boolean::class.java` actual-argument type to match the legacy `XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)` call.
- If any frozen member is missing or incompatible, `resolve` returns `null` and the callback selects `COMPLETE_LEGACY`.

---

## 4. SNAPSHOT CONTRACT

```kotlin
data class NotificationAutoExpandSnapshot(
    val modeRaw: String,
    val selectedApps: Set<String>,
)
```

- `modeRaw` is the raw `system_expandnotifs` string. It is **not** parsed during refresh.
- Malformed mode strings throw `NumberFormatException` in the callback, before `chain.proceed()`, preserving the legacy failure point.
- `selectedApps` is built from a single `MainModule.mPrefs.getAll()` generation.
- Missing or wrong-type `system_expandnotifs_apps` value becomes an empty, unmodifiable set.
- The set is copy-owned and unmodifiable; mutating the source set after refresh does not affect the snapshot.
- Relevant keys only: `system_expandnotifs`, `system_expandnotifs_apps`.
- `null` key in the observer triggers a full rebuild.
- Failed refresh clears the snapshot to `null`.
- Fatal refresh failures clear the snapshot and are rethrown.

---

## 5. RUNTIME PUBLICATION / LIFECYCLE

- `NotificationAutoExpandRuntimeState.install()` creates and initializes the process singleton at most once.
- The `ModuleHelper.PreferenceObserver` is registered exactly once per process.
- Repeated `install()` calls return the same instance (`installed` + `instance` double-checked under `installLock`).
- The initial refresh runs outside the hooked callback.
- `snapshotRef` is `AtomicReference<NotificationAutoExpandSnapshot?>`; the initial value is `null`.
- `install()` sets `installed = true` only after the observer is registered and the initial refresh completes.
- The runtime state does not retain row, view, context, or activity instances.

---

## 6. FAST / LEGACY SELECTION MATRIX

| Condition | Path | Notes |
|---|---|---|
| Hook target class missing | N/A | `ModuleHelper.hookAllMethods` logs `TARGET_CLASS_MISSING`; no callback; no runtime state created. |
| `abi == null` | `COMPLETE_LEGACY` | Callback installed; all work uses `XposedHelpers` helpers. |
| `snapshot == null` | `COMPLETE_LEGACY` | Snapshot unavailable, e.g. after refresh failure or deliberate clear. |
| `thisObject == null` | `COMPLETE_LEGACY` | Exact-root policy cannot be verified. |
| `thisObject.javaClass !== resolutionRoot` | `COMPLETE_LEGACY` | Subclass / proxy mismatch. |
| `thisObject != null && thisObject.javaClass === resolutionRoot && snapshot != null` | FAST | Frozen `mOnKeyguard`/`getEntry`/`setSystemExpanded` handles; no classloader lookups; no preference reads. Retained LEGACY helpers still resolve `mSbn` and `getPackageName` dynamically. |

No FAST→LEGACY retry is performed after the FAST boundary is crossed.

---

## 7. FAST PATH EXECUTION ORDER

```text
1. thisObject = chain.thisObject
2. mOnKeyguard = abi.mOnKeyguardField.getBoolean(thisObject)
3. if mOnKeyguard == true:
       return chain.proceed()
4. entry      = abi.getEntryMethod.invoke(thisObject)
5. notification = XposedHelpers.getObjectField(entry, "mSbn")
6. pkgName    = XposedHelpers.callMethod(notification, "getPackageName") as String
7. opt        = Integer.parseInt(snapshot.modeRaw)
8. isSelected = snapshot.selectedApps.contains(pkgName)
9. if (opt == 2 && !isSelected) || (opt == 3 && isSelected):
       abi.setSystemExpandedMethod.invoke(thisObject, true)
10. return chain.proceed()
```

- `setSystemExpanded` is invoked at most once per callback.
- No preference reads, no new `findClass`/`findClassIfExists` calls, no `ClassLoader` lookups, and no HashMap/Set allocation in the normal FAST path. The frozen `findField`/`findMethodBestMatch` work is done once at install; retained LEGACY `mSbn`/`getPackageName` helpers may use their normal XposedHelpers caches.

---

## 8. LEGACY PATH EXECUTION ORDER

```text
1. thisObject = chain.thisObject
2. mOnKeyguard = XposedHelpers.getBooleanField(thisObject, "mOnKeyguard")
3. if !mOnKeyguard:
       entry      = XposedHelpers.callMethod(thisObject, "getEntry")
       notification = XposedHelpers.getObjectField(entry, "mSbn")
       pkgName    = XposedHelpers.callMethod(notification, "getPackageName") as String
       opt        = Integer.parseInt(MainModule.mPrefs.getString("system_expandnotifs", "1") ?: "1")
       isSelected = MainModule.mPrefs.getStringSet("system_expandnotifs_apps").contains(pkgName)
       if (opt == 2 && !isSelected) || (opt == 3 && isSelected):
           XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)
4. return chain.proceed()
```

The legacy path replicates the original `SystemNotificationHooks.ExpandNotificationsHook` callback exactly.

---

## 9. FAILURE / FATAL SEMANTICS

| Failure | Behavior |
|---|---|
| Malformed `modeRaw` | `NumberFormatException` thrown before `chain.proceed()`; rethrown via `throwOrReturn`. |
| `getEntry` invocation failure | `InvocationTargetException` → `XposedHelpers.InvocationTargetError`; `chain.proceed()` not reached. |
| `mSbn` field failure | `NoSuchFieldError` / `IllegalArgumentException`; `chain.proceed()` not reached. |
| `getPackageName` failure | `XposedHelpers.InvocationTargetError`; `chain.proceed()` not reached. |
| `setSystemExpanded` failure | `InvocationTargetException` → `XposedHelpers.InvocationTargetError`; `chain.proceed()` not reached. |
| `chain.proceed()` throws | Captured by `MethodHook.intercept` and rethrown via `XposedHelpers.throwOrReturn`. |
| `IllegalAccessException` on fast field/method | Logged and rethrown as `IllegalAccessError`. |
| Fatal errors (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) | Never swallowed; propagated through all catch boundaries. |
| No retry | After the FAST boundary is selected, there is no fallback to LEGACY within the same callback. |
| No rollback | A partial `setSystemExpanded` mutation is not rolled back. |

---

## 10. HOT-PATH MODEL

The FAST path is designed to avoid the following in a normal callback:

- `MainModule.mPrefs` reads.
- New `findClass`/`findClassIfExists` calls, `ClassLoader` lookups, or per-callback discovery of the three frozen members.

- `HashMap`, `Set`, or `ArrayList` creation.
- Normal-path logging, timestamps, or diagnostic writes.
- Allocation of callback-local objects beyond the temporary `entry`, `notification`, `pkgName`, `opt`, and `isSelected`.

The pre-resolved Field.getBoolean and Method.invoke handles for the three frozen members are invoked directly. The retained LEGACY mSbn and getPackageName helpers remain dynamic and may perform their own XposedHelpers reflection; they are not resolved at install time.

---

## 11. TESTS

All new tests are under `app/src/test/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/`.

| Test class | Coverage |
|---|---|
| `NotificationAutoExpandResolverTest` | Primitive `mOnKeyguard` acceptance, wrapper rejection, missing members, `getEntry` zero-arg resolution with overload disambiguation, `setSystemExpanded` exact `Boolean.class` / primitive `Boolean.TYPE` selection, fatal class-loading propagation. |
| `NotificationAutoExpandRuntimeStateTest` | Default `modeRaw = "1"`, raw string preservation, malformed `modeRaw` preserved unparsed, missing/wrong-type app set, copy-owned set, one `getAll` generation per rebuild, relevant-key filtering, `null`-key rebuild, singleton publication, observer count, fatal refresh. |
| `NotificationAutoExpandEffectTest` | ABI null / snapshot null / subclass root → `COMPLETE_LEGACY`; exact root → `FAST`; `mOnKeyguard == true` short-circuit; blacklist mode 2; whitelist mode 3; other modes no expansion; malformed mode throws before `chain.proceed`; real `getEntry` invocation failure; `mSbn` missing failure; `getPackageName` failure; `setSystemExpanded` failure; `chain.proceed` rethrow; `setSystemExpanded` at most once; `InvocationTargetException` → `InvocationTargetError`; `IllegalAccessException` → `IllegalAccessError`. |
| `NotificationAutoExpandHookTest` | Target class missing; ordinary target probe failure isolated with `ALL_METHODS / INSTALL_FAILED` diagnostic; fatal probe failure propagation. |

Evidence classification for all component tests: `RUNTIME_TESTED_COMPONENT`.
Callback evidence remains `C6_RUNTIME_CALLBACK_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK` because no real SystemUI `setFeedbackIcon` callback or device execution was performed.

---

## 12. SCOPE AND EVIDENCE

- **B1 scope only.** No C1-C5 files were changed. No generic architecture framework (`ArchitectureManager`, `RuntimeRegistry`, `EffectDispatcher`, etc.) was introduced.
- **A0 freeze and target-selection documents were not modified.**
- **No `XposedHelpers`, `ModuleHelper`, `PrefMap`, shared preference bootstrap, or shared architecture primitives were changed.**
- **No B2 consolidation or completion work was started.**
- All verification results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 13. END STATE

```text
C6_A0 = PASS
C6_B1 = AUTHORIZED / IMPLEMENTED
C6_B2 = NOT STARTED
```

This document and the associated production/test files constitute the C6-B1 production implementation. The next Architecture C Gatekeeper review is required before C6-B2 consolidation.
