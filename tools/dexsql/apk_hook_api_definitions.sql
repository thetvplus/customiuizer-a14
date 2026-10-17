-- Must be empty for the final module APK: the Hook API is compileOnly.
-- Service SDK classes are allowed; external API references are not definitions.
SELECT d.source_path, d.entry_name, c.descriptor
FROM classes c
JOIN dex_files d ON d.file_id = c.file_id
WHERE c.descriptor LIKE 'Lio/github/libxposed/api/%'
ORDER BY d.source_path, d.entry_name, c.descriptor;
