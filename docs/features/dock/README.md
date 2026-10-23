# Dock 与模糊

[功能目录](../README.md) · [精修与验收记录](../../maintenance/A14_REFINEMENT_ROADMAP.md)

| 机制 | 开关与失效范围 |
| --- | --- |
| Android BlurController | 模块 `system_disable_window_blurs` 与系统 `disable_window_blurs` 取 OR；不覆盖用户开关 |
| Launcher BackdropBlurRelativeLayout | 私有材质可能选择 Google 跨窗口或 MIUI 后端；支持能力不等于当时有模糊 |
| DockGlassBackdrop / Optics | 仅采样壁纸合成层并以 Shader 绘制，不是全局窗口模糊，不采集应用窗口 |
| 状态栏 RenderEffect | 模块自己的短时覆盖画面；模块禁用模糊时使用 alpha |

桌面 Dock 可选全宽底栏、原生材质和壁纸采样玻璃，图标位置与点击范围保持。采样每个装饰最多一张受尺寸限制的纹理、一个在途请求；动态壁纸刷新最多 30 Hz，隐藏停止。采样 / Shader 失败一次即退役该采样器，使用原生材质与着色回退；替换、隐藏、配置失效及 detach 释放 Bitmap / Shader / 回调。不为 Dock 重新开启全局模糊，也不因静态检查宣称更流畅。

背景加入 `HotSeats.getUserPresentAnimationChildList()` 返回的原生参与者列表，由 Launcher 自己准备、驱动与重置解锁动画。全宽背景保留根容器布局，通过一个随 HotSeats 释放的无交互容器继承位移、缩放及透明度；原生解锁属性和父级变换分别持有。编辑动画只读取原生已经求得的 Transformation，不再次推进动画；矩阵恢复不会覆盖原生属性。壁纸采样使用包含该容器动画矩阵的实际视觉坐标。此修复有 ROM 声明 / Smali 与主机逐进度回归，真实锁屏、远程过渡、帧时间和材质效果仍待实机。


主要实现为 `LauncherDockBackground`、`DockDecorationPolicy`、`DockGlassBackdrop` 与 `DockOptics`，位于 [`mods`](../../../app/src/main/java/tv/withaibuild/customiuizer/mods/)；布局、材质、采样及 Launcher Hook 的职责分别保留。公共模糊开关与其他窗口共享，不能作为 Dock 私有状态搬入功能包。功能行为、样本推断与实机测量分别记录。
