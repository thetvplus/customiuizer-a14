# A14 Roadmap

## Now

- 完成文档架构 v2 迁移；
- 删除旧 Runtime Hardening/Review/Implement 控制文档；
- 将仍有价值的未完成功能和缺陷重新写成简洁 backlog；
- 恢复用户可见功能、缺陷和优化的连续交付；
- 继续保护 HyperOS 1 / Android 14 主基线；
- 确保 API 101/102 边界不回归。

## Next

- 按用户影响处理 SystemUI、Launcher、`system_server`；
- 收口已知小窗、通知栏、控制中心和桌面交互问题；
- 仅对有收益证据的热路径进行优化；
- 补齐所有者生命周期、缓存和异常边界测试；
- 固化 Debug APK 构建与校验。

## Later

- 根据目标 ROM 实机证据调整兼容范围；
- 小批量继续 Java→Kotlin；
- 改进正式发布自动化；
- 将稳定不变量固化为代码门禁，而不是增加过程文档。

## 永久废弃

- 长期分支写死；
- 固定 HEAD 任务；
- Review/Implement 双轨；
- 无限阶段审计；
- A13/A14 逐行 parity；
- 只分析不实现；
- 以文档规模代替功能进度。
