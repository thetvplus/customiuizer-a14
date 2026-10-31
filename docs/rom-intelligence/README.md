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

源码关联、ROM 契约及基线的只读查询、字段选择与证据边界见[工具入口](../TOOLS.md#辅助数据按需查询)。

仅在私有接口、Hook 签名或 ClassLoader 不确定时按需通过上述 DexSQL / Catalog 入口取证。源码和当前 ROM 合同优先，旧日期快照不作为现状。原始 APK / JAR、trace、mapping、日志和 profiler 保留在仓库外。
