-- Run against the plugin APK and host APK together to identify artifact scope.
-- Definition views avoid mistaking an external reference for a declared member.
SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
FROM class_methods
WHERE (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;'
  AND method_name IN ('<init>', 'updateState'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeTimerDrawableHelper;'
  AND method_name IN ('initTimerString', 'updateCountDownStateText', 'updateDrawables'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/TimerItem;'
  AND method_name IN ('<init>', 'getTimePos'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/widget/TimerSeekBar;'
  AND method_name IN ('<init>', 'setTimeSegments'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl;'
  AND method_name IN ('computeTimeoutH', 'updateDialogWindowH', 'showH', 'showVolumeDialogH',
                      'dismissH', 'startBlurAnim', 'updateVolumeColumnH', 'getImpliedLevel'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl$VolumeSeekBarChangeListener;'
  AND method_name IN ('<init>', 'onProgressChanged'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/Util;'
  AND method_name = 'isSupportBlurS')
   OR (class_descriptor = 'Lcom/android/systemui/controlcenter/policy/MiuiBrightnessController;'
  AND method_name IN ('onStart', 'onStop', 'onChanged', 'setToggleSliderBase'))
UNION ALL
SELECT 'field' AS kind, file_id, field_descriptor AS descriptor
FROM class_fields
WHERE (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;'
  AND field_name IN ('mIsZen', 'mState', 'mStandardView', 'mBlurView', 'mIcon'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeTimerDrawableHelper;'
  AND field_name IN ('mCurrentSegment', 'mDeterminedSegment', 'mTimeSegmentTitle', 'mContext'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/TimerItem;'
  AND field_name IN ('mContext', 'mDeterminedSegment', 'mTimerSeekbarWidth', 'mTimerTime'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl;'
  AND field_name IN ('mHovering', 'mExpanded', 'mIsSafetyShowing', 'mSafetyWarning',
                     'mAccessibilityMgr', 'mDialogView', 'mWindow', 'mShowing', 'mActiveStream'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl$VolumeSeekBarChangeListener;'
  AND field_name IN ('this$0', 'mColumn'))
   OR (class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl$VolumeColumn;'
  AND field_name IN ('ss', 'stream', 'anim', 'animTargetProgress'))
ORDER BY file_id, kind, descriptor;
