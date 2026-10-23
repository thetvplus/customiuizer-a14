-- M03 CN/TW: supply SystemUI, framework.jar, services.jar, Settings.apk,
-- miui-framework.jar and miui-services.jar. Definitions only, retain source/file keys.
SELECT d.source_path, m.file_id, m.method_idx, m.descriptor, m.access_flags
FROM methods m JOIN dex_files d ON d.file_id = m.file_id
WHERE m.access_flags IS NOT NULL AND (
 (m.class_descriptor IN (
  'Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter;',
  'Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter$1;',
  'Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter$$ExternalSyntheticLambda3;',
  'Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter$$ExternalSyntheticLambda4;'
 ) AND m.name IN ('onNotificationClicked','onDismiss','run','startPendingIntent'))
 OR (m.class_descriptor = 'Landroid/util/MiuiMultiWindowUtils;' AND m.name = 'getActivityOptions')
 OR (m.class_descriptor = 'Lcom/android/server/wm/ActivityStarterImpl;' AND m.name IN ('checkStartActivityByFreeForm','startActivityByFreeForm'))
 OR (m.class_descriptor = 'Lcom/miui/server/SecurityManagerService$LocalService;' AND m.name = 'checkGameBoosterPayPassAsUser')
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/notification/collection/coordinator/CountLimitCoordinator;' AND m.name = 'attach')
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/notification/collection/coordinator/CountLimitCoordinator$$ExternalSyntheticLambda0;' AND m.name = 'onViewBound')
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/notification/collection/coordinator/FoldCoordinator;' AND m.name IN ('attach','scheduleHistoryNotification','transferNotifications'))
 OR (m.class_descriptor LIKE 'Lcom/android/systemui/statusbar/notification/collection/coordinator/FoldCoordinator$%' AND m.name IN ('shouldFilterOut','onEntryAdded','onEntryRemoved','onEntryUpdated'))
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/notification/NotificationUtil;' AND m.name IN ('shouldSuppressFold','setFold','isFold','shouldIgnoreEntry'))
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/phone/NotificationIconAreaController;' AND m.name = 'updateStatusBarIcons')
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/phone/HeadsUpManagerPhone$HeadsUpEntryPhone;' AND m.name IN ('isSticky','updateEntry','reset','calculateFinishTime'))
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/phone/StatusBarNotificationPresenter;' AND m.name = 'onExpandClicked')
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;' AND m.name IN ('setHeadsUp','setEntry','setRemoved','isExpanded'))
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/notification/interruption/NotificationInterruptStateProviderImpl;' AND m.name IN ('canAlertAwakeCommon','canAlertHeadsUpCommon'))
 OR (m.class_descriptor LIKE 'Lcom/android/systemui/statusbar/notification/policy/MiuiAlertManager%' AND m.name IN ('buzzBeepBlink','run'))
 OR (m.class_descriptor = 'Landroid/app/INotificationManager$Stub$Proxy;' AND m.name = 'buzzBeepBlinkForNotification')
 OR (m.class_descriptor = 'Landroid/app/NotificationChannel;' AND m.name IN ('isBlockable','setBlockable','isImportanceLockedByCriticalDeviceFunction'))
 OR (m.class_descriptor = 'Lcom/android/server/notification/PreferencesHelper;' AND m.name IN ('createNotificationChannel','updateNotificationChannel'))
 OR (m.class_descriptor = 'Lmiui/util/NotificationFilterHelper;' AND m.name IN ('isNotificationForcedEnabled','isNotificationForcedFor','canSystemNotificationBeBlocked','containNonBlockableChannel','getNotificationForcedEnabledList'))
 OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/notification/NotificationSettingsManager;' AND m.name IN ('canFloat','canShowOnKeyguard'))
 OR (m.class_descriptor = 'Lcom/android/settings/notification/app/NotificationPreferenceController;' AND m.name IN ('isAppBlockable','isChannelBlockable','isChannelConfigurable','isChannelGroupBlockable'))
 OR (m.class_descriptor IN ('Lcom/android/settings/notification/app/ChannelNotificationSettings;','Lcom/android/settings/notification/ChannelNotificationSettings;') AND m.name IN ('setupChannelDefaultPrefs','updateDependents'))
)
ORDER BY d.source_path, m.file_id, m.method_idx;

-- Direct callers: not a proof of execution, indirect/reflective calls need decode/runtime evidence.
SELECT d.source_path, c.file_id, c.method_idx, c.method, c.callee
FROM method_calls c JOIN dex_files d ON d.file_id = c.file_id
WHERE c.callee_name IN ('shouldSuppressFold','canAlertAwakeCommon','canAlertHeadsUpCommon','checkGameBoosterPayPassAsUser')
 OR (c.method_class LIKE '%StatusBarNotificationActivityStarter%' AND c.callee_name IN ('getActivityOptions','send','sendAndReturnResult','startWorkChallengeIfNecessary','startPendingIntentWithAnimation'))
 OR (c.method_class LIKE '%FoldCoordinator%' AND c.callee_name IN ('addCollectionListener','addPreGroupFilter','cancel','schedule','registerReceiver','registerContentObserver','addCallback'))
 OR (c.method_class LIKE '%CountLimitCoordinator%' AND c.callee_name IN ('addIfAbsent','dismissNotification','getAllNotifs'))
 OR (c.callee_class = 'Landroid/app/NotificationChannel;' AND c.callee_name IN ('isBlockable','isImportanceLockedByCriticalDeviceFunction'))
ORDER BY d.source_path, c.file_id, c.method_idx, c.callee;

-- Read class_fields declarations, not the fields reference universe. Types/owners must be decoded.
SELECT d.source_path, f.file_id, f.class_descriptor, f.field_name, f.field_descriptor
FROM class_fields f JOIN dex_files d ON d.file_id = f.file_id
WHERE (f.class_descriptor LIKE '%StatusBarNotificationActivityStarter$$ExternalSyntheticLambda3;' AND f.field_name IN ('f$0','f$1','f$2','f$3','f$6'))
 OR (f.class_descriptor = 'Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;' AND f.field_name IN ('mEntry','mOnKeyguard','mIsHeadsUp','mUserLocked','mRemoved','mPrivateLayout','mExpandClickListener'))
 OR (f.class_descriptor = 'Lcom/android/systemui/statusbar/notification/row/NotificationContentView;' AND f.field_name = 'mRemoteInputVisible')
 OR (f.class_descriptor LIKE '%HeadsUpEntryPhone;' AND f.field_name IN ('expanded','remoteInputActive','mGutsShownPinned'))
 OR (f.class_descriptor = 'Lcom/android/settings/notification/NotificationBackend$AppRow;' AND f.field_name IN ('systemApp','lockedImportance','uid','pkg'))
ORDER BY d.source_path, f.file_id, f.class_descriptor, f.field_name;
