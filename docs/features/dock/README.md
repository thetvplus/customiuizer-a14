# Dock 与模糊

## SE3 原生参考

iPhone SE 3 / iPhone14,6 / iOS18.6.2 / 22G100 的独立原件已核对，未用 16 Pro 的布局替代。设备/显示上下文经过 48 项 screenType 枚举、尺寸/scale/artwork 信息匹配，再由 provider 写 visualConfiguration；原始名称表、尺寸池和跳转表均已保留。N61/type1 为 375×667 点、scale2，N41/type0 为 320×568 点、scale2；两类配置进入高度 96 点、背景圆角 0、background/list insets=(4,0,0,0) 的非浮岛分支。`isDockInset` 判断背景圆角是否大于 0，内容、背景与 overhang 由各自 metrics 消费。

这是全宽底栏的静态几何参照。实时显示上下文由系统决定，96 点不直接替换为 96dp；A14 继续消费 Launcher 的 HotSeats、根窗口与导航区尺寸，背景和图标输入分属原所有者。材质构造、alpha 与 layout 链已保留，最终合成和既有偶发闪现仍需对应设备证据。五项范围见[核心接入参考](../apple-ui-core-reference.md)。

## A14 行为

[页面功能地图](../../FEATURE_MAP.md) · [待验收与证据](../../maintenance/A14_REFINEMENT_ROADMAP.md)

入口为「桌面 → Dock」。背景默认关闭，修改后重启桌面。仅保留纯色面板（0）与磨砂玻璃（2），旧轻量玻璃（3）及其它无效取值在设置与运行时归一为面板。复用 Launcher 已有 Backdrop 材质和混色，失败时显示简单半透明面板；不维护模块壁纸、折射 Shader、渐变描边或采样任务。需要磨砂且不透明度非零时，在挂载期间注册系统模糊与省电事件，detach 解除；纯色与全透明模式不注册这组监听。

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

全宽装饰 detach 后复用一次性 attach 入口，在同一 HotSeats 原生重挂载时恢复，不依赖再次 updateContent。首次创建、attach 和窗口恢复时读取原生 LauncherState，避免旧场景陈旧或 Overview 内新装饰默认显示；不增加持续轮询。

A14 安装点为 HotSeats.updateContent() 完成后挂载，原方法先加入原生内容并写入 mContent；measure、内容 layout、拖动结束、Overview 状态、编辑与解锁回调继续驱动既有装饰。2026-11-08 对 duchamp CN、fuxi CN / TW 与 munch ID 的原始 Launcher APK 重新运行 DexSQL，未使用旧索引；上述声明、原生材质构造与成员合同均存在。当前功能路由只启用 MIUI Launcher，POCO 原件的接口存在不代表 Dock 已接入；不扩大整个 Launcher catalog。具体评估见[静态复核证据](../../maintenance/A14_REFINEMENT_ROADMAP.md#验证与证据)。静态与 Host 证据不等同设备帧时间、GPU、功耗或实际观感验收。

主要实现为 [`LauncherDockBackground`、`DockBackgroundGeometry` 与 `DockCornerGeometry`](../../../app/src/main/java/tv/withaibuild/customiuizer/mods/)。不恢复旧轻量玻璃的持续采样调度。
