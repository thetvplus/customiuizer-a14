# C7 — Architecture C Target Selection (Termination Freeze)

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**C7 selection base SHA:** `2a76bee0068110dcb6b0f771a3d14004ad7dd4bc`
**Evidence classification:** `STRUCTURAL`, `LOCAL_EXECUTION_EVIDENCE_ONLY`, `NOT_RUNTIME_TESTED_CALLBACK`, `NOT_PROVEN`

> **C7_TARGET_SELECTION_AUTHORIZATION = YES**
> **C7_A0_AUTHORIZATION = NO**
> **C7_PRODUCTION_AUTHORIZATION = NO**

This is a documentation-only target-selection freeze. It does not start A0, does not modify production code, does not modify tests, does not touch C1-C6 historical documents, and does not auto-chain into implementation.

---

## 0. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `2a76bee0068110dcb6b0f771a3d14004ad7dd4bc` | `git rev-parse HEAD` |
| Remote HEAD | `2a76bee0068110dcb6b0f771a3d14004ad7dd4bc` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Merge-base | `2a76bee0068110dcb6b0f771a3d14004ad7dd4bc` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` returned empty |
| Selection base drift | `C7_SELECTION_BASE_DRIFT = false` | remote HEAD matches expected base SHA |
| C1-C6 reopened | `false` | no resolver/ABI/effect/hook changes in this work |
| C7-A0 preflight started | `false` | no A0 preflight document or production code created |
| C7 production started | `false` | no resolver/ABI/snapshot/runtime/effect/hook class created |

START PASS.

---

## 1. SELECTION SUMMARY

```text
C7_TARGET_SELECTION = NO_SUITABLE_TARGET
```

The five Gatekeeper-ranked candidates were reconstructed from the exact C7 selection base. Independent source inspection confirms the Gatekeeper facts but does **not** find any new structural evidence that would justify an Architecture C production migration. The remaining marginal hot-path reduction for each candidate is smaller than the resolver / runtime / publication / test / compatibility cost of a full Architecture C cycle.

The pre-screened rejection `DrawerBlurRatioHook` is recorded separately because it already has an Architecture C-shaped implementation (volatile snapshot, `ThreadLocal` scope, `WeakReference` target cache) and is therefore not a remaining migration target.

---

## 2. EXISTING INFRASTRUCTURE COST (must be considered in benefit assessment)

Architecture C benefits must be evaluated as **marginal hot-path reduction** over the existing warm caches, not as a removal of "full reflection / full preference parse" on every callback.

### 2.1 PrefMap

`app/src/main/java/tv/withaibuild/customiuizer/utils/PrefMap.kt`

```text
private val parsedIntCache = ConcurrentHashMap<String, CachedInt>()
private val snapshot = AtomicReference<Map<String, Any>>(emptyMap())
```

- The published snapshot is an immutable `Map<String, Any>` behind an `AtomicReference` (`PrefMap.kt:25`).
- `currentSnapshot()` is `snapshot.get()` and a single map lookup (`PrefMap.kt:27-31`).
- `getStringAsInt` first reads the snapshot, then, for `String` values, probes `parsedIntCache` and stores the parse result keyed by the raw string (`PrefMap.kt:148-160`). A repeated identical string therefore costs one map lookup, not a parse.
- `getStringSet` returns the stored `Set<String>` or `Collections.emptySet()`; it does **not** copy the set on every call (`PrefMap.kt:162-166`).

`MainModule.mPrefs` is a `PrefMap`:

```text
app/src/main/java/tv/withaibuild/customiuizer/MainModule.java:47
public static final PrefMap mPrefs = new PrefMap();
```

### 2.2 XposedHelpers

`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java`

```text
private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> fieldCache = new ConcurrentHashMap<>();
private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> noArgMethodCache = new ConcurrentHashMap<>();
private static final ConcurrentHashMap<MemberCacheKey.Method, Object> methodCache = new ConcurrentHashMap<>();
private static final ConcurrentHashMap<ClassCacheKey, Object> classCache = new ConcurrentHashMap<>();
```

- `findField` first probes the class -> field nested `ConcurrentHashMap`; on a miss it walks the class hierarchy and caches the `Field` (`XposedHelpers.java:515-527`).
- `findMethodBestMatch` with no arguments probes `noArgMethodCache` by name; on a miss it falls back to `findMethodExact` / `methodCache` (`XposedHelpers.java:771-787`).
- `getIntField`, `setIntField`, `getObjectField`, and no-argument `callMethod` therefore perform a warm cache lookup plus the reflective `Field.get*` / `Method.invoke` execution. They do **not** rescan the hierarchy on every call.

This means the hot-path cost of the legacy callbacks is already: a few `ConcurrentHashMap` lookups + one or two reflective `get` / `invoke` operations + the actual observable mutation (field write, list add/remove, `Handler` post, vibration). Any Architecture C shape must prove it can materially reduce this *marginal* cost.

---

## 3. PRE-SCREENED REJECTION (not a candidate)

`DrawerBlurRatioHook` is **not** one of the five final C7 candidates because it already uses an Architecture C-style shape.

`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt:124-313`

- `drawerBlurModifierPct` is a `@Volatile` snapshot of the single `system_drawer_blur` preference (`SystemDisplayHooks.kt:165-171`). The hot `doFrame` / `applyBlur` / `setBlurRatio` callbacks read the snapshot, not the `PrefMap`.
- `DrawerBlurScope` is a `ThreadLocal<State>` with reusable state (`SystemDisplayHooks.kt:124-158`), so steady-state `doFrame` calls allocate nothing.
- `resolveDrawerBlurTargetRef` caches a `WeakReference<Any>` per callback instance in `XposedHelpers.getAdditionalInstanceField` (`SystemDisplayHooks.kt:209-233`).

Because the blur hook already applies volatile snapshot + `ThreadLocal` + `WeakReference` caching, it is a pre-screened rejection: there is no remaining Architecture C migration value for this feature.

---

## 4. CANDIDATE RECONSTRUCTIONS

All snippets are taken from the exact C7 selection base (`2a76bee0068110dcb6b0f771a3d14004ad7dd4bc`). No code was changed.

---

### 4.1 `MaxNotificationIconsHook`

| Fact | Value | Evidence |
|---|---|---|
| Source file | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | `SystemNotificationHooks.kt:488-512` |
| Hook function | `SystemNotificationHooks.MaxNotificationIconsHook` | `SystemNotificationHooks.kt:488` |
| ROM class | `com.android.systemui.statusbar.phone.NotificationIconContainer` | `SystemNotificationHooks.kt:489` |
| Hook method | `resetViewStates` | `SystemNotificationHooks.kt:489` |
| Hook API surface | `PackageReadyParam` in `com.android.systemui` | `SystemNotificationHooks.kt:488` |
| Domain | Status bar notification icon count limit | `system_maxsbicons` |

#### Exact source

```kotlin
// app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt:488-512
    @JvmStatic
    fun MaxNotificationIconsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationIconContainer", lpparam.classLoader, "resetViewStates", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    var opt = MainModule.mPrefs.getStringAsInt("system_maxsbicons", 0)
                    val maxIcons = XposedHelpers.getIntField(thisObject, "mMaxStaticIcons")
                    opt = if (opt == -1) 999 else opt
                    if (opt != maxIcons && maxIcons != 0) {
                        XposedHelpers.setIntField(thisObject, "mMaxStaticIcons", opt)
                        XposedHelpers.setIntField(thisObject, "mMaxIconsOnLockscreen", opt)
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }
```

#### Reconstruction

| Aspect | Finding |
|---|---|
| Callback order | Custom work first, then `chain.proceed()` |
| `chain.proceed` order | After conditional field writes (`SystemNotificationHooks.kt:504`) |
| Preference reads | 1 (`getStringAsInt("system_maxsbicons", 0)`), returns `Int` |
| Parsing | No explicit parse in the callback; `getStringAsInt` uses the snapshot + `parsedIntCache` |
| Field helper calls | 1 `getIntField`, up to 2 `setIntField` |
| Method helper calls | none in the callback body |
| Collection lookup | none |
| Explicit allocation | none in the callback body |
| Mutation | `mMaxStaticIcons` and `mMaxIconsOnLockscreen` written conditionally before `chain.proceed()` |
| Failure boundary | One `try` wraps custom work and `chain.proceed()`. If custom work throws, `chain.proceed()` is **not** called and the captured `Throwable` is rethrown. If `chain.proceed()` throws, it is captured and rethrown. |
| Fatal behavior | `throwOrReturn` rethrows any captured `Throwable`, including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError`. No `FatalErrors.rethrowIfFatal` call. |
| Ownership / retained refs | `thisObject` is borrowed; no per-callback cache, no retained `View`, `Context`, `Activity`, or controller. |
| Publication requirements | A single `Int` for `system_maxsbicons` (or a typed `MaxSbIconsSnapshot`). |
| Possible Architecture C shape | Pre-resolve `mMaxStaticIcons` / `mMaxIconsOnLockscreen` `Field`s at install time; publish `system_maxsbicons` as a typed `Int`; the hot callback reads the snapshot and writes the pre-resolved fields. If the exact class and field modifiers are known, the reflective `Field` lookup can be removed. |
| FAST / LEGACY feasibility | FAST is possible if the exact class and fields are resolvable; otherwise LEGACY. The failure boundary is simple (proceed only after mutation). |
| Compatibility risk | Low-to-moderate. Field names are ROM-specific and may move or be shadowed by subclasses. `resetViewStates` may be called on subclasses with a different field layout. |
| Expected marginal benefit | Low-to-moderate. Saves one `getStringAsInt` snapshot + `parsedIntCache` lookup and three `XposedHelpers` cache lookups per callback. The reflective `Field.getInt` / `Field.setInt` calls would remain unless the exact class is known and accessible. |

`CALLBACK_FREQUENCY_EVIDENCE = NOT_PROVEN`
`NOT_RUNTIME_TESTED_CALLBACK`

---

### 4.2 `BetterPopupsAllowFloatHook`

| Fact | Value | Evidence |
|---|---|---|
| Source file | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | `SystemWindowHooks.kt:232-260` |
| Hook function | `SystemWindowHooks.BetterPopupsAllowFloatHook` | `SystemWindowHooks.kt:232` |
| ROM class | `com.android.systemui.statusbar.notification.row.MiuiExpandableNotificationRow` | `SystemWindowHooks.kt:233` |
| Hook method | `updateMiniWindowBar` | `SystemWindowHooks.kt:233` |
| Hook API surface | `PackageReadyParam` in `com.android.systemui` | `SystemWindowHooks.kt:232` |
| Domain | Floating-window / popup allow list for per-app notifications | `system_betterpopups_allowfloat_apps`, `system_betterpopups_allowfloat_apps_black` |

#### Exact source

```kotlin
// app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt:232-260
    @JvmStatic
    fun BetterPopupsAllowFloatHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiExpandableNotificationRow", lpparam.classLoader, "updateMiniWindowBar", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val pkgName = XposedHelpers.callMethod(thisObject, "getMiniWindowTargetPkg") as String
                    val selectedApps = MainModule.mPrefs.getStringSet("system_betterpopups_allowfloat_apps")
                    val selectedAppsBlack = MainModule.mPrefs.getStringSet("system_betterpopups_allowfloat_apps_black")
                    val mAppMiniWindowManager = XposedHelpers.callMethod(thisObject, "getMAppMiniWindowManager")
                    val notificationSettingsManager = XposedHelpers.getObjectField(mAppMiniWindowManager, "notificationSettingsManager")
                    val mAllowNotificationSlide = XposedHelpers.getObjectField(notificationSettingsManager, "mAllowNotificationSlide") as List<String>
                    if (selectedApps?.contains(pkgName) == true) {
                        mAllowNotificationSlide.add(pkgName)
                    } else if (selectedAppsBlack?.contains(pkgName) == true) {
                        mAllowNotificationSlide.remove(pkgName)
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }
```

#### Reconstruction

| Aspect | Finding |
|---|---|
| Callback order | Custom work first, then `chain.proceed()` |
| `chain.proceed` order | After the `mAllowNotificationSlide` list mutation (`SystemWindowHooks.kt:252`) |
| Preference reads | 2 (`getStringSet` for allow and black app sets) |
| Parsing | none |
| Field helper calls | 2 (`getObjectField` on `notificationSettingsManager` and `mAllowNotificationSlide`) |
| Method helper calls | 2 no-argument `callMethod` (`getMiniWindowTargetPkg`, `getMAppMiniWindowManager`) |
| Collection lookup | `Set.contains` on two sets, then `List.add` or `List.remove` on a ROM list |
| Explicit allocation | none in the callback body (the two `Set` references come from `PrefMap`; the `List` is a ROM object) |
| Mutation | `mAllowNotificationSlide.add(pkgName)` or `mAllowNotificationSlide.remove(pkgName)` before `chain.proceed()`. Repeated callbacks for the same selected package may call `add` repeatedly, which can insert duplicates. `remove` removes the first occurrence. Architecture C must preserve this exact behavior. |
| Failure boundary | One `try` wraps custom work and `chain.proceed()`. If custom work throws, `chain.proceed()` is not called and the `Throwable` is rethrown. If `chain.proceed()` throws, it is captured and rethrown. |
| Fatal behavior | `throwOrReturn` rethrows any captured `Throwable`, including fatal errors. No `FatalErrors.rethrowIfFatal` call. |
| Ownership / retained refs | `thisObject`, `mAppMiniWindowManager`, `notificationSettingsManager`, and `mAllowNotificationSlide` are borrowed. No per-callback cache or retained controller. |
| Publication requirements | Two immutable / copy-owned `Set<String>` snapshots: allow and black app sets. |
| Possible Architecture C shape | Pre-resolve the two no-argument methods and the two `Field`s; publish the two `Set<String>` values; the hot callback reads the typed snapshot and performs the same `List.add`/`remove` on the resolved ROM list. If the exact `MiuiExpandableNotificationRow` / `MAppMiniWindowManager` / `NotificationSettingsManager` classes and the list field are resolvable, the reflective calls can be removed. |
| FAST / LEGACY feasibility | Possible FAST path if the method / field ABI is resolvable; otherwise LEGACY. The `List.add`/`remove` order and duplicate semantics must be preserved exactly. |
| Compatibility risk | Moderate. `getMAppMiniWindowManager`, `notificationSettingsManager`, and `mAllowNotificationSlide` may be on subclasses or renamed in ROM variants. The `List` identity and membership semantics are observable and must be preserved. |
| Expected marginal benefit | Low-to-moderate. Saves two `getStringSet` snapshot map lookups and four `XposedHelpers` cache lookups per callback. The `Set.contains` and `List.add`/`remove` costs remain. |

`CALLBACK_FREQUENCY_EVIDENCE = NOT_PROVEN`
`NOT_RUNTIME_TESTED_CALLBACK`

---

### 4.3 `NotificationImportanceHook`

| Fact | Value | Evidence |
|---|---|---|
| Source file | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | `SystemUINotificationHooks.kt:118-136` |
| Hook function | `SystemUINotificationHooks.NotificationImportanceHook` | `SystemUINotificationHooks.kt:118` |
| ROM class | `com.android.systemui.statusbar.phone.NotificationIconAreaController` | `SystemUINotificationHooks.kt:119` |
| Hook method | `updateStatusBarIcons` | `SystemUINotificationHooks.kt:119` |
| Hook API surface | `PackageReadyParam` in `com.android.systemui` | `SystemUINotificationHooks.kt:118` |
| Domain | Filter status bar notification icons by per-entry importance | none (no preference read; filters by `getImportance() > 1`) |

#### Exact source

```kotlin
// app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt:118-136
    @JvmStatic
    fun NotificationImportanceHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationIconAreaController", lpparam.classLoader, "updateStatusBarIcons", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mNotificationEntries = XposedHelpers.getObjectField(param.getThisObject(), "mNotificationEntries") as? List<Any> ?: return
                if (mNotificationEntries.isNotEmpty()) {
                    val arrayList = ArrayList<Any>()
                    for (item in mNotificationEntries) {
                        val notifyEntry = XposedHelpers.callMethod(item, "getRepresentativeEntry")
                        val importance = XposedHelpers.callMethod(notifyEntry, "getImportance") as Int
                        if (importance > 1) {
                            arrayList.add(item)
                        }
                    }
                    if (arrayList.size != mNotificationEntries.size) {
                        XposedHelpers.setObjectField(param.getThisObject(), "mNotificationEntries", arrayList)
                    }
                }
            }
        })
    }
```

#### Reconstruction

| Aspect | Finding |
|---|---|
| Callback order | `before` callback; custom work runs before the original method. `chain.proceed()` is implicit and happens after `before` unless `param.returnAndSkip()` is called, which is **not** called here. |
| `chain.proceed` order | Implicit after `before`. The callback does not explicitly call `proceed`. |
| Preference reads | **0** — this callback reads no `PrefMap` values. |
| Parsing | none |
| Field helper calls | 1 (`getObjectField` for `mNotificationEntries`), plus up to 1 `setObjectField` if the filtered list differs. |
| Method helper calls | 2 no-argument `callMethod`s per list item (`getRepresentativeEntry`, `getImportance`) |
| Collection lookup | Iteration over `mNotificationEntries`; `ArrayList` add; size comparison. |
| Explicit allocation | `ArrayList<Any>()` is allocated on every callback where `mNotificationEntries` is non-empty, even if all entries pass the filter and the field is not replaced. |
| Mutation | `mNotificationEntries` is replaced with the filtered `ArrayList` when the sizes differ. |
| Failure boundary | `MethodHook.before` is wrapped by `beforeHook` (`HookerClassHelper.kt:203-215`). Non-fatal throwables from `before` are logged and the original still proceeds. `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError` are rethrown before the catch. |
| Fatal behavior | The `HookerClassHelper` wrapper rethrows `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError`; other throwables are logged and original proceeds. |
| Ownership / retained refs | `param.getThisObject()` and the entries are borrowed. The new `ArrayList` is passed to ROM and not retained by the module. |
| Publication requirements | none (no preferences involved). |
| Possible Architecture C shape | Pre-resolve `mNotificationEntries`, and the two no-argument methods, at install time. The callback still needs to traverse the list, build the filtered list, and optionally replace the field. |
| FAST / LEGACY feasibility | FAST could remove the `XposedHelpers` cache lookups, but it cannot remove the `ArrayList` allocation or the O(N) traversal without a filter-algorithm rewrite. That rewrite is out of scope for target selection. |
| Compatibility risk | Moderate. `NotificationIconAreaController`, `mNotificationEntries`, `getRepresentativeEntry`, and `getImportance` are ROM-specific and may vary. The `ArrayList` allocation and `mNotificationEntries` replacement are observable to the original method. |
| Expected marginal benefit | Low. Saves the field / method cache lookups, but the dominant costs are the per-item reflection (`getRepresentativeEntry`, `getImportance`) and the mandatory `ArrayList` allocation + O(N) traversal. Pre-resolving the methods removes only the cache map lookup, not the `Method.invoke` or the allocation. |

`CALLBACK_FREQUENCY_EVIDENCE = NOT_PROVEN`
`NOT_RUNTIME_TESTED_CALLBACK`

---

### 4.4 `AutoDismissExpandedPopupsHook`

| Fact | Value | Evidence |
|---|---|---|
| Source file | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | `SystemNotificationHooks.kt:515-586` |
| Hook function | `SystemNotificationHooks.AutoDismissExpandedPopupsHook` | `SystemNotificationHooks.kt:515` |
| ROM surfaces | `com.android.systemui.statusbar.phone.HeadsUpManagerPhone$HeadsUpEntryPhone.updateEntry(Boolean)` + `com.android.systemui.statusbar.phone.StatusBarNotificationPresenter.onExpandClicked` | `SystemNotificationHooks.kt:516` and `SystemNotificationHooks.kt:549` |
| Hook API surface | `PackageReadyParam` in `com.android.systemui` | `SystemNotificationHooks.kt:515` |
| Domain | Auto-dismiss pinned expanded heads-up popups after a custom delay | no preferences; fixed 4500ms / 10000ms delays |

#### Exact source

```kotlin
// app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt:515-586
    @JvmStatic
    fun AutoDismissExpandedPopupsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.HeadsUpManagerPhone\$HeadsUpEntryPhone", lpparam.classLoader, "updateEntry", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val headsUpEntry = thisObject
                    val expanded = XposedHelpers.getBooleanField(headsUpEntry, "expanded")
                    val remoteInputActive = XposedHelpers.getBooleanField(headsUpEntry, "remoteInputActive")
                    val mEntry = XposedHelpers.getObjectField(headsUpEntry, "mEntry")
                    val rowPinned = XposedHelpers.callMethod(mEntry, "isRowPinned") as Boolean
                    if (expanded && rowPinned && !remoteInputActive) {
                        val headsUpManagerPhone = XposedHelpers.getSurroundingThis(headsUpEntry)
                        val mHandler = XposedHelpers.getObjectField(headsUpManagerPhone, "mHandler") as Handler
                        val mRemoveAlertRunnable = XposedHelpers.getObjectField(headsUpEntry, "mRemoveAlertRunnable") as Runnable
                        val extended = XposedHelpers.getBooleanField(headsUpEntry, "extended")
                        mHandler.removeCallbacks(mRemoveAlertRunnable)
                        mHandler.postDelayed(mRemoveAlertRunnable, if (extended) 10000L else 4500L)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarNotificationPresenter", lpparam.classLoader, "onExpandClicked", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject
                    val args = chain.args

                    val expanded = args[1] as Boolean
                    val mKeyguardStateController = XposedHelpers.getObjectField(thisObject, "mKeyguardStateController")
                    val mShowing = XposedHelpers.getBooleanField(mKeyguardStateController, "mShowing")
                    if (expanded && !mShowing) {
                        val headsUpManagerPhone = XposedHelpers.getObjectField(thisObject, "mHeadsUpManager")
                        val headsUpEntry = XposedHelpers.callMethod(headsUpManagerPhone, "getHeadsUpEntry", XposedHelpers.getObjectField(args[0], "mKey"))
                        if (headsUpEntry != null) {
                            val isRowPinned = XposedHelpers.callMethod(args[0], "isRowPinned") as Boolean
                            if (isRowPinned) {
                                val mHandler = XposedHelpers.getObjectField(headsUpManagerPhone, "mHandler") as Handler
                                val mRemoveAlertRunnable = XposedHelpers.getObjectField(headsUpEntry, "mRemoveAlertRunnable") as Runnable
                                mHandler.removeCallbacks(mRemoveAlertRunnable)
                                mHandler.postDelayed(mRemoveAlertRunnable, 4500L)
                            }
                        }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }
```

#### Reconstruction

| Aspect | Finding |
|---|---|
| Callback order (both surfaces) | `chain.proceed()` first, then the custom `Handler` effect. |
| `chain.proceed` order | Before all custom work (`SystemNotificationHooks.kt:521` and `:554`). |
| Preference reads | **0** — no `PrefMap` reads. |
| Parsing | none |
| Field helper calls (surface 1) | 4 (`expanded`, `remoteInputActive`, `mEntry`, `extended`) + 1 `getObjectField` on `mHandler` + 1 `getObjectField` on `mRemoveAlertRunnable` = 7 field reads; 1 `getSurroundingThis` (`this$0`) |
| Method helper calls (surface 1) | 1 (`isRowPinned` on `mEntry`) |
| Field helper calls (surface 2) | `mKeyguardStateController`, `mHeadsUpManager`, `mRemoveAlertRunnable`, `mHandler`, `mKey` on `args[0]` = 5; plus `mShowing` on `mKeyguardStateController` (1 `getBooleanField`); plus `isRowPinned` on `args[0]` (1 `callMethod`) |
| Method helper calls (surface 2) | `isRowPinned` on `args[0]`, `getHeadsUpEntry` on `mHeadsUpManager` (2 args) |
| Collection lookup | none |
| Explicit allocation | none in the callback body. The `Runnable` is a ROM object. |
| Mutation | `Handler.removeCallbacks(...)` and `Handler.postDelayed(...)` with a ROM `Runnable`; observable to the ROM scheduling state. Surface 2: same `remove`/`post` pattern. |
| Failure boundary | `chain.proceed()` is called first and its `Throwable` is captured. The post-proceed custom `Handler` effect runs afterwards **regardless** of whether `chain.proceed()` threw. Any `Throwable` from the custom effect — including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` — is caught by the second `catch (t: Throwable)`, logged by `XposedHelpers.log(t)`, and swallowed; the second catch does **not** call `FatalErrors.rethrowIfFatal`. Finally, the original captured `Throwable` is rethrown by `throwOrReturn`. If `chain.proceed()` succeeded and the custom effect fails, the original result is still returned. |
| Fatal behavior | Any `Throwable` thrown by `chain.proceed()` is captured and ultimately rethrown by `throwOrReturn`, including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError`. Any `Throwable` thrown only by the post-proceed custom `Handler` effect is caught by the second `catch (t: Throwable)`, logged, and swallowed (this includes fatal `Throwable`s because the legacy second catch is a plain `catch (Throwable)` with no `FatalErrors.rethrowIfFatal`). If both the original and the custom effect throw, the custom `Throwable` is swallowed and the original captured `Throwable` wins. |
| Ownership / retained refs | `mHandler` and `mRemoveAlertRunnable` are borrowed ROM objects. The callback does not retain `Activity`, `View`, or short-lived controllers after returning, but the posted `Runnable` is retained by the `Handler` queue until it runs or is removed. |
| Publication requirements | none (no preferences; delays are constants). |
| Possible Architecture C shape | Pre-resolve the two ROM classes, the `Handler` / `Runnable` fields, and the `isRowPinned` / `getHeadsUpEntry` methods at install. The hot callback still needs `getSurroundingThis`, `Handler.removeCallbacks`, and `Handler.postDelayed`. A FAST path would have to preserve the two-surface interaction. |
| FAST / LEGACY feasibility | LEGACY is the safer default. A FAST path across two different ROM classes with shared `Handler` semantics is significantly more complex and requires a dedicated resolver for both surfaces plus a proven mapping between `HeadsUpEntryPhone` and `HeadsUpManagerPhone`. |
| Compatibility risk | High relative to the others. Two ROM classes, two callbacks, `Handler` timing, and the failure/fatal surface (original throwable must be preserved and rethrown after custom work) are all observable. Any ABI drift in `mHandler`, `mRemoveAlertRunnable`, `getHeadsUpEntry`, or `isRowPinned` breaks the feature. |
| Expected marginal benefit | Low. The dominant work is `Handler` scheduling and the original `chain.proceed()`, not the `XposedHelpers` cache lookups. Removing reflection would save a few map lookups but would not reduce the `Handler` cost or the two-surface complexity. |

`CALLBACK_FREQUENCY_EVIDENCE = NOT_PROVEN`
`NOT_RUNTIME_TESTED_CALLBACK`

---

### 4.5 `QSHapticHook`

| Fact | Value | Evidence |
|---|---|---|
| Source file | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | `SystemAudioHooks.kt:37-69` |
| Hook function | `SystemAudioHooks.QSHapticHook` | `SystemAudioHooks.kt:37` |
| ROM class | `com.android.systemui.qs.tileimpl.QSTileImpl` | `SystemAudioHooks.kt:38` |
| Hook method | `click(View)` | `SystemAudioHooks.kt:38` |
| Hook API surface | `PackageReadyParam` in `com.android.systemui` | `SystemAudioHooks.kt:37` |
| Domain | Per-tile haptic feedback on QS tile click | `system_qshaptics`, `system_qshaptics_ignore` |

#### Exact source

```kotlin
// app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt:37-69
    @JvmStatic
    fun QSHapticHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mState = XposedHelpers.callMethod(thisObject, "getState")
                    val state = XposedHelpers.getIntField(mState, "state")
                    if (state != 0) {
                        val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                        val ignoreSystem = MainModule.mPrefs.getBoolean("system_qshaptics_ignore")
                        val opt = MainModule.mPrefs.getStringAsInt("system_qshaptics", 1)
                        if (opt == 2)
                            HookUtils.performLightVibration(mContext, ignoreSystem)
                        else if (opt == 3)
                            HookUtils.performStrongVibration(mContext, ignoreSystem)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
                }
        })
    }
```

#### Reconstruction

| Aspect | Finding |
|---|---|
| Callback order | `chain.proceed()` first, then the custom haptic effect. |
| `chain.proceed` order | Before the haptic work (`SystemAudioHooks.kt:43`). |
| Preference reads | 2 when `state != 0`: `getBoolean("system_qshaptics_ignore")`, `getStringAsInt("system_qshaptics", 1)` |
| Parsing | none in the callback; `getStringAsInt` uses `parsedIntCache`. |
| Field helper calls | 1 (`getIntField` on `mState` for `state`) + 1 (`getObjectField` for `mContext`) = 2 |
| Method helper calls | 1 no-argument `callMethod` (`getState`) |
| Collection lookup | none |
| Explicit allocation | `HookUtils.performLightVibration` / `performStrongVibration` constructs an `HapticFeedbackUtil` (`HookUtils.kt:71`) and performs a system haptic feedback call. This is a heavy, allocation-bearing operation. |
| Mutation | Vibration effect only; no ROM field mutation. |
| Failure boundary | `chain.proceed()` is called first and its `Throwable` captured. The post-proceed haptic work runs afterwards. Any `Throwable` from the haptic work — including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` — is caught by the second `catch (t: Throwable)`, logged by `XposedHelpers.log(t)`, and swallowed; the second catch does **not** call `FatalErrors.rethrowIfFatal`. The original captured `Throwable` is rethrown by `throwOrReturn`. |
| Fatal behavior | Any `Throwable` thrown by `chain.proceed()` is captured and ultimately rethrown by `throwOrReturn`, including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError`. Any `Throwable` thrown only by the post-proceed haptic work (`getState`, `getIntField`, `getObjectField`, preference reads, `performLightVibration` / `performStrongVibration`) is caught by the second `catch (t: Throwable)`, logged, and swallowed (this includes fatal `Throwable`s because the legacy second catch is a plain `catch (Throwable)` with no `FatalErrors.rethrowIfFatal`). If both the original and the haptic work throw, the haptic `Throwable` is swallowed and the original captured `Throwable` wins. |
| Ownership / retained refs | `thisObject`, `mState`, `mContext` borrowed. The `HapticFeedbackUtil` is a short-lived local. |
| Publication requirements | One `Boolean` (`system_qshaptics_ignore`) and one `Int` (`system_qshaptics`). |
| Possible Architecture C shape | Pre-resolve `getState` and the `state` / `mContext` fields; publish the two preferences as typed values. The hot callback still calls `HookUtils.perform*Vibration`, which is the dominant cost. |
| FAST / LEGACY feasibility | FAST could remove the three reflection calls, but the `HapticFeedbackUtil` construction and vibration call dominate the callback. If the `QSTileImpl` class or `getState` shape changes, a FAST path would break silently without ABI proof. |
| Compatibility risk | Moderate. `QSTileImpl`, `getState`, `state` field, `mContext` field, and the `HapticFeedbackUtil` path are ROM-specific. The vibration effect must remain guarded by `state != 0`. |
| Expected marginal benefit | Low. Removing three `XposedHelpers` cache lookups saves little compared with the `HapticFeedbackUtil` allocation and the system vibration call. No click-frequency evidence exists, so the total savings cannot be shown to be significant. |

`CALLBACK_FREQUENCY_EVIDENCE = NOT_PROVEN`
`NOT_RUNTIME_TESTED_CALLBACK`

---

## 5. CALLBACK FREQUENCY EVIDENCE

| Candidate | Real callback frequency | Evidence |
|---|---|---|
| `MaxNotificationIconsHook` (`NotificationIconContainer.resetViewStates`) | `NOT_PROVEN` | No runtime trace or GitHub CI timing data. |
| `BetterPopupsAllowFloatHook` (`MiuiExpandableNotificationRow.updateMiniWindowBar`) | `NOT_PROVEN` | No runtime trace or GitHub CI timing data. |
| `NotificationImportanceHook` (`NotificationIconAreaController.updateStatusBarIcons`) | `NOT_PROVEN` | No runtime trace or GitHub CI timing data. |
| `AutoDismissExpandedPopupsHook` (`HeadsUpManagerPhone$HeadsUpEntryPhone.updateEntry` + `StatusBarNotificationPresenter.onExpandClicked`) | `NOT_PROVEN` | No runtime trace or GitHub CI timing data. |
| `QSHapticHook` (`QSTileImpl.click(View)`) | `NOT_PROVEN` | No runtime trace or GitHub CI timing data. |

```text
OVERALL_CALLBACK_FREQUENCY_EVIDENCE = NOT_PROVEN
NOT_RUNTIME_TESTED_CALLBACK
```

Method names alone do not prove frequency, per-frame rate, or per-user-action rate. Any benefit calculation that assumes high frequency is unsupported by local or CI evidence.

---

## 6. COMPARISON MATRIX

| Candidate | Callback style | Prefs / hot path | Explicit allocation | Mutation before/after `proceed` | Failure/fatal surface | FAST feasibility | Compatibility risk | Marginal benefit | Rank |
|---|---|---|---|---|---|---|---|---|---|
| `MaxNotificationIconsHook` | `intercept` | 1 `getStringAsInt` | none | before | single `try`; fatal rethrown by `throwOrReturn` | moderate | low-to-moderate | low-to-moderate (saves snapshot + 3 reflection cache lookups) | 1 |
| `BetterPopupsAllowFloatHook` | `intercept` | 2 `getStringSet` | none | before | single `try`; fatal rethrown | moderate | moderate | low-to-moderate (saves 2 set lookups + 4 reflection cache lookups) | 2 |
| `NotificationImportanceHook` | `before` | **0** | `ArrayList` whenever list non-empty | before (implicit `proceed` after) | `MethodHook.before` wrapper logs non-fatal; original still proceeds | low (ABI alone cannot remove allocation/traversal) | moderate | low (saves cache lookups but not `ArrayList` + O(N) traversal) | 3 |
| `AutoDismissExpandedPopupsHook` | `intercept` (two surfaces) | **0** | none | after (`proceed` first) | two callbacks; `Handler` scheduling; post-proceed custom `Throwable` (including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError`) swallowed; original `Throwable` rethrown | low | high (two classes, `Handler` semantics, legacy fatal-swallow surface) | low (dominant cost is `Handler` scheduling, not reflection) | 4 |
| `QSHapticHook` | `intercept` | 2 reads when active | `HapticFeedbackUtil` per active click | after (`proceed` first) | single `try`; post-proceed haptic `Throwable` (including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError`) swallowed; original `Throwable` rethrown | low (vibration dominates) | moderate (legacy fatal-swallow surface) | low (vibration call dominates; reflection is a tiny fraction) | 5 |

---

## 7. RANKING

Independent verification of the Gatekeeper ranking on the exact C7 base:

```text
1. MaxNotificationIconsHook
2. BetterPopupsAllowFloatHook
3. NotificationImportanceHook
4. AutoDismissExpandedPopupsHook
5. QSHapticHook
```

The rank order is preserved, but **rank #1 does not automatically justify production migration**. The marginal benefit of the #1 candidate is still small compared with the existing warm caches and the resolver / runtime / publication / test / compatibility cost of an Architecture C cycle.

---

## 8. NO_SUITABLE_TARGET RATIONALE

```text
C7_TARGET_SELECTION = NO_SUITABLE_TARGET
```

The remaining Architecture C marginal runtime benefit does not justify the additional resolver / runtime / publication / test / compatibility complexity for any of the five candidates.

This is **not** the claim that "nothing can be optimized." Further local micro-optimizations may exist, but no remaining target reaches the Architecture C production migration benefit/risk threshold because:

1. **The existing infrastructure is already warm.** `PrefMap` uses an `AtomicReference` snapshot and a `parsedIntCache`. `XposedHelpers` uses nested `ConcurrentHashMap` caches for fields, no-argument methods, and classes. A hot legacy callback already pays only a few map lookups plus the reflective `get`/`invoke`, not a full hierarchy scan or a full preference parse.
2. **The dominant costs cannot be removed by a frozen ABI alone.**
   - `NotificationImportanceHook` must allocate an `ArrayList` and traverse `mNotificationEntries` on every non-empty call. A frozen ABI does not eliminate that allocation or the O(N) traversal.
   - `AutoDismissExpandedPopupsHook` is dominated by `Handler.removeCallbacks` / `Handler.postDelayed` and the two-surface interaction.
   - `QSHapticHook` is dominated by the `HapticFeedbackUtil` construction and the system vibration call.
3. **No real callback-frequency evidence exists.** All candidates are `NOT_PROVEN` for frequency. A frequency assumption is required to argue that the small per-call savings would accumulate into a meaningful runtime win.
4. **Compatibility and fatal-error surfaces are non-trivial.** Each candidate depends on ROM field/method names, possible subclasses, and observable state mutation. `AutoDismissExpandedPopupsHook` and `QSHapticHook` use a proceed-first two-`try` legacy shape: the second `catch (Throwable)` has no `FatalErrors.rethrowIfFatal`, so any `Throwable` from the post-proceed custom effect — including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` — is logged and swallowed. The two-surface `AutoDismissExpandedPopupsHook` and the list-mutation `BetterPopupsAllowFloatHook` have the largest overall surfaces.
5. **The pre-screened `DrawerBlurRatioHook` is already Architecture C-shaped.** It uses a volatile snapshot, a `ThreadLocal` scope, and a `WeakReference` target cache. There is no remaining target of similar shape that has not already been optimized.

Therefore the Architecture C pipeline terminates at C7 with no selected target.

---

## 9. ARCHITECTURE C LIFECYCLE / TERMINATION RATIONALE

```text
C1 = CLOSED
C2 = CLOSED
C3 = CLOSED
C4 = CLOSED
C5 = CLOSED
C6 = CLOSED

C1_C2_C3_C4_C5_C6_REOPEN = NO

C7 = TARGET_SELECTION_TERMINATION_FREEZE

C8 = NOT_AUTHORIZED
```

No A0, resolver, runtime, effect, hook, production, or test implementation is authorized by this target-selection document. Any future Architecture C work must start from a new, explicit contract.

---

## 10. REOPEN STATE

| Scope | State |
|---|---|
| C1-C6 historical docs | unchanged |
| C1-C6 resolvers / runtimes / effects / hooks | no reopen (`C1_C2_C3_C4_C5_C6_REOPEN = NO`) |
| C7 target selection | frozen (`NO_SUITABLE_TARGET`) |
| C7 A0 | not started (`C7_A0_AUTHORIZATION = NO`) |
| C7 production | not started (`C7_PRODUCTION_AUTHORIZATION = NO`) |
| C8 | not authorized (`C8 = NOT_AUTHORIZED`) |

---

## 11. AUTHORIZATION STATE

```text
C7_TARGET_SELECTION_AUTHORIZATION = YES
C7_A0_AUTHORIZATION = NO
C7_PRODUCTION_AUTHORIZATION = NO
```

This document is authorized only as a target-selection audit. It does not authorize code, tests, resolvers, effects, hooks, A0 preflight, or any C1-C6 historical edits.

---

## 12. EVIDENCE CLASSIFICATION

| Evidence type | Classification | Notes |
|---|---|---|
| Source reconstruction | `STRUCTURAL` | Code, field names, method names, and control flow are taken directly from the exact base SHA. |
| Execution / CI | `LOCAL_EXECUTION_EVIDENCE_ONLY` | No GitHub Actions / CI run data is available. |
| Callback frequency | `NOT_PROVEN` | No runtime trace or timed callback data. |
| Per-callback runtime behavior | `NOT_RUNTIME_TESTED_CALLBACK` | No real-device or CI runtime verification of the callback paths. |
| GitHub CI status | `NONE` | `GITHUB_CI_STATUS = NONE` |
| GitHub workflow runs | `NONE` | `GITHUB_WORKFLOW_RUNS = NONE` |

Do not use `NONE` for evidence classification. The allowed classifications used here are `STRUCTURAL`, `LOCAL_EXECUTION_EVIDENCE_ONLY`, `NOT_RUNTIME_TESTED_CALLBACK`, and `NOT_PROVEN`.

---

## 13. END GATE

The end-gate checks are performed after the C7 documentation commit is pushed to `origin/devin/a14-architecture-c-r14.20.0`. The exact final SHAs are reported in the session's final report because the commit that adds this file cannot contain its own SHA.

| Check | Expected condition | Evidence |
|---|---|---|
| `local HEAD == remote HEAD` | yes | `git rev-parse HEAD` and `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Selection base is ancestor of final HEAD | yes | `git merge-base` (must equal `2a76bee0068110dcb6b0f771a3d14004ad7dd4bc`) |
| Worktree clean | yes | `git status --short` |
| Only `C7_TARGET_SELECTION.md` changed since selection base | yes | `git diff --name-status 2a76bee0068110dcb6b0f771a3d14004ad7dd4bc..HEAD` |
| Production code changed | `false` | no `app/src/main/**` changes |
| Tests changed | `false` | no `app/src/test/**` changes |
| C1-C6 historical docs changed | `false` | no `docs/architecture-c/C[1-6]*` changes |
