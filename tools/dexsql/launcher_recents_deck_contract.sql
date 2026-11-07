-- A14 Launcher inputs only. Compare full descriptors and sample hashes, never
-- dataset-local IDs. Decode the selected callers to establish ordering/ownership.
SELECT file_id, source_path, entry_name FROM dex_files ORDER BY file_id;

-- Real landscape: one policy is shared by RotationHelper and RecentsContainer.
SELECT file_id, method_descriptor, access_flags FROM class_methods
WHERE (class_descriptor='Lcom/miui/home/launcher/DeviceConfig;'
 AND method_name='isKeepRecentsViewPortrait')
 OR (class_descriptor='Lcom/miui/home/recents/util/RotationHelper;'
 AND method_name IN ('notifyChange','updateMultiWindowRotationRequest'))
ORDER BY method_descriptor;

SELECT file_id, descriptor, superclass_descriptor FROM classes
WHERE descriptor IN ('Lcom/miui/home/recents/views/TaskStackView;',
 'Lcom/miui/home/recents/views/TaskStackLayoutAlgorithm;',
 'Lcom/miui/home/recents/views/TaskStackViewsAlgorithmHorizontal;',
 'Lcom/miui/home/recents/views/TaskView;',
 'Lcom/miui/home/recents/views/TaskViewThumbnail;',
 'Lcom/miui/home/recents/views/TaskStackViewTouchHandler;',
 'Lcom/miui/home/recents/views/TaskStackViewTouchHandler$1;',
 'Lcom/miui/home/recents/views/SwipeHelperForRecents;',
 'Lcom/miui/home/recents/views/VerticalSwipe;',
 'Lcom/miui/home/recents/views/TaskViewTransform;',
 'Lcom/miui/home/recents/NavStubView;',
 'Lcom/miui/home/recents/anim/WindowElement;',
 'Lcom/miui/home/recents/util/RectFSpringAnim;',
 'Lcom/miui/home/recents/util/ClipAnimationHelper;') ORDER BY descriptor;

SELECT file_id, method_descriptor FROM class_methods
WHERE class_descriptor LIKE 'Lcom/miui/home/recents/%'
 AND method_name IN ('calculate','onMoveUpdate','prepareSnapBackAnimation',
 'getViewTranslationAnimator','onDragEnd','onChildSnappedBack','snapChild',
 'onTouchEvent','setChildrenViewAlpha','setCornerRadius','bindVisibleTaskViews',
 'applyToTaskView','modifyTransformVisible','setRunningTaskHidden',
 'onReturnViewToPool','onPickUpViewFromPool','resetViewProperties',
 'initTaskViews','animTo','onUpdate','reset','initAllAnimations',
 'calculateWindowCrop','startClosingWindowToRecentsAnimators',
 'isLandscapeVisually','getRecentsContainerRotation')
ORDER BY method_descriptor;

SELECT file_id, class_descriptor, field_descriptor FROM class_fields
WHERE (field_name='mCornerRadius' AND class_descriptor LIKE 'Lcom/miui/home/recents/%')
 OR (class_descriptor='Lcom/miui/home/recents/views/TaskStackLayoutAlgorithm;'
 AND field_name IN ('mWindowRect','mTaskViewRectF','mVisualRotationSystemInsets'))
 OR (class_descriptor='Lcom/miui/home/recents/views/SwipeHelperForRecents;'
 AND field_name IN ('mVerticalSwipe','mCurrView','mSwipeDirection'))
ORDER BY class_descriptor,field_descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE callee_class LIKE 'Lcom/miui/home/recents/%'
 AND callee_name IN ('calculate','onMoveUpdate','prepareSnapBackAnimation',
 'getViewTranslationAnimator','onDragEnd','onChildSnappedBack',
 'setChildrenViewAlpha','setCornerRadius','applyToTaskView',
 'modifyTransformVisible','bindVisibleTaskViews')
ORDER BY method,callee;

SELECT file_id, method, field, using_type FROM method_field_accesses
WHERE (field_name='mCornerRadius' AND field_class LIKE 'Lcom/miui/home/recents/%')
 OR (field_class='Lcom/miui/home/recents/views/TaskStackLayoutAlgorithm;'
 AND field_name IN ('mWindowRect','mTaskViewRectF','mVisualRotationSystemInsets'))
 OR (field_class='Lcom/miui/home/recents/views/SwipeHelperForRecents;'
 AND field_name IN ('mVerticalSwipe','mCurrView'))
ORDER BY method,field,using_type;

-- Legacy enum / property setters share native response conversion. These are
-- optional motion interfaces, not a version-number admission list.
SELECT file_id, method_descriptor, access_flags FROM class_methods
WHERE class_descriptor='Lcom/miui/home/recents/util/RectFSpringAnim;'
 AND method_name IN ('setAnimParam','setAnimParamByType','initAllAnimations',
 'getModifyResponse','calculateStiffFromResponse')
ORDER BY method_descriptor;

SELECT file_id, field_descriptor, access_flags FROM class_fields
WHERE class_descriptor='Lcom/miui/home/recents/util/RectFSpringAnim;'
 AND field_name IN ('RECT_CENTERX','RECT_CENTERY','RECT_WIDTH','RECT_RATIO',
 'mCenterXDamping','mCenterXStiffness','mCenterYDamping','mCenterYStiffness',
 'mWidthDamping','mWidthStiffness','mRatioDamping','mRatioStiffness')
ORDER BY field_descriptor;
