-- Both fuxi services.jar and framework.jar inputs. Read the returned definitions
-- and Smali before adapting native scaling; absence alone does not authorize retirement.
SELECT d.source_path, d.entry_name, m.file_id, m.method_descriptor
FROM class_methods m JOIN dex_files d ON d.file_id = m.file_id
WHERE (m.class_descriptor = 'Lcom/android/server/VibratorService;' AND m.method_name = 'doVibratorOn')
   OR (m.class_descriptor = 'Lcom/android/server/vibrator/VibratorManagerService;'
       AND m.method_name IN ('vibrate', 'vibrateInternal'))
   OR (m.class_descriptor = 'Lcom/android/server/vibrator/VibrationScaler;' AND m.method_name = 'scale')
ORDER BY m.file_id, m.method_descriptor;

SELECT file_id, method, callee
FROM method_calls
WHERE callee_class = 'Lcom/android/server/vibrator/VibrationScaler;' AND callee_name = 'scale'
ORDER BY file_id, method, callee;

SELECT d.source_path, d.entry_name, m.file_id, m.method_descriptor
FROM class_methods m JOIN dex_files d ON d.file_id = m.file_id
WHERE m.class_descriptor IN ('Landroid/os/VibrationEffect;',
                             'Landroid/os/VibrationEffect$Composed;',
                             'Landroid/os/vibrator/StepSegment;',
                             'Landroid/os/vibrator/PrebakedSegment;')
  AND m.method_name = 'scale'
ORDER BY m.file_id, m.method_descriptor;
