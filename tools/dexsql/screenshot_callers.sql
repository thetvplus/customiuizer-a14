-- Direct invoke references only; runtime dispatch/reflection are not expanded.
WITH targets AS (
    SELECT DISTINCT method FROM method_strings
    WHERE value LIKE '%saveBitmapToUri: external storage%'
)
SELECT DISTINCT file_id, method_idx, method, callee
FROM method_calls
WHERE callee IN (SELECT method FROM targets)
ORDER BY file_id, method_idx, method, callee;
