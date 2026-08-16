# ST1-L1 Notification StrongToast Legacy Removal

## 1. Authority

- 阶段：ST1-L1（PRODUCT DECISION 授权生产清理）。
- 基线：`4717a961c9505c8111a20f4e7fc6352c457e7556`。
- 目标：移除旧的通知域 `DisableStrongToastFeature`，保留新的 `StrongToastPresentationFeature`。

## 2. Product Decision

```text
LEGACY_NOTIFICATION_STRONG_TOAST_REMOVAL = AUTHORIZED
DND_ONLY_SEMANTICS_REMOVAL = INTENTIONAL_ACCEPTED
DND_ONLY_MIGRATION_REQUIRED = NO
```

- 旧的 `系统 → 通知 → 灵动额头` 控制路径被明确废弃。
- DND-only 行为被明确放弃，不迁移到新模式。
- 这不是意外兼容性回归，而是授权的产品变更。

## 3. ST0 历史语义保留

```text
ST0_KEEP_SEPARATE_REASON = DND_ONLY_SEMANTICS_EXISTED
PRODUCT_DECISION = DND_ONLY_SEMANTICS_INTENTIONALLY_DROPPED
```

- ST0/ST1 审计文档继续作为历史证据保留，未重写为 DND-only 能力从未存在。

## 4. 删除内容

### 4.1 旧通知 UI

- 文件：`app/src/main/res/xml/prefs_system.xml`
- 删除 preference：
  - `pref_key_system_notif_disable_strong_toast`
  - `pref_key_system_notif_disable_strong_toast_always`
  - `pref_key_system_notif_disable_strong_toast_dnd`
- 同时删除已注释的旧 SeekBar 块（依赖 `pref_key_system_notif_disable_strong_toast`）。

### 4.2 FeatureId

- 文件：`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt`
- 删除 `DisableStrongToastFeatureId`（id = 106）。
- 未重新编号相邻 ID：105、107 保持不变。

### 4.3 Feature 类与注册

- 文件：`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt`
- 删除 `DisableStrongToastFeature` 类。
- 删除对应的 `LazyFeatureSpec`。

### 4.4 Runtime hook

- 文件：`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUI.kt`
- 删除 `DisableStrongToastHook`。
- 删除 conditional `ZenModeController` 查找与 `isZenModeOn` 反射调用。

### 4.5 字符串资源

删除以下字符串，仅在被旧 UI 引用且无其他 consumer 时：

- `system_notif_disable_strong_toast_title`
- `system_notif_disable_strong_toast_summ`

已移除的 locale：`values`、`values-zh-rCN`、`values-vi-rVN`、`values-pt-rBR`、`values-ja-rJP`、`values-ru-rRU`、`values-cs-rCZ`。

`system_statusbaricons_dnd_title` 仍被其他功能使用，保留。

### 4.6 TweakStrongToastHook

- `SystemUI.TweakStrongToastHook` 与 `system_notif_strong_toast_width` 保持为 `DEAD_LEGACY`。
- 本次未进行额外清理。

## 5. 生成文件更新

- `feature-semantics/a14.json` 从基线 `4717a961` 恢复后，仅删除被移除的旧通知 StrongToast 相关条目；未使用 `--init` 重新生成，未降级任何非相关条目的审核语义。
- `docs/rom-intelligence/A14_PROCESS_MATRIX.*` 通过 `tools/extract_process_matrix.py` 重新生成。
- 生成结果不再包含 `Disable Strong Toast` 或 `system_notif_disable_strong_toast`。

## 6. 保持不变的 NEW StrongToast

```text
system_strong_toast_mode = PRESERVED
StrongToastPresentationFeature = PRESERVED
SystemUIStrongToastHooks = PRESERVED
SYSTEM_DEFAULT = PRESERVED
MATCH_STATUS_BAR_HEIGHT = PRESERVED
HIDE = PRESERVED
strong_toast_height replacement = PRESERVED
getWindowParam().height hook = PRESERVED
targetHeightPx = PRESERVED
status-bar height resolution = PRESERVED
```

## 7. 行为兼容性冻结

```text
OLD_ALWAYS_HIDE = REMOVED
OLD_DND_ONLY_HIDE = REMOVED
NEW_HIDE = PRESERVED
NEW_MATCH_STATUS_BAR_HEIGHT = PRESERVED
NEW_SYSTEM_DEFAULT = PRESERVED
```

升级行为：之前仅依赖旧通知 StrongToast 抑制设置的用户，在升级后将不再获得该抑制。这是有意的。

## 8. 几何边界保持

```text
STRONG_TOAST_HEIGHT_CONSUMERS = NOT_PROVEN
OUTER_WINDOW_GEOMETRY = UNKNOWN
CAPSULE_ROOT_GEOMETRY = UNKNOWN
BACKGROUND_GEOMETRY = UNKNOWN
CORNER_GEOMETRY = UNKNOWN
ANIMATION_GEOMETRY = UNKNOWN
MATCH_HEIGHT_ROOT_CAUSE = NOT_PROVEN
MATCH_HEIGHT_FIX_DIRECTION = NONE
ST2_AUTHORIZATION = NO
```

本次清理不改变任何 ROM geometry 结论。

## 9. 测试

新增 `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StrongToastLegacyRemovalTest.kt` 覆盖：

- A. `DisableStrongToastFeature` 不再注册。
- B. 旧 preference keys 不触发任何 SystemUI feature enablement。
- C. `StrongToastPresentationFeature` 仍注册。
- D. `SYSTEM_DEFAULT / MATCH / HIDE` 模式解析不变。
- E. 相邻 FeatureId 未重新编号。
- F. 通过 `StrongToastPresentationFeature.evaluateEnabled` 验证启用语义。

## 10. 旧 preference 数据

```text
PREFERENCE_MIGRATION = NONE
STALE_LEGACY_PREF_KEYS = INERT_AND_NO_RUNTIME_CONSUMER
```

未添加 SharedPreferences 清理、迁移、观察者或 I/O。

## 11. 性能 / 生命周期预期

- 一条重复的 `showCustomStrongToast` hook 被移除。
- 旧 preference 读取被移除。
- conditional `ZenModeController` 依赖查找被移除。
- conditional `isZenModeOn` 反射调用被移除。
- 无新 observer、cache、retained owner、migration-time hot path、额外运行时分配。

---

*ST1-L1 legacy removal; authorized production change.*
