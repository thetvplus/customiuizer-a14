# C5 — VolumeDialogAutohideDelay Architecture C Target Selection (Final Factual Corrective)

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**C4 final freeze SHA:** `502abe469184690925a4e0f02c5bc935f1dfcd4f`
**C5 selection final corrective base SHA:** `7aabda3e6f18fda6350479d1e8aa87ae4573b344`
**Evidence classification:** `LOCAL_EXECUTION_EVIDENCE_ONLY`

This is a documentation-only final factual corrective. No production migration is authorized.

---

## 0. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `7aabda3e6f18fda6350479d1e8aa87ae4573b344` | `git rev-parse HEAD` |
| Remote HEAD | `7aabda3e6f18fda6350479d1e8aa87ae4573b344` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Merge-base against `2d16b038...` | `7aabda3e6f18fda6350479d1e8aa87ae4573b344` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` returned empty |
| C1/C2/C3/C4 production changed | `false` | source inspection |
| C5 production started | `false` | no resolver/ABI/effect/hook changes for C5 |

START PASS.

---

## 1. SELECTION SUMMARY

**Selected C5 target remains:**

```text
TARGET_HOOK:     SystemUIControlCenterHooks.VolumeDialogAutohideDelayHook
TARGET_METHOD:   com.android.systemui.miui.volume.MiuiVolumeDialogImpl.computeTimeoutH
TARGET_DOMAIN:   Control center / volume dialog auto-hide delay
```

**Decisive reason (not operation-count based):** the hook is a narrow, read-only, single-method `before` callback. It never writes a ROM field, never calls a ROM method, and never retains a `View`, `Context`, `Window`, or dialog instance. That surface is smaller and more lifecycle-bounded than any other serious finalist. Operation counts are conditional due to short-circuit branches and a runtime safety-warning field alias fallback, and those conditional counts are not the basis for the selection.

**Decisive A0 blocker:** the legacy `mIsSafetyShowing` → `mSafetyWarning` runtime fallback is triggered by *any* non-fatal `Throwable` on the first alias, not only a missing field. How a frozen ABI can preserve this contract is `NOT_PROVEN` and must be resolved in C5-A0 before any production migration is authorized.

---

## 2. CANDIDATE INVENTORY

The following domains were inspected in the current production source at `7aabda3e...`.

| Domain | Primary hook(s) | Why inspected | Status |
|---|---|---|---|
| Volume dialog auto-hide delay | `VolumeDialogAutohideDelayHook` | Conditional `getBooleanField` / `getObjectField` + conditional `MainModule.mPrefs.getInt` on a single `computeTimeoutH` callback; unresolved safety-alias failure contract | **Finalist / selected** |
| Volume dialog background blur | `BlurVolumeDialogBackgroundHook` | Recurring `getObjectField` / `getBooleanField` + `callMethod` on volume dialog; already has a snapshot | **Finalist** |
| Drawer blur | `DrawerBlurRatioHook` | Previously deferred from C4; reassessed with current source | **Finalist** |
| Status bar digital signal | `StatusBarDigitalSignalHook` | Many `getObjectField` / `callMethod` / preference reads across multiple mobile-state callbacks | **Finalist** |
| Notification auto-expand | `ExpandNotificationsHook` | Conditional field/method access + conditional `getStringSet` on `setFeedbackIcon` | **Finalist** |
| Notification heads-up expand | `ExpandHeadsUpHook` | Similar to `ExpandNotificationsHook` but allocates `Runnable` and posts delayed callbacks | Rejected from finalist list |
| Heads-up display delay | `BetterPopupsHideDelayHook` | Constructor-only; already has preference observer and install-time int-field writes | Not a recurring hot path |
| Notification icon limit | `MaxNotificationIconsHook` | Small but writes `mMaxStaticIcons` on every `resetViewStates` and uses `intercept` | Not selected |
| Lock screen PIN scramble | `ScramblePINHook` | Runs at view inflation, not a recurring hot path | Not selected |
| Control center header | `CCHeaderHook` | Multiple dynamic method calls, `ConstraintSet` reflection, prompt state | Too broad for one Architecture C migration |
| Auto-brightness range | `AutoBrightnessRange` (SystemDisplayHooks) | Already has snapshot; `DisplayPowerController` hooks are low frequency | Not selected |

Excluded by contract (not re-opened):
- C1 `StatusBarHeight`
- C2 `SystemClock`
- C3 `Battery Style`
- C4 `HideIconsSignal`

---

## 3. FINALIST MATRIX

Ranking priority: correctness > lifecycle/ownership > concurrency/publication > behavior compatibility > hot-path cost > cold-path elegance > code size.

| # | Attribute | **VolumeDialogAutohideDelayHook** | **BlurVolumeDialogBackgroundHook** | **DrawerBlurRatioHook** | **StatusBarDigitalSignalHook** | **ExpandNotificationsHook** |
|---:|---|---|---|---|---|---|
| 1 | **Exact production hook surface** | `ModuleHelper.findAndHookMethod` of `MiuiVolumeDialogImpl.computeTimeoutH` (zero explicit parameter types) <br> `REAL_COMPUTE_TIMEOUT_H_RETURN_TYPE = NOT_PROVEN`; the module supplies `Int` values via `param.returnAndSkip` <br> `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt:122` | `findAndHookMethod` of `MiuiVolumeDialogImpl.updateDialogWindowH` and `showH` (different arg signatures) <br> `SystemUIControlCenterHooks.kt:183-214` | `findAndHookMethod` of `NotificationShadeDepthController$updateBlurCallback$1.doFrame`, `BlurUtilsExt.applyBlur`, `ControlPanelWindowManager.setBlurRatio` <br> `SystemDisplayHooks.kt:296-312` | `hookAllMethods` on `MobileStatusTracker.mCallback.onMobileStatusChanged`, `StatusBarMobileView.applyMobileState` / `updateState` / `applyDarknessInternal`, and `StatusBarIconControllerImpl.setMobileIcons` <br> `SystemUIStatusBarHooks.kt:887-996` | `hookAllMethods` on `ExpandableNotificationRow.setFeedbackIcon` <br> `SystemNotificationHooks.kt:43-70` |
| 2 | **Callback shape** | `before(param)` → `param.returnAndSkip(Int)` or fall through | `after(param)` with `Window` mutation and `startBlurAnim` calls | `intercept(chain)` → `chain.proceed()` / `chain.proceed(args)` on 3 surfaces | `before` / `after` on multiple classes; view creation and state mutation | `intercept(chain)` → `chain.proceed()` with conditional `setSystemExpanded` |
| 3 | **Callback owner** | `MiuiVolumeDialogImpl` instance | `MiuiVolumeDialogImpl` instance | `NotificationShadeDepthController$updateBlurCallback$1`, `BlurUtilsExt`, `ControlPanelWindowManager` | `MobileStatusTracker$Callback`, `StatusBarMobileView`, `StatusBarIconControllerImpl` | `ExpandableNotificationRow` |
| 4 | **Known or unknown runtime frequency** | `NOT_PROVEN`. Triggered by volume dialog timeout computations; no real-device timing available. | `NOT_PROVEN`. Triggered by volume dialog show/resize; no timing available. | `NOT_PROVEN`. `doFrame` may run during panel animations; real frequency not measured. | `NOT_PROVEN`. Mobile status and icon updates occur on signal changes; exact frequency not measured. | `NOT_PROVEN`. `setFeedbackIcon` is called per notification feedback update; frequency not measured. |
| 5 | **Recurring field/member access set** | `mHovering` (boolean), `mIsSafetyShowing` / `mSafetyWarning` (Boolean, candidate-pair fallback), `mExpanded` (boolean) | `mWindow` (Window), `mExpanded` (boolean), `startBlurAnim` method | `getAdditionalInstanceField` (cached `WeakReference`), `WeakReference.get()`, `DrawerBlurScope` `ThreadLocal`, `chain` arg access | `mSubscriptionInfo`, `signalStrength`, `getDbm`, `mState`, `mMobile`, `mobileIconState` (`visible`, `airplane`, `subId`), `mMobileTypeSingle`, `setMobileIcons` args | `mOnKeyguard` (boolean), `getEntry()` method, `mSbn` field, `getPackageName()` method, `setSystemExpanded` method |
| 6 | Conditional `XposedHelpers` or cache work per callback | **1–4 attempted field-helper calls** depending on short-circuit and alias fallback. Each attempt is a `findField` cache map lookup + `Field.get` / `Field.getBoolean` for the relevant field. `REFLECTION_BOXING_ALLOCATION = NOT_PROVEN`; no allocation is structurally proven. | `updateDialogWindowH`: 2 field reads + 1 method call. `showH`: 1 field read + 1 method call. (Conditional on which hook fires.) | `doFrame`: `getAdditionalInstanceField` + `WeakReference.get()` + `ThreadLocal` enter/exit + `chain.proceed()`. `applyBlur`/`setBlurRatio`: arg mutation + `chain.proceed()`; no field access. `findBlurUtilsExt` only on cache miss. | Many `getObjectField` / `getBooleanField` / `callMethod` per surface; `signalLevelMap` read/write. | `mOnKeyguard` always; if not keyguard, `getEntry`, `mSbn`, `getPackageName`; conditionally `setSystemExpanded`. All field/method access counts are conditional on the guard. |
| 7 | **Conditional preference / config reads per callback** | **0–1 `MainModule.mPrefs.getInt`** depending on branch: 0 if `mHovering` true; 1 if `mHovering` false and `mSafetyWarning` true; 1 if `mHovering` false and `mSafetyWarning` false (the expanded/collapsed choice is just a key-string branch inside the single `getInt`). | None in callback; uses `volumeBlurSnapshot` updated by observer. | None in callback; `drawerBlurModifierPct` is a `volatile Float` updated by observer. | Two `MainModule.mPrefs.getBoolean` per `updateState`/`applyMobileState` callback when active. | 0 if `mOnKeyguard` true; otherwise 1 `getString` + 1 `getStringSet`. |
| 8 | **Structurally provable per-invocation allocations** | `STRUCTURALLY_PROVEN_PER_CALLBACK_ALLOCATION = none`. `PrefMap.getInt` normalizes the key string; the allocation behavior of that normalization is `NOT_PROVEN`. `REFLECTION_BOXING_ALLOCATION = NOT_PROVEN`. | None structurally proven in hot path. | None structurally proven after first `WeakReference` cache. `DrawerBlurScope` `ThreadLocal` entry on each `doFrame`. | TextView and `String` building on init; `String` dBm formatting per update. | `getStringSet` returns a `Set<String>`; the `String`/`Set` may allocate depending on `PrefMap` implementation. |
| 9 | **Resolver feasibility** | **High, with an A0 blocker.** Single class `MiuiVolumeDialogImpl`; `mHovering` and `mExpanded` are unambiguous. The safety-warning field is an ordered candidate pair, but the *runtime failure contract* is not yet proven for a frozen ABI. | **High.** Single class; two fields and one method. Method overload set must be preflighted. | **Medium.** Multiple target classes; `BlurUtilsExt` discovery already has candidate-name + type fallback. The `WeakReference` cache is not a real field, so an ABI cannot fully replace it without re-design. | **Low–Medium.** Multiple resolution roots (`MobileStatusTracker$Callback`, `StatusBarMobileView`, `StatusBarIconControllerImpl`, `MobileIconState`); method and field name set is broad and the callback mutates `mobileIconState`. | **Medium.** Single class `ExpandableNotificationRow`; field `mOnKeyguard` and `mSbn`; methods `getEntry`, `getPackageName`, `setSystemExpanded` must be resolved. |
| 10 | **Frozen-ABI feasibility** | **High, if the A0 blocker is solved.** All target data is in fields; no method cache needed for the return decision. The safety-warning fallback strategy must be frozen first. | **Medium.** Fields freeze easily; `startBlurAnim` requires a frozen `Method`, and two hook points call it with different effective arguments. | **Low–Medium.** `doFrame` target uses `getSurroundingThis` to a controller, then `findBlurUtilsExt`; the BlurUtilsExt target has no stable field name. Re-architecting to a frozen ABI would require owning the BlurUtilsExt object lifecycle. | **Low.** Too many fields, methods, and classes to freeze in one C5 cycle; some state is mutated and other state is created (TextView). | **Medium.** Can freeze `mOnKeyguard`, `mSbn`; `getEntry` and `getPackageName` require method ABI; `setSystemExpanded` requires method ABI. |
| 11 | **Runtime subclass/shadowing risk** | Low. Exact-root FAST can compare `thisObject.javaClass === MiuiVolumeDialogImpl`; any other class falls through to legacy `XposedHelpers` lookup. | Low. Same root class; `Window`/`mExpanded` are inherited but declared in known class. | Medium–high. Callback is an anonymous inner class (`updateBlurCallback$1`) and the blur target is dynamically discovered; exact root is hard to pin. | Medium. `StatusBarMobileView` and `MobileIconState` have subclasses; the existing hook uses `getObjectField` with runtime class, which naturally walks the hierarchy. | Low. `ExpandableNotificationRow` exact-root FAST possible, with legacy fallback for subclasses. |
| 12 | **Lifecycle/owner-retention risk** | Very low. Effect can hold `Field` references and an immutable `Int` snapshot; no `View`, `Context`, `Activity`, `Window`, or dialog instance is retained beyond the callback frame. | Low. Effect holds `Field`/snapshot; `Window` is read from the dialog instance at callback time and never retained, but the hook does mutate it. | Medium. `WeakReference` lifecycle and `ThreadLocal` scope ownership must be preserved; the target may be re-discovered if the reference is cleared. | Medium–high. The hook creates and inserts a `TextView` and stores `signalLevelMap` in the hook closure; this is longer-lived state that does not fit the C4/Architecture C immutable Effect model. | Low. No strong references retained; the `Set<String>` snapshot is a small immutable object. |
| 13 | **Concurrency/publication risk** | Low. A `VolumeDialogAutohideDelaySnapshot` can be atomically published (e.g. `AtomicReference` or `@Volatile`); only two `Int`s. `CALLBACK_THREAD = NOT_PROVEN`, so correct atomic publication is required regardless. | Low. `VolumeBlurSnapshot` already exists and uses `volatile`. | Low. `drawerBlurModifierPct` is `volatile`; `DrawerBlurScope` is a `ThreadLocal`. | Medium. `signalLevelMap` is a non-atomic `SparseIntArray` written by one callback and read by another; publication is not explicit. | Low. A snapshot of `Int` mode + `Set<String>` apps can be atomically published. |
| 14 | **Failure/fatal compatibility risk** | Medium–Low. `rethrowIfFatal` propagates `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`. Ordinary `Throwable` on the first safety alias currently falls back to the second alias. `SAFETY_ALIAS_RUNTIME_FAILURE_CONTRACT` for a frozen ABI is `NOT_PROVEN`. `returnAndSkip` is non-mutating, so a wrong timeout is the worst visible failure. | Low. `Window` and `mExpanded` field access and `startBlurAnim` call can throw; the existing hook has no per-field fallback, so ABI-mismatch must be handled by the global exact-root/FAST gate. | Medium. `findBlurUtilsExt` swallows `Throwable` except fatal errors; silently returning null is the existing fallback. An ABI that does not match could produce a no-op blur. | Medium. Multiple surfaces and `setObjectField` / view insertion mean a missed field could corrupt mobile icon state or crash SystemUI. | Low–Medium. `setSystemExpanded` is a method call; missing it or `mSbn` is recoverable. |
| 15 | **Behavior-oracle complexity** | **Very low, if the A0 blocker is solved.** Pure function of three booleans and two preference values; exact `returnAndSkip` sequence is one short conditional chain. | Low. Two similar callbacks compute `blurRatio` from `mExpanded` and snapshot; both call `startBlurAnim`/`clearFlags` in slightly different conditions. | Medium. Three different callback shapes, dynamic target discovery, and nested `DrawerBlurScope` make the exact oracle harder to state in one Effect. | High. Combines signal-level tracking, view creation, icon state mutation, and dual-row state synchronization; oracle is multi-method and order-sensitive. | Low. Boolean decision from `mOnKeyguard`, package name, mode, and app set. |
| 16 | **Expected Architecture C reduction (conditional)** | Conditional: replaces **0–1** `MainModule.mPrefs.getInt` and **1–4** `XposedHelpers` field-helper attempts with frozen `Field.get` / `Field.getBoolean` and a single immutable snapshot reference. The actual reduction depends on short-circuit and safety-alias branch. The unresolved A0 blocker means the *safety fallback* portion of the reduction is not yet proven. | Remove up to 2 `XposedHelpers` field lookups and up to 2 `XposedHelpers.callMethod` calls per relevant callback; `VolumeBlurSnapshot` already exists, so the preference-read reduction is already achieved. | Limited. `getAdditionalInstanceField` / `WeakReference.get` remain because the target has no stable frozen field; the main win would be atomic `DrawerBlurSnapshot` publication, which already partially exists. | Large in absolute terms, but the surface is too broad for a single C5 cycle; would need multiple sub-migrations. | Remove conditional preference reads and conditional reflective method/field lookups; requires method ABI for `getEntry`, `getPackageName`, `setSystemExpanded`. |
| 17 | **Evidence quality** | `STRUCTURAL` for hook surface, field names, and preference keys; `NOT_PROVEN` for runtime frequency, field-type semantics, callback thread, and safety-alias frozen-ABI contract; `LOCAL_EXECUTION_EVIDENCE_ONLY` for start gate and validation. | `STRUCTURAL` for hook surface, fields, method name, and existing snapshot; `NOT_PROVEN` for frequency and overload set. | `STRUCTURAL` for source design; `NOT_PROVEN` for real ROM target names, `doFrame` frequency, and `BlurUtilsExt` field ownership. | `STRUCTURAL` for source shape; `NOT_PROVEN` for callback frequency, exact mobile icon state class, and `SubscriptionManager` behavior. | `STRUCTURAL` for source shape; `NOT_PROVEN` for `setFeedbackIcon` overload set, `mSbn` ownership, and callback frequency. |

---

## 4. INDEPENDENT SOURCE EVIDENCE

### 4.1 Selected target — `VolumeDialogAutohideDelayHook`

`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt:121-145`

```kotlin
fun VolumeDialogAutohideDelayHook(classLoader: ClassLoader) {
    ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "computeTimeoutH", object : MethodHook() {
        override fun before(param: BeforeHookCallback) {
            val mHovering = XposedHelpers.getBooleanField(param.getThisObject(), "mHovering")
            if (mHovering) { param.returnAndSkip(16000); return }
            val mSafetyWarning = try {
                XposedHelpers.getObjectField(param.getThisObject(), "mIsSafetyShowing") as Boolean
            } catch (e: Throwable) {
                FatalErrors.rethrowIfFatal(e)
                XposedHelpers.getObjectField(param.getThisObject(), "mSafetyWarning") as Boolean
            }
            if (mSafetyWarning) {
                val opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
                param.returnAndSkip(if (opt > 0) opt else 5000)
                return
            }
            val mExpanded = XposedHelpers.getBooleanField(param.getThisObject(), "mExpanded")
            val opt = MainModule.mPrefs.getInt(if (mExpanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed", 0)
            if (opt > 0) param.returnAndSkip(opt)
        }
    })
}
```

#### Structural branching model

The production oracle is a short-circuit chain, not a fixed sequence of operations. The exact counts per `computeTimeoutH` call depend on runtime values.

**Conditional `XposedHelpers` field-helper attempts:**

| Branch | Attempted field-helper calls |
|---|---|
| `mHovering == true` | 1 (`mHovering`) |
| `mHovering == false && mIsSafetyShowing` succeeds && `mSafetyWarning == true` | 2 (`mHovering`, `mIsSafetyShowing`) |
| `mHovering == false && mIsSafetyShowing` succeeds && `mSafetyWarning == false` | 3 (`mHovering`, `mIsSafetyShowing`, `mExpanded`) |
| `mHovering == false && mIsSafetyShowing` fails (non-fatal), `mSafetyWarning` succeeds && `mSafetyWarning == true` | 3 (`mHovering`, `mIsSafetyShowing` attempt, `mSafetyWarning`) |
| `mHovering == false && mIsSafetyShowing` fails (non-fatal), `mSafetyWarning` succeeds && `mSafetyWarning == false` | 4 (`mHovering`, `mIsSafetyShowing` attempt, `mSafetyWarning`, `mExpanded`) |

Therefore: `XPOSED_FIELD_HELPER_ATTEMPTS_PER_CALLBACK = 1–4` depending on short-circuit and alias fallback.

**Conditional preference reads:**

| Branch | `MainModule.mPrefs.getInt` calls |
|---|---|
| `mHovering == true` | 0 |
| `mHovering == false && mSafetyWarning == true` | 1 (`system_volumedialogdelay_expanded`) |
| `mHovering == false && mSafetyWarning == false` | 1 (`system_volumedialogdelay_expanded` or `system_volumedialogdelay_collapsed`, chosen by `mExpanded`) |

Therefore: `PREFERENCE_READS_PER_CALLBACK = 0–1`.

**Callback thread:**

`CALLBACK_THREAD = NOT_PROVEN`. The source does not prove the invoking thread. Any Architecture C snapshot must therefore be correctly published with `AtomicReference` or `@Volatile` regardless of the actual callback thread.

#### Failure / fatal contract

`FatalErrors.rethrowIfFatal` only propagates `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError`. All other `Throwable`s caught from the first `getObjectField("mIsSafetyShowing")` (including a missing field, `IllegalAccessException`, `IllegalArgumentException`, `ClassCastException` from `as Boolean`, or any other non-fatal `Throwable`) currently trigger a fallback to `getObjectField("mSafetyWarning")`.

This means:

- `SAFETY_ALIAS_RUNTIME_FAILURE_CONTRACT_FOR_FROZEN_ABI = NOT_PROVEN`.
- A frozen ABI cannot naively replace the `try/catch` with "resolve `mIsSafetyShowing` first, then `mSafetyWarning`" without deciding how to preserve ordinary-failure fallback semantics.
- The A0 preflight must freeze one behavior-compatible strategy before production implementation is authorized.

Key structural observations:
- The hook is `before` on a single `computeTimeoutH` method with zero explicit parameter types and only ever calls `param.returnAndSkip(Int)` or falls through. `REAL_COMPUTE_TIMEOUT_H_RETURN_TYPE = NOT_PROVEN`; compatibility of the module's `Int` return value with the real method is an A0 obligation.
- It does **not** modify any ROM object field, call any ROM method, or retain any `View`/`Context`.
- The `mIsSafetyShowing` vs `mSafetyWarning` ambiguity is already handled by a runtime `try/catch` fallback; a cold Resolver can *discover* which field exists, but how to preserve the runtime failure fallback is an open A0 question.

### 4.2 Strongest alternative — `BlurVolumeDialogBackgroundHook`

`SystemUIControlCenterHooks.kt:183-214`

- Already has `VolumeBlurSnapshot` and a preference observer (`refreshVolumeBlurSnapshot()`).
- Remaining hot-path reflection per callback is conditional on which hook fires:
  - `updateDialogWindowH`: `mWindow` field, `mExpanded` field, `startBlurAnim` method.
  - `showH`: `mWindow` field, `startBlurAnim` method.
- Why it is still #2: it is already partially optimized (snapshot), lives on the same `MiuiVolumeDialogImpl` class, and has no unresolved runtime failure fallback. However, it is not read-only (it mutates `Window` flags and calls a ROM method) and it requires a method ABI, which keeps its correctness and behavior-compatibility risk above the selected target.

### 4.3 Drawer blur reassessment

`SystemDisplayHooks.kt:190-312`

Verified current design:
- A volatile `drawerBlurModifierPct` value is updated by a preference observer.
- `DrawerBlurScope` is a `ThreadLocal`.
- The blur target is stored as an additional-instance `WeakReference`; it is re-discovered on cache miss.
- `findBlurUtilsExt` walks candidate field names, then declared fields, then the whole class hierarchy; this is a cold path.
- `doFrame` steady-state does `getAdditionalInstanceField`, `WeakReference.get()`, `ThreadLocal` enter/exit, and `chain.proceed()`.
- No stable, name-known field is proven for the blur target.
- Real `doFrame` frequency is `NOT_PROVEN`; do not assume it runs every frame.

Conclusion for C5: the remaining hot-path work is not easily replaced by a frozen `Field` ABI because the blur target is not stored in a stable, name-known field. The lifecycle and discovery risks are still higher than the selected target, and the expected reduction is smaller.

### 4.4 `StatusBarDigitalSignalHook`

`SystemUIStatusBarHooks.kt:887-996`

- Spans four distinct hook surfaces, multiple classes, view creation, and `mobileIconState` mutation.
- `SparseIntArray` signal level map is held in the hook closure and written by one callback while read by another.
- While it has many `XposedHelpers` accesses, the surface is too broad for a single C5 cycle and the behavior oracle is multi-step and order-sensitive.
- Conclusion: deferred to a later Architecture C cycle.

### 4.5 `ExpandNotificationsHook`

`SystemNotificationHooks.kt:43-70`

- Single class `ExpandableNotificationRow`, method `setFeedbackIcon`.
- Conditional per callback: `mOnKeyguard` first; if not keyguard, `getEntry()`, `mSbn`, `getPackageName()`, `MainModule.mPrefs.getString` + `getStringSet`, and conditional `setSystemExpanded`.
- Requires a method ABI for `getEntry`, `getPackageName`, and `setSystemExpanded`, plus a `Set<String>` snapshot.
- Conclusion: bounded, but more complex and riskier than `VolumeDialogAutohideDelayHook` due to the `Set` snapshot, multiple method calls, and `intercept` shape.

---

## 5. SELECTED TARGET AND REJECTION REASONING

### 5.1 Selected: `VolumeDialogAutohideDelayHook`

It remains the selected target, but the decisive reason is now stated correctly.

| Axis | Why it still wins |
|---|---|
| **Correctness** | The hook is a pure timeout function of three booleans and two ints. The only open correctness issue is the safety-alias runtime failure contract, which is an explicit A0 blocker. |
| **Lifecycle / ownership** | The Effect can be a hook-local immutable object that owns only `Field` references and a snapshot supplier. No `View`, `Context`, `Activity`, `Window`, or dialog controller is retained. |
| **Concurrency / publication** | The snapshot is two `Int`s; atomic publication is trivial. No shared mutable `signalLevelMap`, `WeakReference`, or `ThreadLocal`. No UI-thread assumption is required. |
| **Behavior compatibility** | The legacy fallback is exactly the current `XposedHelpers` path. The resolver only needs to try two field names for the same semantic value, but *how* to preserve the ordinary-failure fallback is an A0 blocker. |
| **Hot-path cost** | Conditional: replaces up to 1 `PrefMap.getInt` and up to 4 `XposedHelpers` field-helper attempts with frozen `Field` access and a single snapshot reference. Operation counts are not the primary selection reason. |
| **Cold-path elegance** | One resolver, one ABI, one effect, one snapshot, one runtime-state object, all in a single existing file. |
| **Code size** | Narrow scope: one class, one method, three fields, two preferences. |

**Primary selection statement (not based on counts):** the hook is the only finalist that is a single `before` callback, read-only, returns a value, and touches no ROM method, no ROM field write, and no lifecycle-carrying object. That surface is smaller than `BlurVolumeDialogBackgroundHook` (which mutates `Window` and calls a method), `DrawerBlurRatioHook` (which has dynamic discovery and a `ThreadLocal`/`WeakReference` lifecycle), `StatusBarDigitalSignalHook` (which creates `View`s and mutates state across four surfaces), and `ExpandNotificationsHook` (which requires a `Set<String>` snapshot and three method resolutions).

### 5.2 Rejected / deferred finalists

- **`BlurVolumeDialogBackgroundHook`**: #2. Already has a snapshot and no unresolved safety-alias fallback, but it is not read-only and requires a method ABI. The `Window` mutation and `startBlurAnim` call keep its behavior-compatibility risk above the selected target.
- **`DrawerBlurRatioHook`**: #3. Already has `ThreadLocal`, `WeakReference` cache, and volatile preference state. Dynamic discovery and lack of a stable name-known field mean an Architecture C frozen ABI would not significantly reduce steady-state cost.
- **`StatusBarDigitalSignalHook`**: #4. Too broad; four surfaces, view creation, and state mutation. Deferred to a later cycle.
- **`ExpandNotificationsHook`**: #5. Bounded but requires a `Set<String>` snapshot and three method resolutions. The `Set` snapshot and `intercept` shape make it riskier than the selected target.

---

## 6. EXPLICIT `NOT_PROVEN` ITEMS

The following facts are **not** structurally or runtime proven in this document:

- `REAL_CALLBACK_FREQUENCY` for `MiuiVolumeDialogImpl.computeTimeoutH`, volume blur, drawer blur, status-bar digital signal, or `ExpandableNotificationRow.setFeedbackIcon`.
- `REAL_COMPUTE_TIMEOUT_H_RETURN_TYPE` — the source only proves that `ModuleHelper.findAndHookMethod` is called with class `MiuiVolumeDialogImpl`, method name `computeTimeoutH`, and zero explicit parameter types. The real `Method.returnType`, whether overloads exist, and whether `returnAndSkip(Int)` is compatible with the real method are `NOT_PROVEN`.
- `REAL_HYPEROS_FIELD_TYPE_FOR_SAFETY_WARNING` — the source treats it as a `Boolean` object via `getObjectField(...) as Boolean`, but the real ROM could use `boolean` primitive; the effect must handle both.
- `REAL_MISAFETYSHOWING_VS_MSAFETYWARNING_PREVALENCE` — which ROM variant uses which field is unknown; the resolver must try both.
- `MIUIVOLUMEDIALOGIMPL_SUBCLASS_BEHAVIOR` — whether HyperOS subclasses `MiuiVolumeDialogImpl` and/or shadows these fields is `NOT_PROVEN`. Exact-root FAST with legacy fallback is the conservative choice.
- `CALLBACK_THREAD` for `computeTimeoutH` — the invoking thread is `NOT_PROVEN`.
- `SAFETY_ALIAS_RUNTIME_FAILURE_CONTRACT_FOR_FROZEN_ABI` — how to preserve the legacy `mIsSafetyShowing` ordinary-failure → `mSafetyWarning` fallback in a frozen ABI is `NOT_PROVEN`.
- `PERFORMANCE_GAIN_PERCENTAGE_OR_LATENCY` — no timing, allocation counts, or device measurements are stated.

---

## 7. PROPOSED C5-A0 PREFLIGHT SCOPE

A future `C5_A0_PREFLIGHT.md` should fully define, without writing production code, the following frozen contracts.

### 7.1 A0 blocker: safety-alias runtime failure contract

The following must be frozen in A0 before implementation:

> How can Architecture C preserve the legacy runtime fallback
> `mIsSafetyShowing` ordinary failure → `mSafetyWarning`
> without changing fatal semantics, changing ordinary failure semantics, retrying incorrectly after an irreversible FAST operation, or changing runtime-class/shadowing behavior?

**Candidate strategies that A0 may analyze (not prescribed):**

- Strategy A: Resolver discovers exactly one of `mIsSafetyShowing` or `mSafetyWarning` at cold time, installs it into the ABI, and the FAST effect uses only that field. The legacy path is kept for non-exact-root classes and reproduces the original `try/catch` fallback.
- Strategy B: Resolver resolves both field names into the ABI, and the FAST effect attempts the first `Field.get`; a non-fatal failure causes it to attempt the second. This preserves the runtime fallback but may retain a catch on the hot path.
- Strategy C: The resolver treats the field type itself as part of the ABI: if the field is a `Boolean` object, read and unbox it; if it is primitive `boolean`, read it directly. The fallback is decided at install time, not at callback time.
- Strategy D: Keep a complete legacy path (no FAST) for any class where the safety-warning field cannot be unambiguously resolved, and only enable FAST when the resolver can prove a single, stable field.

A0 must select and freeze exactly one behavior-compatible strategy. Target selection does not prescribe the choice.

### 7.2 Other A0 contracts

1. **Exact hook surface**
   - `HOOK_TARGET_NAME = computeTimeoutH` on `com.android.systemui.miui.volume.MiuiVolumeDialogImpl`.
   - `HOOK_TARGET_PARAMETER_SHAPE = zero explicit parameter types` (the `ModuleHelper.findAndHookMethod` call provides no parameter-type varargs).
   - `REAL_COMPUTE_TIMEOUT_H_RETURN_TYPE = NOT_PROVEN`; A0 must resolve the real `Method.returnType` and verify that `BeforeHookCallback.returnAndSkip(Int)` is compatible with it.
   - `LEGACY_HOOK_RESULT_VALUE = Int` values are supplied by the module via `param.returnAndSkip(Int)`.
   - Confirm `ModuleHelper.findAndHookMethod` semantics for this single method.
   - Confirm `BeforeHookCallback` / `returnAndSkip(Int)` behavior and the exact `MethodHook` priority.
   - Freeze behavior if the resolved method is absent or its ABI is incompatible (e.g. resolver returns `null` and the hook falls back to legacy, or the feature is marked failed).

2. **Legacy callback oracle**
   - Preserve the exact short-circuit order: `mHovering` → safety warning (`mIsSafetyShowing` / `mSafetyWarning`) → `mExpanded`.
   - Preserve the timeout constants: `16000`, `5000`.
   - Preserve the preference keys: `system_volumedialogdelay_expanded`, `system_volumedialogdelay_collapsed`.
   - Preserve the fall-through-to-original behavior when the selected preference value is `0`.

3. **Resolver contract**
   - Resolve `com.android.systemui.miui.volume.MiuiVolumeDialogImpl` as the single resolution root.
   - Resolve `mHovering` and `mExpanded` as primitive `boolean` fields.
   - Resolve the safety-warning field as an ordered candidate list: `["mIsSafetyShowing", "mSafetyWarning"]` with type `Boolean`.
   - Define failure mode: if the resolution root or any required field is missing, Resolver returns `null` and the hook installs a complete legacy path.
   - Define exact-root FAST eligibility: `thisObject != null && thisObject.javaClass === resolutionRootClass`.

4. **Frozen-ABI contract**
   - Data class with `resolutionRootClass: Class<*>` and `Field` references for `mHovering`, `safetyWarning`, and `mExpanded`.
   - `safetyWarning` reader must unbox a `Boolean` object, or a primitive `boolean` if the field is `Boolean.TYPE`.

5. **Typed config / snapshot contract**
   - `VolumeDialogAutohideDelaySnapshot(expanded: Int, collapsed: Int)`.
   - `VolumeDialogAutohideDelayRuntimeState` with `AtomicReference<VolumeDialogAutohideDelaySnapshot?>` and a `PreferenceObserver`.
   - `currentOrBuildVolumeDialogAutohideDelaySnapshot()` must be the only hot-path reader of `MainModule.mPrefs` for these keys.

6. **Effect contract**
   - `VolumeDialogAutohideDelayEffect(abi, snapshotProvider)`.
   - `before(param)` reads `thisObject`, applies the frozen decision chain, and calls `param.returnAndSkip(timeout)` or returns.
   - No ROM field writes, no `Context`/`View` capture, no `callMethod`.

7. **Failure / fatal compatibility**
   - `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError` must propagate.
   - Non-fatal reflection errors must fall back to legacy at the correct boundary, as determined by the A0 blocker strategy.

8. **Testability**
   - Resolver tests using a fake `MiuiVolumeDialogImpl` fixture with both `mIsSafetyShowing` and `mSafetyWarning` variants.
   - Effect tests using a fake `BeforeHookCallback` that records `returnAndSkip` values.
   - Snapshot tests verifying that preference observer updates produce a new snapshot.

---

## 8. VALIDATION

| Command | Result | Evidence |
|---|---|---|
| `git diff --check` | pass | exit code 0, no output |
| `python tools/check_document_contracts.py` | pass | exit code 0, output: "Document contract checks pass." |
| `python tools/verify.py full` | **not run** | This is a documentation-only change; no production or test files were modified, so Android compilation is not in scope per project rules. |

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 9. SUBMISSION FIELDS

| Field | Value |
|---|---|
| Base SHA | `7aabda3e6f18fda6350479d1e8aa87ae4573b344` |
| Final SHA | *(to be recorded after commit and push)* |
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Changed files | `docs/architecture-c/C5_TARGET_SELECTION.md` |
| Production changed | `false` |
| Tests changed | `false` |
| Docs changed | `true` |

---

C5_TARGET_SELECTION_CORRECTIVE_READY_FOR_INDEPENDENT_AUDIT
