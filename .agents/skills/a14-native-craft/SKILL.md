---
name: a14-native-craft
description: Design, implement, or review A14 native Android View/Canvas UI craft, including Dynamic Island, Dock, settings, status bar, motion, gestures and overlay geometry. Use for A14 native interface quality work; not general backend, build, release or web UI tasks.
---

# A14 原生界面工艺

让界面清楚、克制、响应可靠；将 Apple、Emil 和 Telegram 的适用原则转译到现有 Android View / Canvas 实现。先确定用户操作和预期反馈，再决定视觉与动效，不为风格扩张功能或依赖。

## 约束与入口

以当前用户任务和 [AGENTS](../../../AGENTS.md)、[运行时合同](../../../ARCHITECTURE.md#运行时不变量)、涉及功能的现行合同为准；已在上下文且未变化的内容不重复加载。本 Skill 补充界面判断，不另建架构、参数或验证规则。

- 保留 A14 的 View / Canvas、平台与宿主边界；不据外部 Skill 迁移 SwiftUI、React、Compose 或升级 SDK。
- 使用当前功能已有的状态、动效、几何与材质 owner。Dock 的原生圆角矩形与岛的 G4 胶囊各按自身合同，不能统一替换。
- 时长与曲线先查现有实现；不把 Web 的 300 ms、CSS 合成规则或 iOS 点数当作 Android 门禁。
- 审查请求保持只读；实施与验证沿用 [安全实施](../a14-safe-implementation/SKILL.md) 和 [测试分级](../../../docs/TESTING.md#执行分级)，不因加载本 Skill 启动构建、MCP 服务或发布。

## 按问题加载

只读相关项；一个局部问题无需加载全部参考。

| 质量方面 | 需要判断时加载 |
| --- | --- |
| 视觉 | [视觉与无障碍](references/visual-accessibility.md)：层级、排版、材质、状态 |
| 动效 | [动效与输入](references/motion-input.md)：目的、当前呈现值、重定向与终态 |
| 手势 | [动效与输入](references/motion-input.md)：方向、取消、多指与系统手势边界 |
| 渲染性能 | [绘制与生命周期](references/rendering-lifecycle.md)：热路径、失效、测量 |
| 生命周期 | [绘制与生命周期](references/rendering-lifecycle.md)：异步所有权、释放、迟到结果 |
| 窗口几何 | [功能与几何入口](references/surfaces.md)：灵动岛、Dock、设置、状态栏 |
| 无障碍 | [视觉与无障碍](references/visual-accessibility.md)：语义、字号、对比、减弱动态 |

涉及上述四类界面时，再查功能入口中对应的一行；核验出处或许可证时才读 [来源与转译边界](references/sources.md)。这些参考自足，不要求其他全局美学 Skill 或 MCP 可用。

## 工作与交付

围绕这次具体操作追踪 owner、状态、绘制 / 输入几何和取消路径。视觉判断优先利用已有截图或授权设备证据；只有源码时，明确哪些观感尚未看到，不推测当前截图效果。

先复用现有字体、间距、颜色与状态表达，再处理有证据的局部差异。实施前能说明“触发 → 反馈 → 中断 / 失效 → 终态”；若动画没有帮助理解或操作，保留直接状态变化。

交付区分：可确认的正确性 / 可用性缺陷、可选审美建议、待测的视觉 / 设备性能。给出相关路径 / 符号、影响和实际验证；不以动画复杂度、最少代码行数或未测的帧率作为质量结论。审查 diff 时，缺陷严重性与报告方式复用 [独立审查](../a14-independent-review/SKILL.md)，不把偏好写成回归。
