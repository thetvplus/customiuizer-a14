# A14 工作流

## 单任务

从模板创建一个 `FIX`、`FEATURE`、`OPTIMIZE`、`PORT` 或 `INFRA` 任务。
Review 与 Implement 不分拆。

## Explore

先限定进程、feature、installer、目标 Hook/Controller 和测试。已有可信路径后直接实现。

## Implement

修改代码、测试和必要的长期文档。普通技术决定由 Devin 自行完成。禁止顺手进行无关
依赖升级、资源清理或大规模重排。

正常编辑即可，`tools/verify.py` 会自动检查 EOL/Encoding；只有 verify 报告 EOL 失败时才人工调查。

## Verify

1. 针对性单元测试；
2. `python tools/verify.py fast --changed`（内置 EOL/Encoding 检查）；
3. 修复；
4. `python tools/verify.py full`（内置 EOL/Encoding 检查）；
5. 工具改动时运行 Python 测试；
6. 任务要求时构建 APK。

正常任务无需单独运行 `git ls-files --eol`。

## Final Review

ChatGPT/人工只审最终 diff，重点检查：

- Hook 时序和 proceed 次数；
- API 101/102 隔离；
- 生命周期所有权；
- 热路径分配和阻塞；
- ClassLoader/反射/R8；
- 行为回归；
- 测试是否真实。

所有反馈回到原任务修复。

## Blocked

只允许真实外部依赖。编译失败、lint 失败、测试失败和代码理解困难不是阻塞理由。

## Done

任务完成后移动到 completed，压缩为结果、验证、commit 和限制；ROADMAP 只更新状态。
