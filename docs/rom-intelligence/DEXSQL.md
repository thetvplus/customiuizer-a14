# DexSQL 开发取证

DexSQL 是 ROM 适配、Hook 定位、目标差异和最终 DEX 复核的核心主机取证入口，
与当前 DexKit 运行时定位、ROM 清单和契约扫描协作；优先复用官方 CLI / MCP 与[现有查询集](../../tools/dexsql/README.md)。
它不进入 APK、不修改 Hook 注册，也不是默认构建 / CI 的前置条件。
工具连接状态、样本测量和逐次评估留在本地证据或 Git 历史；使用前核对已安装工具及其文档。

## 离线 ROM 知识库

`python tools/rom_catalog.py --help` 是已提取 ROM 的统一入口。使用 Python 3.11+ 标准库，复用官方 DexSQL，
不启动常驻服务、不修改分区、不展开 Java / Smali，也不进入 APK / CI。
默认从 Git 主工作区的同级 `forA14-ROM-ext` 定位样本，兼容工作树与不同盘符；
显式路径用 `update --root PATH` 或 `A14_ROM_ROOT`。无法唯一定位时要求指定路径，不扫描整盘。
ROM ID 保留真实目录名，版本 / SDK / 区域及 ODM 设备来自分区属性；fingerprint 同时记录属性键与文件，
vendor / ODM 指纹可能沿用旧平台，不能替代 system 的 SDK 或作为整机运行证明。

### 跨版本参考隔离

生产检索要求 system SDK 34、Android 14、HyperOS 主版本 1 的属性同时成立，目录名称不授予权限。
样本及功能准入见[兼容矩阵](../../COMPATIBILITY.md#a14-样本与能力准入)。Catalog 以 ODM device 识别 duchamp，保留原目录名。
`dada-cn-A16-HyperOS3` 的实际 SDK 36 / HyperOS 3 标记为 `REFERENCE_ONLY`；未知元数据标 `UNCLASSIFIED`。
旧 Catalog 升级后先拒绝未分类样本，显式 `update` 只刷新身份；默认不遍历 reference 的内容。
默认 `locate / search / prepare / compare / benchmark` 均排除参考样本，显式给出其名称也不会绕过。
查询会检查身份属性文件是否变化，源身份变化必须 update；跨版本相同哈希也不能复用生产查询结果的授权。

仅在明确研究动画、视觉或交互时，显式选择一个 ROM 及参考目的：

```powershell
python tools/rom_catalog.py update --reference-rom dada --reference-purpose animation
python tools/rom_catalog.py locate --rom dada --component systemui --reference-purpose animation
python tools/rom_catalog.py prepare --rom dada --component systemui --reference-purpose animation
python tools/rom_catalog.py compare class 'Lcom/android/systemui/SystemUIApplication;' --exact --component systemui --rom fuxi-cn --reference-rom dada --reference-purpose animation
```

目的值限 `animation / visual / interaction`。普通地区 / 硬件对比用 `compare --rom fuxi-cn --against-rom munch`，只选两个生产来源。
跨版本结果分别带 `scope` 与 `DEX_STATIC`，不能合并为兼容结论；新系统 API、签名、字段和资源 ID 不得进入 A14 合同。
参考算法必须重新定位 A14 的原生 owner、方法和生命周期，再独立做 A14 静态及实机验证；静态样本不自动成为实机通过。

SQLite 与官方 `cache.dxc2` 默认位于主工作区同级 `forA14-ROM-index`，可用 `A14_ROM_INDEX` 或
全局参数 `--index-dir PATH` 改写。原分区保持只读；拒绝把索引放进 ROM 或当前 Git 工作区。
首次只需执行一次清单更新与核心预热：

```powershell
python tools/rom_catalog.py doctor
python tools/rom_catalog.py update
python tools/rom_catalog.py prepare --priority
```

`doctor` 检查现有可执行文件、官方缓存命令及短生命周期 MCP 握手；不会改写客户端配置。
工具从 `DEXSQL_EXE`、PATH、现有 Codex 配置及用户安装目录选择程序，`--dexsql PATH` 可显式指定。
协议可用不代表当前聊天已加载 MCP；已加载时，同一会话的大量探索继续复用官方 Dataset。
持久缓存由官方 `prepare-cache / query-cache` 维护，始终保留默认来源校验，禁止 `--trusted-input`。

日常 Agent 先定位，再按完整描述符或子串检索；结果为有确定排序、来源路径和 SHA-256 的精简 JSON。
`--rom` 匹配目录名子串，组件可选 `systemui / launcher / miuihome / poco / plugin / framework`，
也可指定路径或包名子串。已知角色按精确归属筛选，不把 SystemUI 包名前缀或资源 overlay 当宿主。

```powershell
python tools/rom_catalog.py status
python tools/rom_catalog.py locate --rom munch --component launcher
python tools/rom_catalog.py search class 'Lcom/android/systemui/SystemUIApplication;' --exact --rom fuxi-cn --component systemui
python tools/rom_catalog.py search method 'WindowElement' --rom fuxi-cn --component launcher --limit 10
python tools/rom_catalog.py search string 'SystemUIService' --rom fuxi-cn --component systemui
python tools/rom_catalog.py search callers 'Lcom/android/systemui/SystemUIApplication;->startServicesIfNeeded()V' --exact --rom fuxi-cn --component systemui
python tools/rom_catalog.py compare class 'Lcom/miui/home/recents/anim/WindowElement;' --exact --component launcher
```

`callees` 查调用目标，`callers` 查直接调用者；类 / 方法只查定义视图。`--exact` 指精确匹配，默认是字面子串，
不会把 `% / _` 当通配符。相同文件跨 ROM 只查一次并合并来源；比较使用描述符与证据行，不连接局部索引。
默认每份内容 20 行、最大 100 行，查询最多选 64 个文件；沿 `next_offset` 使用相同条件和 `--offset` 继续。
字符串仅展示前 240 字符并附原长度；缺失 DEX / 无效归档明确列为 skipped，零行不证明运行时不存在或已经移除。

只有 `update` 会遍历 APK / JAR / DEX 文件。它按大小、修改 / 元数据时间跳过未变化文件，
新内容计算 SHA-256，按哈希去重 Manifest 与 DEX 条目元数据；不解析全仓 DEX。
使用 `update --rehash` 可强制重核内容，复制 / 替换或来源存疑时应执行。
清单记录归档内 DEX 路径；APEX、ART / OAT 的深层提取不在这个最小闭环内。
更新失败回滚清单；目录迁移显式 update，官方缓存若引用旧路径，重新 prepare 绑定可用来源。

核心预热仅覆盖 SystemUI、MiuiHome / POCO、SystemUI 插件，以及 `framework / services / miui-framework / miui-services.jar`；
其他组件保留轻量文件清单，确有需要时 `prepare --rom ... --component ...`。
缓存以工具二进制哈希与输入哈希隔离，构建成功并校验后替换；缺失、改动或损坏会报错，使用 `prepare --rebuild` 修复。
旧输入 / 工具的缓存保留在外部目录，不自动删除证据；确定不用后可删除其独立哈希目录，之后按需重建。

少量常用查询另有 SQLite 结果复用，最多保留 128 条、每条 64 KiB；键包含输入哈希、工具哈希及完整查询。
每次只检查选中源文件的大小和时间，源文件变化会要求 update；不做全目录扫描。
保留时间戳的异常替换需 `update --rehash`，独立复核用 `search / compare --fresh` 绕过结果复用并让官方引擎校验源。
缓存失效不回退为未经说明的空结果。`locate` 给出原件绝对路径与缓存文件，复杂 SQL 直接使用既有查询集，
Smali 用官方 `decode`，Java / Kotlin 分析按已收敛的类交给 JADX；不另建解码或匹配引擎。

### 检索测量

`benchmark` 复用 search 的条件，交替运行官方 lazy `query` 与 `query-cache`，验证结果完全相同，
报告中位耗时、预热成本、查询次数和输出字节；不清系统文件缓存，不把输出字节等同实际 Token 账单。

```powershell
python tools/rom_catalog.py benchmark string 'SystemUIService' --rom fuxi-cn --component systemui --repeats 3
```

小查询的官方持久缓存可能更慢，不能仅凭建立缓存宣称加速。是否扩大预热以实测决定；
重复结果复用的查找时间与完整命令启动时间分开记录。原始输出、分词器估算和复验凭据只保存在外部索引的
`evidence/`，当前决定与证据入口见[原路线图](../maintenance/A14_REFINEMENT_ROADMAP.md)。

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

1. ROM 输入先用上述知识库定位，不为新会话重复扫描；再按任务读本目录的 [证据规则](README.md)、过程矩阵和生产匹配条件。
   既有 `rom_inventory.py` 可补充独立样本的 provenance / ABI；记录来源、ROM fingerprint、
   获取时间及候选 ClassLoader。原件与输出放忽略目录或仓库外，不上传厂商样本。
2. 通过 MCP `resources/list` / `resources/read` 阅读已安装版本的内嵌文档；
   `list_tables` / `describe_table` 核实 schema，再 `open_dataset`，复用返回的 ID。
   检查 `has_dex`、`archive_inputs` 和 diagnostics；无 DEX 的 JAR 不能证明目标不存在。
3. 选择 [查询集](../../tools/dexsql/README.md)，把文件内容作为 `query.sql` 数组中的一项。
   输入宿主、插件或 split 时分别保留来源，用 `dex_files` 将 `file_id` 映射到源路径和 DEX。
   搜索先收敛字符串与签名，再查调用 / 字段交叉引用，最后 `decode`。
   相关 SQL 合并为一次批量请求；重复使用的筛选可定义 `named_temp_queries`，其生命周期仅限该 Dataset，
   不会物化结果或建立磁盘缓存。接口实现先查 `class_interfaces`，再结合定义与调用关系复核。
4. 大结果按 `next_offset` 继续；查询分页须保持 SQL 与确定排序不变。
   decode 的文本续页位于对应 `methods[]` 条目中，使用其 `next_offset`。
   `int64` / `uint64` 单元格是十进制字符串，不按浮点数读取；空结果不是执行失败。
   已安装 schema 支持且客户端能读取 `structuredContent` 时，查询、decode、Manifest / Resources 工具优先
   使用 `compact: true`；同时用选择性 WHERE、SQL LIMIT、`limit` / `max_bytes` 限制工作和输出。
   旧版本不传未支持的参数；精简输出不会替代查询范围限制或改变分页规则。
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
