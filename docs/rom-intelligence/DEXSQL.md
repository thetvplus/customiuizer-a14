# DexSQL 开发取证

DexSQL 是 ROM 适配、Hook 定位、目标差异和最终 DEX 复核的核心主机取证入口，
与当前 DexKit 运行时定位、ROM 清单和契约扫描协作；优先复用官方 CLI / MCP 与[现有查询集](../../tools/dexsql/README.md)。
它不进入 APK、不修改 Hook 注册，也不是默认构建 / CI 的前置条件。
工具连接状态、样本测量和逐次评估留在本地证据或 Git 历史；使用前核对已安装工具及其文档。

## 与 API 102 的联合验证

1. 对真实 ROM 运行 `bootstrap_contract.sql`，从定义视图和 `dex_files` 确认安装点属于宿主、插件还是 boot framework。
   对匹配方法使用 `decode`、`method_calls` 和字段引用复核原回调所需的签名 / 加载链。
2. 为既有基础设施安装点选择固定 ID；最低 API 102 原生配置 ID / 回读确认。
   实现与生命周期范围见 [API 102 架构](../../ARCHITECTURE.md#api-102-安装与-service)。
3. R8 后重新打开该 APK，先运行 `definition_sources.sql`、`apk_hook_api_definitions.sql` 和
   `apk_settings_api_refs.sql` 复核来源与设置进程边界，再运行 `api102_bridge_calls.sql`，用同产物 mapping 确认原生安装 invoke 的 owner。
   检查 setId/getId 及其安装调用链，replaceHook 应为零；不以 R8 内联后的指令数推断运行安装次数。
4. 用 `api102_hook_ids.sql` 检查 ID 的实际指令使用位置。它只证明产物包含安装配置，
   还须在 API 102 设备记录 `<id> (confirmed)` 才能确认 readback。
5. 关闭数据集并保存工具版本、输入哈希、SQL、mapping 归属和验证范围；不要把这些结果转成自动热重载授权。

R8 会合并类或内联安装方法。类映射只说明输出容器，还需匹配方法 / 行号映射才能归属原安装器；
不要仅凭反混淆类名判断 Hook 属于哪个功能。

## 安装与接入

使用 [官方发布](https://github.com/LSPosed/dexsql-release/releases) 中与主机匹配的可执行文件，
核对发布 API 的 SHA-256，保留同版本 LICENSE 和 LICENSE-AUXILIARY。
DexSQL 引擎闭源，公开文档和安装技能另用 MIT；不将引擎或许可证副本打包进本项目。
安装路径由本机管理，不在仓库固定绝对路径或强制某个版本。

官方提供 [安装技能](https://github.com/LSPosed/dexsql-release/tree/master/skills/install-dexsql)。
手动接入 Codex 的形式为：

```text
codex mcp add dexsql -- <本机可执行文件绝对路径> mcp
codex mcp get dexsql --json
```

修改已有配置前备份；已有服务应保留自定义参数、环境和超时。
配置成功不等于当前聊天已加载工具，必要时重载 MCP 连接 / 重新打开聊天。
以实际工具可见性和 initialize / tools/list 结果分别确认客户端与协议状态。

## Codex 日常流程

1. 先读本目录的 [证据规则](README.md)、过程矩阵和该功能的生产匹配条件。
   用 `rom_inventory.py` 记录 APK 身份、版本和 SHA-256；记录来源、ROM fingerprint、
   获取时间及候选 ClassLoader。原件与输出放忽略目录或仓库外，不上传厂商样本。
2. 通过 MCP `resources/list` / `resources/read` 阅读已安装版本的内嵌文档；
   `list_tables` / `describe_table` 核实 schema，再 `open_dataset`，复用返回的 ID。
   检查 `has_dex`、`archive_inputs` 和 diagnostics；无 DEX 的 JAR 不能证明目标不存在。
3. 选择 [查询集](../../tools/dexsql/README.md)，把文件内容作为 `query.sql` 数组中的一项。
   输入宿主、插件或 split 时分别保留来源，用 `dex_files` 将 `file_id` 映射到源路径和 DEX。
   搜索先收敛字符串与签名，再查调用 / 字段交叉引用，最后 `decode`。
4. 大结果按 `next_offset` 继续；查询分页须保持 SQL 与确定排序不变。
   decode 的文本续页位于对应 `methods[]` 条目中，使用其 `next_offset`。
   `int64` / `uint64` 单元格是十进制字符串，不按浮点数读取；空结果不是执行失败。
5. 用现有解析器、SDK dexdump 或 DexKit 核对关键目标；保存原 SQL、工具版本、
   输入哈希、完整描述符、文件来源和匹配数量。独立 DEX 索引不能跨文件直接连接。
   R8 分析使用同一 APK 的 mapping，不把混淆名写成长期生产合同。
6. 结果写成 ARTIFACT / CANDIDATE，人工确认后再交给现有契约 / diff 流程。
   设备操作和实际 Hook 日志另外记录 RUNTIME。完成后 `close_dataset`。
   输入变化后关闭重开；运行超时 / 取消会关闭受影响的数据集，重查 `list_datasets`。

可复用的提问形式：

> 对指定 APK 先记录身份，再按对应查询集找候选。返回定义来源、完整签名、
> 字符串证据、直接调用者及关键 Smali，并注明缺失输入、反射和 ClassLoader 的边界。
> 多候选或零结果时先补证据，不自动生成宽泛 Hook fallback。

### CLI 复核

以下 PowerShell 参数由调用者填入，查询文件不需要另一个封装脚本：

```powershell
$dexsqlExe = '本机 DexSQL 可执行文件绝对路径'
$sampleApk = '本机 MIUIScreenshot.apk 绝对路径'
Get-FileHash -LiteralPath $sampleApk -Algorithm SHA256
$hookQuery = Get-Content -LiteralPath tools/dexsql/screenshot_candidates.sql -Raw -Encoding utf8
& $dexsqlExe query --input $sampleApk "--sql=$hookQuery" --timing
```

使用 `--sql=...` 使文件开头的 SQL 注释不被 CLI 当成参数。
一次 CLI 调用可以重复 `--input` 和 `--sql=...`；每项 SQL 保持一条语句。
需要反复探索时优先复用 MCP 数据集；CLI 的按需加载与 MCP 完整准备耗时口径不同。
大量重复会话按实际成本评估官方 `prepare-cache` / `query-cache`，不默认增加缓存管理层。
保留默认来源校验，不使用 `--trusted-input` 绕过缓存新鲜度检查。

## 结果的边界

| 问题 | 可以得到的证据 | 仍需补充 |
| --- | --- | --- |
| 类 / 方法 / 字段存在 | `classes`、`class_methods`、`class_fields` 的声明 | 是否被加载、是否走到 Hook |
| 字符串和混淆目标 | 子串、参数类型、字段读写与调用的组合候选 | 唯一性、语义和运行行为 |
| 调用关系 | `method_calls` 中的直接 invoke 引用 | 动态派发、反射、JNI 和实际调用次数 |
| ROM 目标变化 | 两份有来源的 APK 中的声明与候选差异 | APK / split / 插件是否齐全、同版本 boot / ART 映像 |
| ClassLoader | 定义属于哪个输入 APK / DEX | 运行时加载器与可见性；Manifest 进程不是 ClassLoader 证明 |
| 资源 / Manifest | IDs、配置、声明和解码 XML | AAPT2 / 设备交叉核验；资源引用不是已求值的运行结果 |

`methods` / `fields` 包含外部引用，不能用它们证明声明存在。
`file_id` 和各类索引仅在当前数据集内有效；跨 APK 比较使用完整描述符与样本哈希。
SQL 的 `COUNT(*)` 不等于 Hook 调用次数。
Smali 输出不是完整 Java / Kotlin 反编译，必要时继续用 JADX。
Quickened / odex 指令按已安装版本确认支持情况；缺少 ART 映像时保持 UNKNOWN。

`rom_inventory.py` 继续管理 provenance / ABI，`rom-contract-scan.py` 继续扫描已有 smali 合同，
`rom_target_diff.py` 继续分类已确认的目标矩阵。DexSQL 为这些流程补充调用与指令证据，
不凭相似字符串自动宣判 RENAMED / REMOVED，也不自动扩大 Android 支持范围。
生产 DexKit 继续使用实际目标 ClassLoader 解析方法并遵守既有关闭生命周期。
