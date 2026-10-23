-- This SystemUI dispatch path is separate from Launcher navigation gestures.
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
