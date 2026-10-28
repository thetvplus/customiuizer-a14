# 功能与几何入口

用于选对 owner 与现行合同，不维护第二份数值或功能清单。若功能文档与此索引描述变化，以当前功能合同和实现为准。

| 界面 | 权威合同 | 定位入口 |
| --- | --- | --- |
| 灵动岛 | [形态、输入、窗口与生命周期](../../../../docs/features/island/README.md)、[音乐手势](../../../../docs/features/island/MUSIC_GESTURES.md) | [IslandActivityController](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/island/IslandActivityController.kt)、[IslandActivityView](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/island/IslandActivityView.kt)、[IslandTouchWindow](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/island/IslandTouchWindow.kt)、[IslandMotion](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/island/IslandMotion.kt) |
| Dock | [Dock 与模糊](../../../../docs/features/dock/README.md) | [LauncherDockBackground](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherDockBackground.kt)、[DockCornerGeometry](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/DockCornerGeometry.kt)、[DockGlassBackdrop](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/DockGlassBackdrop.kt) |
| 设置 | [设置与持久化](../../../../ARCHITECTURE.md#设置生成与持久化)；需要导航才读 [功能地图](../../../../docs/FEATURE_MAP_SIMPLE.md) | [PreferenceFragmentBase](../../../../app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt)、`app/src/main/res/xml/prefs_*.xml`；具体页面按当前请求定位 |
| 状态栏 | [状态归属与几何](../../../../ARCHITECTURE.md#功能所有权与源码位置) | [StatusBarSafeGeometry](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarSafeGeometry.kt)、[StatusBarContentGeometry](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarContentGeometry.kt)、[SystemStatusBarInsetsHooks](../../../../app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt) |

## 要问的问题

**灵动岛：** 这次变化属于来源内容、形态、运动还是输入？沿现有单窗口与共享有限时钟处理。屏幕看见了什么、真实热区是什么、何时占用状态栏，三者是否一致？旧退场、通知抢占、隐私、控制中心、旋转和 detach 能否释放当前租约且不伤及新会话？不要为更像某个平台增加独立大卡片、第二窗口或封面截图层。

**Dock：** 装饰是否仍跟随 HotSeats 的实际变换，图标与点击范围是否保持？当前外轮廓是原生圆角矩形，动态岛的 G4 胶囊不能搬来替换。玻璃 / 磨砂 / 回退是否共用同一实际边界和单一材质所有权？全宽底栏是否仍无交互、不松开祖先裁切？采样和原生动画跟随的限制只查 Dock 合同，不在此另设频率。

**设置：** 层级和分组是否帮助找到功能，名称、说明、当前值是否各尽其责？绑定和回收时能否清掉旧值？新增样式不能改变稳定偏好键、搜索目标、原生依赖或配置含义；仅改文案 / 布局时不重构整个导航与生成链。

**状态栏：** 明确测量值来自哪个 Window / Display / Configuration，区分状态栏安全区、挖孔、物理圆角和实际内容边界。布局本地、窗口和屏幕坐标转换考虑父变换、Insets 与旋转；绘制边界、避让区域、输入区域可不同但必须由同一呈现解释。原生几何短暂缺失不随意用魔数覆盖，未知布局不退回整条隐藏。只恢复当前 binding 仍拥有的原生属性，不 Hook 全局 View setter 或创建全局窗口缓存。

跨表面通用检查为 [动效与输入](motion-input.md)、[绘制与生命周期](rendering-lifecycle.md)；无需为查一个 Dock 半径加载全部岛来源和媒体文档。
