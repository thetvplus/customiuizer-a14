-- Input: both supported services.jar and framework.jar. No global app/Activity hook is implied.
SELECT file_id, method_descriptor FROM class_methods
WHERE (class_descriptor='Lcom/android/server/wm/DisplayPolicy$DecorInsets$Info;' AND method_name='update')
 OR (class_descriptor='Lcom/android/server/wm/DisplayPolicy$DecorInsets;' AND method_name IN ('<clinit>','invalidate'))
 OR (class_descriptor='Lcom/android/server/wm/DisplayPolicy;'
 AND method_name IN ('layoutWindowLw','getDecorInsetsInfo','updateDecorInsetsInfo'))
 OR (class_descriptor='Lcom/android/server/wm/WindowState;' AND method_name='setFrames')
 OR (class_descriptor='Landroid/view/InsetsSource;' AND method_name='setFrame')
 OR (class_descriptor='Landroid/view/WindowInsets;'
 AND method_name IN ('getInsets','getInsetsIgnoringVisibility','getSystemWindowInsets','getSystemWindowInsetTop','getDisplayCutout'))
ORDER BY file_id, method_descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE (method_class='Lcom/android/server/wm/DisplayPolicy$DecorInsets$Info;'
 AND method_name='update' AND callee_name IN ('simulateLayoutDisplay','calculateInsets','max'))
 OR (method_class='Landroid/view/WindowInsets;'
 AND method_name IN ('getInsets','getInsetsIgnoringVisibility','getSystemWindowInsets','getSystemWindowInsetTop')
 AND callee_name IN ('max','getInsets','getInsetsIgnoringVisibility','getSystemWindowInsets'))
ORDER BY file_id, method, callee;
