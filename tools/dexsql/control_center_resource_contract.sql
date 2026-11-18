-- Run on MIUISystemUIPlugin.apk. AAPT2 confirms the IDs and values in its resource table.
SELECT 'class' AS kind, file_id, descriptor, superclass_descriptor AS detail
FROM classes
WHERE descriptor IN (
    'Lmiui/systemui/controlcenter/panel/main/header/StatusHeaderController;',
    'Lmiui/systemui/controlcenter/utils/ControlCenterViewController;'
)
UNION ALL
SELECT 'method', file_id, method_descriptor, NULL
FROM class_methods
WHERE (class_descriptor = 'Lmiui/systemui/controlcenter/panel/main/header/StatusHeaderController;'
       AND method_name IN ('createStatusBarViews', 'adjustCarrierOrPrompt', 'updateConstraint',
                           'onExpandChange', 'onCreate$lambda-2$lambda-0'))
   OR (class_descriptor = 'Lmiui/systemui/util/ViewController;' AND method_name = 'getView')
   OR (class_descriptor = 'Lmiui/systemui/controlcenter/qs/tileview/QSTileItemView;'
       AND method_name IN ('onFinishInflate', 'updateSize', 'updateContainerHeight',
                           'changeExpand', '_$_findCachedViewById'))
UNION ALL
SELECT 'field', file_id, field_descriptor, NULL
FROM class_fields
WHERE (class_descriptor = 'Lmiui/systemui/controlcenter/R$id;'
       AND field_name IN ('header_carrier_view', 'header_status_bar_icons', 'icon_frame'))
   OR (class_descriptor = 'Lmiui/systemui/controlcenter/R$style;'
       AND field_name = 'TextAppearance_Header_Text')
   OR (class_descriptor = 'Landroidx/constraintlayout/widget/ConstraintLayout$LayoutParams;'
       AND field_name IN ('bottomToTop', 'endToStart'))
   OR (class_descriptor = 'Lmiui/systemui/controlcenter/qs/tileview/QSTileItemView;'
       AND field_name = 'containerHeight')
ORDER BY file_id, kind, descriptor;
