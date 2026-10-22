-- Direct invokes only; decode W's switch, native gates and pending-command replay.
SELECT d.source_path, d.entry_name, c.file_id, c.method_idx, c.method, c.callee
FROM method_calls c
JOIN dex_files d ON d.file_id = c.file_id
WHERE (c.callee_class IN ('Landroid/media/AudioManager;', 'Landroid/media/IAudioService;',
        'Lcom/android/server/audio/SoundDoseHelper;')
    AND c.callee_name IN ('disableSafeMediaVolume', 'setSafeMediaVolumeEnabled'))
   OR (c.callee_class = 'Lcom/android/systemui/volume/VolumeDialogControllerImpl$C;'
    AND c.callee_name IN ('onShowSafetyWarning', 'onShowCsdWarning'))
   OR (c.method_class = 'Lcom/android/server/audio/SoundDoseHelper;'
    AND c.callee_class = 'Lcom/android/server/audio/AudioService;'
    AND c.callee_name = 'onSetStreamVolume')
ORDER BY c.file_id, c.method_idx, c.callee_method_idx;
