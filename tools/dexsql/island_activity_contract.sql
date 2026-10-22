-- Inputs: CN/TW SystemUI APKs plus their boot framework.jar. Keep file_id provenance.
-- Definitions only; NotificationMediaManager owns primary-player selection and normal callbacks.
SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
FROM class_methods
WHERE (class_descriptor='Lcom/android/systemui/statusbar/NotificationMediaManager;'
 AND method_name IN ('<init>','findAndUpdateMediaNotifications','dispatchUpdateMediaMetaData'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/NotificationMediaManager$1;'
 AND method_name IN ('onPlaybackStateChanged','onMetadataChanged','onSessionDestroyed'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/StatusBarStateControllerImpl;'
 AND method_name IN ('<init>','addCallback','removeCallback','getState','isDozing','isExpanded'))
 OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToast;'
 AND method_name IN ('getWindowParam','showCustomStrongToast','hideStrongToast','onDetachedFromWindow'))
 OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToastControl;'
 AND method_name='showCustomStrongToast')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/coordinator/HeadsUpCoordinator$bindForAsyncHeadsUp$1;'
 AND method_name='onBindFinished')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/interruption/HeadsUpViewBinder;'
 AND method_name IN ('abortBindCallback','unbindHeadsUpView'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/NotificationClicker;'
 AND method_name='onClick')
 OR (class_descriptor='Landroid/media/AudioManager;'
 AND method_name IN ('setRingerMode','setRingerModeInternal'))
 OR (class_descriptor='Lcom/android/systemui/toast/SystemUIToast;'
 AND method_name IN ('<init>','getView','onOrientationChange'))
 OR (class_descriptor='Landroid/widget/ToastPresenter;'
 AND method_name IN ('addToastView','show','hide'))
UNION ALL
SELECT 'field', file_id, field_descriptor FROM class_fields
WHERE (class_descriptor='Lcom/android/systemui/statusbar/NotificationMediaManager;'
 AND field_name IN ('mMediaController','mMediaMetadata','mMediaListener','mMediaNotificationKey'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/StatusBarStateControllerImpl;'
 AND field_name IN ('mState','mIsDozing','mIsExpanded'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;'
 AND field_name IN ('mSbn','mRanking','row','mSensitive','hideSensitiveByAppLock','mIcons','interruption'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/coordinator/HeadsUpCoordinator$bindForAsyncHeadsUp$1;'
 AND field_name='$tmp0')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/coordinator/HeadsUpCoordinator;'
 AND field_name IN ('mEntriesBindingUntil','mHeadsUpViewBinder'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;' AND field_name='mEntry')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/icon/IconPack;' AND field_name='mStatusBarIcon')
ORDER BY file_id, kind, descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE method_class='Lcom/android/systemui/statusbar/NotificationMediaManager$1;'
 AND callee_name IN ('findAndUpdateMediaNotifications','dispatchUpdateMediaMetaData','unregisterCallback')
ORDER BY file_id, method, callee;
