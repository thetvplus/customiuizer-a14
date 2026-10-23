-- Input: the actual HyperOS 1 Launcher APK. Definition source and sample hash
-- must be recorded separately. This is not a general RectFSpringAnim hook.
SELECT file_id, method_idx, method_descriptor
FROM class_methods
WHERE (class_descriptor = 'Lcom/miui/home/recents/NavStubView;'
       AND method_name IN ('onAttachedToWindow', 'onDetachedFromWindow', 'onTouchEvent'))
   OR (class_descriptor = 'Lcom/miui/home/recents/anim/WindowElement;'
       AND method_name IN ('animTo', 'animClear', 'animResetReady', 'runningAnimUpdate',
           'updateCurrentRect', 'updateCurrentDisplayRotation', 'initTargetView',
           'updateFloatingIconView', 'handleFloatingIconViewWhenNewTargetViewNull',
           'resetFloatingIcon', 'onFinishCompleted', 'cancelAnimWithoutEndListener'))
   OR (class_descriptor = 'Lcom/miui/home/recents/anim/RectFParams;' AND method_name = '<init>')
ORDER BY file_id, class_descriptor, method_idx;

SELECT file_id, field_descriptor
FROM class_fields
WHERE class_descriptor = 'Lcom/miui/home/recents/anim/RectFParams;'
   OR (class_descriptor = 'Lcom/miui/home/recents/NavStubView;'
       AND field_name IN ('mRunningTaskId', 'mRunningTaskUserId', 'mRunningTaskComponentName', 'mRunningTaskInfo'))
   OR (class_descriptor = 'Lcom/miui/home/recents/anim/WindowElement;'
       AND field_name IN ('remoteAnimationTargetSet', 'launcherTargetView', 'mAnim'))
   OR (class_descriptor = 'Lcom/miui/home/recents/util/RemoteAnimationTargetSet;' AND field_name = 'apps')
   OR (class_descriptor = 'Lcom/android/systemui/shared/recents/system/RemoteAnimationTargetCompat;'
       AND field_name IN ('packageName', 'taskId', 'mode', 'activityType', 'MODE_CLOSING', 'ACTIVITY_TYPE_STANDARD'))
ORDER BY file_id, class_descriptor, field_idx;

-- Decode the complete callers: CLOSE_TO_HOME must keep native velocity,
-- listener, cancellation, finish and surface ownership. Null targetView uses
-- the native floating-icon cleanup; do not remove or hide the real desktop icon.
SELECT DISTINCT file_id, method, callee
FROM method_calls
WHERE method_class = 'Lcom/miui/home/recents/anim/WindowElement;'
  AND (callee LIKE 'Lcom/miui/home/recents/util/RectFSpringAnim;->reset(%'
       OR callee LIKE 'Lcom/miui/home/recents/util/RectFSpringAnim;->updateAllAnimValues(%'
       OR callee LIKE 'Lcom/miui/home/recents/anim/WindowElement;->animResetReady(%'
       OR callee LIKE 'Lcom/miui/home/recents/anim/WindowElement;->runningAnimUpdate(%'
       OR callee LIKE 'Lcom/miui/home/recents/anim/WindowElement;->resetFloatingIcon(%'
       OR callee LIKE 'Lcom/miui/home/recents/FloatingIconInterface;->recycle(%')
ORDER BY file_id, method, callee;
