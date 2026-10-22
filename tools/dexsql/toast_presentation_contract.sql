-- Input: supported SystemUI APKs and boot framework.jar, with file_id kept per definition.
-- Decode both regions: plain ToastPresenter insertion/hide and the native text_bitmap model path.
SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
FROM class_methods
WHERE (class_descriptor='Landroid/widget/ToastPresenter;'
  AND method_name IN ('addToastView','show','hide','adjustLayoutParams','createLayoutParams','updateLayoutParams'))
   OR (class_descriptor='Lcom/android/systemui/toast/SystemUIToast;'
  AND method_name IN ('<init>','getView','onOrientationChange'))
   OR (class_descriptor='Lcom/android/systemui/toast/ToastUI;'
  AND method_name='onConfigurationChanged')
   OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToastControl;'
  AND method_name IN ('<init>','showCustomStrongToast'))
   OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToast;'
  AND method_name IN ('showCustomStrongToast','hideStrongToast','setValue'))
UNION ALL
SELECT 'field', file_id, field_descriptor
FROM class_fields
WHERE (class_descriptor='Landroid/widget/ToastPresenter;'
  AND field_name IN ('mView','mParams','mPackageName'))
   OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToastControl;'
  AND field_name IN ('mContext','mMiuiStrongToast'))
   OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToast;'
  AND field_name IN ('mLeftTextView','mCurrentToastCategory','mStrongToastShow','mCheckStartAnimation','mCheckInOutStrongToasting'))
ORDER BY file_id, kind, descriptor;
