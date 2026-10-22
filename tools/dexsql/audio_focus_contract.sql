-- Both fuxi services.jar inputs; definitions only. Keep each ROM's file_id/source.
SELECT d.source_path, d.entry_name, t.kind, t.file_id, t.descriptor
FROM (
    SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
    FROM class_methods
    WHERE (class_descriptor = 'Lcom/android/server/audio/FocusRequester;'
        AND method_name IN ('handleFocusLoss', 'frameworkHandleFocusLoss', 'handleFocusGain', 'release'))
       OR (class_descriptor = 'Lcom/android/server/audio/AudioService;'
        AND method_name = 'requestAudioFocus')
       OR (class_descriptor = 'Lcom/android/server/audio/AudioService$VolumeController;'
        AND method_name = 'suppressAdjustment')
       OR (class_descriptor = 'Lcom/android/server/audio/MediaFocusControl;'
        AND method_name IN ('requestAudioFocus', 'abandonAudioFocus', 'removeFocusStackEntry',
            'removeFocusStackEntryOnDeath', 'notifyTopOfAudioFocusStack'))
    UNION ALL
    SELECT 'field' AS kind, file_id, field_descriptor AS descriptor
    FROM class_fields
    WHERE (class_descriptor = 'Lcom/android/server/audio/AudioService;'
        AND field_name = 'mMediaFocusControl')
       OR (class_descriptor = 'Lcom/android/server/audio/MediaFocusControl;'
        AND field_name IN ('mAudioFocusLock', 'mFocusStack', 'mFocusPolicy',
            'mMultiAudioFocusEnabled', 'mRingOrCallActive'))
       OR (class_descriptor = 'Lcom/android/server/audio/FocusRequester;'
        AND field_name IN ('mPackageName', 'mClientId', 'mFocusLossReceived', 'mFocusLossWasNotified'))
       OR (class_descriptor = 'Lcom/android/server/audio/AudioService$VolumeController;'
        AND field_name = 'mController')
) t
JOIN dex_files d ON d.file_id = t.file_id
ORDER BY t.file_id, t.kind, t.descriptor;
