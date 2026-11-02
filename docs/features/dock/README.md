# Dock 与模糊

[页面功能地图](../../FEATURE_MAP.md) · [待验收与证据](../../maintenance/A14_REFINEMENT_ROADMAP.md)

入口为「桌面 → Dock」。背景默认关闭，修改后重启桌面。仅保留纯色面板（0）与磨砂玻璃（2），旧轻量玻璃（3）及其它无效取值在设置与运行时归一为面板。复用 Launcher 已有 Backdrop 材质和混色，失败时显示简单半透明面板；不维护模块壁纸、折射 Shader、渐变描边、模糊 / 省电监听或采样任务。

「背景尺寸微调」同时调整上下和左右：正值四边扩展、负值四边收缩，默认 0 dp（存储值 17，减去偏移 17）；按可用空间和导航安全区对称限幅，图标仍使用原生位置与大小。全宽底栏隐藏圆角、水平间距和尺寸微调参数；有效旧高度与上下边距键保持兼容。

自动圆角沿用原生面板比例，屏幕底部物理圆角仅作上限，浅面板最多取半高的 64%。DockCornerGeometry 统一提供原生圆角矩形外轮廓；所有材质和回退共用同一布局与半径，不把动态岛 G4 胶囊搬到 Dock。半透明填充的原生圆角路径仅在尺寸 / 圆角变化时重建。有效设备圆角缓存至配置 / Insets 变化，临时缺失不重置。

模块创建的原生材质显式关闭该 View 的厂商 SmoothCorner 附加绘制，避免最近任务的离屏合成进入空 SkSurface 快照路径；原生材质圆角与模块轮廓继续生效。不修改宿主其他 View、Drawable 或全局系统属性。

| 机制 | 所有权与回退 |
| --- | --- |
| Android BlurController | 模块 system_disable_window_blurs 与系统 disable_window_blurs 取 OR，不重新开启全局模糊 |
| Launcher BackdropBlurRelativeLayout | 复用已有 Google / MIUI 后端；支持能力不等于当前有模糊，失效时撤销原生 owner 并退回简单面板 |
| 状态栏 RenderEffect | 属于动态岛短时覆盖，不与 Dock 共用图像状态；模块禁用模糊时使用 alpha |

背景加入 HotSeats.getUserPresentAnimationChildList()，由 Launcher 准备、驱动和重置解锁动画。全宽装饰通过无交互容器继承 HotSeats 的位移、缩放与透明度，不移动图标、不改点击范围、不松开祖先裁切。编辑 Hook 先完成原调用，再核验弱 owner；只读取原生已计算的 Transformation，不再次推进动画。原生终帧仍保留矩阵 / alpha，等宿主清除或替换动画后退休，避免松手弹回。普通嵌入背景由父节点原生变换带动；Google blur 与全宽装饰保留必要的透明度跟随。

几何更新来自原生 measure / layout / 拖动完成等事件；稳定的普通 / 透明背景解除 PreDraw，需要独立合成透明度的原生材质只观察宿主已有帧，不自行请求下一帧。隐藏暂停几何工作，detach 取消宽度动画并释放布局与编辑监听。没有纹理 generation、异步捕获结果或旧 Bitmap 需要移交，整套自定义图像所有权已删除。

A14 已有安装点仍为 HotSeats.updateContent()、onWallpaperColorChanged() 及原生编辑 / 解锁参与者；本轮不增加 ROM API 或扩大 Hook 范围。2026-10-18 的 fuxi CN / TW MiuiHome 静态声明证据继续适用于这些未变化入口。旧采样与 Shader 的数值预算已退役；主机几何 / 生命周期 / 材质回退测试不等同设备帧时间、GPU、功耗或实际观感验收。

主要实现为 [`LauncherDockBackground`、`DockBackgroundGeometry` 与 `DockCornerGeometry`](../../../app/src/main/java/tv/withaibuild/customiuizer/mods/)。回退单位是本轮轻量玻璃移除与原生材质 / 简单面板取舍，不单独恢复旧的持续采样调度。
