-- Definition provenance for the existing SystemUI bootstrap / plugin hook.
-- Application.attach is a boot-framework method; a missing framework input is UNKNOWN.
SELECT d.source_path, d.entry_name, m.method_descriptor
FROM class_methods m
JOIN dex_files d ON d.file_id = m.file_id
WHERE (m.class_descriptor = 'Lcom/android/systemui/SystemUIInitializer;' AND m.method_name = 'init')
   OR (m.class_descriptor = 'Lcom/android/systemui/shared/plugins/PluginInstance$PluginFactory;' AND m.method_name = 'createPlugin')
ORDER BY m.file_id, m.method_idx;
