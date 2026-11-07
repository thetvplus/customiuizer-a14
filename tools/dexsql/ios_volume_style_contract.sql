-- A14 host and plugin artifacts. Full definitions, not external method references.
SELECT file_id, source_path, entry_name FROM dex_files ORDER BY file_id;

SELECT file_id, method_descriptor FROM class_methods
WHERE class_descriptor='Lcom/android/systemui/volume/VolumeDialogControllerImpl;'
 AND method_name='onVolumeChangedW';

SELECT file_id, method_descriptor FROM class_methods
WHERE class_descriptor IN (
 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl;',
 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl$VolumeSeekBarChangeListener;',
 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogMotion;',
 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogMotion$Callback;',
 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogView;',
 'Lcom/android/systemui/miui/volume/MiuiVolumeSeekBar;',
 'Lcom/android/systemui/miui/volume/MiuiVolumeSeekBarProgressView;',
 'Lcom/android/systemui/miui/volume/RoundRectFrameLayout;',
 'Lmiui/systemui/util/MiBlurCompat;',
 'Lc/f/b/a/a/g;')
AND method_name IN ('showH','showVolumeDialogH','updateColumnH','updateExpandedH','destroy',
 'updateColumnSliderBlendColor','updateVolumeColumnH','onProgressChanged','onStartTrackingTouch',
 'onStopTrackingTouch','createShowAnimator','onComputeInternalInsets','onTouchEvent',
 'isBlurEnabledAndSupported','setBlurEnabled','setProgress','dispatchDraw',
 'onExpandClicked','getBackgroundBlurOpened') ORDER BY method_descriptor;

SELECT file_id, field_descriptor FROM class_fields
WHERE class_descriptor GLOB 'Lcom/android/systemui/miui/volume/MiuiVolume*'
AND field_name IN ('mExpanded','mShowing','mDialogView','mDialogContentView','mShadowView',
 'mExpandButton','mColumns','mTempColumn','view','slider','progressView','progressViewBg','icon',
 'mColumn','ss','mHeight','mProgress','mIsExpandButton','mCallback','mMotion') ORDER BY field_descriptor;

SELECT file_id, method, callee FROM method_calls
WHERE method_class='Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl;'
AND method_name IN ('showH','updateExpandedH')
AND callee_name IN ('showVolumeDialogH','updateExpandedH') ORDER BY method,callee;
