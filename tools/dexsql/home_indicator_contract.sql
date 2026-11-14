-- A14 drawing capability only. Build identity is not an admission requirement.
-- Require instance/final Paint and inspect its constructor-only writes.
SELECT file_id, source_path, entry_name FROM dex_files ORDER BY file_id;

SELECT file_id, descriptor, superclass_descriptor FROM classes
WHERE descriptor IN (
 'Lcom/android/systemui/navigationbar/gestural/NavigationHandle;',
 'Lcom/android/systemui/navigationbar/gestural/QuickswitchOrientedNavHandle;',
 'Lcom/miui/home/recents/views/FakeNavigationBarView;',
 'Lcom/android/systemui/navigationbar/NavigationBarView;',
 'Lcom/android/systemui/fsgesture/GestureLineUtils;') ORDER BY file_id, descriptor;

SELECT file_id, method_descriptor FROM class_methods
WHERE (class_descriptor='Lcom/android/systemui/navigationbar/gestural/NavigationHandle;'
 AND method_name IN ('onDraw','setDarkIntensity','setDefaultColor','setAlpha'))
 OR (class_descriptor='Lcom/android/systemui/navigationbar/gestural/QuickswitchOrientedNavHandle;'
 AND method_name IN ('onDraw','computeHomeHandleBounds','setDeltaRotation'))
 OR (class_descriptor='Lcom/miui/home/recents/views/FakeNavigationBarView;'
 AND method_name IN ('onDraw','onMeasure'))
 OR (class_descriptor='Lcom/miui/home/recents/views/RecentsContainer;'
 AND method_name IN ('showLandscapeOverviewGestureView','showFakeNavBar','hideFakeNavBarForHidingGestureLine',
 'isInLandscapeOverview','exitLandscapeOverview'))
 OR (class_descriptor='Lcom/android/systemui/navigationbar/NavigationBarView;'
 AND method_name IN ('updateNavButtonIcons','updateIcons','onNavigationModeChanged','reorient'))
 OR class_descriptor='Lcom/android/systemui/fsgesture/GestureLineUtils;'
ORDER BY file_id, method_descriptor;

SELECT file_id, class_descriptor, field_descriptor, access_flags FROM class_fields
WHERE (class_descriptor='Lcom/android/systemui/navigationbar/gestural/NavigationHandle;'
 AND field_name IN ('mPaint','mRadius','mBottom'))
 OR (class_descriptor='Lcom/android/systemui/navigationbar/gestural/QuickswitchOrientedNavHandle;'
 AND field_name='mDeltaRotation')
 OR (class_descriptor='Lcom/miui/home/recents/views/FakeNavigationBarView;'
 AND field_name IN ('mPaint','mOffset','mNavShowWidth'))
 OR (class_descriptor='Lcom/android/systemui/navigationbar/NavigationBarView;' AND field_name='mNavBarMode')
ORDER BY file_id, field_descriptor;

SELECT file_id, method, field, using_type FROM method_field_accesses
WHERE field_class IN ('Lcom/android/systemui/navigationbar/gestural/NavigationHandle;',
 'Lcom/miui/home/recents/views/FakeNavigationBarView;')
 AND field_name='mPaint' ORDER BY file_id,method,using_type;
