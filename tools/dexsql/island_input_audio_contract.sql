-- Input: matching SystemUI and boot framework.jar. Permission grants/AppOps are runtime evidence.
SELECT file_id, method_idx, descriptor
FROM methods
WHERE access_flags IS NOT NULL
  AND ((class_descriptor = 'Landroid/view/ViewTreeObserver;'
        AND name IN ('addOnComputeInternalInsetsListener', 'removeOnComputeInternalInsetsListener'))
    OR (class_descriptor = 'Landroid/view/ViewTreeObserver$InternalInsetsInfo;'
        AND name = 'setTouchableInsets')
    OR (class_descriptor = 'Landroid/media/session/MediaSession$Token;' AND name = 'getUid')
    OR (class_descriptor = 'Landroid/media/AudioPlaybackConfiguration;'
        AND name IN ('getClientUid', 'getSessionId', 'isActive')))
ORDER BY file_id, method_idx;
