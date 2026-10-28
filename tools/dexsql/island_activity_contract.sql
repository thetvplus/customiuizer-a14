-- Inputs: CN/TW SystemUI APKs plus their boot framework.jar. Keep file_id provenance.
-- Definitions only; NotificationMediaManager owns primary-player selection and normal callbacks.
SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
FROM class_methods
WHERE (class_descriptor='Lcom/android/systemui/statusbar/NotificationMediaManager;'
 AND method_name IN ('<init>','findAndUpdateMediaNotifications','dispatchUpdateMediaMetaData'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/NotificationMediaManager$1;'
 AND method_name IN ('onPlaybackStateChanged','onMetadataChanged','onSessionDestroyed'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/StatusBarStateControllerImpl;'
 AND method_name IN ('<init>','addCallback','removeCallback','getState','isDozing','isExpanded','updateUpcomingState'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/policy/KeyguardStateControllerImpl;'
 AND method_name IN ('addCallback','removeCallback','notifyKeyguardState','notifyKeyguardGoingAway','setKeyguardFadingAway'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/policy/KeyguardStateController$Callback;'
 AND method_name IN ('onKeyguardShowingChanged','onKeyguardGoingAwayChanged','onKeyguardFadingAwayChanged',
  'onPrimaryBouncerShowingChanged','onLaunchTransitionFadingAwayChanged','onUnlockedChanged'))
 OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToast;'
 AND method_name IN ('getWindowParam','showCustomStrongToast','hideStrongToast','onDetachedFromWindow'))
 OR (class_descriptor='Lcom/android/systemui/toast/MIUIStrongToastControl;'
 AND method_name='showCustomStrongToast')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/coordinator/HeadsUpCoordinator$bindForAsyncHeadsUp$1;'
 AND method_name='onBindFinished')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/interruption/HeadsUpViewBinder;'
 AND method_name IN ('abortBindCallback','unbindHeadsUpView'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/coordinator/SensitiveContentCoordinatorImpl;'
 AND method_name='onBeforeRenderList')
 OR (class_descriptor='Lcom/android/systemui/statusbar/NotificationLockscreenUserManagerImpl;'
 AND method_name='isLockscreenPublicMode')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/policy/NotificationSensitiveController;'
 AND method_name='showSensitiveByAppLock')
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
 AND field_name IN ('mState','mUpcomingState','mKeyguardRequested','mIsDozing','mIsExpanded','mIsDreaming'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/policy/KeyguardStateControllerImpl;'
 AND field_name IN ('mShowing','mKeyguardGoingAway','mKeyguardFadingAway','mPrimaryBouncerShowing','mLaunchTransitionFadingAway'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;'
 AND field_name IN ('mSbn','mRanking','row','mSensitive','hideSensitiveByAppLock','mIcons','interruption'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/coordinator/HeadsUpCoordinator$bindForAsyncHeadsUp$1;'
 AND field_name='$tmp0')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/collection/coordinator/HeadsUpCoordinator;'
 AND field_name IN ('mEntriesBindingUntil','mHeadsUpViewBinder'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;' AND field_name IN ('mEntry','mRemoved'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/NotificationLockscreenUserManagerImpl;' AND field_name='mCurrentUserId')
 OR (class_descriptor='Lcom/android/systemui/statusbar/notification/icon/IconPack;' AND field_name='mStatusBarIcon')
ORDER BY file_id, kind, descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE method_class='Lcom/android/systemui/statusbar/NotificationMediaManager$1;'
 AND callee_name IN ('findAndUpdateMediaNotifications','dispatchUpdateMediaMetaData','unregisterCallback')
ORDER BY file_id, method, callee;

-- SHADE alone is not proof of unlock. Inspect these producers/callbacks and the native
-- bind tail/unbind flag before changing takeover; list visibility is a separate contract.
SELECT file_id, method, field, using_type
FROM method_field_accesses
WHERE (field_class='Lcom/android/systemui/statusbar/policy/KeyguardStateControllerImpl;'
 AND field_name IN ('mShowing','mKeyguardGoingAway','mKeyguardFadingAway','mPrimaryBouncerShowing')
 AND using_type='put')
 OR (field_class='Lcom/android/systemui/statusbar/StatusBarStateControllerImpl;'
 AND field_name IN ('mUpcomingState','mKeyguardRequested') AND using_type='put')
 OR (field_class='Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;' AND field_name='interruption')
ORDER BY file_id, method_idx, field_idx, using_type;

SELECT file_id, method, type_descriptor FROM method_used_types
WHERE method_class='Lcom/android/systemui/Dependency;' AND method_name='start'
 AND type_descriptor='Lcom/android/systemui/statusbar/policy/KeyguardStateController;'
ORDER BY file_id, method_idx, type_idx;
