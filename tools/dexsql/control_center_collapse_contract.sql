-- Selected A14 SystemUI contract. The async state refresh must finish normally;
-- suppress only its late cosmetic header message when auto-collapse is enabled.
SELECT file_id, method_descriptor FROM class_methods
WHERE class_descriptor='Lcom/android/systemui/qs/tileimpl/QSTileImpl;'
 AND method_name IN ('click','handleRefreshState','handleShowStateMessage','showStateMessage')
ORDER BY method_descriptor;

SELECT DISTINCT file_id, method, callee FROM method_calls
WHERE method_class='Lcom/android/systemui/qs/tileimpl/QSTileImpl;'
 AND callee_name IN ('handleShowStateMessage','onShowStateMessage')
ORDER BY method,callee;
