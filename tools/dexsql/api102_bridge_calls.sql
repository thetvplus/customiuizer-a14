-- Inspect ALL rows against this APK's mapping: setId/getId belong to
-- the shared native HookInstallation policy; replaceHook must remain absent.
-- COUNT(*) counts invoke instructions; DISTINCT callers alone would hide duplicates.
SELECT file_id, method_idx, method, callee, COUNT(*) AS invoke_count
FROM method_calls
WHERE callee IN (
    'Lio/github/libxposed/api/XposedInterface$HookBuilder;->setId(Ljava/lang/String;)Lio/github/libxposed/api/XposedInterface$HookBuilder;',
    'Lio/github/libxposed/api/XposedInterface$HookHandle;->getId()Ljava/lang/String;',
    'Lio/github/libxposed/api/XposedInterface$HookHandle;->replaceHook(Lio/github/libxposed/api/XposedInterface$Hooker;)Lio/github/libxposed/api/XposedInterface$HookHandle;'
)
GROUP BY file_id, method_idx, method, callee
ORDER BY file_id, method_idx, method, callee;
