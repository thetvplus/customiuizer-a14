-- Launcher, SystemUI and boot framework inputs may share one dataset; file_id owns each definition.
-- Follow native HotSeats rendering and computed editing transformations; do not advance animations.
SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
FROM class_methods
WHERE (class_descriptor = 'Lcom/miui/home/launcher/Launcher;'
  AND method_name IN ('isFolderShowing', 'fadeInOrOutScreenContentWhenFolderAnimate',
    'resetScreenContent', 'setScreenContentAlpha', 'showBottomAreaEditPanelIfNeed'))
   OR (class_descriptor = 'Lcom/miui/home/launcher/hotseats/HotSeats;'
  AND method_name IN ('updateContent', 'onMeasure', 'onWallpaperColorChanged'))
   OR (class_descriptor = 'Lcom/miui/home/launcher/hotseats/HotSeatsScreenViewContent;'
  AND method_name IN ('onLayout', 'layoutImmediately', 'addItemIcon', 'removeItemIcon', 'onDrop', 'onDragExit'))
   OR (class_descriptor = 'Lcom/miui/home/launcher/DragController;'
  AND method_name IN ('endDrag', 'cancelDrag'))
   OR (class_descriptor = 'Lcom/android/internal/policy/ScreenDecorationsUtils;'
  AND method_name IN ('getMiuiWindowCornerRadius', 'getWindowCornerRadius'))
   OR (class_descriptor = 'Landroid/view/RoundedCorners;'
  AND method_name IN ('getRoundedCornerBottomRadius', 'getRoundedCornerRadiusBottomAdjustment'))
   OR (class_descriptor = 'Lcom/miui/home/recents/NavStubView;'
  AND method_name IN ('enterHomeHoldState', 'exitHomeHoldState'))
   OR (class_descriptor = 'Landroid/view/animation/AnimationSet;'
  AND method_name = 'getTransformation')
   OR (class_descriptor = 'Lcom/miui/blur/sdk/backdrop/BackdropBlurRelativeLayout;'
  AND method_name IN ('isGoogleBlurSupported', 'setBlurEnabled', 'setBlurRadius',
    'setCornerRadius', 'onAttachedToWindow', 'getParentAlpha', 'onVisibilityAggregated'))
   OR (class_descriptor = 'Lcom/android/internal/graphics/drawable/BackgroundBlurDrawable;'
  AND method_name IN ('setBounds', 'updatePath', 'setAlpha'))
   OR (class_descriptor = 'Landroid/app/WallpaperManager$Globals;'
  AND method_name = 'getCurrentWallpaperSnapshot')
   OR (class_descriptor = 'Landroid/window/ScreenCapture;'
  AND method_name = 'captureWallpaperLayer')
   OR (class_descriptor = 'Landroid/window/ScreenCapture$WallpaperCaptureArgs$Builder;'
  AND method_name IN ('<init>', 'build'))
   OR (class_descriptor = 'Landroid/window/ScreenCapture$CaptureArgs$Builder;'
  AND method_name IN ('setSourceCrop', 'setFrameScale', 'setUid'))
   OR (class_descriptor = 'Landroid/window/ScreenCapture$ScreenshotHardwareBuffer;'
  AND method_name IN ('getHardwareBuffer', 'getColorSpace', 'containsSecureLayers', 'containsHdrLayers'))
   OR (class_descriptor = 'Lcom/android/systemui/navigationbar/NavigationBar$1;'
  AND method_name IN ('getSampledRegion', 'onRegionDarknessChanged'))
UNION ALL
SELECT 'field' AS kind, file_id, field_descriptor AS descriptor
FROM class_fields
WHERE (class_descriptor = 'Lcom/miui/home/launcher/Launcher;'
  AND field_name IN ('mHotseatEditingEnter', 'mHotseatEditingExit'))
   OR (class_descriptor = 'Lcom/miui/home/launcher/hotseats/HotSeats;'
  AND field_name IN ('mContent', 'mDragController'))
   OR (class_descriptor = 'Lcom/miui/home/launcher/DragController;'
  AND field_name IN ('mLauncher', 'mDragging'))
   OR (class_descriptor = 'Lcom/miui/home/launcher/Launcher;'
  AND field_name = 'mHotSeats')
ORDER BY file_id, kind, descriptor;

-- Native icon ownership, independent of Dock decoration. Query declarations first,
-- then decode actual callers; a floating spring's onAnimationEnd is not its cleanup owner.
SELECT file_id, method_idx, method_descriptor FROM class_methods
WHERE (class_descriptor='Lcom/miui/home/launcher/ItemIcon;'
 AND method_name IN ('setIconVisibility','getIconLocation','getIconImageViewOriginalLocation'))
 OR (class_descriptor='Lcom/miui/home/launcher/ShortcutIcon;'
 AND method_name IN ('onBackAnimStart','onBackAnimStop','onEnterHomeAnimStart','onEnterHomeAnimFinish',
 'postResetBackAnim','resetPendingBackAnim','rebindInfo'))
 OR (class_descriptor='Lcom/miui/home/launcher/Workspace;' AND method_name='getCurrentShowShortcutIcons')
 OR (class_descriptor='Lcom/miui/home/launcher/anim/LaunchAppAndBackHomeAnimTarget;'
 AND method_name IN ('onLaunchAppAnimStart','onLaunchAppWindowAlphaChange','onEnterHomeAnimFinish','showIcon'))
 OR (class_descriptor='Lcom/miui/home/recents/views/FloatingIconView2;'
 AND method_name IN ('init','setIcon','onAnimationEnd','onAnimationEnd$lambda-3','onAnimationCancel',
 'finishImmediately','recycle','recycleView','recycleView$lambda-7','prepareBackAnim$lambda-1'))
 OR (class_descriptor='Lcom/miui/home/recents/views/FloatingIconView;'
 AND method_name IN ('finish','finishImmediately','resetShortcutIcon','onAnimationCancel','onAnimationEnd'))
 OR (class_descriptor='Lcom/miui/home/recents/anim/WindowElement;'
 AND method_name IN ('onAnimCancelByShellFinishedExecute','onFinishCompleted','resetFloatingIcon',
 'resetFloatingIcon$lambda-7','handleFloatingIconViewWhenNewTargetViewNull','updateLauncherTargetView',
 'initTargetView','updateFloatingIconView','replaceFloatingIconViewContent','cancelAnim',
 'cancelAnimWithoutEndAndEndListener','cancelAnimWithoutEndListener','finishTransition','isSameElement'))
 OR (class_descriptor IN ('Lcom/miui/home/recents/anim/WindowElement$mRectFSpringAnimListener$1;',
 'Lcom/miui/home/recents/anim/WindowAnimParamsProvider$getClosingWindowAnimParams$animListener$1;',
 'Lcom/miui/home/recents/anim/WindowAnimParamsProvider$getOpeningWindowsAnimParams$animListener$1;',
 'Lcom/miui/home/recents/anim/WindowAnimParamsProvider$getQuickOpeningWindowsAnimParams$animListener$1;')
 AND method_name IN ('onAnimationStart','onAnimationCancel','onAnimationEnd'))
 OR (class_descriptor='Lcom/miui/home/recents/anim/WindowElement$init$1;'
 AND method_name IN ('onTransitionMerged','onTransitionMerged$lambda-1','onFinishCompleted','onAnimCancelByShellFinished'))
 OR (class_descriptor='Lcom/miui/home/recents/util/RectFSpringAnim;'
 AND method_name IN ('cancel','cancelWithoutEnd','cancelDirectly','lambda$initListener$1$RectFSpringAnim'))
 OR (class_descriptor='Lcom/miui/home/library/utils/LooperExecutor;' AND method_name='execute')
 OR (class_descriptor='Lcom/miui/home/recents/QuickstepAppTransitionManagerImpl;'
 AND method_name IN ('hasControlRemoteAppTransitionPermission','registerRemoteAnimations','registerRemoteTransitions',
 'lambda$registerRemoteTransitions$3','resetShortcutIconAlpha'))
ORDER BY file_id, method_idx;

SELECT file_id, method, callee FROM method_calls
WHERE (callee_class='Lcom/miui/home/launcher/anim/LaunchAppAndBackHomeAnimTarget;'
 AND callee_name IN ('onLaunchAppAnimStart','onEnterHomeAnimStart','onLaunchAppWindowAlphaChange','showIcon'))
 OR (callee_class='Lcom/miui/home/recents/FloatingIconInterface;' AND callee_name='recycle')
 OR (callee_class='Lcom/miui/home/recents/views/FloatingIconView2;' AND callee_name='finishImmediately')
 OR (callee_class='Lcom/miui/home/recents/anim/WindowElement;'
 AND callee_name IN ('resetFloatingIcon','onFinishCompleted','onAnimCancelByShellFinishedExecute',
 'cancelAnimWithoutEndAndEndListener','cancelAnimWithoutEndListener'))
ORDER BY file_id, method_idx, callee_method_idx;

-- Visibility owners are instances, not package names. Read get/put sites together;
-- a delayed runnable reading a mutable target is a candidate, not proof of a race.
SELECT file_id, method, field, using_type FROM method_field_accesses
WHERE (field_class='Lcom/miui/home/recents/anim/WindowElement;'
 AND field_name IN ('launcherTargetView','floatingIcon','allowEndListener','isDuringMerge'))
 OR (field_class='Lcom/miui/home/recents/views/FloatingIconView2;'
 AND field_name IN ('mAnimTargetRef','hasInit'))
 OR (field_class='Lcom/miui/home/recents/QuickstepAppTransitionManagerImpl;'
 AND field_name IN ('mOpeningAnimTargetRef','mIsOpenAnimRunning'))
 OR field_name='mSkipCallExecuteFinish'
ORDER BY file_id, method_idx, field_idx, using_type;

-- Compare coordinate consumers before attributing a scaled landing to icon hiding.
SELECT file_id, method, callee FROM method_calls
WHERE (callee_name IN ('getIconLocation','getIconImageViewOriginalLocation')
 AND callee_class LIKE 'Lcom/miui/home/%')
 OR (callee_class='Lcom/miui/home/launcher/animate/SpringAnimator;' AND callee_name='getSpringForce')
 OR (callee_class='Lcom/miui/home/recents/QuickstepAppTransitionManagerImpl;'
 AND callee_name='hasControlRemoteAppTransitionPermission')
ORDER BY file_id, method_idx, callee_method_idx;

-- Native focused-notification IPC can select a synthetic status-bar target.
-- It is distinct from the module's SystemUI island; callers do not prove runtime causality.
SELECT file_id, method, callee FROM method_calls
WHERE callee_name IN ('onFocusedNotifUpdate','onFocusNotifyAnimFinish','isNeedShowStatusBarTypeAnim')
ORDER BY file_id, method_idx, callee_method_idx;
