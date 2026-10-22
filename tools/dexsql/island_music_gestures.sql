-- Optional desktop evidence for the proposed music-only gesture actions.
-- Inputs: identified CN/TW SystemUI APKs, miui.systemui.plugin and boot framework.jar.
-- Verify installed schema first; class_methods/class_fields prove declarations.
-- Record input SHA-256, file_id, defining APK, descriptor and candidate count.
-- No default CI execution and no new production Hook implied by a match.
SELECT file_id, method_descriptor AS descriptor
FROM class_methods
WHERE (class_descriptor='Landroid/media/session/MediaController$TransportControls;'
 AND method_name IN ('play','pause','skipToNext','skipToPrevious'))
 OR (class_descriptor='Landroid/media/session/MediaController;'
 AND method_name IN ('getPlaybackState','getPlaybackInfo','getTransportControls','adjustVolume','registerCallback','unregisterCallback'))
 OR (class_descriptor='Landroid/media/session/PlaybackState;'
 AND method_name IN ('getState','getActions'))
 OR (class_descriptor='Landroid/media/session/MediaController$PlaybackInfo;'
 AND method_name IN ('getPlaybackType','getVolumeControl'))
 OR (class_descriptor='Landroid/media/AudioManager;'
 AND method_name IN ('adjustStreamVolume','adjustVolume'))
 OR (class_descriptor='Lcom/android/systemui/statusbar/NotificationMediaManager;'
 AND method_name IN ('findAndUpdateMediaNotifications','dispatchUpdateMediaMetaData'))
ORDER BY file_id, descriptor
LIMIT 200;

-- Verify the existing global-action volume-panel chain, without broad fallback.
SELECT file_id, field_descriptor AS descriptor
FROM class_fields
WHERE (class_descriptor='Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;'
 AND field_name='mVolumeComponent')
 OR (class_descriptor LIKE '%/volume/%'
 AND field_name IN ('mDialog','mVolumeDialogImpl','mHandler','mShowing'))
ORDER BY file_id, descriptor
LIMIT 100;

-- Definition provenance above and caller evidence below are separate reports.
SELECT DISTINCT file_id, method, callee
FROM method_calls
WHERE method_class='Lcom/android/systemui/statusbar/NotificationMediaManager$1;'
 AND callee_name IN ('findAndUpdateMediaNotifications','dispatchUpdateMediaMetaData','unregisterCallback')
ORDER BY file_id, method, callee
LIMIT 100;
