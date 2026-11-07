-- A14 SystemUI artifacts. Missing optional audio/Control Center members are
-- independent fallback boundaries; methods with equal arity are not substitutes.
SELECT file_id, source_path, entry_name FROM dex_files ORDER BY file_id;

SELECT file_id, method_descriptor FROM class_methods
WHERE class_descriptor IN (
 'Lcom/android/systemui/statusbar/views/MiuiBatteryMeterView;',
 'Lcom/android/systemui/statusbar/policy/BatteryControllerImpl;',
 'Lcom/android/systemui/statusbar/policy/MiuiBatteryControllerImpl;',
 'Lcom/android/systemui/statusbar/policy/MiuiWifiSignalController;',
 'Lcom/android/systemui/statusbar/connectivity/MobileSignalController;',
 'Lcom/android/systemui/statusbar/connectivity/CallbackHandler;',
 'Lcom/android/systemui/statusbar/StatusBarWifiView;',
 'Lcom/android/systemui/statusbar/StatusBarMobileView;',
 'Lcom/android/systemui/statusbar/mobile/MobileStatusTracker$MobileTelephonyCallback;',
 'Lcom/android/systemui/statusbar/policy/BluetoothControllerImpl;')
AND method_name IN ('onAttachedToWindow','onDetachedFromWindow','updateAll','onDarkChanged',
 'fireBatteryLevelChanged','firePowerSaveChanged','notifyListeners','setIsAirplaneMode','setSubs',
 'applyWifiState','applyMobileState','updateState','setVisibleState','onActiveDataSubscriptionIdChanged',
 'onConnectionStatusFetched','onActiveDeviceChanged','onBluetoothStateChanged','setBluetoothBattery')
ORDER BY method_descriptor;

SELECT file_id, field_descriptor FROM class_fields
WHERE class_descriptor GLOB 'Lcom/android/systemui/statusbar/*'
AND field_name IN ('mLevel','mCharging','mPowerSave','mCurrentState','mSubscriptionInfo',
 'mWifiSignalController','mMobileSignalControllers','mAirplaneMode','mState','mBatteryPercentView',
 'mIntentReceiver','mHeadsetMap','mEnabled','mConnectionState','mAudioProfileOnly','mIsActive',
 'mBluetoothBattery','mConnectedDevices') ORDER BY field_descriptor;
