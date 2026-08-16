# A14 Settings / Backup / Maintenance Finalization

## Stage

A14 Settings / Backup / Maintenance engineering finalization for `r14.20.0`.

## Current final tree

Determined by the final bookkeeping commit of this task (to be recorded after
push).

## Engineering status

- `M2_BACKUP_V2 = PASS`
- `M4_ABOUT_LANGUAGE = PASS`
- `VOLUME_ROOT_VISIBILITY_ENGINEERING = PASS`
- `VOLUME_SHARED_VISIBILITY_ENGINEERING = PASS`
- `VOLUME_COLOR_ENGINEERING = PASS`
- `DYNAMIC_ISLAND_ENGINEERING = PASS`
- `RECENTS_HIDE_APP_NAME_MIGRATION = PASS`
- `REPOSITORY_EOL_POLICY = PASS`

`ENGINEERING_STAGE = COMPLETE`

## Device acceptance

`DEVICE_ACCEPTANCE = PENDING_FINAL_SIGNED_APK`

Engineering PASS is not device visual PASS. The signed release APK built from
the final maintenance tree must be installed on the target device and the
following user-visible items verified:

1. Dynamic Island no longer appears as a cropped ROM capsule.
2. A single Dynamic Island event shows complete content.
3. Dynamic Island dismiss animation is smooth.
4. Consecutive Dynamic Island events behave correctly.
5. Volume background / icon color is visibly applied.
6. Mute-only / DND-only hide states are correct.
7. Mute + DND double-hide leaves no divider, empty shell, or shadow.
8. Recents "隐藏应用名称" Boolean switch behaves correctly.

## Scope lock

This finalization commit is docs-only. No production code, resource, test,
Gradle, signing, or EOL policy file changes are introduced.
