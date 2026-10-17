-- Both substrings must occur in the SAME method in the SAME DEX.
SELECT DISTINCT a.file_id, a.method_idx, a.method
FROM method_strings a
JOIN method_strings b ON b.file_id = a.file_id AND b.method_idx = a.method_idx
WHERE a.value LIKE '%getUnSystemAppList error%'
  AND b.value LIKE '%AntiDefraudAppManager%'
  AND a.method_class NOT LIKE 'Ltmsdk%'
  AND a.method_class NOT LIKE 'Lxcrash%'
  AND a.method_class NOT LIKE 'Lcom/tencent%'
  AND a.method_class NOT LIKE 'Lcom/xiaomi%'
ORDER BY a.file_id, a.method_idx, a.method;
