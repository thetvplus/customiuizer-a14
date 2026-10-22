-- Direct native loss/gain/release and stack paths, not runtime invocation counts.
SELECT DISTINCT file_id, method, callee
FROM method_calls
WHERE (callee_class = 'Lcom/android/server/audio/FocusRequester;'
    AND callee_name IN ('handleFocusLoss', 'frameworkHandleFocusLoss', 'handleFocusGain',
        'release', 'maybeRelease'))
   OR (method_class = 'Lcom/android/server/audio/MediaFocusControl;'
    AND method_name IN ('requestAudioFocus', 'abandonAudioFocus', 'removeFocusStackEntry',
        'removeFocusStackEntryOnDeath')
    AND callee_class = 'Ljava/util/Stack;')
ORDER BY file_id, method, callee;
