-- Direct instruction references from manifest entry points; normally empty.
-- Also inspect THIS APK's mapping and existing classloading tests: helpers,
-- signatures, field types, inheritance and reflection are not a transitive proof.
WITH entrypoints AS (
    SELECT descriptor FROM classes
    WHERE descriptor IN (
        'Ltv/withaibuild/customiuizer/MainApplication;',
        'Ltv/withaibuild/customiuizer/MainActivity;',
        'Ltv/withaibuild/customiuizer/PrefsProvider;',
        'Ltv/withaibuild/customiuizer/Credentials;',
        'Ltv/withaibuild/customiuizer/CredentialsShortcut;',
        'Ltv/withaibuild/customiuizer/tasker/UnlockSettings;',
        'Ltv/withaibuild/customiuizer/tasker/UnlockReceiver;',
        'Ltv/withaibuild/customiuizer/qs/AutoRotateService;'
    )
), refs AS (
    SELECT file_id, method, 'invoke' AS kind, callee AS target
    FROM method_calls
    WHERE method_class IN (SELECT descriptor FROM entrypoints)
      AND callee LIKE 'Lio/github/libxposed/api/%'
    UNION ALL
    SELECT file_id, method, 'type' AS kind, type_descriptor AS target
    FROM method_used_types
    WHERE method_class IN (SELECT descriptor FROM entrypoints)
      AND type_descriptor LIKE 'Lio/github/libxposed/api/%'
)
SELECT DISTINCT d.source_path, d.entry_name, r.method, r.kind, r.target
FROM refs r
JOIN dex_files d ON d.file_id = r.file_id
ORDER BY d.source_path, d.entry_name, r.method, r.kind, r.target;
