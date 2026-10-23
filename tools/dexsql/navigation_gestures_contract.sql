-- Decode the exact initialization and setSize methods before changing width scaling.
SELECT file_id, method_descriptor FROM class_methods
WHERE class_descriptor IN ('Lcom/miui/home/recents/GestureStubView;',
 'Lcom/miui/home/recents/GestureStubView$3;', 'Lcom/miui/home/recents/GestureBackArrowView;',
 'Lcom/miui/home/recents/NavStubView;')
 AND method_name IN ('initScreenSizeAndDensity','initGestureStubSize','setSize',
 'getGestureStubWindowParam','disableQuickSwitch','isDisableQuickSwitch','getNextTask',
 'onSwipeStop','setReadyFinish','onTouchEvent','onPointerEvent')
ORDER BY file_id, method_descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE callee = 'Lcom/miui/home/recents/GestureStubView;->initScreenSizeAndDensity()V'
 OR (method_class = 'Lcom/miui/home/recents/GestureStubView;'
 AND method_name IN ('initGestureStubSize','onConfigurationChanged') AND callee_name = 'setSize')
ORDER BY file_id, method, callee;

SELECT file_id, field_descriptor FROM class_fields
WHERE class_descriptor = 'Lcom/miui/home/recents/GestureStubView;'
 AND field_name IN ('mGestureStubDefaultSize','mGestureStubSize')
ORDER BY file_id, field_descriptor;
