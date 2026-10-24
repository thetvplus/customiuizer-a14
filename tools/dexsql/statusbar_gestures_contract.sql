-- Open SystemUI and its control-center plugin together; this is separate from Launcher.
SELECT file_id, method_descriptor FROM class_methods
WHERE class_descriptor IN ('Lcom/android/systemui/statusbar/phone/PhoneStatusBarView;',
 'Lcom/android/systemui/statusbar/phone/MiuiPhoneStatusBarView;')
 AND method_name IN ('onTouchEvent','onInterceptTouchEvent','handleEvent',
 'onAttachedToWindow','onDetachedFromWindow')
ORDER BY file_id, method_descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE method_class = 'Lcom/android/systemui/statusbar/phone/PhoneStatusBarView;'
 AND method_name IN ('onTouchEvent','onInterceptTouchEvent')
 AND callee_name IN ('onTouchEvent','onInterceptTouchEvent','handleEvent','updateBarInteracting')
ORDER BY file_id, method, callee;

-- Plugin owners have a declared destroy(), but inherit onDetachedFromWindow.
-- onTouchEvent forwards external=false; handleMotionEvent's second flag is not intercept.
SELECT d.source_path, d.entry_name, m.method_descriptor
FROM class_methods m
JOIN dex_files d ON d.file_id = m.file_id
WHERE m.class_descriptor = 'Lmiui/systemui/controlcenter/windowview/ControlCenterWindowViewImpl;'
 AND m.method_name IN ('onAttachedToWindow','onDetachedFromWindow','destroy',
 'handleMotionEvent','onTouchEvent','onInterceptTouchEvent')
ORDER BY m.file_id, m.method_idx;

SELECT DISTINCT file_id, method, callee
FROM method_calls
WHERE method_class = 'Lmiui/systemui/controlcenter/windowview/ControlCenterWindowViewImpl;'
 AND method_name IN ('onAttachedToWindow','destroy','onTouchEvent','onInterceptTouchEvent')
 AND callee_name IN ('onAttachedToWindow','notifyLifecycleStateChanged','handleMotionEvent')
ORDER BY file_id, method, callee;
