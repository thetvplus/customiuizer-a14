-- SystemUI inputs. Preserve the ROM's rotation/cutout and subscription ownership.
SELECT file_id, method_descriptor FROM class_methods
WHERE (class_descriptor = 'Lcom/android/systemui/statusbar/phone/StatusBarContentInsetsProvider;'
 AND method_name IN ('getStatusBarContentInsetsForCurrentRotation','getStatusBarContentAreaForRotation',
 'onDensityOrFontScaleChanged','onMaxBoundsChanged','onThemeChanged'))
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/phone/MiuiPhoneStatusBarView;'
 AND method_name IN ('onFinishInflate','onAttachedToWindow','onDetachedFromWindow','updateResources',
 'updateCutoutLocation','updateNotificationIconAreaInnnerParent'))
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/connectivity/NetworkControllerImpl;'
 AND method_name IN ('updateMobileControllers','doUpdateMobileControllers'))
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/views/MiuiBatteryMeterView;'
 AND method_name IN ('onAttachedToWindow','onDetachedFromWindow','onBatteryLevelChanged'))
ORDER BY file_id, method_descriptor;
