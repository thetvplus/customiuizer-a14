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
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/phone/StatusBarIconControllerImpl;'
 AND method_name IN ('setMobileIcons','removeAllIconsForSlot'))
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/phone/MiuiStatusBarSignalPolicy;'
 AND method_name = 'setSubs')
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/StatusBarMobileView;'
 AND method_name IN ('applyMobileState','updateState','setDripEnd','onDarkChanged','applyDarknessInternal'))
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/views/MiuiBatteryMeterView;'
 AND method_name IN ('onAttachedToWindow','onDetachedFromWindow','onBatteryLevelChanged'))
ORDER BY file_id, method_descriptor;

-- Mobile state and its native image and tint owners.
SELECT file_id, field_descriptor FROM class_fields
WHERE (class_descriptor = 'Lcom/android/systemui/statusbar/StatusBarMobileView;'
 AND field_name IN ('mState','mMobile','mSmallHd','mSmallRoaming','mMobileRoaming','mLight','mUseTint'))
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/phone/StatusBarIconControllerImpl;'
 AND field_name = 'mContext')
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/phone/StatusBarSignalPolicy$MobileIconState;'
 AND field_name IN ('airplane','dataConnected','showName','strengthId','subId'))
 OR (class_descriptor = 'Lcom/android/systemui/statusbar/phone/StatusBarSignalPolicy$SignalIconState;'
 AND field_name IN ('activityIn','activityOut','visible'))
ORDER BY file_id,field_descriptor;

-- Decode onDarkChanged to confirm this draw call follows its native tint and image writes.
SELECT file_id, method, callee FROM method_calls
WHERE callee = 'Lcom/android/systemui/statusbar/StatusBarMobileView;->applyDarknessInternal()V'
 OR (method LIKE 'Lcom/android/systemui/statusbar/StatusBarMobileView;->applyMobileState(%'
 AND callee IN (
 'Lcom/android/systemui/statusbar/phone/StatusBarSignalPolicy$MobileIconState;->equals(Ljava/lang/Object;)Z',
 'Lcom/android/systemui/statusbar/StatusBarMobileView;->updateState(Lcom/android/systemui/statusbar/phone/StatusBarSignalPolicy$MobileIconState;)Z'))
 OR (method = 'Lcom/android/systemui/statusbar/phone/MiuiStatusBarSignalPolicy;->setSubs(Ljava/util/List;)V'
 AND callee IN (
 'Lcom/android/systemui/statusbar/phone/StatusBarIconControllerImpl;->removeAllIconsForSlot(Ljava/lang/String;)V',
 'Lcom/android/systemui/statusbar/phone/StatusBarIconControllerImpl;->setMobileIcons(Ljava/lang/String;Ljava/util/List;)V'))
ORDER BY file_id, method;
