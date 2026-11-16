---
name: a14-safe-implementation
description: Change A14 host Hooks, ROM capability probes, or shared installation and ownership boundaries.
---

# 安全实施

1. 从实际 Feature 与 Installer 找到宿主入口、加载器和状态所有者。ROM 声明或调用时序有疑点时，用 [DexSQL](../../../docs/rom-intelligence/DEXSQL.md#离线-rom-知识库) 查对应宿主或插件的定义及 caller。
2. 沿[运行时合同](../../../ARCHITECTURE.md#运行时不变量)核对接管前提、原调用、异常、部分安装与释放。验证旧 owner、loader 替换和失败后的原生路径，以实际受影响的边界为准。
3. 用 [TESTING](../../../docs/TESTING.md#执行分级) 选择验证。设备重放与 Hook 效果证据见[自主验证](../../../docs/AGENTIC_VERIFICATION.md#可复用场景与断言边界)。
