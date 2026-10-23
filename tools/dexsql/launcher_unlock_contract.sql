-- Launcher inputs only. A declaration is not proof of the runtime ClassLoader.
SELECT file_id, method_descriptor FROM class_methods
WHERE (class_descriptor IN ('Lcom/miui/home/launcher/hotseats/HotSeats;',
 'Lcom/miui/home/launcher/hotseats/HotSeatsScreenViewContent;')
 AND method_name = 'getUserPresentAnimationChildList')
 OR (class_descriptor = 'Lcom/miui/home/launcher/compat/UserPresentAnimationCompatComplex;'
 AND method_name IN ('operateAllPresentAnimationRelatedViews','resetView','resetAnimation'))
 OR (class_descriptor = 'Lcom/miui/home/launcher/compat/UserPresentAnimationCompatV12Base;'
 AND method_name IN ('prepareUserPresentAnimation','setViewPrepareInfo'))
 OR (class_descriptor = 'Lcom/miui/home/launcher/compat/UserPresentAnimationCompatV12Phone;'
 AND method_name IN ('showUserPresentAnimation','endAnimation'))
ORDER BY file_id, method_descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE method_class LIKE 'Lcom/miui/home/launcher/compat/UserPresentAnimation%'
 AND callee_name IN ('getUserPresentAnimationChildList','setAlpha','setScaleX','setScaleY',
 'setTranslationX','setTranslationY','startDelay')
ORDER BY file_id, method, callee;
