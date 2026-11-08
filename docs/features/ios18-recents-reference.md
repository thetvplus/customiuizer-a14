# iOS 18 任务堆叠参考与 A14 接入边界

固定研究样本为 iPhone 16 Pro / iOS 18.6.2 / 22G100，与既有岛体研究同构建；研究对象是最近任务的 Deck 卡片堆叠。来源为六文件核心包的汇编、索引和仅作辅助的 C 重建；原件私有留存，不进入源码或 APK。

## 当前判断

实现为米客的 **Launcher 进程 Hook**，复用原生任务卡片、快照、回收与窗口控制，不修改或重签桌面 APK。

已实现默认关闭的 `system_recents_ios18_deck`，支持完整原生合同下的横向最近任务与横竖屏几何；按 Legacy / WindowElement 能力选择窗口路径，不再限定桌面版本号。分屏、组合任务、辅助触摸、减弱动态及未满足运行合同的场景保留原生。原 `system_recents_card_style` 只用于隐藏应用名，不能借用为堆叠模式。

沿手势指示条左右快速切换应用由原生 app-to-app 链路控制，不属于 DECK 转场。原生 DOWN 分类及全屏 / 半屏快速切换共用一个接管边界，重建任务栈仍使用同一边界；接入快速切换时归还旧矩阵、窗口票据和几何端点，明确进入最近任务后再恢复 DECK。共享的窗口起点查询不创建用户意图，已有点击转场继续保留原生坐标换算。手势指示条本身只改变显示，不接管任务缩放或输入。

## 已恢复的原生结构

| 责任 | 原生实现 |
| --- | --- |
| 稳态布局、深度、缩放、吸附与可见性 | `SBDeckSwitcherModifier` |
| 滚动、容器、内容及 modifier 协调 | `SBFluidSwitcherViewController` / `SBDeckSwitcherViewController` |
| 连续 Home 手势及 stackedProgress | `SBHomeGestureSwitcherModifier` |
| 松手进入 Deck、Deck 进入应用 | `SBHomeGestureToSwitcherSwitcherModifier` / `SBDeckToFullScreenSwitcherModifier` |
| 上滑关闭、删除后模拟及相邻项插值 | `SBFluidSwitcherItemContainer` / `SBSwipeToKillSwitcherModifier` |
| 减弱动态的独立分页路径 | `SBReduceMotionDeckSwitcherModifier` |

选定主链的函数汇编与关键伪代码已保存。布局使用指数深度，而非固定间距：既有映射采用 pageScale=.75，四项已核验常量包括 depthPadding=.13、指数系数 9、平移常量 −18 和深度缩放系数 .12；每卡深度由任务索引、数量与滚动进度共同计算，变换 scale 为 `1+.12×depth`。渲染 scale 乘 pageScale，而另一容器几何消费者使用 `pageScale+.12×depth`，两者不能混用。

当前进度/内容偏移、RTL、首卡露出边距、居中补偿及可见范围均有独立计算。松手落点按候选目标距离和方向筛选，不能直接替换成固定 gap 的四舍五入。原生减速率 .993 不能直接当 Android friction。

`SBFFluidBehaviorSettings` 基础 response=.531 / damping=.845，criticallyDamped 为 .336 / 1；稳态 layout、进入应用、手势跟踪和关闭透明度使用不同配置。gestureInitiatedAppToSwitcher 继承 .531 并覆盖 damping=.94；stackedTracking 使用独立 trackingResponse=.14 / trackingDamping=1.2。Home 手势设备还有 layout tracking 覆盖。response 不是动画总时长，Android 求解器换算仍是工程映射。

关闭手势同时检查当前轴向偏移与预测终点；其中 62 点是当前偏移门槛，不是速度阈值或完整关闭距离。相邻项通过临时删除后的布局状态参与回填。Ghidra 对 CGPoint/CGRect 返回寄存器及认证跳转有失真，关键结论以 ARM64 和实际常量交叉复核。

## A14 已核对的入口与限制

MiuiHome 样本版本为 `RELEASE-4.39.30.8604-08262248`。现有 Catalog 中 fuxi CN、fuxi TW、duchamp CN 为同内容 APK，可复用静态研究；另已核对 POCO 的 `RELEASE-4.39.14.8255-09101541` APK，具有不同的点击 / 窗口分支，不能套用同名入口。这不代表任何新效果已通过实机。

| 入口 | 必须处理的实际合同 |
| --- | --- |
| `TaskStackViewLayoutStyleHorizontal` | 同时创建 current / stable 两份布局算法；不得只改一份 |
| `TaskStackViewsAlgorithmHorizontal.getTaskViewTransform` | 原固定间距与半屏预载需要换成一致的 Deck 几何和可见性 |
| `TaskStackView.computeVisibleTaskTransforms` / `bindVisibleTaskViews` | 可见标记决定还池，保留当前/目标双位置及删除邻卡 |
| `TaskViewTransform.applyToTaskView` | 当前更新链主要应用 LTRB，未直接消费 scale/alpha，且调用方关闭 z 更新 |
| `TaskStackView.isTouchPointInView` | 原命中只看 LTRB+translation，没有 scale/pivot；必须与最终可见区域一致 |
| `TaskStackViewTouchHandler.findViewAtPoint` | 任务遍历次序需与视觉遮挡一致，防止点中后方卡片 |
| `TaskStackViewHorizontalScroller` | 原固定 gap 吸附需要重新映射到 Deck 候选落点 |
| `VerticalSwipe` / 删除插值 | 保留锁定、任务身份和业务删除；只调整视觉与回填几何 |
| 原生窗口衔接 | 新版含 `TaskViewsElement`；上述 POCO 样本不含此类，使用 `getRecentsWindowAnimatorNew` → `getTaskViewStartRectF` → `RectFSpringAnim` → `ClipAnimationHelper`，必须同步原生 Surface 起止矩形 |
| `TaskView.resetViewProperties` / detach | 恢复自持属性和命中状态，避免还池后污染 |

不能只在 TaskView 上后置缩放；这样会漏卡、点错对象或与真实应用 Surface 脱节。实现使用独立开关、默认关闭，支持普通手机横向最近任务的横竖屏；分屏、组合任务、小窗及接口不兼容的场景保留原生回退。继续沿用米客的 Feature 安装、API 102、ClassLoader 与生命周期合同，不每帧读取偏好、查类或重建任务表。

## 卡片变换与所有权

独立 animationMatrix 持有 Deck 展示变换，不改用于原生删除资格的根 scale / .8 门槛。绘制、逆变换命中、可见池、遮挡顺序、邻卡回填和 Surface 端点必须共用同一几何；Android 普通命中与 getMatrix 不会自动包含该矩阵。滚动及吸附仍由原生物理驱动，不把 Apple .993 直接当 Android friction。

卡片层级以真实任务列表为准，包含尚未提交删除的目标，避免临时 ignore 后与邻卡重复排名；stable / 旧插值帧不重新决定前后顺序。接管布局不再创建原生 Z 动画；活跃堆叠在现有 pre-draw 内校正导航抬高、延迟清零、每次拖动的 +10 与关闭动画的负 Z，复用有界卡片列表且不请求循环帧、不做逐帧反射。原生缩放继续用于删除判定；拖动展示由下文的独立矩阵补偿，删除 / 取消业务仍归宿主；还池、离开堆叠、旋转和 detach 释放观察器与自持属性，较新的外部写入保留。

## 卡片阴影与提交删除

22G100 的 `shadowOpacityForLayoutRole:atIndex:`（`0x1d8d0fa18`）恒返 1，`shadowOffsetForIndex:`（`0x1d8d0fa20`）恒返 0，`shadowStyleForLayoutRole:inAppLayout:`（`0x1d8d0fcb8`）恒返 1。这不代表阴影最终为纯黑不透明；具体滤镜仍未恢复，不凭层级主观添加逐卡透明度曲线。

A14 将排序放在共同 16 dp 投影平面上，再保留原有细小排名差；不再将实际 Z 压到不足一个物理像素。卡片自身的原生 Outline 只覆盖快照内容，沿用宿主圆角并响应其设置事件，保留标题及祖先裁切。颜色与投影高度是 Android 调整，不等同 Apple 材质。投影随同一 TaskView 的矩阵、原生内容 alpha、位移和回收更新，没有位图模糊或独立阴影时钟；释放按属性所有权恢复原值。原生窗口交接会让 thumbnail alpha=0 而根 alpha=1；只跟根透明度会在慢速 HOLD 留下大块孤立投影。当前绑定时读取 thumbnail alpha，后续消费原生 setChildrenViewAlpha 更新 Outline alpha，不遮盖壁纸、不改变模糊或应用 Surface。

22G100 的 `scaleForIndex:`（`0x1d8b8ee98`）不为被拖卡附加缩小，`opacityForLayoutRole:inAppLayout:atIndex:`（`0x1d8b8f4e8`）仅在配置允许且释放目标指向删除后清零，不支持 MOVE 全程淡出。`handleSwipeToKillEvent:` 区分 translation、progress、isDragging 和 decelerationTargetProgress；越过位移门槛不能替代释放后的业务提交。邻卡 frame / scale / shadow 由临时删除状态与 progress 插值，不能从一个常量恢复全部链路。

A14 在 `VerticalSwipe.calculate` 消费本次原生原始位移，在 `TaskStackViewTouchHandler$1.onMoveUpdate` 消费方向判定后的位移；这两个已核对版本的覆写不调用基类。独立展示矩阵抵消原生根缩放及其对位移的倍率，按实际手指位移呈现，拖动卡保持不透明；不改根 scale 或 .8 删除门槛。HOLD 不推进第二个时钟，反向沿当前输入接续；UP/CANCEL 沿原生 snap 的位置比例归还矩阵补偿。提交前替换原生视觉 animator，沿用其时长、插值器、进度 / 完成 listener；移出距离按包含展示矩阵的可见底缘计算。Z 与阴影继续同一 RenderNode，回收或其他 owner 接管 Matrix 时释放自持属性。

原生向上分支会传入负零目标，必须用 Float 数值比较准入。锁定、删除判定、方向仲裁、相邻回填、取消 spring、Surface 和 finish 仍归 HyperOS；邻卡在未松手阶段的完整 iOS 模拟删除布局、标题跟踪和释放速度到 UIKit 求解器的映射仍未迁移，不能称完整 SwipeToKill 等价。整批清理、接口不兼容的实现和不支持场景保留原生。

Thumbnail 通过冷解析的 mCornerRadius:Int 和原生 setter 同步 Outline。首次绑定 / Outline / PreDraw 读取实际内容 alpha，Header 深度透明度与根 alpha 分别交接；迟到的原生写入继续由既有 PreDraw 校正。snapChild 在原生 spring 构造前归还自持 alpha，较新的宿主值和外来 Matrix 优先。不能把开关开启后回退的原生界面当作 Deck 通过。

`VerticalSwipe.calculate(F)V` 与 `$1.onMoveUpdate(View, float, float): void` 在同一次原生 `SwipeHelperForRecents.onTouchEvent` 内同步调用，中间先写 alpha / scale 再写 translation。原始位移现仅在该 helper 的触摸范围内消费；重复 calculate 取当前值，异常、未消费、其他 helper 与重入均清除，不把旧值借给下一次 MOVE。PreDraw 仅更新唯一活动卡片；原生临时 Z 仍由既有无分配列表校正。

## Deck 多 ROM 静态合同（2026-11-05）

通过[专用查询](../../tools/dexsql/launcher_recents_deck_contract.sql)实际复核定义、继承、完整描述符、直接调用与字段读写，并 decode 触摸、匿名覆写、Transform、Thumbnail、spring 及 Surface 相关类。输入定义位于 Launcher APK 的 `classes2.dex`；这证明静态归属，运行时继续使用现有 PackageReady ClassLoader，不由 DEX 路径推断动态加载器。

| 样本 | APK SHA-256 | 决策 |
| --- | --- | --- |
| fuxi CN / fuxi TW / duchamp CN，4.39.30.8604 | `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b` | 同内容，使用 WINDOW_ELEMENT 完整 ABI；各 ROM 的实机范围分别待验 |
| 4.39.14.8255，既有 LEGACY 样本 | `3287c0f9b26a0faa75177949e85667b623e491329b35ea4e46cc3657ba8f54cf` | 使用 LEGACY 完整 ABI；没有 WindowElement，保留旧 Quickstep / Nav spring 交接 |
| munch ID 自带 POCO 4.39.14.7603 | `1b1eb43b4279ab6b6e7ec1712c74b7e7e45c5e00cee4df497aa6bf4b55ab81f6` | 补齐完整成员差异、触摸 / 卡池 / 窗口调用链核对与旧属性式弹簧适配；能力准入，实机矩阵待验 |
| dada HyperOS 3 | 未作为本轮 A14 输入 | REFERENCE_ONLY，不提供 A14 ABI 或资源合同 |

上述三个 A14 样本的 `mCornerRadius:I` / `setCornerRadius(I)V`、`setChildrenViewAlpha(F)V`、`calculate(F)V`、`onMoveUpdate(View;FF)V`、`prepareSnapBackAnimation(View;Object)V`、`snapChild(View;F)V` 和 `getViewTranslationAnimator(View;F)ObjectAnimator` 已核对。`$1` 直接继承 SwipeHelper，两个覆写均不调用 super；原生 snap 的完成 Runnable 再调用 `onChildSnappedBack(View;F)V`。冷安装精确校验本轮所用参数、返回值、继承与字段类型，完整表失败即保留原生，不添加模糊反射回退。原始来源、SQL 输出及 Smali 私有留存，不将原始样本或设备界面入库。

同构建 Deck 的 `topMostLayoutElements` 返回任务列表，`shouldBringCardToFrontDuringInsertionOrRemoval` 返回 false；SwipeToKill 未覆盖该排序，controller 按组应用顺序。因此不保留 A14 在拖动 / 删除间由正 Z 跨到负 Z 的层级切换。横向仅融合方向候选筛选：同一次原生 fling 内将手指速度转换为相反的 content-offset 方向，在原生 q 格点选同方向候选，仍由原生预测及 PhysicalScroller 驱动。普通低速吸附、边界与退化任务数保留原生；未把 .993 当 friction，也未移植完整 UIKit 跟手求解器或原生候选边距。

## 标题层次与窗口动效

使用条件：在已适配的 HyperOS 1 桌面中，将“设置 → 桌面 → 最近任务排列方式”设为“横向”，修改后重启桌面；这与桌面图标布局及系统导航方式分别独立。全面屏手势为建议条件，三键模式不据此直接禁用。纵向最近任务、不满足完整接口合同的桌面及未准入场景保持原生，不能把回退当作 Deck 成功。

回收以真实展示矩形相交和有界尾部判断，不用固定滚动截止提前回收仍可见的大卡片。

标准16 Pro 的 header 与卡片缩放分离：无Home键且参考高度小于926点的分支使用26点图标、Subhead、8点图标/快照与图标/文字间距，标题基线距快照16点。Subhead默认15点来自 [Apple Typography](https://developer.apple.com/design/human-interface-guidelines/typography.md)。Android按dp/sp映射，保留原生app图标、字体与fontScale；测实际drawable框，避免将原生padding算成图形大小。图标和文字分别提供默认自动及50%–125%原生尺寸选择。

原生每组顺序为header underlay、卡片内容、overlay，组之间再按前后序排列。整体header透明度H与title透明度T是独立的深度函数；邻卡可以只露图标而让文字先消失。Android保留逐卡原生层次与对象，完全淡出改INVISIBLE而非GONE，恢复时归还可见性和alpha，不给所有header加全局高Z。

窗口spring的冷初始化复用原生setAnimParam：打开应用和按钮进入为response .336 / damping 1，Nav手势松手进入为 .531 / .94；只改位置和尺寸四轴，原生转换系统时长并继续持有初速、Surface与finish。可单独关闭此项，圆角和alpha保持原生。关闭、取消、未开始和重复切换分别释放有限ticket及自有listener。水平滚动仍使用原生物理，完整iOS滚动求解器未迁移。

## 旧版弹簧与能力准入补齐（2026-11-06）

7603 的 `setAnimParam(FloatPropertyCompat;FF)V` 接受原生 `RECT_CENTERX / RECT_CENTERY / RECT_WIDTH / RECT_RATIO` 静态 final 属性；8255 使用 RectAnimType，8604 使用 SpringBundle。旧属性式与枚举式复用同一参数适配器，原生 setter 继续执行系统倍率和 stiffness 转换，四轴在 `initAllAnimations()` 消费，不接管初速度、半径、透明度或 Surface。

旧版没有 `getLastAminType()`，按钮进入改由真实 `setAnimParamByType(CLOSE_TO_RECENTS)` 在既有调用范围内确认 spring；已知 Nav 实例与打开返回值仍按原生对象归属标记。没有全局场景缓存或逐帧检测。恢复以阻尼 / 刚度对为单位，原生改动其中任一值后整对归还给原生；失败时逐项回滚。缺少可选动效 ABI 不阻断 Deck 本体。

7603 与 8255 的 MOVE、snap 准备、VerticalSwipe、Transform、卡池及 listener 主链逐段一致；窗口创建、reset、配置和原生 start / finish 差异另行核对。此证据支持同族能力适配，不代表未测版本已经通过设备行为或视觉验收。POCO 仅增加主进程的独立 Deck Feature，保留通用进程路由。

## 尚未达到的完整还原范围

静态主链资料与接入评估已经形成；同构建 iPhone 逐帧录制、UIKit 动画器和 UIScrollView 内部求解器、像素级材质、全部方向/辅助功能/场景分支，以及 Android 实现和帧时间仍待验。生成的公式样本不能称为原生运动采样，配置的高刷新请求也不能证明设备帧率。

后续设备验收分别核对慢拖、快甩、反向接续、取消、连续删除、标题层次和真实应用窗口转场；Host几何/参数回归不能代替这些运行结果。当前决定与未决项见[待实机验收](../maintenance/A14_REFINEMENT_ROADMAP.md#待实机验收)。

## 新版 HyperOS 1 桌面窗口接口

已准入 4.39.30 APK 的 `WindowElement` Smali：`mAnim` 唯一写入在构造方法，`animTo`、reset、运行中重定向及 start 均读取同一字段，因此当前前置 mark 不会绑定被原生替换的旧 Spring。窗口更新及 closing provider 改为完整参数和返回类型解析。此为 DEX_STATIC 身份与时序证据；对应版本真实播放器、duration ratio、速度继承及取消的运行时验收仍须分别记录，不用测试 Spring 代替。

4.39.30 的 `WindowElement.animTo(Object)V` 在 `animResetReady(RectFParams)V` 中先 reset，再调用 `RectFSpringAnim.setAnimParamByType(AnimType)V`，最后 `startInMainThread()`；运行中走 `runningAnimUpdate` / `updateAllAnimValues`。原生四轴是 `mCenterXBundle / mCenterYBundle / mWidthBundle / mRatioBundle`，由私有 `setAnimParam(String,FF)V` 消费 `centerX / centerY / Width / Ratio`，经原生 duration response 转换写入 SpringBundle。新 `DeckMotion / DeckModernSpringParameters` 只在准入的卡片 / ticket / 原生 AnimType 下使用这条消费者，普通打开 / 按钮进入映射 .336/1，`performAppToRecents(Z)V` 同步手势范围映射 .531/.94；不访问 Legacy 字段。原生初速、radius / alpha、Surface、目标框、取消和 finish 不变。

每次原生重新配置前归还自持参数，成功后再覆盖四轴；end / cancel / 超时 / 旋转释放时仅恢复仍等于本次写入的参数对，后续原生写入优先。失败逐轴回滚，可选动效冷解析失败仅保留原生时序；必需窗口合同失败则 Deck 整批不接管。该变化是 iOS 配置到 Android 原生求解器的映射，并非 UIKit 求解器或全链路等价。WindowElement 的当前显示旋转为 0、90、270° 时共用交接；不要求默认 homeRotation 元数据与其相等。180°、quick switch 或分屏仍保留原生运动，原生坐标转换与参数身份保留。

阴影继续使用同一 TaskView RenderNode，透明度为原生截图 alpha × (1−mFullscreenProgress)，截图不可见 / 未准备时为零，没有引入新滤镜或阴影时钟。16dp 投影平面仍为 Android 工程近似，环境 / 定向颜色保留 HyperOS 当前原值。卡片截图的圆角与裁切由原生 thumbnail 管理，内容投影同步原生半径，不增加另一套曲率。现有 iOS 静态证据只确认 opacity=1 / offset=0 / style=1，无法据此发明 CoreMaterial 模糊半径或逐卡透明曲线。入退场逐帧、真实首合成帧和全方向交接仍需设备与原机对照。

Legacy 与 WindowElement 使用两类窗口 profile；类存在只选择解析器，必需布局、触摸、卡池、窗口和生命周期成员仍在冷安装前完整解析。后者已移除旧 Quickstep closing 入口，改由 WindowElement 持有 RectFSpringAnim；不能只放开版本门禁或把旧字段当作参数别名。

新版从 WindowAnimParamsProvider 提前建立有限交接，复用当前几何提供起止矩形；WindowElement 接受原生参数副本时仅匹配 owned 卡片并调整对应端圆角，从实际 mAnim 链接原有 end/cancel。新 Nav 路线不再读取已不参与的旧 mAppToRecentsAnim2。Surface、参数、初速度和 finish 仍由原生窗口动画持有，参数由 DeckMotion 选择对应 profile 适配器。原生参数副本和窗口场景回归已通过；4.39.30 已定向验证后排打开、第二 / 第三卡删除与末端回填，未覆盖的手势与场景仍待验。

旧 Nav / Quickstep 收回时，原生 endRadius 已除以放大后的 endpoint 宽度，Surface 最终只显示未放大的本地半径；thumbnail 的原生圆角则随根 Deck A 放大。现将 frozen rect 与 A 一起保存，在同次原生 closing 的 reset 参数中补偿一次，与动效开关独立；限定 owner、ticket、目标 spring 和端点尺寸，零半径 / 其他流程保持。现代 WindowElement 使用屏幕半径，closing 取冻结 A 且最终 native scale=1，opening 取当前 A×P。继续使用原生 drawRoundRect、Surface 和中途 radius 更新，未新增自绘轮廓或逐帧半径覆盖。


## 目标端点、回填与打开遮挡

Nav 可在当前滚动位置尚未变化时，用目标 q 查询窗口矩形。预测位置、尺寸缩放与无标题内容框必须使用该同一 q；冻结结果也区分 q，不混用当前 scale。布局、手势端点与窗口查询的缩放统一到同一受限 presentationCursor，修正离屏来回拖动时位置已限位而比例仍读原始游标的差异。点击打开继续由 TaskViewUtils.getTaskViewStartRectF 消费该内容框并执行 last / current rotation 转换，模块不再在转换后用未旋转的屏幕矩形覆盖结果；8255 / 8604 两份原生消费者已核对。不可见区仅限制候选可见性与指数范围，不设置统一的远左占位坐标。旧端点保留真实入场侧，回填中新可见卡也沿原生进度插值位置与展示缩放；直接使用目标姿态会让左侧边界删除时的右邻卡跳入终点。取消沿同一进度反向接续。

新版普通打开的应用 leash 位于整个 Launcher 之上，TaskView 的 Z 不能让前卡遮住它。恢复原生窗口进度下的邻卡让位，从实际 Deck 起始间距连续展开到原生全屏槽位及间隔，使邻卡在窗口完成前离开应用范围；在 Surface 提交前计算同帧姿态。仅全屏零原点、正向轴对齐矩阵和原生内容框一致时，限制选中应用 Surface 的独占 windowCrop 到前卡左边界；完全遮挡复制原参数仅将 alpha 置零。圆角边界采用保守矩形近似，未恢复像素级原生遮挡。其他目标、坐标不符、旋转 / 分屏均保留原生输出，不修改 layer、leash、矩阵或 finish。交接、取消与回收按所有权释放邻卡变换，不增加预览窗口、快照或第二套动画时钟。

## 横屏与卡片大小（2026-11-06）

本地 22G100 的 `containerViewBounds`（0x1d85a82a0）直接读取当前 view.bounds；Deck 水平深度公式本身未按方向另换参数，不能用固定竖屏窗口替代当前容器。原生标题透明度默认分支的竖屏 C=.023 / D=40，横屏 C=.0074 / D=75，常量表与取值分支已核对。原生 resting margin 还包含图标度量及方向分支，尚未等价迁移，不把任意 dp 当完整原生边距。

A14 保留 DeviceConfig / RotationHelper 的原生方向策略，以视觉 viewport、mVisualRotationSystemInsets 和卡片宽高计算横屏安全区；手机可以在竖向 Window 内旋转任务容器。卡片几何、命中与窗口共用展示矩阵，原生 root scale 和删除资格不变。当前原件依据见[方向合同](#a14-横屏原件重新分析2026-11-08)，不要求 Display / Configuration 与视觉方向相等。180°、平板、RTL、分屏及不满足接口合同的路径保留既有回退。

视觉方向变化先撤销几何准入，清空旧端点、插值和窗口票据，归还卡片与两份原生滚动边界，再等待两份原生视觉 viewport 准备。Transform 的 `copyFrom` 不得把旧代际重新标成当前；旧矩形也必须整份拒绝，不能只丢掉 Deck 缩放后交回原生应用。无 owner 引用的失效标记沿缓存复制传播，新几何计算或原生 reset 才解除。WindowElement 的参数保留弱归属收据，每次邻卡 / Surface 修正必须匹配原 owner、票据及几何代际。原生 spring、Surface 与 finish 仍继续执行。连续方向交错、迟到复制和旧窗口收据有 Host 回归，实机快速旋转及首合成帧仍待验。

方向融合点为原生 `RecentsContainer.updateRotation(I)V`、`TaskViewTransform` 与窗口参数提交：在原生容器布局前撤销旧几何，包含宽高和 landscape 布尔值均不变的 90→270°；新窗口票据只需两份原生视觉 viewport 已准备，不要求 Display 与 View Configuration 相等。手机原生固定旋转可能保持竖向 Activity / Window，再交换容器测量宽高并旋转呈现横屏。`setInOverviewState` 已沿 reloadStackView / setTasks 请求布局，模块只刷新准入，不重复请求布局。没有新增方向监听、手势分类器或动画时钟。

卡片与窗口内容框统一使用原生完整 TaskView frame 的中心。原生 TaskViewsElement 在窗口更新时重写 pivot，其他入场和滑动路径也会改变 pivot；旧展示矩阵取用临时 pivot 后缓存，可能与窗口按 frame 中心计算的落点不同。现在自持矩阵固定使用 frame 中心，滑动期间仅在自持矩阵补偿原生 pivot / scale 的组合，保留原生根属性和删除资格。实际屏幕内容框、逆变换命中和窗口模型有同一中心的行为回归，包含 pivot 迟到改变及非均匀原生缩放。原生截图、Surface 提交和 finish 继续由原对象持有。

上滑过程另有独立的原生姿态消费者：8604 的 `TaskViewsElement.updateTaskView(TaskView,Int,RectF,Int,Int,Float,Float,Boolean,Float,Float,Runnable,Float,Float,Boolean,Int,Float,Int,Rect)V`。原生以屏幕窗口宽度求根 scale、线性页间距、Header 中心与 `300×mPer³` 位移，直接叠上 Deck 矩阵会二次缩放。WindowElement Profile 现冷解析该精确实例方法，在同一次原生调用前将窗口姿态映射到自持矩阵之前的坐标；收拢程度直接由当前原生窗口宽度与已准备端点求出，不再消费分段的手势进度。接纳的姿态统一走原生即时分支，不再用逐卡计数器和 Runnable 包装保留第二层追赶弹簧。已核对原方法的即时分支会取消旧弹簧、写入本次姿态并执行原 Runnable；原生弹簧取消仍调用自己的结束监听，不丢弃原生完成工作。原生 alpha、fullscreenProgress、任务索引、窗口 spring、Surface 和 finish 保持。

`alignTaskViewWhenAppToRecent` 沿同一窗口几何跟随原生 closing / end 回调，不再整段跳过邻卡对齐。端点取自本次已准备的布局，Header / gap / count 随布局更新；每卡一个复用 RectF，owner 两个工作 RectF，不逐帧创建几何模型或额外时钟。原生准备早于首次布局时，在既有姿态回调内同步矩阵尺寸后再做逆换算；该时序已有先失败后通过的回归。Legacy 的同类上滑消费者在下述横屏响应续修中补齐；未测样本仍需独立验收。

后排标题 / 图标的深度透明度在现有 PreDraw 内随有界卡片列表校正，涵盖首次布局之后绕过 TaskView setter 的原生 Header 动画 / reset 写入；不再只校正绑定后的第一次绘制。相同 alpha 不重复写入，不请求额外帧，不增加监听器或业务时钟；回收、旋转、退出仍释放原生属性所有权。此项有首次绘制后多次迟到写入的 Host 回归，实际上滑呼出的最终观感待实机验收。

设置入口为“系统 → 最近任务 → iOS 任务卡片”；“iOS DECK 堆叠任务”使用原键，“卡片比例”控制整体大小。默认自动按 .75 页比例与可用高度求上限，自定义 55–85%，最大显示高度限制为可用高度的 90%；这组范围为 Android 安全映射。新设置关闭 / 自动时不会改变原生业务尺寸或截图宽高比。16:9、19.5:9、20:9、多密度及边界大小有 Host 几何回归，实机覆盖与未覆盖场景分别记录在路线图。

Legacy 同步接入已核对的 NavStubView.updateTaskView(TaskView,Int,RectF,Int,Int,Float,Float,Boolean,Float,Float,Runnable)V，读取同一原生 mPer 并排除 quick switch；7603 / 8255 的横向根姿态与上述 WindowElement 消费者一致。横竖屏现共用同一 iOS 场景表，取消此前仅横屏使用原生节奏的分支。原生圆角更新、截图及完成回调仍在原方法内执行；Host 只验证映射和所有权，不代表设备性能通过。

## 动效统一管理（2026-11-07）

卡片外形与运动策略分开：截图、圆角来源、裁切及阴影颜色沿用 HyperOS 1；窗口入退场、手势松手与点击恢复的几何四轴由同一个 DeckMotion 管理已确认的 iOS 场景。横竖屏没有独立参数或另一套动画时钟，新旧 Launcher 只分别实现 DeckSpringTuning 的原生字段 / SpringBundle 存储与转换，场景表位于 DeckSpringScene。

同一管理入口负责有限调用范围、实际 spring 身份、ticket、原生场景类型、重定向前归还和结束 / 取消释放。旧版手势进入嵌套 closing 时沿用 GESTURE，不能被内层按钮 TOGGLE 覆盖；closing provider 在同步调用中换 ticket 时只继承同一个 live owner 的手势来源，参数写入仍核验新 ticket。明确标记的 OPEN 优先于外层宽范围的手势，其他 owner 或无关原生场景不继承。

当前继续使用既有 A14 参数映射 .336/1 与 .531/.94，不从 response 推算总时长；跟手与邻卡排布共用现有 Deck 几何和原生窗口进度。六文件核心包已完成首轮有界汇编对照；完整滚动 / 删除求解器、未松手邻卡模拟布局、首次合成帧及真实横竖屏观感仍待对应证据精校。统一入口不是整条链路与 UIKit 等价的结论。

## 核心包首轮精校（2026-11-07）

来源固定为 iPhone17,1 / d93ap / iOS 18.6.2 / 22G100。核心包的 verification 记录逐字节汇编核对通过且 runtime_verified=false；这证明选定函数的静态原件一致性，不证明动态 receiver、全部调用关系或原机轨迹。C 重建包含 ABI 与间接控制流未知，采用实际 ASM 分支作决定。

| 证据与消费者 | 对照结论与 A14 决定 |
| --- | --- |
| depthForIndex 0x1d860e550、_scaleForTransformForIndex 0x1d860e698、scaleForIndex 0x1d860e64c、leadingOffsetForIndex 0x1d860e44c | 四个数据常量 .13 / 9 / -18 / .12 已核验。渲染是 pageScale × (1 + .12 × depth)，leadingOffset 最后也乘渲染比例；现有 IosDeckGeometry 保持此链。固定映射输入的数值样本单独回归，不把 .75 和零起始原点称为本轮恢复的场景默认值 |
| _scaleInSwitcherViewForIndex 0x1d8d117dc、_scaleTransformedFrameForIndex 0x1d8d116f4 | 容器几何使用 pageScale + .12 × depth；不能据此替换卡片渲染矩阵或窗口端点比例。当前可见池 / 命中继续按 A14 实际呈现几何处理，未宣称与 UIKit 容器模型完全等价 |
| _restingScrollProgressForProgress 0x1d8d10e08 | ASM 可确认按目标候选距离及方向筛选；C 丢失间接控制流，不作为完整算法。A14 仍只迁移方向候选到原生 q 格点，desiredXOriginForQuantizedTopPage 的完整值与求解器仍未知 |
| DeckToFullScreen._layoutSettings 0x1d8db498c、HomeGestureToSwitcher._layoutSettings 0x1d8dc06f0 | 可确认 toggleAppSwitcherSettings / launchAppFromSwitcherSettings 与 gestureInitiatedAppToSwitcherSettings 是不同消费者；不能由 SBFFluidBehaviorSettings 基础默认值推定每个配置对象的最终赋值。保留现有 A14 参数映射并更正证据等级，不猜新参数 |
| DeckToFullScreen.handleTransitionEvent 0x1d8db465c | 原生以 transitionID 匹配事件。A14 同次点击的 start-rect 读取改为复用已建立票据、冻结端点和 listener；实际新点击仍可替换票据。前卡打开没有前置遮挡项，直接跳过遮挡扫描；后排仍沿同帧矩阵校正。邻卡更新改为现有列表索引遍历，不新增状态或时钟 |
| SwipeToKill 的模拟删除、frame / scale / opacity 消费者 | 原生会在临时删除状态中插值邻卡；A14 未松手阶段的完整邻卡模拟及私有 spring 求解器仍未迁移，不能把现有原生回填 / 取消合同视为完整等价 |

本轮优先消除重复交接和无效逐帧工作，卡片仍用 HyperOS 原生圆角与阴影，横竖屏继续共用 Deck 动效入口。代码层的调用 / 遍历减少不换算为实机帧率、延迟或功耗收益。

## A14 横屏原件重新分析（2026-11-08）

本轮不复用 ROM 索引，逐一从四份 A14 Launcher APK 及各自 overlay 提取选定类的 Java / Smali。三份 MIUI 为逐字节相同的 8604，POCO 为 7603；overlay 未定义额外横屏几何策略。DeviceConfig、RotationHelper、Launcher、RecentsContainer 的链路确认：普通手机原生固定旋转与视觉横屏可以同时成立。此前全局强制 isKeepRecentsViewPortrait=false 的策略已撤销，历史实机结果不能替代本次策略的验收。

WindowElement 更新同时传递 Window 原始矩形与已旋转 Surface 矩形。邻卡展开将原始 Window 矩形逆映射到任务栈局部空间，保留实际卡片中心，再消除展示缩放对位移的二次缩小；收回时原生布局参考框已是局部坐标，只转换移动窗口。Surface 遮挡按同一局部卡片矩形一次投影到应用方向，复用每 owner 的 Matrix，不逐卡追溯祖先矩阵。旧版最终对齐传递同一个局部 RectF 的情形不做第二次转换；原生四分之一转交换端点宽高时，圆角仍按同一 owner / ticket / spring 的缩放交接。

TaskView.mFullscreenProgress 的原生写入与截图绘制也已从两类原件核对：全屏为 1、任务堆叠为 0。阴影沿此进度与截图可见性连续显隐，首次 outline 仍读取最新截图 alpha；不修改截图透明度、根缩放或原生圆角。不新增监听、滤镜或动画时钟。以上属于静态调用链与 Host 几何证据，横屏持续 HOLD、反向、松手、点击恢复的真实帧时间与功耗仍待设备验收。
