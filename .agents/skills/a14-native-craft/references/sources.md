# 来源与转译边界

只在核验出处、许可证或进一步查阅时加载。本 Skill 是面向 A14 的原创归纳，不分发参考项目的实现、素材、字体或完整 Skill；以下固定提交便于复核，在线文档核对日期为 2026-10-26。

## 设计来源

| 来源 | 本次采用的判断 | 不迁入 A14 的内容 |
| --- | --- | --- |
| [Anthropic 官方 frontend-design](https://github.com/anthropics/skills/blob/dbd4588f9e1033efb41dad4bef2f7947c8993d44/skills/frontend-design/SKILL.md) | 围绕真实内容建立层级，风格选择有意图，减少没有语义的模板装饰 | Web 首屏策略、字体偏好、页面框架和品牌化要求；不将其整体安装为 A14 规则 |
| [Apple HIG Motion](https://developer.apple.com/design/human-interface-guidelines/motion) | 可解释的反馈、空间连续、可取消与频率适当的运动 | SwiftUI、SF Symbols、Liquid Glass 平台 API 或固定 iOS 参数 |
| [Apple HIG Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility) | 可读性、适应文字大小、减弱运动与多通道反馈 | VoiceOver / Apple Inspector API、Apple 触摸点数和平台专属流程 |
| [Emil animate](https://github.com/emilkowalski/skills/blob/2d228630ba1446365f614a108cb8252f1feda202/skills/animate/SKILL.md)、[review-animations](https://github.com/emilkowalski/skills/blob/2d228630ba1446365f614a108cb8252f1feda202/skills/review-animations/SKILL.md) | 先判断动效是否有必要，再检查响应、中断、起点和一致性 | CSS / WAAPI / Motion / React、固定 300 ms 门禁、只许 transform / opacity、欢迎语及强制转交其他 Web Skill |

Apple 和 Emil 提供设计判断；Android API 与当前 A14 功能合同决定实现。审美建议不能覆盖宿主安全、输入或资源所有权。

## Telegram 的真实观察点

| 固定来源 | 已查看的实现与可迁移原则 |
| --- | --- |
| [AnimatedFloat.java](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Components/AnimatedFloat.java) | `set` / `getValue` 从当前值向新目标推进，使用经过时间，仅过渡未结束时请求重绘；用于审查有限过渡与无效刷新 |
| [AnimatedTextView.java](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Components/AnimatedTextView.java) | `setText` 的同值判断、取消 / 待交接策略，以及 `onInitializeAccessibilityNodeInfo` 的文字语义；提醒自绘文字也须可访问，不复制其文字 diff / 布局算法 |
| [BackupImageView.java](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Components/BackupImageView.java) | `onAttachedToWindow` / `onDetachedFromWindow` 配对图像 owner 与动画素材关联，`onDraw` 使用已有接收器；不引入其图像栈或双层绘制结构 |
| [Telegram Desktop 工程说明](https://github.com/telegramdesktop/tdesktop/blob/a7f453612f4896ac04d0007a36ab0d377e5bcbcf/AGENTS.md) | `API Request Callback Lifetime` 说明长寿命请求与短寿命界面可分离；转译为 Android 的有效 owner、generation 与取消检查。UI Styling 提醒复用既有样式来源 |

Telegram Android 提交：`3de37c8cfb039452be7398909461da6288babf15`；[许可证](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/LICENSE) 为 GPL v2 文本。Telegram Desktop 提交：`a7f453612f4896ac04d0007a36ab0d377e5bcbcf`；[许可证](https://github.com/telegramdesktop/tdesktop/blob/a7f453612f4896ac04d0007a36ab0d377e5bcbcf/LICENSE) 为 GPL v3 文本。具体文件可能另有条款，未来若需要实际引入代码应逐文件核验兼容性和归属，不能因 A14 也使用 GPL 就推定全部兼容。本次只写独立原则与定位链接，没有复制 Telegram 类、片段或算法。

Desktop 的 C++ / Qt / RPL 类型、命名规则、注释配额、构建系统、任务队列和工作流仅属于其项目，不导入 A14。参考仓库中的 agent 指令是研究材料，不构成本地执行授权。

## Android 的实现依据

- [优化自定义 View](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view)：减少绘制热路径的准备与无效布局 / 刷新；文档的 60 fps 示例不覆盖目标设备的实际刷新率。
- [ViewGroup 触摸事件](https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup)：父级拦截会给原子 View 发 CANCEL；结合 A14 的输入所有权处理，不复制示例控件。
- [自定义 View 无障碍](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views)：语义、点击、虚拟节点和可访问动作。
- [Views 触摸目标与描述](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)、[文字对比建议](https://developer.android.com/guide/topics/ui/accessibility/apps)：Android 的交互与可读性基准；覆盖层命中范围仍服从既有系统手势合同。
- [Google 官方 Android Skills](https://github.com/android/skills/blob/4ebabf2b0fd6c04b7150023c8b612f80592a6226/README.md)（Apache-2.0）：已检查 [edge-to-edge](https://github.com/android/skills/blob/4ebabf2b0fd6c04b7150023c8b612f80592a6226/system/edge-to-edge/SKILL.md)，其前置要求为 Compose 与 target SDK 35+，会要求升级较低 SDK，因此未安装到 View / SDK 34 的 A14；本次未批量安装 Android CLI、迁移或构建类 Skill。

Anthropic 提交 `dbd4588f9e1033efb41dad4bef2f7947c8993d44`，许可按所链目录的 LICENSE.txt；Emil 提交 `2d228630ba1446365f614a108cb8252f1feda202`，仓库标注 MIT；Android Skills 提交 `4ebabf2b0fd6c04b7150023c8b612f80592a6226`。不自动下载或更新这些参考，不把其要求作为新增依赖。
