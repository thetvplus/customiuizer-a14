# A14 Volume Mode Shortcut Identity Evidence

This document records the source-of-truth used for the Mute / DND independent
hide feature in CustoMIUIzer r14.20.0.

## ROM / plugin artifacts

### Primary plugin
- local sample: `local-rom-samples/fuxi-V816.0.7.0-20260814/MIUISystemUIPlugin.apk`
- device path: `/product/app/MIUISystemUIPlugin/MIUISystemUIPlugin.apk`
- package: `miui.systemui.plugin`
- versionName: `15.0.3.69.0`
- versionCode: `150036900`
- minSdk: `33`, targetSdk: `34`
- SHA-256: `3dafd9e068ebee7e88344ae1c7d146c7e2d41e79b5c52b7736cd3e58be0cc999`

### Host SystemUI (reference package)
- local sample: `local-rom-samples/fuxi-V816.0.7.0-20260814/MiuiSystemUI.apk`
- device path: `/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk`
- package: `com.android.systemui`
- versionName: `20230316.0`
- versionCode: `202303160`
- minSdk: `34`, targetSdk: `34`
- SHA-256: `5d8f2fe0b65d8a1a947b4280f8053b524f8c5de73f48a74f8792d415ae76e513`

Both artifacts are from the `customiuizer-fuxi-rom-20260813` device image set.
They were pulled again from the connected device on 2026-08-14. Their hashes
are unchanged, so the static class/resource evidence below applies to the
currently installed ROM.

## Live volume-key evidence (2026-08-14)

The device was unlocked through ADB, `logcat` was cleared, and
`KEYCODE_VOLUME_UP` was injected. The resulting window was:

- owner package: `com.android.systemui`, UID 1000;
- window title: `MiuiVolumeDialogImpl`;
- window type: `VOLUME_OVERLAY`;
- flags: `NOT_FOCUSABLE`, `NOT_TOUCH_MODAL`, `LAYOUT_IN_SCREEN`,
  `TRUSTED_OVERLAY` and related volume-overlay flags;
- frame: `[0,0][1080,2400]`, with a visible SurfaceFlinger layer named
  `MiuiVolumeDialogImpl`.

The plugin runtime logged the exact state path used by this feature:

```text
vol.MiuiVolumeDialogImp-plugin: showVolumeDialogH reason:1 mActiveStream:3
RingerModeLayout: updateState is zen= false, state=false
RingerModeLayout: updateState is zen= true, state=true
```

This proves that one volume-key invocation reaches two
`RingerButtonHelper.updateState()` instances on this ROM. Combined with the
constructor ABI below, `mIsZen=false` identifies the Mute shortcut and
`mIsZen=true` identifies the DND shortcut. The live sample observed Mute off
and DND on; it did not change either user setting.

`uiautomator dump` did not expose the volume overlay because the overlay is
non-focusable; the captured tree remained the underlying Settings activity.
Use WindowManager/SurfaceFlinger/logcat plus the plugin artifact for this
surface, not accessibility-node absence as evidence that the panel is absent.

## Class evidence

### MiuiRingerModeLayout

File in plugin smali: `com/android/systemui/miui/volume/MiuiRingerModeLayout.smali`

Relevant instance fields:

```smali
.field public mDndHelper:Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;
.field public mRingerHelper:Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;
.field public mRingerMode:Z
.field public mZenMode:Z
```

`initialize()` (lines 159-209) shows how the two shortcuts are located and how
helpers are constructed:

```smali
.method private initialize()V
    sget v0, Lcom/android/systemui/miui/volume/R$id;->ringer_layout:I
    invoke-virtual {p0, v0}, Landroid/widget/LinearLayout;->findViewById(I)Landroid/view/View;
    move-result-object v0

    sget v1, Lcom/android/systemui/miui/volume/R$id;->dnd_layout:I
    invoke-virtual {p0, v1}, Landroid/widget/LinearLayout;->findViewById(I)Landroid/view/View;
    move-result-object v1

    ...

    new-instance v4, Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;
    const/4 v5, 0x0
    invoke-direct {v4, p0, v0, v5, v2}, ...-><init>(...Landroid/view/View;ZZ)V
    iput-object v4, p0, ...->mRingerHelper:...

    new-instance v4, Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;
    const/4 v6, 0x1
    invoke-direct {v4, p0, v1, v6, v3}, ...-><init>(...Landroid/view/View;ZZ)V
    iput-object v4, p0, ...->mDndHelper:...
```

The first `RingerButtonHelper` is constructed with the `ringer_layout` view,
third argument `0` and the current silent-mode state as the fourth argument.
The second helper is constructed with the `dnd_layout` view, third argument `1`
and the current zen-mode state as the fourth argument.

Conclusion:
- the constructor `View` argument (`p2`) is the whole shortcut root.
- third constructor argument `0` = ringer/Mute, `1` = DND.

### MiuiRingerModeLayout$RingerButtonHelper

File in plugin smali: `com/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper.smali`

Relevant instance fields:

```smali
.field public mBlurView:Lc/f/b/a/a/g;
.field public mExpanded:Z
.field public mIcon:Landroid/widget/ImageView;
.field public mIsZen:Z
.field public mLastState:Z
.field public mStandardView:Landroid/view/View;
.field public mState:Z
```

Constructor (`<init>(...Landroid/view/View;ZZ)V`):

```smali
.method public constructor <init>(Lcom/android/systemui/miui/volume/MiuiRingerModeLayout;Landroid/view/View;ZZ)V
    ...
    sget v0, Lcom/android/systemui/miui/volume/R$id;->miui_standard_btn:I
    invoke-virtual {p2, v0}, Landroid/view/View;->findViewById(I)Landroid/view/View;
    move-result-object v0
    iput-object v0, p0, ...->mStandardView:Landroid/view/View;

    ...
    iput-boolean p3, p0, ...->mIsZen:Z
    ...
```

`p2` is the whole root passed in by `MiuiRingerModeLayout.initialize()`;
`mStandardView` is the **inner** `miui_standard_btn` presentation layer, found
inside `p2`. The role field is `mIsZen` (set from `p3`).

`updateState()` is `private` and has no parameters:

```smali
.method private updateState()V
    .registers 8
```

It is reached through the package-private synthetic accessor:

```smali
.method public static synthetic access$000(Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;)V
    invoke-direct {p0}, ...->updateState()V
    return-void
.end method
```

## Resource evidence

From `aapt2 dump resources fuxi-MIUISystemUIPlugin.apk`:

```
resource 0x7f0a011a id/dnd_layout
resource 0x7f0a02ae id/ringer_layout
resource 0x7f0a008c id/bottom_layout
resource 0x7f0a0080 id/bg_blur
resource 0x7f0a01db id/miui_standard_btn
resource 0x7f0a017d id/icon
```

Layout `res/layout/miui_volume_dialog_ringer_mode.xml` (decompiled with
`pyaxmlparser.AXMLPrinter`):

```xml
<com.android.systemui.miui.volume.MiuiRingerModeLayout ...>
  <FrameLayout android:id="@7F0A01DA">
    <LinearLayout android:id="@7F0A01D9">
      <include android:id="@7F0A02AE" layout="@7F0D0076" />  <!-- ringer_layout -->
      <View android:id="@7F0A01DE" ... />                    <!-- divider -->
      <include android:id="@7F0A011A" layout="@7F0D0076" />  <!-- dnd_layout -->
    </LinearLayout>
  </FrameLayout>
</com.android.systemui.miui.volume.MiuiRingerModeLayout>
```

`res/layout/miui_ringer_mode_layout.xml` (the included single-button layout):

```xml
<LinearLayout android:id="@7F0A008C">                       <!-- bottom_layout -->
  <com.android.systemui.miui.volume.widget.VolumeBlurFrameLayout android:id="@7F0A0080">  <!-- bg_blur -->
    <LinearLayout android:id="@7F0A01DB">                   <!-- miui_standard_btn -->
      <ImageView android:id="@7F0A017D" ... />              <!-- icon -->
    </LinearLayout>
    <RoundCornerProgressBar ... />
  </com.android.systemui.miui.volume.widget.VolumeBlurFrameLayout>
  <include ... layout="@7F0D007B" />
</LinearLayout>
```

## Whole-root visibility ownership

Based on the exact plugin artifact
`SHA-256 = 3dafd9e068ebee7e88344ae1c7d146c7e2d41e79b5c52b7736cd3e58be0cc999`:

- `RingerButtonHelper` does **not** store the whole shortcut root `p2` in any
  of its instance fields.
- `RingerButtonHelper.updateState()` only mutates `mStandardView`, `mIcon`, and
  activation/selected/background/icon color/size state of the inner
  presentation layer.
- `RingerButtonHelper.onExpanded()` only mutates `mStandardView` layout params
  and `mBlurView`.
- The outer `MiuiRingerModeLayout` methods `updateExpandedStateH()` and
  `updateExpandedH()` only dispatch to each helper's `updateState()` or
  `onExpanded()`.
- `RingerButtonHelper.cleanUp()` only stops an internal worker thread.
- A search of `MiuiRingerModeLayout` and `MiuiRingerModeLayout$RingerButtonHelper`
  for `setVisibility` / `getVisibility` invocations on any whole-root `View`
  returns **zero** matches.

Therefore:

```text
RingerButtonHelper.updateState whole-root access = NO
Whole-root setVisibility by ROM = NO
ROM_WHOLE_ROOT_RESTORE = NOT_PRESENT
LAST_ROM_VISIBILITY_MUST_BE_OWNED_BY_MODULE = YES
```

The module must capture the root visibility before it first writes `View.GONE`,
track whether the current `GONE` is module-owned, and restore the captured
visibility when the hide preference is disabled. If the ROM or another owner
changes the root visibility while the module owns it or before the module
releases it, the newer visibility wins.

## Conclusions

| Question | Evidence | Verdict |
|----------|----------|---------|
| A. `miui_ringer_standard_btn` is whole Mute root? | No. `miui_standard_btn` is the inner presentation `LinearLayout`; the whole Mute root is `ringer_layout`. | NOT PROVEN as root. |
| B. `miui_ringer_dnd_btn` is whole DND root? | Resource not present in A14 plugin; DND root is `dnd_layout`. | NOT PROVEN; `miui_ringer_dnd_btn` does not exist. |
| C. `mRingerMode == 4` is Mute? | `RingerButtonHelper` has no `int mRingerMode`; `MiuiRingerModeLayout.mRingerMode` is a `boolean` state, and the helper role is `mIsZen`. | NOT PROVEN; remove fallback. |
| D. `mRingerMode == 1` is DND? | Same as C. | NOT PROVEN; remove fallback. |
| E. `mStandardView` is whole shortcut root? | `mStandardView` is `findViewById(R.id.miui_standard_btn)` inside the constructor `View`. | NOT PROVEN as root; it is a child. |
| Mute whole shortcut root | Constructor `View` argument `p2` for the first helper is `MiuiRingerModeLayout` `findViewById(ringer_layout)`. | PROVEN. |
| DND whole shortcut root | Constructor `View` argument `p2` for the second helper is `MiuiRingerModeLayout` `findViewById(dnd_layout)`. | PROVEN. |
| Mute role | `RingerButtonHelper` constructor receives `mIsZen = 0` for the `ringer_layout` helper. | PROVEN by explicit ROM mode field (`mIsZen = false`). |
| DND role | `RingerButtonHelper` constructor receives `mIsZen = 1` for the `dnd_layout` helper. | PROVEN by explicit ROM mode field (`mIsZen = true`). |
| UNKNOWN fail-open | If `mIsZen` cannot be read or the constructor `View` root is missing, role is UNKNOWN and no hide is applied. | YES. |

## Implementation rule derived from evidence

1. Hook all constructors of `MiuiRingerModeLayout$RingerButtonHelper` using the
   `miui.systemui.plugin` ClassLoader.

2. Constructor `after` hook:
   - `callback.getArg(1)` is the whole shortcut root: `ringer_layout` for Mute,
     `dnd_layout` for DND.
   - `helper.mIsZen` (`boolean`) is the sole role discriminator:
     `false` = Mute, `true` = DND.
   - UNKNOWN is fail-open: no role, no root, no hide.
   - Store `(role, WeakReference<View>(root))` in additional instance fields.
   - Create a per-helper visibility ownership state.
   - Capture `root.visibility` before any custom `View.GONE` write.

3. Ownership state:
   - `romVisibility` = last real ROM / external visibility.
   - `customHidden` = whether the current `GONE` was written by this module.
   - The state must not hold a `View`, `Context`, `Activity`, `ClassLoader`, or
     controller; the only view reference remains the pre-bound `WeakReference`.

4. Constructor `after` and `updateState` `after` hooks:
   - Read the prepared `VolumeModeButtonVisibilitySnapshot`.
   - Read the pre-bound role, `WeakReference<View>`, and ownership state.
   - Compute `shouldHide` independently for Mute and DND.
   - Reconcile through the production reversible ownership helper.
   - Write `root.visibility` only when reconciliation requests a write.
   - Skip custom work if the original ROM callback threw; do not replace or
     swallow the original throwable.

5. Hide enable:
   - If the current root is already `GONE` and the module does not own it, do
     not claim it and do not write.
   - Otherwise capture the current real visibility as `romVisibility`, mark
     `customHidden = true`, and write `View.GONE`.

6. Hide disable:
   - If the current `GONE` is module-owned, restore the exact saved
     `romVisibility`.
   - Never hard-code `View.VISIBLE` as the restore value.

7. External state:
   - If another owner changes visibility while the module owns `GONE`, adopt
     that newer visibility as the restore baseline and re-apply `GONE`.
   - If the external owner changes visibility before the module releases it,
     external state wins and the module does not overwrite it.

8. Boundary:
   - Constructor `after` and `updateState` `after` are the only visibility
     boundaries used.
   - No `updateState` `before` hook is required because exact ROM evidence
     proves `updateState()` does not access the whole root.

9. Hot path:
   - No preference reads.
   - No resource lookup (`getIdentifier`, `getResourceEntryName`, etc.).
   - No role discovery beyond the pre-bound `mIsZen`.
   - No `findViewById`, parent traversal, or `resources` access.
   - No new `WeakReference` allocation per callback.
   - No ownership state allocation per callback.

10. Current architectural limitations remain:

    - `IMMEDIATE NO-EVENT REPAINT = DEFERRED`
    - `COLD BOTH-OFF → LIVE ENABLE = ARCHITECTURALLY DEFERRED`

## Shared visual container ownership

Device evidence on `fuxi` / `V816.0.7.0.UMCTWXM` with both Mute and DND roots
hidden (`ringer_layout` and `dnd_layout` both `GONE`) shows:

- `miui_volume_ringer_divider` = `VISIBLE` (size 849x64)
- `miui_ringer_btn_layout` = `VISIBLE` (size 849x395)
- `miui_volume_ringer_layout` = `VISIBLE` (size 832x395, `GradientDrawable` background)

The residual shadow / empty shell comes from the shared container and divider
that live **outside** the individual button roots.

### Exact hierarchy from the plugin layout

`res/layout/miui_volume_dialog_ringer_mode.xml`:

```
com.android.systemui.miui.volume.MiuiRingerModeLayout (id=miui_volume_ringer_layout)
└── FrameLayout
    └── LinearLayout (id=miui_ringer_btn_layout)
        ├── include (id=ringer_layout)
        ├── View       (id=miui_volume_ringer_divider)
        └── include (id=dnd_layout)
```

### Collapse-owner decision

- `miui_volume_ringer_layout` (the outer `MiuiRingerModeLayout`) is the
  authoritative parent. Hiding it alone fully collapses the `FrameLayout`,
  `miui_ringer_btn_layout`, the divider, and both button roots.
- `miui_ringer_btn_layout` and the intermediate `FrameLayout` do **not** need
  independent module visibility writes.
- The divider (`miui_volume_ringer_divider`) is a direct child of
  `miui_ringer_btn_layout` and must be hidden whenever **at least one** button
  is hidden, because a separator between two buttons is visually wrong when one
  of them is gone.

### Final policy

```text
SHADOW_FAILURE_BOUNDARY = shared divider/container outside helper roots

dividerHidden = hideMute || hideDnd
containerHidden = hideMute && hideDnd
```

### Ownership requirements

The same reversible ownership model used for the button roots is extended to
the shared views:

- One `VolumeModeSharedVisibilityState` per `MiuiRingerModeLayout` instance.
- The state is shared by both `RingerButtonHelper` instances belonging to the
  same layout.
- Capture `layout.visibility` and `divider.visibility` before any module `GONE`
  write.
- Track `romVisibility` and `customHidden` per shared view.
- Restore the exact captured visibility when module hide is disabled.
- If the ROM or another owner changes a shared view while module-hidden, adopt
  that newer visibility as the restore baseline.
- ROM-owned `GONE` must not be claimed by the module.
- No hard-coded `View.VISIBLE` restoration.

### Binding

At cold constructor time, the helper receives the outer `MiuiRingerModeLayout`
as constructor argument 0 and the whole shortcut root as argument 1. The
shared `miui_volume_ringer_layout` is the layout instance itself. The divider
is resolved through `layout.findViewById(layout.resources.getIdentifier(
"miui_volume_ringer_divider", "id", ...))`. This is a cold-only resource
lookup; the hot `updateState` path only reads pre-bound state and writes the
reconciled visibility.

## Volume Mute / DND shortcut color ownership

Device evidence on `fuxi` / `V816.0.7.0.UMCTWXM` shows that previous module
writes to `mStandardView.backgroundTintList`, `mBlurView.backgroundTintList`, and
`mIcon.imageTintList` succeed at the Android property level but have no visible
result. The active ROM visual path is different.

### ROM icon visual owner

In `com/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper.smali`
around `setIcon()` and `updateState()` the ROM sets the icon color through:

```smali
invoke-virtual {p0, v0}, Landroid/widget/ImageView;->setColorFilter(I)V
```

That is `ImageView.setColorFilter(int)` (one-argument overload, default blending
mode `PorterDuff.Mode.SRC_ATOP`). The module now applies the configured icon
color through the same channel by creating an immutable
`PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)` and calling
`ImageView.setColorFilter(ColorFilter)` on the pre-bound `mIcon` after the ROM
`updateState()` has completed.

### ROM background visual owner

The visible shortcut background is not owned by the `View`'s `backgroundTintList`
property. The actual background is the `mStandardView.background` drawable:

```text
mStandardView = android.widget.LinearLayout (id=miui_standard_btn)
mIcon       = android.widget.ImageView       (id=icon)
mBlurView   = com.android.systemui.miui.volume.widget.VolumeBlurFrameLayout (id=bg_blur)
```

The background drawable chain is a `miuix.smooth.SmoothContainerDrawable` that
wraps a child `<shape>` with a `<solid>` color. The observed selected ROM color
on device is `#FF3482FF`; the solid color source is the selector
`res/color/miui_volume_ringer_btn_bg_color.xml`.

Drawable chain:

```text
miuix.smooth.SmoothContainerDrawable
└── child
    └── shape (GradientDrawable)
        └── solid
            └── ColorStateList selector
                ├── state_selected=true  -> color/miui_volume_color_btn_seleted
                └── state_selected=false -> color/miui_volume_bg_color
```

The module now applies the configured background color by mutating the actual
background drawable and setting a `PorterDuffColorFilter(backgroundColor,
PorterDuff.Mode.SRC_IN)` on it. This is the same `Drawable.setColorFilter`
public API, but targeted at the actual visual owner rather than at the
ineffective `backgroundTintList` view property.

### Module operations

```text
ICON_ROM_VISUAL_OWNER   = ImageView.setColorFilter(int)
ICON_MODULE_OPERATION   = ImageView.setColorFilter(PorterDuffColorFilter)

BACKGROUND_DRAWABLE_CHAIN = SmoothContainerDrawable -> child -> shape -> solid -> ColorStateList
BACKGROUND_ROM_VISUAL_OWNER = mStandardView.background (SmoothContainerDrawable)
BACKGROUND_MODULE_OPERATION = standardView.background.mutate().setColorFilter(PorterDuffColorFilter)

SHARED_RESOURCE_MUTATION = YES
MUTATE_ISOLATION = YES
MUTATE reason = setBackgroundResource loads a drawable that shares a ConstantState
                with the same resource used by the sibling shortcut and
                potentially by other volume panels. Calling setColorFilter on a
                shared drawable would recolor those other instances. mutate()
                produces a per-view ConstantState copy before the color filter is
                applied.
```

### Hot path requirements

- All color preferences are resolved into the process-owned
  `VolumeModeButtonColorSnapshot` once, before `updateState()` runs.
- `updateState()` and constructor `after` hooks read the snapshot and
  pre-bound `WeakReference<View>` / `WeakReference<ImageView>`.
- `PorterDuffColorFilter` objects are cached per helper and only recreated when
  the configured color changes.
- The hot path does not read preferences, perform resource lookups,
  discover fields through reflection, or allocate ownership state per callback.

### Disable / restore behavior

When the color feature is disabled, the module stops writing custom color
filters on the next `updateState()`. The ROM continues to call
`mStandardView.setBackgroundResource(...)` and `mIcon.setColorFilter(romColor)`,
so the original ROM visual path is restored for the background drawable and the
icon. The module does not attempt to store or restore ROM colors because the ROM
itself re-applies them at the next state update.

## Active-state color semantics

ROM decompilation of `MiuiRingerModeLayout$RingerButtonHelper.updateState()`
shows that color is a function of the button's **selected** state, not of the
shortcut role or of an unconditional module override. The relevant ROM decision
tree for the icon and background can be summarized as:

- `mState == true` (selected / active):
  - Blur disabled: the icon gets an active color filter; the background gets an
    active blend color or active background drawable.
  - Blur enabled: `Util.setMiViewBlurAndBlendColor` is called on both the icon
    and the background with the active blend-color arrays.
- `mState == false` (unselected / inactive):
  - ROM reverts the icon and background to the inactive blend colors, inactive
    drawable, or collapsed/expanded defaults.
- `mExpanded`, dark/light theme, and blur-support flags only affect **which**
  ROM color or blend is used for a given state; they do not remove the
  active/inactive distinction.

The module's previous implementation applied one user color to both states, which
collapsed the active/inactive distinction on the device. The corrected
implementation:

1. Reads `RingerButtonHelper.mState` on every `updateState()` callback.
2. Only applies the user background and icon `PorterDuffColorFilter`s when
   `mState == true`.
3. When `mState == false`, removes a module-owned color filter only if the
   current filter on the view/drawable is exactly the cached module filter. This
   preserves any ROM filter the ROM may have just installed and avoids leaving
   the previous active color behind when the ROM did not replace the view.
4. Does not re-implement the ROM's expanded/collapsed or blur/dark branches;
   those remain ROM-owned and are restored automatically when the ROM re-runs
   `updateState()`.

This keeps the user color as the active-state color while preserving ordinary
ROM behavior for the inactive state.

## Gate / status

- `VOLUME_ENGINEERING_GATE = PASS`
- `ROOT_VISIBILITY = PASS`
- `SHARED_VISIBILITY = PASS`
- `COLOR_VISUAL_OWNER = PASS`
- `ACTIVE_STATE_COLOR_SEMANTICS = PASS`
- `DEVICE_ACCEPTANCE = PENDING_FINAL_SIGNED_APK`

Engineering implementation is complete and consistent with ROM evidence. Final
real-device visual verification (mute/DND hide states, shared container/divider
collapse, color application, no empty shell/shadow) requires the signed release
APK built from the final maintenance tree.
