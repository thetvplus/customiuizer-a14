-- Open the host, plugin/splits and required boot framework in one dataset.
-- Header method_count includes references; class_count counts declarations.
SELECT file_id, source_path, entry_name, dex_version, byte_len, class_count
FROM dex_files
ORDER BY source_path, entry_name, file_id;
