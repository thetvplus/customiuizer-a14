# CustoMIUIzer A14 项目章程

## 目标

维护 HyperOS 1 / Android 14 专用 CustoMIUIzer 分支，优先保证系统进程稳定、
功能关闭低成本、Hook 热路径可控，并恢复用户可见功能和优化的持续推进。

## 固定边界

- HyperOS 1 / Android 14 / SDK 34；
- applicationId：`tv.withaibuild.customiuizer.r14`；
- minSdk / targetSdk：34 / 34；
- 独立版本、签名、发布和兼容策略；
- API 101 为最低 libxposed 运行基线；
- API 102 能力隔离；
- 不支持 A13、A15、A16。

## 优先级

```text
SystemUI/Launcher/system_server 不崩溃
> 现有用户行为不回归
> 用户明确缺陷
> 长期搁置功能
> HyperOS 1 兼容
> 性能、耗电和内存
> 结构与 Kotlin 现代化
```

## 开发模型

- 用户确定目标；
- ChatGPT 检查代码、制定任务合同、审查最终 diff；
- Devin 实现、验证和构建；
- A14 静态规则、测试、Gradle 和 lint 是客观门禁；
- 当前 Git 分支是唯一真实状态。

## 非目标

- 继续无限 Runtime Hardening 阶段；
- 只做审计而长期不交付功能；
- 为 A13/A14 逐行 parity 修改生产代码；
- 追求 Kotlin 数量而破坏 JVM/框架边界；
- 把 APK 构建成功等同于目标 ROM 实机验证。
