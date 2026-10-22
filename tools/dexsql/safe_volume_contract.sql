-- Both fuxi hosts + framework.jar + services.jar; definitions only, per-source IDs.
SELECT d.source_path, d.entry_name, t.kind, t.file_id, t.descriptor
FROM (
    SELECT 'method' AS kind, file_id, method_descriptor AS descriptor
    FROM class_methods
    WHERE (class_descriptor = 'Lcom/android/systemui/volume/VolumeUI;'
        AND method_name = 'start')
       OR (class_descriptor = 'Lcom/android/systemui/volume/VolumeDialogControllerImpl$W;'
        AND method_name = 'handleMessage')
       OR (class_descriptor = 'Lcom/android/systemui/volume/VolumeDialogControllerImpl$VC;'
        AND method_name IN ('displaySafeVolumeWarning', 'displayCsdWarning'))
       OR (class_descriptor = 'Lcom/android/systemui/volume/VolumeDialogControllerImpl$C;'
        AND method_name IN ('onShowSafetyWarning', 'onShowCsdWarning'))
       OR (class_descriptor = 'Landroid/media/AudioManager;'
        AND method_name = 'disableSafeMediaVolume')
       OR (class_descriptor = 'Lcom/android/server/audio/AudioService;'
        AND method_name = 'disableSafeMediaVolume')
       OR (class_descriptor = 'Lcom/android/server/audio/SoundDoseHelper;'
        AND method_name IN ('disableSafeMediaVolume', 'setSafeMediaVolumeEnabled',
            'onCheckMusicActive', 'onConfigureSafeMedia', 'updateSafeMediaVolume_l',
            'raiseVolumeDisplaySafeMediaVolume'))
    UNION ALL
    SELECT 'field' AS kind, file_id, field_descriptor AS descriptor
    FROM class_fields
    WHERE (class_descriptor = 'Lcom/android/systemui/volume/VolumeDialogControllerImpl$W;'
        AND field_name = 'this$0')
       OR (class_descriptor = 'Lcom/android/systemui/volume/VolumeDialogControllerImpl;'
        AND field_name IN ('mAudio', 'mShowSafetyWarning'))
       OR (class_descriptor = 'Lcom/android/server/audio/SoundDoseHelper;'
        AND field_name IN ('mSafeMediaVolumeState', 'mSafeMediaVolumeStateLock',
            'mPendingVolumeCommand', 'mMusicActiveMs'))
) t
JOIN dex_files d ON d.file_id = t.file_id
ORDER BY t.file_id, t.kind, t.descriptor;
