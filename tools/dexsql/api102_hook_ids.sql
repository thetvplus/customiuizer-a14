-- String use sites, NOT proof of installation or a (string_pool) presence check.
-- Match installer owners to this APK's mapping, then confirm IDs on the device.
SELECT DISTINCT file_id, method_idx, method, value
FROM method_strings
WHERE value IN ('systemui.init', 'systemui.plugin.create', 'application.attach')
ORDER BY file_id, method_idx, method, value;
