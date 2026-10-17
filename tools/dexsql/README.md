# DexSQL 开发查询入口

用于开发期 APK / DEX 取证，不参与 Gradle、APK runtime 或默认 CI。
安装、MCP 操作、证据边界及实测结果见 [DexSQL 指引](../../docs/rom-intelligence/DEXSQL.md)。

每个 `.sql` 文件是一条可直接传给官方 CLI 或 MCP `query.sql` 的语句：

| 文件 | 输入 | 用途 |
| --- | --- | --- |
| `definition_sources.sql` | 宿主、插件 / split、必要 boot framework | 统一记录 DEX 定义来源；跨目标比较使用完整描述符和样本哈希，不比较局部索引。 |
| `apk_hook_api_definitions.sql` | 最终 R8 模块 APK | 应为零行：检查 compileOnly Hook API 未被打包，Service SDK 不在禁止范围。 |
| `apk_settings_api_refs.sql` | 最终 R8 模块 APK | 检查 Manifest 入口的直接 Hook API 指令引用；结合 mapping、类加载回归及设备启动复核间接路径。 |
| `screenshot_candidates.sql` | MIUIScreenshot APK | 当前 DexKit 子串、排除包和第 5 个参数约束；生产入口要求唯一候选。 |
| `screenshot_callers.sql` | MIUIScreenshot APK | 字符串目标的直接调用者；仍需用 candidates 查询筛选、用 decode 复核。 |
| `guardprovider_candidates.sql` | GuardProvider APK | 同一 DEX、同一方法同时使用两个子串；零结果只约束输入样本。 |
| `volume_contract.sql` | SystemUI 宿主 + plugin APK | 查询音量按钮声明，配合 `dex_files` 确认来自哪个 APK。 |
| `bootstrap_contract.sql` | SystemUI 宿主及插件 APK | 查询既有初始化 / 插件创建的声明与来源；boot framework 缺失仍是 UNKNOWN。 |
| `api102_bridge_calls.sql` | 最终 R8 模块 APK | 统一统计 setId / getId / replaceHook 的 invoke；结合 mapping 核对原生安装策略及禁止替换，替代旧 setId 单项查询。 |
| `api102_hook_ids.sql` | 最终 R8 模块 APK | 找到三个固定 ID 的指令使用位置；需结合 mapping 和设备回读。 |

修改生产匹配条件或 API 102 入口时同步审查对应查询。查询结果是 ARTIFACT 证据，
不能直接生成生产 Hook、推断实际 ClassLoader 或标记设备功能验收。
SQL 保留 `file_id` 连接键与确定排序；不要将字符串子串搜索改成精确相等。
R8 类合并 / 内联时同时查成员映射；输出类名不能单独证明原安装器归属。

ROM 定位依次运行来源查询、对应功能查询及直接调用者查询，再 decode 唯一候选；
目标差异交给已有 ROM contracts / `rom_target_diff.py`，不另建匹配引擎。
构建产物复核运行来源、两个 `apk_*` 边界查询及 `api102_*` 查询，保存同产物 mapping 的哈希。
这些查询共用官方 CLI 的一次多 SQL 调用或 MCP 数据集，只在相关开发 / 适配任务运行。
