# A14 P4 IME dismiss-button ownership preflight

> Scope: evidence-only preflight. No production source changes. No APK built, installed, or reboot performed.

## 1. Target

The bottom-left down-arrow/chevron button shown while the soft keyboard is visible, used to hide/dismiss the IME.

| Item | Value |
|---|---|
| Device | fuxi (Xiaomi 13) |
| ROM | HyperOS 1 / Android 14 (SDK 34) |
| Region | CN (live); Global/TW (ROM static cross-check) |
| Test app/screen | `com.android.mms/com.android.mms.ui.NewMessageActivity` |
| Active IME | `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME` |
| Navigation mode | Gesture (2-button/3-button not active) |

## 2. Direct device evidence

### 2.1 Visual and interaction

- Screenshot `p4_ime_visible.png` shows a circular down-arrow (`v`) at the bottom-left of the display, below the keyboard bottom row and to the left of the gesture home handle.
- Pressing it changed IME state from:
  - `mRequestedShowExplicitly=true mInputShown=true` (visible)
  - to `mRequestedShowExplicitly=false mInputShown=false` (dismissed)

This proves the control successfully hides the IME, but does **not** by itself prove which process renders or receives the touch.

### 2.2 Window ownership (dumpsys window windows)

- `NavigationBar0` is owned by `com.android.systemui` / uid `1000` and is `ty=NAVIGATION_BAR`.
- Its touchable region is exactly the bottom-left button area:
  ```text
  touchable region=SkRegion((49,2259,225,2400))
  ```
- `InputMethod` window (`com.google.android.inputmethod.latin`, uid `10188`) has a disjoint touchable region:
  ```text
  touchable region=SkRegion((0,1512,1080,2259))
  ```
  The bottom 141 px (y=2259-2400) are **not** in the IME touchable region.

Conclusion: the touch for the bottom-left button is handled by the `NavigationBar0` window, not the IME window.

### 2.3 SurfaceFlinger layer ordering

From `sf_dump_visible.txt`:

- `InputMethod#1564` display frame `[0,132,1080,2400]`
- `NavigationBar0#1082` display frame `[0,2259,1080,2400]`

`NavigationBar0#1082` is the topmost visible layer in the bottom 141 px; the button is drawn inside it.

### 2.4 IME navigation-bar controller state

From `dumpsys input_method` (both visible and dismissed states):

```text
mNavigationBarController={mImeDrawsImeNavBar=false mNavigationBarFrame=null ...}
```

The IME is **not** drawing the navigation bar in this state. The system `NavigationBar0` owns the bar.

## 3. Static analysis of the owner APK

Pulled `/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` (`com.android.systemui`, version `20230316.0`).

### 3.1 Navigation bar layout

`res/values/strings.xml`:

```text
<string name="config_navBarLayoutHandle">back[70AC];home_handle;ime_switcher[70AC]</string>
```

This is the layout used in gesture/handle mode. The leftmost element is `back`.

`res/layout/back.xml`:

```text
E: com.android.systemui.navigationbar.buttons.KeyButtonView
  A: ... id=0x7f0a00fb
  A: ... layout_width=@0x7f070d59  (navigation_key_width = 60 dp)
  A: ... contentDescription=@0x7f130046  (accessibility_back)
  A: ... keyCode=4
```

The left button is a `KeyButtonView` whose `keyCode` is `KEYCODE_BACK` (4).

### 3.2 Icon and rotation

`NavigationBarView.getBackDrawableRes()` selects `ic_sysbar_back_quick_step` (`0x7f0811bf`) in gesture/swipe-up mode.

`res/drawable/ic_sysbar_back_quick_step.xml` is a left-facing chevron. `NavigationBarView.orientBackButton()` rotates it `-90.0` degrees when `mNavigationIconHints & 0x1` is set and the nav bar is in gestural mode, turning the left chevron into the observed down-arrow.

### 3.3 Class ownership

- Owner class: `com.android.systemui.navigationbar.NavigationBarView`
- Button class: `com.android.systemui.navigationbar.buttons.KeyButtonView`
- Rotation method: `NavigationBarView.orientBackButton(...)`
- Click path: `KeyButtonView` dispatches `KEYCODE_BACK`; the system routes it to the focused window, and the active `InputMethodService` hides the keyboard.

## 4. Cross-ROM check (CN / Global / TW)

All three extracted `MiuiSystemUI.apk` artifacts contain:

| Check | CN | Global | TW |
|---|---|---|---|
| `Lcom/android/systemui/navigationbar/NavigationBarView;` | present | present | present |
| `orientBackButton` method | present | present | present |
| `res/layout/back.xml` with `KeyButtonView` and `keyCode=4` | identical | identical | identical |
| `res/drawable/ic_sysbar_back_quick_step.xml` | identical left chevron | identical left chevron | identical left chevron |

This indicates the same owner and the same dismiss/back mechanism on all three fuxi variants.

Live device evidence was captured only on the CN device; Global and TW verification is static-ROM only.

## 5. Ownership conclusion

| Aspect | Owner | Confidence | Evidence |
|---|---|---|---|
| Rendering | `com.android.systemui` / `MiuiSystemUI` `NavigationBar0` | High | `NavigationBar0#1082` is the topmost layer in y=2259-2400; `ic_sysbar_back_quick_step` rotated by `NavigationBarView.orientBackButton()` matches the observed down arrow. |
| Touch events | `com.android.systemui` / `NavigationBar0` | High | `dumpsys window` shows `touchable region=(49,2259,225,2400)` for `NavigationBar0`; IME touchable region stops at y=2259. |
| Dismiss action | `KeyButtonView` with `KEYCODE_BACK` (4) | High | `res/layout/back.xml` sets `keyCode=4`; pressing the button sets `mInputShown=false`. |
| IME participation | None for rendering/touch; only for consuming the back key | High | `mImeDrawsImeNavBar=false mNavigationBarFrame=null` in `dumpsys input_method`. |

The bottom-left "收回输入法" button is **the `back` navigation button in the SystemUI gesture navigation bar**, visually rotated to a down arrow when the IME is visible. It is **not** owned by the IME, the framework `InputMethodService.NavigationBarController`, `com.miui.home`, or the target application.

## 6. Commands run and results

| Command | Purpose | Exit code | Notes |
|---|---|---|---|
| `adb shell dumpsys input_method > input_method_visible.txt` | Capture IME state before dismiss | 0 | `mInputShown=true` |
| `adb shell dumpsys window windows > window_visible.txt` | Capture window ownership and touchable regions | 0 | `NavigationBar0` owns the bottom-left touch region |
| `adb shell dumpsys SurfaceFlinger > sf_dump_visible.txt` | Layer ordering | 0 | `NavigationBar0` above `InputMethod` |
| `adb pull /system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` | Pull owner APK | 0 |  |
| `apktool d -f -o systemui/smali MiuiSystemUI.apk` | Disassemble for smali review | 0 |  |
| `aapt2 dump xmltree --file res/layout/back.xml MiuiSystemUI.apk` | Inspect back button layout | 0 | `keyCode=4` |
| `aapt2 dump xmltree --file res/drawable/ic_sysbar_back_quick_step.xml MiuiSystemUI.apk` | Inspect back icon | 0 | left chevron |
| `aapt2 dump xmltree --file res/drawable/ic_ime_switcher_default.xml MiuiSystemUI.apk` | Rule out keyboard-switcher icon | 0 | keyboard grid, not the down arrow |
| Python androguard cross-check on CN/Global/TW `MiuiSystemUI.apk` | Verify `NavigationBarView` and `orientBackButton` across ROMs | 0 | all three positive |

## 7. P4-B implementation candidate

P4-A0 gate = PASS

P4-B authorization = YES

### 7.1 Verified contract

| Member | Exact descriptor | Status |
|---|---|---|
| `com.android.systemui.navigationbar.NavigationBarView.updateNavButtonIcons` | `()V` | verified on CN device + CN/Global/TW ROM |
| `com.android.systemui.navigationbar.NavigationBarView.getBackButton` | `()Lcom/android/systemui/navigationbar/buttons/ButtonDispatcher;` | verified on all three ROMs |
| `com.android.systemui.navigationbar.NavigationBarView.mNavigationIconHints` | `I` | verified on all three ROMs |
| `com.android.systemui.navigationbar.NavigationBarView.mNavBarMode` | `I` | verified on all three ROMs |
| `com.android.systemui.navigationbar.buttons.ButtonDispatcher.setVisibility` | `(I)V` | verified on all three ROMs |

CONTRACT_SAME_3_OF_3 = YES

### 7.2 Implementation summary

- Feature id: `252`
- Feature key: `controls_hide_ime_dismiss_button`
- Feature target: `SYSTEM_UI`
- Install phase: `PACKAGE_READY`
- Hook class: `com.android.systemui.navigationbar.NavigationBarView`
- Hook method: `updateNavButtonIcons`
- Callback:
  1. Call `chain.proceed()` first.
  2. Read `mNavigationIconHints` and `mNavBarMode`.
  3. If `(hints & 0x1) != 0 && navBarMode == 2`, get the back `ButtonDispatcher` and call `setVisibility(View.INVISIBLE)`.
  4. Otherwise do nothing (do not force `VISIBLE`).
- No runtime preference read, no observer, no broadcast, no polling, no DexKit, no region branch, no version branch.
- Reflection members resolved once at hook installation.

### 7.3 Side-effect freeze

- Does not modify `InputMethodFeatures`, `InputMethodInstaller`, `ProcessRouter`, or `scope.list`.
- Does not change `home_handle`, `ime_switcher`, navigation-bar height, IME insets, keyboard height, back-gesture areas, or 3-button navigation.
- Does not reuse `controls_nonavbar_fix_inputmethod` preference.
- No P3 RestartPagePolicy change required; controls/navbar page already targets SystemUI restart.

### 7.4 Validation

- `python tools/verify.py full` and `python tools/verify.py fast --changed` must pass.
- `SystemUiFeaturesWiringTest` count updated from 96 to 97.
- New `HideImeDismissButtonTest` covers the boolean condition.

### 7.5 P4-B self-assessment

P4_B_SELF_ASSESSMENT = PASS_CANDIDATE

## 8. Blockers and remaining work

- P4-A0 evidence is complete.
- P4-B candidate is implemented and validated; waiting for independent P4-B final gate.
- No Global/TW live-device capture was performed; the three-ROM conclusion rests on static APK evidence and the assumption that the live navigation-mode configuration is equivalent.
