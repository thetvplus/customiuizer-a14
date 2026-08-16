# A14 Dynamic Island shell / dismiss order / geometry corrective

This document records the final implementation for the HyperOS 1 A14
`r14.20.0` Dynamic Island corrective.

## Problem statement

Runtime diagnosis identified three related defects:

1. **The Dynamic Island was not a true independent surface.**
   The module was transforming the ROM message container
   `cl_strong_toast_msg` in place and forcing it to become a rounded
   capsule. The result looked like a cropped or repurposed ROM container
   because `cl_strong_toast_msg` still contained ROM-owned children and
   backgrounds.

2. **Disappear animation was choppy or incomplete.**
   The module was racing with ROM `setValue()` because it animated alpha on
   every direct child of the ROM container. Dismiss also ran ROM `clearAll()`
   before the module exit animation finished, which mutated the view content
   and requested layout while the capsule was still transforming.

3. **Geometry / motion clipping on the first and bottom events.**
   The original host Window height was sized only for the resting capsule
   plus a fixed bottom margin. It did not reserve a rounding/pixel-safety
   margin for the scaled entrance/exit edge, and the shell layout placed the
   shell using margins/padding that did not make the parent height match the
   Window height. The scaled capsule at the start and exit could therefore
   touch or exceed the Window surface, causing the first trigger to be
   invisible or the bottom capsule to be clipped by a few pixels. The shell
   entrance was also gated only by `View.post`, which could fire before the
   first layout pass completed.

## Root causes

- `cl_strong_toast_msg` is the ROM content owner. ROM `setValue()` and
  `updateStrongToast()` mutate its children, visibility, text, images, video
  state and colors. Module-owned child alpha animations could be overwritten
  mid-animation, which matched the observed "standalone event incomplete /
  second event completes" behavior.
- The module did not create a separate visual shell. All transforms and
  background changes were applied to the ROM content view itself, keeping the
  island coupled to ROM layout and clipping.
- `realHideStrongToast` was calling ROM `clearAll()` before the module
  `ViewPropertyAnimator` exit started. `clearAll()` then removed the view and
  reset children while the exit transform was still running.
- The Window height and shell margins were computed independently. There was
  no single pure envelope that guaranteed the scaled shell stayed inside the
  host surface at all phases, and no rounding safety margin absorbed
  RenderNode integer-rounding overflow at the edge.

## Implementation

`SystemUIStrongToastHooks.kt` now:

1. **Wraps `cl_strong_toast_msg` in a module-owned `FrameLayout` shell**
   via `bindDynamicIslandShell()`. The shell receives the original container's
   outer `LinearLayout.LayoutParams` and is inserted at the original content
   index. The ROM content is reparented into the shell with
   `MATCH_PARENT` / `MATCH_PARENT` and its own background is set to `null` so
   the shell draws the black rounded pill.

2. **Owns all transforms on the shell only.**
   `prepareDynamicIslandCapsule()`, `runDynamicIslandEntrance()`,
   `handleDynamicIslandTouch()`, `animateDynamicIslandDismiss()` and
   `restoreDynamicIslandAfterDrag()` now all target the shell. The ROM content
   inside the shell is never scaled, translated or alpha-animated by the
   module.

3. **Removes module-owned direct child alpha animation.**
   `prepareDynamicIslandContent()`, `animateDynamicIslandContent()` and
   `animateDynamicIslandContentOut()` were deleted. The module no longer owns
   ROM child alpha.

4. **Fixes dismiss ordering.**
   `buildDynamicIslandDismissComplete()` constructs the completion runnable.
   The sequence is:
   1. `restoreStatusBarContents(strongToast)`
   2. `XposedHelpers.callMethod(strongToast, "clearAll")`
   3. `XposedHelpers.callMethod(strongToast, "onComplete")`

   `clearAll()` only runs inside the `withEndAction` callback, after the
   shell exit `ViewPropertyAnimator` has fully completed.

5. **Makes shell binding idempotent and teardown exact.**
   `DynamicIslandShellState` is stored on the `MIUIStrongToast` root via an
   additional instance field. `bindDynamicIslandShell()` returns an existing
   shell when the state is still valid. `restoreDynamicIslandShell()` returns
   the content to the original parent/index, restores the original
   `LayoutParams` and background, removes the shell, and cancels animations.
   `onDetachedFromWindow()` cancels the shell animator, removes listeners,
   removes the expanded touch region, restores status-bar contents, and then
   calls `restoreDynamicIslandShell()` before the ROM super method runs.

6. **Captures and restores every module-owned baseline.**
   `DynamicIslandShellState` now records the original parent padding, parent
   `LinearLayout.gravity`, the forehead bottom view reference and its exact
   original visibility, and the original `clipChildren` / `clipToPadding` of
   every ancestor up to the `MIUIStrongToast` root. `prepareDynamicIslandCapsule()`
   validates ROM dimensions before any hierarchy mutation and wraps post-bind
   setup in a single transaction: on any non-fatal failure it calls
   `restoreDynamicIslandShell()` and removes the shell state, leaving the ROM
   hierarchy intact. `prepare` captures the bottom-view visibility and the
   ancestor-clip baselines without mutating, then publishes the fully updated
   `DynamicIslandShellState` before it changes the parent padding/gravity,
   disables ancestor clipping or hides the bottom view, ensuring every module
   mutation is recoverable from the published state. `bindDynamicIslandShell()`
   itself rolls back content reparenting and background clearing if the
   transaction fails.

7. **Adds a pure `DynamicIslandWindowEnvelope` geometry helper.**
   `DynamicIslandWindowEnvelope.kt` is a new testable, side-effect-free
   class that produces a host Window height, shell margins, parent padding
   and parent gravity from the position, visual height, top/bottom insets,
   and a rounding safety margin. It proves the shell fits at the scaled
   entrance start, at rest, and at the scaled exit end. The envelope is the
   single source of truth used by `prepareDynamicIslandCapsule()`,
   `resolveDynamicIslandMotionProfile()`, and `getWindowParam()` so the host
   Window and the shell are always sized consistently.

8. **Reserves a rounding safety margin and keeps the scaled edge inside.**
   `CAPSULE_ROUNDING_SAFETY_MARGIN_DP = 2` is added to the host height and
   subtracted from the entrance/exit travel so the scaled capsule starts
   inside the Window surface, not flush with it. This absorbs
   `Math.round()` / RenderNode half-pixel expansion that was clipping the
   first frame on the target device.

9. **Fixes parent padding / margin layout to make `mDarkToast` height match
   the host Window.**
   `prepareDynamicIslandCapsule()` now uses the envelope's `shellTopMargin`,
   `shellBottomMargin`, `parentPaddingTop` and `parentPaddingBottom`. For the
   top position the top margin is the near-edge inset and the bottom padding
   is the far-edge safety; for the bottom position the bottom margin is the
   near-edge inset and the top padding is the far-edge safety. This makes
   the wrapping `LinearLayout` total height equal the computed Window height
   and keeps the shell correctly aligned through all animation phases.

10. **Gates the first entrance on a one-shot pre-draw listener.**
    `startDynamicIslandEntrance()` no longer uses `View.post`. It adds an
    `OnPreDrawListener` that removes itself before calling
    `runDynamicIslandEntrance()`, guaranteeing the shell has been measured
    and laid out before the first transform is applied.

11. **Uses hardware layers for shell animations.**
    Entrance, dismiss, and the drag-settle restore animation now call
    `ViewPropertyAnimator.withLayer()` to reduce first-frame texture setup
    latency and avoid drawing the transformed shell through the normal
    software/RenderNode path.

12. **Updates UI labels and removes the obsolete center-pop resource.**
    The `system_strong_toast_mode_dynamic_island_center_pop` string resource
    was removed from all locale files and from the localization test fixture
    after confirming no production, array, reflection or dynamic reference.
    Locale labels for `system_strong_toast_mode_dynamic_island` were shortened
    to just "Dynamic Island" (and translated equivalents).

## Test coverage

New tests:

- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/feature/DynamicIslandWindowEnvelopeTest.kt`
  - Verifies top and bottom envelopes produce the expected host height,
    shell margins, parent padding/gravity, resting top/bottom, and signed
    entrance translation.
  - Verifies `fitsAllPhases()` passes for both positions with and without
    rounding and with a very small bottom padding.
  - Verifies the scaled start top/bottom is inside the host when rounding is
    used and closer to the edge when rounding is not used.

- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandShellContractTest.kt`
  - Source-contract assertions for `bindDynamicIslandShell`,
    `restoreDynamicIslandShell`, `prepareDynamicIslandCapsule`,
    `startDynamicIslandEntrance`, `runDynamicIslandEntrance`,
    `animateDynamicIslandDismiss`, `buildDynamicIslandDismissComplete`,
    `realHideStrongToast`, `installExpandedWindowTouchRegion`,
    `findDynamicIslandShell` and `onDetachedFromWindow`.
  - Verifies the shell is a `FrameLayout`, idempotent, preserves original
    background / layout params, restores hierarchy, and that Dynamic Island
    no longer owns direct child alpha.
  - Verifies `DynamicIslandShellState` captures parent padding/gravity,
    bottom-view visibility, and ancestor `clipChildren`/`clipToPadding`
    baselines, and that `restoreDynamicIslandShell()` restores them exactly.
  - Verifies `prepareDynamicIslandCapsule()` validates ROM dimensions before
    binding, publishes the fully updated `DynamicIslandShellState` before any
    ancestor-clip, parent padding/gravity or bottom-view mutation, and that
    both `prepare` and `bind` fail open with rollback.

- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandDismissLifecycleTest.kt`
  - Functional test for `buildDynamicIslandDismissComplete()`:
    `clearAll()` runs before `onComplete()`, each runs once, and `onComplete()`
    still runs even if `clearAll()` throws.

Updated tests:

- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/feature/DynamicIslandMotionProfileTest.kt`
  - Updated all cases to include `roundingSafetyPx = 2` and the matching
    expected window heights (197 / 251) and bottom rest top (20).
  - Added `topWindowHeightRespectsStatusBarInsetWhenLargerThanCapsule` and
    `bottomWindowHeightIncludesRoundingSafety` to verify the envelope is
    reflected in the public motion profile.

- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StrongToastPresentationModeTest.kt`
  - Added source-contract assertions that shell entrance/exit/rest
    `ViewPropertyAnimator` chains call `.withLayer()` and that the first
    entrance is gated by a one-shot `addOnPreDrawListener`.

Existing `SystemUIStrongToastHooksTest` MATCH-status-bar tests continue to
pass.

## Verification

Run these in order:

```powershell
python tools/verify.py fast --changed
python tools/verify.py fast --tests SystemUIStrongToastHooksTest
python tools/verify.py fast --tests DynamicIslandMotionProfileTest DynamicIslandWindowEnvelopeTest
python tools/verify.py fast --tests DynamicIslandShellContractTest DynamicIslandDismissLifecycleTest
python tools/verify.py full
```

Static rules, invariants, `compileDebugKotlin`, `testDebugUnitTest`, and
`lintDebug` all pass.

## Files changed

Production:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/DynamicIslandWindowEnvelope.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/DynamicIslandMotionProfile.kt`

Resources:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/main/res/values-vi-rVN/strings.xml`
- `app/src/main/res/values-tr-rTR/strings.xml`
- `app/src/main/res/values-ru-rRU/strings.xml`
- `app/src/main/res/values-pt-rBR/strings.xml`
- `app/src/main/res/values-ja-rJP/strings.xml`
- `app/src/main/res/values-es-rES/strings.xml`
- `app/src/main/res/values-cs-rCZ/strings.xml`

Tests and fixtures:

- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/feature/DynamicIslandWindowEnvelopeTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/feature/DynamicIslandMotionProfileTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StrongToastPresentationModeTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandShellContractTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandDismissLifecycleTest.kt`
- `tools/tests/test_recent_feature_localizations.py`

Documentation:

- `docs/audit/A14_DYNAMIC_ISLAND_FINAL.md`

## Runtime limitations

- This corrective is verified statically and via JVM unit tests. A device
  runtime smoke test is still required to confirm the visual result on the
  target HyperOS 1 build.
- The shell relies on `WindowManager` adding the `MIUIStrongToast` root once
  and reusing it for consecutive events. If a future ROM revision reparents
  `cl_strong_toast_msg` outside `mDarkToast` the `bindDynamicIslandShell()`
  copy logic will need a matching update.

## Native animation ownership audit (P0-D)

This section records the exact HyperOS 1 `fuxi` ROM evidence for who owns
`MATCH_STATUS_BAR_HEIGHT` and Dynamic Island motion, and the engineering
verdict on direct ROM animation reuse.

### ROM artifacts

- `MiuiSystemUI.apk` SHA-256:
  `5d8f2fe0b65d8a1a947b4280f8053b524f8c5de73f48a74f8792d415ae76e513`
- `MIUISystemUIPlugin.apk` SHA-256:
  `3dafd9e068ebee7e88344ae1c7d146c7e2d41e79b5c52b7736cd3e58be0cc999`
- Smali source audited:
  `com/android/systemui/toast/MIUIStrongToast.smali`
  - `getWindowParam()`
  - `onAttachedToWindow()`
  - `realHideStrongToast()`
  - `updateStrongToast()`
  - `setValue()`
  - `doShowViewAnimationForTB()`
  - inner class `MIUIStrongToast$2` (the `OnPreDrawListener`)

### Exact ROM window / style

- `getWindowParam()` builds a `WindowManager.LayoutParams` with:
  - `flags = 0x1800728`, `type = 0x7e1`, `format = -3`
  - `gravity = 0x31` (`Gravity.TOP | Gravity.CENTER_HORIZONTAL`) in **all** cases
  - `windowAnimations = 0` when `mShowBottom` is `false`
  - `windowAnimations = 0x7f1401ac` (`style/MIUIStrongToast`) when `mShowBottom` is `true`
- `style/MIUIStrongToast` (`0x7f1401ac`) has parent `0x7f140021` and sets
  `android:windowAnimationStyle` to `0x7f01022e`, which is `anim/translate_enter`.
- `0x7f071114` resolves to `dimen/strong_toast_width_window`; `0x7f05000e`
  (`check_is_default_strong_toast`) drives `mShowBottom`.
- `mShowBottom` does **not** mean a vertical bottom position; it selects the
  wider, default-toast window and animation path.  Vertical position is still
  fixed at `Gravity.TOP` by the ROM.

### ROM entrance / exit mechanics

- `onAttachedToWindow()` adds an `OnPreDrawListener` (`MIUIStrongToast$2`,
  class-id `2`) to the root `ViewTreeObserver`.  This is the primary motion.
- In that pre-draw:
  - `mShowBottom = true` -> `Folme.useAt` on `mRLLeft`/`mRLRight` animates
    `ViewProperty.X` (from the left/right edge) and `ViewProperty.ALPHA` using
    `mStartXConfig` / `mStartAlphaConfig`.
  - `mShowBottom = false` -> an `AnimatorSet` of `ObjectAnimator`s on
    `mCirCleCenter`/`mSTBgFL`/`mRightVideoView` with `DecelerateInterpolator(1.5f)`.
- `realHideStrongToast()` for `mShowBottom = true` builds `mDFWAnimationSet`:
  - `mRLLeft` and `mRLRight` fade to `0` over `200ms`
  - `mDarkToast` translates up by its height over `500ms`, start-delay `100ms`
  - all three use `DecelerateInterpolator(2.0f)`.
- `doShowViewAnimationForTB()` is a secondary Folme alpha transition for
  left/right text called from `updateStrongToast()` on content changes, not the
  first entrance.

### MATCH animation owner

`MATCH_STATUS_BAR_HEIGHT` only changes the outer window/container height and
keeps the original ROM child hierarchy.  The observed smooth MATCH motion is
therefore ROM-owned:

- `MATCH_ANIMATION_OWNER = ROM_VIEW_ANIMATION`
- `ROM_WINDOW_ANIMATION = 0` for `mShowBottom = false`; `style/MIUIStrongToast`
  (windowAnimationStyle → `anim/translate_enter`) for `mShowBottom = true`
- `ROM_TOP_ASSUMPTION = Gravity.TOP | Gravity.CENTER_HORIZONTAL` with the
  original `strong_toast_width_window` / `mCurrentTextHeight * 2` window geometry
- `ROM_BOTTOM_SUPPORT = NO` native top/bottom directional entrance; `mShowBottom`
  is a layout/animation selector, not a vertical position, and the gravity never
  changes.

### Dynamic Island direct ROM reuse verdict

- `DYNAMIC_ISLAND_DIRECT_ROM_ANIMATION_REUSE = UNSAFE`

Why:

1. The ROM `OnPreDraw` / `realHideStrongToast` motion targets the original
   `mRLLeft`/`mRLRight`/`mDarkToast` children.  Those children are now inside the
   module-owned shell, so ROM Folme would transform them independently of the
   shell, producing a double transform / clip / empty-look on the first frame.
2. Removing the module `layoutParams.windowAnimations = 0` would only expose the
   window-level `translate_enter`; the primary Folme/object-animator content
   motion would still fight the shell.
3. ROM entrance/exit is bound to the original `cl_strong_toast_msg` geometry and
   fixed `Gravity.TOP` window; it does not adapt to a top/bottom module shell
   with a full-width transparent envelope.
4. `realHideStrongToast` starts its own exit and then calls `clearAll()`.  The
   module must already intercept this path to keep `clearAll()` inside the shell
   exit `withEndAction`, so direct ROM reuse of exit is already unsafe for the
   dismiss order.

### Final Dynamic Island animation ownership

- `FINAL_DYNAMIC_ISLAND_ANIMATION_OWNER = MIXED` with a strict, documented boundary:
  - **Module** owns the shell `scaleY`/`translationY`/`alpha` via
    `ViewPropertyAnimator` (one cached `PathInterpolator`, `withLayer()`,
    one-shot `OnPreDrawListener` gate).
  - **ROM** still owns content-child Folme / `ObjectAnimator` alpha because the
    module does not set child alpha.
  - **Window** animation is zeroed (`layoutParams.windowAnimations = 0`) so no
    third window-level entrance is layered on top.
- `DECISION = ROM_DERIVED_MODULE_TRANSFORM`
- `WHY`: the ROM motion semantics (edge-driven X/alpha for left/right text,
  `mDarkToast` height translate on exit, `DecelerateInterpolator(2.0f)`) cannot
  be mapped directly onto the module shell without binding to the original ROM
  geometry.  The existing module `ViewPropertyAnimator` shell is the single,
  safe owner of the shell transform, and the `DynamicIslandWindowEnvelope`
  reproduces the ROM-safe geometry without the ROM's original-geometry coupling.
- `MODULE_INTERPOLATOR_SOURCE = EXISTING_EQUIVALENT`
- `EXACT_MODULE_INTERPOLATOR = PathInterpolator(0.25f, 1f, 0.5f, 1f)`
- `MOTION_PARAMETERS_CHANGED = NO`
- `PRE_DRAW_LIFECYCLE = RETAINED` because the shell still needs a one-shot
  `OnPreDrawListener` to guarantee measured width/height before the first frame.
- `ROM_CONTENT_CHILD_OWNERSHIP = ROM`
- `DISMISS_COMPLETION_STATUS = PRESERVED` (`clearAll()` only runs after the
  shell `withEndAction`, then `onComplete()` once).
- `mCacheModel STATUS = PRESERVED` (no access or mutation in the animation path).
- `WINDOW_ANIMATIONS_ZEROED = YES` for Dynamic Island (`layoutParams.windowAnimations = 0`).

## Gate / status

- `DYNAMIC_ISLAND_SHELL_LIFECYCLE_ENGINEERING_GATE = PASS`
- `DYNAMIC_ISLAND_GEOMETRY_MOTION_ENGINEERING_GATE = PASS`
- `DYNAMIC_ISLAND_NATIVE_ANIMATION_AUDIT_ENGINEERING_GATE = PASS`
- `DYNAMIC_ISLAND_PRODUCTION_ENGINEERING = PASS`
- `P0_D_NATIVE_ANIMATION_AUDIT = PASS`
- `P0_D_CORRECTIVE_IN_PROGRESS = NO`
- `P0_D_AUDIT_SHA = bcec915e3903cb8ce83834dfe1e7d0d7d1afaa4c`
- `P0_D_AUDIT_ENDPOINT = bcec915e3903cb8ce83834dfe1e7d0d7d1afaa4c`
- `DYNAMIC_ISLAND_PRODUCTION_FREEZE = d78c04c1cf82c41d17ed5e85d7c9e6ce9b3aea8f`
- `FINAL_TREE_CONTAINS_CORRECTIVE = YES`
- `DEVICE_VISUAL_ACCEPTANCE = PENDING_NEXT_SIGNED_APK`

The geometry/motion envelope is now derived from a single pure
`DynamicIslandWindowEnvelope` that reserves a rounding safety margin, sizes the
host Window and parent `LinearLayout` consistently, and proves the shell fits
at every animation phase. The implementation is complete and consistent with the
HyperOS 1 A14 evidence. A device runtime smoke test with the signed release APK
is still required to confirm the visual result on the target ROM.
