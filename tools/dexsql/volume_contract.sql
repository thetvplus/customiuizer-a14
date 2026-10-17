-- Run against the plugin APK and host APK together to identify artifact scope.
-- Definition views avoid mistaking an external reference for a declared member.
SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
FROM class_methods
WHERE class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;'
  AND method_name IN ('<init>', 'updateState')
UNION ALL
SELECT 'field' AS kind, file_id, field_descriptor AS descriptor
FROM class_fields
WHERE class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiRingerModeLayout$RingerButtonHelper;'
  AND field_name IN ('mIsZen', 'mStandardView', 'mBlurView', 'mIcon')
ORDER BY file_id, kind, descriptor;
