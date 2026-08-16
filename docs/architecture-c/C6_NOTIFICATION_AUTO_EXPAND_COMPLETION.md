# C6 — Notification Auto-Expand Architecture C Completion Freeze

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**C6-B1 completion freeze / B2 base SHA:** `196d263b82a93ebfe9d9eca01d6044e07456dc1b`
**Scope:** `SystemNotificationHooks.ExpandNotificationsHook` → `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow.setFeedbackIcon` → notification auto-expand
**Type:** B2 consolidation / completion record — no production, no test changes.

---

## 1. Completion status

| Gate | State |
|---|---|
| `C6_TARGET_SELECTION` | `PASS` |
| `C6_A0` | `PASS` |
| `C6_B1` | `PASS` |
| `C6_B2` | `READY_FOR_INDEPENDENT_AUDIT` |

`C6_RUNTIME_CALLBACK_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK`.

---

## 2. Target

```text
TARGET_HOOK:      SystemNotificationHooks.ExpandNotificationsHook
TARGET_ROM_CLASS: com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
TARGET_METHOD:    setFeedbackIcon
TARGET_DOMAIN:    notification auto-expand
```

Legacy callback behavior preserved:

```text
mOnKeyguard
→ if false:
   getEntry()
   → mSbn
   → getPackageName()
   → read system_expandnotifs
   → read system_expandnotifs_apps
   → evaluate (opt == 2 && !isSelected) || (opt == 3 && isSelected)
   → optionally setSystemExpanded(true)
→ chain.proceed()
```

---

## 3. Exact SHA chain

| Milestone | SHA |
|---|---|
| C6 selection base | `e98a1566abd93e2160f57372e9c89d29c6652779` |
| C6 target-selection completion freeze | `8001f972e194bd388341c45f7064175cbcb27560` |
| C6-A0 completion freeze | `173b422fd6b98302039587e32684b026d8b28904` |
| C6-B1 original production | `b901c34e5b575e081538609a25badad224050eb4` |
| C6-B1 install/probe corrective | `b60ab32082d1564cbe48f046168269d9e2d96d30` |
| C6-B1 final production-bearing closure | `90fff20ec5d507805e4280753ec44872f3da038b` |
| C6-B1 completion freeze / B2 base | `196d263b82a93ebfe9d9eca01d6044e07456dc1b` |

---

## 4. Architecture strategy

**Strategy name:** `CONSERVATIVE_PARTIAL_FAST`

Architecture C flow:

```text
Cold Resolve
→ Frozen ABI
→ Immutable Snapshot
→ Process Runtime State
→ Effect
→ Thin Hook
→ Hot Execute
```

Frozen ABI members:

```text
mOnKeyguard  Field
getEntry     Method
setSystemExpanded Method
```

Retained dynamic LEGACY helpers:

```text
mSbn         via XposedHelpers.getObjectField(entry, "mSbn")
getPackageName via XposedHelpers.callMethod(notification, "getPackageName")
```

The FAST path does not eliminate reflection entirely; it removes per-callback ABI discovery for the three frozen members while retaining the two legacy helpers.

---

## 5. Frozen ABI

| Member | FAST | Resolution |
|---|---|---|
| `mOnKeyguard` | `Field.getBoolean(thisObject)` | `XposedHelpers.findField` + `type == Boolean.TYPE`; wrapper `Boolean.class` rejected. |
| `getEntry` | `Method.invoke(thisObject)` | `XposedHelpers.findMethodBestMatch(resolutionRootClass, "getEntry")`; zero-argument selected. |
| `setSystemExpanded` | `Method.invoke(thisObject, true)` | `XposedHelpers.findMethodBestMatch(resolutionRootClass, "setSystemExpanded", java.lang.Boolean::class.java)`; boxed `Boolean` exact overload preferred, primitive compatible fallback. |
| `mSbn` | LEGACY only | `XposedHelpers.getObjectField(entry, "mSbn")` at callback time. |
| `getPackageName` | LEGACY only | `XposedHelpers.callMethod(notification, "getPackageName")` at callback time. |

---

## 6. Snapshot / runtime contract

```kotlin
data class NotificationAutoExpandSnapshot(
    val modeRaw: String,
    val selectedApps: Set<String>,
)
```

- `modeRaw` is the raw `system_expandnotifs` string; not parsed during refresh.
- Malformed `modeRaw` such as `"not-a-number"` is publishable; `NumberFormatException` occurs only at callback parse time.
- `selectedApps` is copy-owned and immutable from the callback perspective.
- One `MainModule.mPrefs.getAll()` generation is captured per rebuild.
- Runtime state is a process-scoped singleton with one preference observer and `AtomicReference` publication.
- Runtime state does not retain `ExpandableNotificationRow`, `View`, `Activity`, or `Context` instances.

---

## 7. FAST / COMPLETE_LEGACY selection

| Condition | Path |
|---|---|
| `abi == null` | `COMPLETE_LEGACY` |
| `snapshot == null` | `COMPLETE_LEGACY` |
| `thisObject == null` | `COMPLETE_LEGACY` |
| `thisObject.javaClass !== resolutionRoot` | `COMPLETE_LEGACY` |
| `thisObject != null && thisObject.javaClass === resolutionRoot && snapshot != null` | `FAST` |

- No FAST→LEGACY retry after the FAST boundary is crossed.
- No rollback of a partial `setSystemExpanded` mutation.

---

## 8. Failure / fatal semantics

| Failure | Behavior |
|---|---|
| Malformed `modeRaw` | `NumberFormatException` thrown before `chain.proceed()`; rethrown via `XposedHelpers.throwOrReturn`. |
| `getEntry` target exception | `InvocationTargetException` → `XposedHelpers.InvocationTargetError`; `chain.proceed()` not reached. |
| Missing `mSbn` | `NoSuchFieldError`; `chain.proceed()` not reached; no COMPLETE_LEGACY retry (`getEntryCalls == 1`). |
| `getPackageName` target exception | `XposedHelpers.InvocationTargetError`; `chain.proceed()` not reached. |
| `setSystemExpanded` target exception | `InvocationTargetException` → `XposedHelpers.InvocationTargetError`; `chain.proceed()` not reached. |
| `chain.proceed()` throws | Captured and rethrown via `XposedHelpers.throwOrReturn`. |
| `IllegalAccessException` on fast field/method | Logged and rethrown as `IllegalAccessError`. |
| Fatal `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError` | Never swallowed at resolution/install/refresh/callback boundaries. |

---

## 9. Lifecycle / ownership

- `NotificationAutoExpandRuntimeState.install()` publishes the singleton at most once.
- Preference observer registered exactly once per process.
- Initial refresh runs outside the hooked `setFeedbackIcon` callback.
- Failed refresh clears the snapshot.
- Fatal refresh failure clears the snapshot and propagates.
- No retention of row, view, context, or activity instances.

---

## 10. Concurrency / publication

- `snapshotRef` is `AtomicReference<NotificationAutoExpandSnapshot?>`.
- Refresh is serialized by a private lock.
- Observer changes trigger a full rebuild for `null` key or relevant keys only (`system_expandnotifs`, `system_expandnotifs_apps`).

---

## 11. Hot-path model

The FAST path avoids in a normal callback:

- `MainModule.mPrefs` reads.
- New `findClass`/`findClassIfExists` calls or per-callback discovery of the three frozen members.
- `HashMap`, `Set`, or `ArrayList` creation.
- Normal-path logging, timestamps, or diagnostic writes.

It still uses the retained LEGACY `mSbn` and `getPackageName` helpers, which may use their existing XposedHelpers reflection caches.

---

## 12. Test evidence

All C6 tests are under `app/src/test/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/`.

| Test class | Coverage |
|---|---|
| `NotificationAutoExpandResolverTest` | Primitive `mOnKeyguard` acceptance, wrapper rejection, missing members, zero-arg `getEntry`, overloaded `getEntry` zero-arg selection, exact boxed `Boolean` overload preference, primitive-only `Boolean` compatibility, ordinary resolution failure, fatal resolution failure. |
| `NotificationAutoExpandRuntimeStateTest` | Default `modeRaw`, malformed raw preserved unparsed, copy-owned `selectedApps`, one source generation per rebuild, relevant-key filtering, `null`-key rebuild, ordinary failure clears snapshot, fatal refresh propagation, singleton/observer behavior. |
| `NotificationAutoExpandEffectTest` | FAST/LEGACY eligibility, keyguard short-circuit, modes 2/3/other, malformed mode failure, real `getEntry` target failure, missing `mSbn` failure with no-retry oracle (`NoSuchFieldError`, `getEntryCalls == 1`, `chain.proceedCount == 0`), `getPackageName` failure, `setSystemExpanded` failure, `chain.proceed` rethrow, `setSystemExpanded` at most once, exception mappings. |
| `NotificationAutoExpandHookTest` | Target class missing, ordinary probe failure isolated with `ALL_METHODS / INSTALL_FAILED` diagnostic, fatal probe failure propagation. |

Evidence classification: `RUNTIME_TESTED_COMPONENT`.
Real SystemUI callback evidence: `NOT_RUNTIME_TESTED_CALLBACK`.

---

## 13. Runtime evidence classification

```text
C6_RUNTIME_CALLBACK_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK
```

No real `ExpandableNotificationRow.setFeedbackIcon` callback or device execution was performed. All verification is `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 14. Scope

- C6-B2 is a consolidation / completion record.
- No production changes.
- No test changes.
- No C1-C5 changes.
- No shared architecture framework, manager, or generic abstraction introduced.
- No C7 or next-target selection work started.

---

## 15. Completion freeze marker

```text
C6_TARGET_SELECTION = PASS
C6_A0             = PASS
C6_B1             = PASS
C6_B2             = READY_FOR_INDEPENDENT_AUDIT

C6_RUNTIME_CALLBACK_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK
```
