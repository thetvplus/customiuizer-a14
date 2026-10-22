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
