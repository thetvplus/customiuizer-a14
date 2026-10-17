-- Mirror ScreenshotConfigHook's substring search and parameter guard.
-- Inspect all returned definitions: production requires exactly one candidate.
SELECT DISTINCT m.file_id, m.method_idx, m.descriptor
FROM method_strings s
JOIN methods m ON m.file_id = s.file_id AND m.method_idx = s.method_idx
JOIN proto_params p ON p.file_id = m.file_id AND p.proto_idx = m.proto_idx
WHERE s.value LIKE '%saveBitmapToUri: external storage%'
  AND m.param_count >= 7
  AND p.ordinal = 4
  AND p.descriptor = 'Landroid/graphics/Bitmap$CompressFormat;'
  AND m.class_descriptor NOT LIKE 'Landroid%'
  AND m.class_descriptor NOT LIKE 'Lcom/xiaomi%'
  AND m.class_descriptor NOT LIKE 'Lcom/google/json%'
  AND m.class_descriptor NOT LIKE 'Lkotlin%'
  AND m.class_descriptor NOT LIKE 'Lmiuix%'
ORDER BY m.file_id, m.method_idx, m.descriptor;
