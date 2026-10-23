-- Input: SecurityCenter, PowerKeeper, GuardProvider, MiuiDaemon and AnalyticsCore.
-- Manifest components and permissions must be read separately, including all pages.
SELECT file_id, source_path, entry_name FROM dex_files ORDER BY file_id;

SELECT file_id, method_idx, descriptor
FROM methods
WHERE access_flags IS NOT NULL
  AND (class_descriptor IN (
    'Lcom/miui/powerkeeper/cloudcontrol/CloudUpdateJobService;',
    'Lcom/miui/daemon/performance/cloudcontrol/CloudControlSyncService;',
    'Lcom/miui/daemon/mqsas/jobs/JobDispatcher;',
    'Lcom/miui/daemon/mqsas/jobs/HeartBeatUploadService;',
    'Lcom/miui/daemon/mqsas/jobs/EventUploadService;',
    'Lcom/miui/daemon/mqsas/jobs/FileUploadService;',
    'Lcom/miui/guardprovider/manager/CacheBuildJobService;',
    'Lcom/miui/guardprovider/sdk/android/work/ReportWorker;',
    'Lcom/miui/securitycenter/service/ConnectivityChangeJobService2;',
    'Lcom/miui/securitycenter/service/CloudDataUpdateService;',
    'Lcom/miui/analytics/internal/JobSchedulerService;')
    OR (class_descriptor = 'Lcom/miui/daemon/mqsas/utils/Utils;'
        AND name IN ('isUploadEnabled', 'isReportEnabled'))
    OR (class_descriptor = 'Lcom/miui/analytics/AnalyticsApp;'
        AND name = 'startJobScheduler'))
ORDER BY file_id, method_idx;

-- Invoke presence is a candidate; decode constants, branches and direct callers.
SELECT DISTINCT file_id, method_idx, method, callee
FROM method_calls
WHERE (method_class LIKE 'Lcom/miui/%' OR method_class LIKE 'Lcom/xiaomi/%')
  AND callee_class IN ('Landroid/app/job/JobScheduler;', 'Landroid/app/job/JobInfo$Builder;',
    'Landroid/app/AlarmManager;', 'Landroid/os/PowerManager$WakeLock;')
  AND callee_name IN ('schedule', 'cancel', 'setPeriodic', 'setMinimumLatency',
    'setBackoffCriteria', 'setRequiredNetworkType', 'setRequiresCharging',
    'setRequiresDeviceIdle', 'setPersisted', 'setExact', 'setRepeating',
    'setInexactRepeating', 'setExactAndAllowWhileIdle', 'acquire', 'release')
ORDER BY file_id, method_idx, method, callee;
