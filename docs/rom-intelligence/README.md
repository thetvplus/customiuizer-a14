# 进程矩阵与 ROM 证据

开发期深查字符串、调用和指令时，可使用 [DexSQL 指引](DEXSQL.md)。它是可选补充，不代替现有清单、契约门禁或实机证据。

通知专项的启动事务、Heads-up、权限与 Count/Fold 定位合同见 [M03 通知精校交接](M03_NOTIFICATION_CONTRACT.md)；完成状态仍维护在精修路线图原页。

## 当前源码生成物

[A14_PROCESS_MATRIX.json](A14_PROCESS_MATRIX.json)（完整字段、scope 与 routing）和 [CSV](A14_PROCESS_MATRIX.csv) 由 [extract_process_matrix.py](../../tools/extract_process_matrix.py) 从 Feature catalog、入口和 scope 生成，属于静态调用矩阵，不是 ROM 实机覆盖表。

```powershell
python tools/extract_process_matrix.py
python tools/query_aux_data.py matrix --target SYSTEM_UI --limit 5
```

修改 catalog / routing 后核对生成 diff，并运行 Python 工具测试和 CI determinism。不要手动改生成表。非表格进程 / ClassLoader 合同维护在 [架构](../../ARCHITECTURE.md#入口与进程路由)，不再复制数万字的生成 Markdown。

语义、ROM 契约及基线的只读查询、字段选择与证据边界见[工具入口](../TOOLS.md#辅助数据按需查询)。

## 历史 fuxi 样本

- [运行时采样](FUXI_HYPEROS1_RUNTIME_AUDIT_2026-08-14.md)：StrongToast、维护动作和权限页的当时观察。
- [ABI 与样本 provenance](FUXI_HYPEROS1_AI_HOOK_CORPUS_2026-08-14.md)：插件 / 宿主边界、原件 SHA-256 与取证限制。
- [机器可读目标](FUXI_HYPEROS1_HOOK_TARGETS_2026-08-14.json)：同一历史样本的类、字段和方法签名。

这些是日期固定的证据快照；文件中的“当前 / 本轮”指当时采样，不代表当前设备、源码、框架或发行 APK 已验收。2026-08-14 shell 未获得 root，后续其他采样的 root 能力不能倒推给该批样本。JAR 未暴露 DEX 也不能证明方法不存在。

新取证先记录设备 / ROM 指纹、APK revision、样本 SHA-256、ClassLoader 和权限，再区分 SOURCE、ARTIFACT、RUNTIME 与 CANDIDATE。原始 APK / JAR、trace、mapping、日志与 profiler 留在忽略目录或仓库外。当前支持范围和待确认行为见 [COMPATIBILITY.md](../../COMPATIBILITY.md)。
