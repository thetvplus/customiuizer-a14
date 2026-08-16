# M1 Settings Backup / Maintenance Hardening — Implementation Notes

## Scope

M1 = **BACKUP / RESTORE RELIABILITY ONLY**.

Production changes:
- `PreferenceFragmentBase.kt`: backup filename, stream ownership, fatal propagation, restore result UI.
- `AppSelectionSanitizer.kt`: sanitize result with `changedPrimaryCount`.
- `BackupRestore.kt`: new M1 owner (cold-path only, no framework).

Tests:
- `BackupRestoreTest.kt`
- `AppSelectionSanitizerTest.kt` (updated)
- `FakeSharedPreferences.kt` (updated with commit sequence / visible state / edit/apply failure seams)
- `AppLocaleReconcileTest.kt` (updated to reflect real `commit()` in-memory mutation semantics)

No V2, no ObjectInputFilter final codec, no CRC32, no startup/observer/background work.

## Backup

- Default filename: `r14bak_<MMddHHmmss>`.
- Formatter: immutable `java.time.DateTimeFormatter` (`MMddHHmmss`, `Locale.US`) with `LocalDateTime.now()`.
- Removed the previous shared mutable `BACKUP_DATE_FORMAT` `SimpleDateFormat`.
- Stream ownership: `openOutputStream(uri)?.use { raw -> ObjectOutputStream(raw).use { ... } }`.
- Fatal propagation: `FatalErrors.rethrowIfFatal(t)` before ordinary failure dialog.
- Legacy wire format preserved: root `java.util.HashMap`, string sets as `java.util.HashSet`.

## Restore

### Pipeline

```text
bounded input read
→ ObjectInputStream decode
→ root validation (Map<*, *>)
→ entry validation
→ tombstone / device-derived filter
→ AppSelectionSanitizer (package query before destructive work)
→ pre-restore snapshot capture (defensive StringSet copy)
→ clear + put + commit
→ if commit false: best-effort rollback from snapshot
→ if commit true: locale fast-path invalidate + launcher reconcile
→ deviceReconciled = localeReconciled && launcherReconciled
→ result
```

### Validation

- Root not a `Map<*, *>` → structural `FAILURE`.
- Non-`String` key, unsupported value, malformed `StringSet` → skip entire entry, `invalidSkipped++`.
- Dropped keys:
  - `pref_key_system_notif_disable_strong_toast`
  - `pref_key_system_notif_disable_strong_toast_always`
  - `pref_key_system_notif_disable_strong_toast_dnd`
  - `pref_key_system_notif_strong_toast_width`
- Device-derived / non-exportable keys ignored:
  - `pref_key_miuizer_locale_applied`
  - `pref_key_miuizer_synced_from_lsposed`

### Commit and rollback

- `SharedPreferences.commit()` result is checked.
- `commit()` first applies to the in-memory map, then returns durability status.
- `commit() == false` → `FAILURE`; best-effort rollback from `PRE_RESTORE_SNAPSHOT`.
- Rollback success does not change the result from `FAILURE`.
- No launcher/locale reconcile on commit failure.
- `RESTORE_ATOMICITY = BEST_EFFORT_TRANSACTIONAL_RECOVERY`, not full atomicity.

### Side effects

- Launcher icon: reconciled only after successful commit, using
  `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)`.
  Default enabled if key absent.
- Locale: `AppLocaleController.invalidateFastPath(prefs)` after commit success;
  source `pref_key_miuizer_locale_applied` never trusted.
- `deviceReconciled` is true only when both locale and launcher reconciles succeed.
  A failed side effect after durable commit produces `PARTIAL_FAILURE`, not rollback.

### Input bound

- M1 provisional legacy guard: `M1_LEGACY_MAX_BYTES = 2 MiB`.
- Bounded read of `InputStream` before `ObjectInputStream` decode.
- Labelled in code as **provisional**, not the M2 final `MAX_FILE_SIZE` contract.

### Result model

`RestoreResult` fields:
- `status` (`SUCCESS` / `PARTIAL_FAILURE` / `FAILURE`)
- `restored`
- `deprecatedIgnored`
- `invalidSkipped`
- `appSelectionsSanitized`
- `migrated` (0 in M1, reserved)
- `commitSucceeded`
- `commitConfirmedDurable`
- `deviceReconciled`
- `rollbackAttempted`
- `rollbackSucceeded`

## Test coverage

- Backup filename format (`r14bak_` + 10 digits, no extension).
- Legacy `HashMap` root decode.
- Tombstone and device-derived filtering.
- Root / entry validation.
- Malformed, truncated, oversized input.
- Commit success / partial failure (reconcile failure) / failure and rollback.
- Commit false visible state history (in-memory mutation, rollback snapshot restoration).
- Snapshot defensive copy.
- App-selection sanitization count and post-sanitizer `restored` count.
- Locale reconcile failure as `PARTIAL_FAILURE`.

## Validation run

```text
python tools/verify.py full
python tools/audit-feature-semantics.py --validate
.\gradlew.bat :app:assembleDebug
```

All passed.

## Authorization state

- M0 = PASS
- M1_AUTHORIZATION = YES
- M2_AUTHORIZATION = NO
- PRODUCTION_CHANGE_AUTHORIZATION = NO (M1 only)
