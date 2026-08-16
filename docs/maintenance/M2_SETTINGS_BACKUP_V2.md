# M2 Settings Backup / Maintenance Hardening — V2 Format & Restricted Legacy Reader

## 1. Scope

M2 = **V2 backup format + restricted legacy reader**.

- New backups use V2.
- Restore accepts V2 and historical legacy Java-serialized backups.
- Legacy reader is fail-closed and graph-bounded.
- M1 transaction / rollback / side-effect reconciliation is reused unchanged.

## 2. V2 Wire Format

```
Offset  Length  Field                 Type
0       4       MAGIC                 "CUI2" (0x43 0x55 0x49 0x32)
4       4       FORMAT_VERSION        Int32 big-endian = 1
8       4       APP_REVISION          Int32 big-endian = BuildConfig.VERSION_CODE
12      4       ENTRY_COUNT           Int32 big-endian
16      var     TYPED_ENTRIES
var     4       CRC32                 UInt32 big-endian
```

CRC32 covers bytes `[0 .. 16+TYPED_ENTRIES.length-1]`.

Type tags (frozen constants, not enum ordinals):

| Tag | Meaning      |
|-----|--------------|
| 1   | BOOLEAN      |
| 2   | INT          |
| 3   | LONG         |
| 4   | FLOAT        |
| 5   | STRING       |
| 6   | STRING_SET   |

### 2.1 Key / Value Encoding

- **Key**: unsigned 16-bit big-endian length + UTF-8 bytes.
- **BOOLEAN**: 1 byte, only `0` or `1`.
- **INT**: 4-byte signed big-endian.
- **LONG**: 8-byte signed big-endian.
- **FLOAT**: IEEE-754 32-bit big-endian.
- **STRING**: 4-byte signed length + UTF-8 bytes.
- **STRING_SET**: 4-byte item count + each item as STRING.

Negative length / count → `BackupFormatException`.  
Unknown type tag → `BackupFormatException`.  
Malformed / unmappable UTF-8 → `BackupFormatException`.
Malformed / unmappable UTF-16 source (e.g. unpaired surrogates) → `BackupFormatException`; the encoder does not replacement-encode.

## 3. Final Bounds

Offline corpus validation:

```text
30 preference XML files scanned
623 distinct keys
max UTF-8 key length: 66 bytes
```

Frozen constants:

```text
MAX_FILE_SIZE    = 2_097_152  (2 MiB)
MAX_ENTRY_COUNT  = 4_096
MAX_KEY_BYTES    = 128
MAX_STRING_BYTES = 65_535
MAX_SET_ITEMS    = 1_024
```

## 4. Determinism

Pre-filtered entries are sorted by key. `STRING_SET` elements are sorted.  
Same preference state → same payload → same CRC32.

## 5. Backup Writer

`BackupRestore.performBackup`:

1. Copies current preferences.
2. Removes `DROPPED_KEYS` and `NON_EXPORTABLE_KEYS`.
3. Validates all values are supported types.
4. `BackupFormatV2.encode` pre-computes the exact total encoded size,
   validates every bound, then allocates a bounded `ByteArrayOutputStream` and
   writes. No temporary payload larger than `MAX_FILE_SIZE` is created.
5. Writes to SAF output stream.

Unsupported value → `BackupFormatException`, backup FAILS (no partial file).

`BackupFormatV2.encode` enforces:
- `MAX_ENTRY_COUNT`
- `MAX_KEY_BYTES`
- `MAX_STRING_BYTES`
- `MAX_SET_ITEMS`
- total encoded size <= `MAX_FILE_SIZE` (preflight, before buffer allocation)
- final encoded size == pre-computed size

Dropped / non-exportable keys:

```text
pref_key_system_notif_disable_strong_toast
pref_key_system_notif_disable_strong_toast_always
pref_key_system_notif_disable_strong_toast_dnd
pref_key_system_notif_strong_toast_width
pref_key_miuizer_locale_applied
pref_key_miuizer_synced_from_lsposed
```

## 6. Format Detection

First 4 bytes:

- `43 55 49 32` (`CUI2`) → V2
- `AC ED 00 05` → legacy Java serialization
- anything else → `FAILURE`

`PreferenceFragmentBase` does **not** perform format detection; it calls
`BackupRestore.performBackup` / `BackupRestore.performRestore`.

## 7. V2 Decode Order

1. File length within `MAX_FILE_SIZE`.
2. Minimum header/footer length (`MIN_FILE_LENGTH = 20`).
3. Stored CRC32 footer.
4. Recompute CRC32 over payload.
5. CRC mismatch → `BackupFormatException`.
6. Magic, version, entry count.
7. Per-entry key length / type tag / value length.
8. No trailing bytes after `ENTRY_COUNT` entries.

## 8. Restricted Legacy Reader

### 8.1 Mechanism

`LegacyBackupDecoder` — a focused restricted Java-serialization parser that
replaces `ObjectInputStream` / `ObjectInputFilter`:

- Parses only the proven historical ObjectOutputStream wire subset.
- Enforces graph, reference, and allocation bounds before any object is fully
  instantiated or any custom `readObject` can run.
- No reflection, no `setObjectInputFilter`, no per-stream hidden-API calls.
- Runs on API 34 without depending on JDK 17 `ObjectInputFilter` availability.

`BackupRestore.decodeLegacyBackup` delegates to `LegacyBackupDecoder.decode` and
re-throws fatal / format errors as `BackupRestoreException`.

### 8.2 Exact Wire Allowlist

Historical fixture evidence (host JDK 17 `ObjectOutputStream` / API 34 AOSP
`ObjectStreamClass` constants):

| Class | SUID | Flags | Fields | Superclass |
|-------|------|-------|--------|------------|
| `java.util.HashMap` | `362498820763181265` | `0x03` (`SC_SERIALIZABLE \| SC_WRITE_METHOD`) | `float loadFactor`, `int threshold` | none |
| `java.util.HashSet` | `-5024744406713321676` | `0x03` | none | none |
| `java.lang.Boolean` | `-3665804199014368530` | `0x02` (`SC_SERIALIZABLE`) | `boolean value` | none |
| `java.lang.Integer` | `1360826667806852920` | `0x02` | `int value` | `java.lang.Number` |
| `java.lang.Long` | `4290774380558885855` | `0x02` | `long value` | `java.lang.Number` |
| `java.lang.Float` | `-2671257302660747028` | `0x02` | `float value` | `java.lang.Number` |
| `java.lang.Number` | `-8742448824652078965` | `0x02` | none | none |

`java.lang.String` is `TC_STRING`, not an object class descriptor.
`java.lang.Object` never appears as a serializable class descriptor in the
proven graph.

The decoder explicitly rejects `LinkedHashMap`, `LinkedHashSet`, `ArrayList`,
`Date`, `Double`, `Enum`, arrays, proxies, custom `Serializable`, and any
application class before it can be instantiated.

Android API 34 runtime evidence for the above wire graph is **not available**
in this environment; the allowlist is based on the historical host fixture.

### 8.3 Graph / Allocation Limits

Historical host fixture evidence (5 entries + 3-item `HashSet`):

```text
maxDepth       = 3
maxReferences  = 23
maxArrayLength = 16
```

Decoder-enforced headroom limits:

```text
LEGACY_MAX_DEPTH            = 16    (nested object graph)
LEGACY_MAX_DESCRIPTOR_DEPTH = 8     (class-descriptor superclass recursion)
LEGACY_MAX_REFERENCES       = 100_000  (allocated wire handles)
LEGACY_MAX_ARRAY_LENGTH     = 16_384
```

`LEGACY_MAX_ARRAY_LENGTH` is set to the next power-of-two headroom for a
full 4 096-entry `HashMap` / `HashSet` table (`4 096 / 0.75 ≈ 8 192`;
`16_384` provides margin). The bound is checked on the declared `HashMap` /
`HashSet` capacity before the backing table is allocated.

Handle allocation (`class descriptors`, `objects`, `strings`) is bounded in
`LegacyBackupDecoder.assignHandle()`; the handle table cannot exceed
`LEGACY_MAX_REFERENCES`.

Class-descriptor recursion (`readNewClassDesc` → `superClassDesc`) is bounded
separately from object depth.

Fail-closed descriptor policy:
- Unknown class name is rejected immediately after it is read, before fields,
  class annotations, or superclass descriptors are parsed.
- SUID, flags, field count, field names/types, and superclass relationship are
  validated against the exact frozen schema for the class name.
- `SC_EXTERNALIZABLE`, `SC_ENUM`, `SC_BLOCK_DATA`, or unknown flag bits are
  rejected for every allowlisted class.
- Proxy / enum → rejected.
- `TC_ARRAY` → rejected (the historical wire graph contains no serialized arrays;
  internal `Node[]` allocation is bounded by `LEGACY_MAX_ARRAY_LENGTH`).

### 8.4 Proxy / Enum / Array Policy

- `TC_PROXYCLASSDESC` → `BackupRestoreException`.
- `TC_ENUM` → `BackupRestoreException`.
- `TC_ARRAY` → `BackupRestoreException`.
- `TC_CLASS` / `TC_LONGSTRING` / unknown type code → `BackupRestoreException`.

### 8.5 API 34 Runtime Evidence

Per-stream `ObjectInputFilter` capability on Android 14 / API 34 was **not
independently verified** in this environment. `LegacyBackupDecoder` removes all
`ObjectInputStream` / `ObjectInputFilter` dependence and instead parses only the
proven, frozen ObjectOutputStream wire subset.

The decoder validates every class descriptor against the exact historical schema
(SUID, flags, fields, superclass) and rejects any deviation before object data is
read. Host JDK 17 unit tests cover the normal, evil `readObject`, graph-limit,
descriptor-depth, handle-count, and array-capacity paths. An Android
instrumentation test for API 34 is not available in this environment and is not
included.

### 8.6 Residual Limitations

- The legacy parser supports only the proven historical wire graph. If an
  Android device emits a different `ObjectOutputStream` layout, a different
  `serialVersionUID`, or a different superclass chain, the parser rejects it.
  Adding a new descriptor to the allowlist requires an explicit API 34 / AOSP
  fixture and an independent audit.
- `TC_ARRAY` is rejected entirely because the historical wire graph contains no
  serialized arrays. If Android evidence shows a required array, the policy must
  be revisited.
- The legacy stream must contain exactly one root object. Trailing bytes or
  additional root objects are rejected.
- Android API 34 runtime evidence is unavailable in this environment.

### 8.7 Raw Safe Invalid Entry Policy

`LegacyBackupDecoder.readHashMap` returns `HashMap<Any?, Any?>` and **does not**
silently drop:

- non-`String` keys;
- values that are not final `SharedPreferences` types but were safely decoded;
- `HashSet` elements that are not `String`.

Every safely-decoded key and value is preserved in the raw map and handed to the
shared `BackupRestore.validateAndNormalizeEntries` for entry-level
classification. The decoder is still responsible for preventing **unsafe**
objects from being instantiated (custom `Serializable`, disallowed classes, etc.),
but it does not perform `SharedPreferences`-specific policy.

`BackupRestore.validateAndNormalizeEntries` now produces:

- `invalidSkipped++` for a non-`String` key;
- `invalidSkipped++` for an unsupported value (e.g. a nested `HashMap`);
- `invalidSkipped++` for a `StringSet` whose members are not all `String`.

### 8.8 HashMap / HashSet Serialized Metadata Validation

The decoder treats collection metadata as structural data and fails closed on
malformed values:

- `HashMap` class-data `loadFactor` must be `> 0` and not `NaN`.
- `HashMap` custom-block `loadFactor` (12-byte variant), if present, must be `> 0`
  and not `NaN`.
- `HashMap` / `HashSet` explicit `capacity`, if present, must be `>= 0`.
  A genuinely absent `capacity` (4-byte legacy variant) is represented as `null`
  and derived from `size`; a negative explicit `capacity` is a malformed stream.
- `HashSet` custom-block `loadFactor` (12-byte variant), if present, must be `> 0`
  and not `NaN`.
- `threshold` is read as a historical serialized field but is not used as restore
  state.

Explicit-capacity `null` (field absent in older wire) and an explicit negative
value are distinguished; only the latter is treated as an attack.

## 9. Post-Decode Bounds

After V2 or legacy decode, `postDecodeBoundsCheck` verifies:

- map entry count <= `MAX_ENTRY_COUNT`
- key UTF-8 bytes <= `MAX_KEY_BYTES`
- String bytes <= `MAX_STRING_BYTES`
- StringSet items <= `MAX_SET_ITEMS`
- StringSet item bytes <= `MAX_STRING_BYTES`

`validateAndNormalizeEntries` then continues M1-style filtering.

## 10. Unified Restore Pipeline

```text
bounded input read (MAX_FILE_SIZE)
→ format detection
→ V2 decode OR restricted legacy decode
→ post-decode bounds
→ validateAndNormalizeEntries
→ AppSelectionSanitizer
→ PRE_RESTORE_SNAPSHOT
→ clear + puts + stage locale reconcile marker + commit
→ rollback on commit(false)
→ launcher reconcile
→ RestoreResult
```

No separate `performV2Restore` / `performLegacyRestore`.

## 11. Security Statement

- CRC32 is for **accidental corruption detection only**.
- No authentication, tamper protection, encryption, protobuf, or database.
- Legacy filter rejects custom `readObject` classes before instantiation.

## 12. Test Matrix

V2:
- header (magic / version / app revision / entry count)
- round-trip all 6 types
- deterministic keys and sets
- dropped / non-exportable filter
- CRC payload / header / footer mutations
- unsupported version
- unknown type / invalid boolean
- negative / oversized lengths
- exact `MAX_FILE_SIZE` and `MAX_FILE_SIZE + 1` writer boundaries
- malformed UTF-8

Legacy (host JDK 17 unit tests; API 34 instrumentation unavailable):
- `HashMap` root with Boolean, Int, Long, Float, String, `HashSet`
- exact class-descriptor schema (SUID, flags, fields, superclass)
- reject `LinkedHashMap`, `LinkedHashSet`, `ArrayList`, `Double`
- reject custom `Serializable` with `readObject` before execution
- reject `TC_PROXYCLASSDESC` / `TC_ENUM` / `TC_ARRAY` / unexpected class descriptor
- reject over `LEGACY_MAX_ARRAY_LENGTH` capacity
- reject over `LEGACY_MAX_DEPTH` nested graph
- reject over `LEGACY_MAX_DESCRIPTOR_DEPTH` class-descriptor chain
- reject over `LEGACY_MAX_REFERENCES` handle allocation
- root-only / trailing-byte / second-root rejection
- wrong root
- oversized input
- `MAX_FILE_SIZE` bounds
- raw invalid entries preserved and counted by `validateAndNormalizeEntries`
  (non-`String` key, malformed `StringSet`, nested `HashMap` value)
- `HashMap` / `HashSet` metadata validation
  (positive finite `loadFactor`, non-negative explicit `capacity`)

## 13. M1 Regression Coverage

M2 changes do not alter:
- `commit(false)` in-memory mutation semantics
- `PRE_RESTORE_SNAPSHOT`
- best-effort rollback
- restored count after sanitizer
- tombstone / device-derived filtering
- locale reconcile marker staged into the primary `commit`
- launcher partial failure
- fatal propagation

## 14. Files

- `app/src/main/java/tv/withaibuild/customiuizer/utils/BackupFormatV2.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/utils/BackupRestore.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/utils/LegacyBackupDecoder.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupFormatV2Test.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BackupRestoreTest.kt`
- `docs/maintenance/M2_SETTINGS_BACKUP_V2.md`

## 15. Authorization

- M1 = PASS
- M2_SELF_ASSESSMENT = PASS_CANDIDATE
- M2_INDEPENDENT_GATE = PASS
- M2_FREEZE_SHA = `026b9364ef362fabc14903cc7ee1695ee135af26`
- M3_AUTHORIZATION = NO

Independent gate coverage includes:

- CUI2 framing / version / app revision / entry count
- all six explicit type tags
- deterministic key / StringSet serialization
- CRC32 payload coverage
- trailing byte rejection
- strict UTF-8 decoder
- strict UTF-8 encoder / malformed UTF-16 rejection
- file / entry / key / String / set bounds
- legacy focused parser
- exact descriptor allowlist
- graph / descriptor-depth / handle / capacity bounds
- no unrestricted ObjectInputStream restore
- unified restore pipeline
- validation/filtering
- commit(false) rollback
- locale reconcile marker
- launcher reconcile
- fatal propagation

API34 legacy runtime instrumentation is **not available / not required for this
static gate**; the current evidence consists of host JDK 17 unit tests, static
analysis, and the restricted historical wire fixture documented above.
