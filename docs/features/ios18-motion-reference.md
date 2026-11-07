# iOS 18 运动参数与 A14 映射边界

2026-11-04；原始样本为 iPhone 16 Pro / iOS 18.6.2 / 22G100。依据原生方法反汇编及常量逐项复核，DONOR 仅作研究入口；原始系统文件、反汇编及设备记录私有留存。此页区分已恢复的配置与 Android 工程映射，当前结论见[路线图](../maintenance/A14_REFINEMENT_ROADMAP.md#ios-18-参考与吸入精修2026-11-04)。

## 岛体

2026-11-06 复核采用私有 `island-22G100/recheck-20261106/A14_HANDOFF.md` 为唯一研究交接入口；`MUSIC_ARRIVAL`、`NATIVE_MODEL`、`REVIEW_DIFF` 与 DI22G-001–020 证据索引已消费。Home 接受和首次播放分开：首次音乐是否提交 bounce request、重定位进度生产 / rebasing、UIKit 当前动画状态对 impulse / smoothing / projection 的消费仍未闭环；原机动态确认缺失。旧自动表 Accepted damping=.016、Stretch impulse=.525 存在反证，不能继续导入。经原指令修正的 Up 参数仍为 Stretch .5/.714/.02、Rebound .35/1/.016、Accepted .513/.525/.02，首 container 的 height / center.y 使用 .513/1 辅助配置；顺序为 response / damping / retargetImpulse。

| 场景与消费者 | A14 当前映射及差异 | 本轮处理 |
| --- | --- | --- |
| 首次音乐、已有 Inert → 音乐；布局 / 媒体准入 | 普通布局弹簧、相机种子与内容显现；首次是否进入 Acceptance 未证实 | 不添加无证 catch 或整体横移；不以 .512/.816 判定等价 |
| 音乐 App → Home、已有音乐岛接收；Acceptance bounds / center | 基础布局 + 附加分轴，负 extraWidth 裁切及最大宽度仍为 Android 约束 | 修复原生进度失效绕过自身收敛；按物理尾部预算延长同一有限时钟，不增加时钟或拆出控制器 |
| 暂停恢复、切歌、音乐 ↔ 计时器、音乐 → 通知 → 音乐 | 来源仲裁、身份变化与内容 / snapshot 各自消费；普通通知的 iOS 活动类别未证明 | 保留场景区别与旧快照身份保护；不把所有事件视为 Home 接受 |
| 快速切换、取消与反向；原生 retarget | Android 延续当前呈现值和速度，不能代替原生 impulse 求解 | 对失效 / 完成通道、分阶段轨迹、峰值、尾部及 60/120Hz 采样做回归；Host 轨迹不是原机实测 |

历史定位：早期可选 iOS 运动逐步扩展到原生返回与缺少原生进度的内容路径，随后统一为主路径并移除额外效果开关。旧版明显左右变化来自本地额外宽度波，不能作为原机依据。本轮不删除尚有生产调用的旧函数，不撤销相机 / 安全区限幅。下一项真正需要的是同构建首次 / 已有 Inert / Home 返回的同步事件、实际 layer 矩形与原机逐帧轨迹，不能靠循环调阻尼替代。

`SBSystemApertureTransitionSettings._setDefaultValuesForBehaviorSettings:`（`0x1d8b86ed0`）与具体布局初始化需联合判断；只读基础配置会遗漏覆盖值。

| 配置 | response | damping | 证据与 A14 消费 |
| --- | --- | --- | --- |
| Minimal / Compact / Custom 默认 | .512 | .816 | `defaultTransitionSettings`：`0x1d8b57660`，布局初始化 `0x1d8c8d31c/354/38c`；映射普通边界 |
| inert 默认 | .512 | .6 | `0x1d8c8cf88–0x1d8c8d064`；映射回到小岛 |
| inert collapsing / compensating | .45 | .816 | 重新初始化基础配置后只覆盖 response；不能继承 inert 的 .6；映射可见岛体缩小 |
| leading / trailing / minimal | .512 | .816 | `behaviorSettingsForProperty:` 分配 subcomponent 配置；映射普通短岛内容 |
| customContent | .6 | .816 | 内容类的基础继承与覆盖均已核对；映射长岛展开内容 |
| snapshot | .3 | .816 | 仅映射实际保存的旧内容显示列表，不冒充普通旧 slot |
| popToCustom | .4 | .78 | 仅 interactionSource=2 且 viewInteractionResult=1，不能泛化为所有长按 |
| contentInteractionBegin | .85 | .694 | behaviorType=1、impulse=.02；按压宽高请求 1.2 倍并按边界裁切，A14 松手复用既有回弹 |

前版 A14 只是借用 .816，实际角频率仍为 `10.5 / .384 ≈ 27.344`；实验改为 `2π / .512 ≈ 12.272`，因此响应会明显改变。这里的 period 转换、误差容限与 A14 状态分配是 **INFERRED**，不是已经恢复 Apple 私有弹簧求解器。

iOS 动效已统一为主实现，移除 `system_island_ios_motion` 用户开关及旧缩放 / 四档模糊支路。尺寸、位移、整体透明度和内容透明度使用同一个有限时钟；按物理像素误差及速度包络决定结束时间，快速重定向从当前值和速度接续。采样不因 60/120 Hz 而改变解析轨迹；绘制尺寸遵守安全区，关闭系统动画或省电直接设终态。实验普通切换不叠加本地 catch 波或唱片翻转，仍保留原来的小岛 / 短岛 / 长岛、仲裁、窗口、输入、G4 和内容布局。

多活动 iOS 效果交接改为本次提交立即显现新内容；硬件绘制时最多保留一份旧内容 RenderNode 显示列表，按 snapshot 配置退出。不捕获位图或背景、不复制来源和采集租约；透明内容容器按祖先矩阵、裁切和 alpha 记录叶子绘制命令，不引用随后会更新的子 View RenderNode，旧时钟文字与波形因而只保留交接时画面。三连替换保留较可见的一侧并复用同一显示列表，不排队播放历史。普通 slot 使用 .512/.816，展开内容使用 .6/.816；只在 inert 初入做横向 squish，普通 compact/custom 交替不无证压扁。局部模糊用同版 16.666 的范围作有界 Android 映射，档位续修见下文；图标、文字和波形共用一个内容容器 / RenderEffect owner，外壳与相机不参与滤镜，旧内容单独使用复用的显示列表；不可硬件绘制、省电或关闭动效时不保留快照。这里的 RenderNode、滤镜核、离散档位与多次替换策略均为 A14 适配，不宣称 UIKit 播放器等价。

Home 吸入由原生窗口轨迹、进度和既有有限承接链决定；上述参数不覆盖该链，也不据 iOS 私有类名寻找 Android Hook。落点矩形宽度为岛体的 62%、高度为 44%，与岛体垂直中线对齐；页面头上限改为落点宽度的 78%，且不超过本次接触口宽度的 80%。接触口在返回请求时取当前可见岛体的交集；新出现的岛体使用相机 / 高度种子宽度，离开源应用时按未来小岛范围收紧。横向收束先于头部接触完成，避免页面头伸出当时的岛体。原页面头部圆角随前缘到达压平，进入岛体后按完整遮蔽规则消失，保留尾部圆角与不透明主体。这些尺寸及裁切是用户要求的 A14 几何映射，不能冒称 Apple 私有 mesh。接触提示不参与逻辑目标身份，复用既有进度通道，不按帧广播。已有岛体接收第二活动时，保留当前外壳与旧内容，前缘阶段到达后再交接内容和承接脉冲；其 12% 相对进度是 Android 近似，并非 Apple 原生接触采样。捕获上限由 768 像素宽 / 512 Ki 像素降为 640 / 320 Ki，网格由 16 行降为 12 行；仅证明像素预算减少 37.5%、顶点由 34 减为 26，不推断设备帧率。Android 手势回弹保持原有输入合同。整体体验是否接近 iOS 仍需人工比较；主机轨迹测试不证明设备帧率或功耗。

恢复响铃的岛体负反馈已核对完整调用：`SBRingerAlertElement._shakeRingerBell` → `elementRequestsNegativeResponse:` → type 1 的 `SBSAShakeRequest` → `SBSAShakeBehaviorProvider`。普通静音边沿中，仅 transitionType 3（退出静音）请求该行为；不据铃铛 CA 图层推导岛体旋转。实际通道为父容器中心及子容器 bounds / center：位移 8、扩宽 22、偏移 3；kick / recenter 的 response .222、damping .141，stretch-out .23 / 1，stretch-settle .732 / 1；里程碑 .923 / .756 是弹簧进度，不是秒数。A14 对恢复响铃执行一次同钟横移、扩宽及释放，按 RTL 镜像，保持 G4、内容反缩放与安全限幅；新出现的岛体留 160 ms 建立外形。点数到 dp、解析求解器、接续速度及入场等待均是 Android 映射，未把私有 impulse .02 当作 Android 速度，也不宣称原生总时长。

活动共用链补齐了状态栏绑定时通过原生 `MiuiDependency.get(MiuiTopActivityObserver.class)` 读取当前前台，后续沿用已有构造 / 更新 Hook，不增加观察者或轮询。所有实时活动仍按来源前台过滤；原生返回目标不因该过滤丢失。横屏时，当前用户已准入的原生闹钟或已响铃计时器让整岛暂时让出显示，忽略岛内的主动关闭标记；保留媒体 / 活动快照，结束或旋转后恢复。此为响铃状态避让，未恢复准确的原生浮窗占位 API；不修改闹钟服务、全屏通知、声音或关闭动作。

最终消失恢复既有 307 ms 退出与入口样式，不再用较长的实验内容收敛时间延后移窗，避免音乐居中收起末尾留下半透明岛体。普通可见形态仍使用实验配置。

Home 音乐返回不属于普通方向入场：无原生窗口进度时仍从相机种子展开，不能因 `mediaReturnProgress=1` 而退回 TOP 的负 Y 滑入。293 实机及双方向选项回归已暴露该遗漏，现以返回身份选择相机锚点；普通通知的方向和最终退出选择保留。该修正只保证回退路径与既有种子合同一致，不增加等待、动画器或接受脉冲。

另修复普通 Toast 在较大字号、较矮自动高度下被完整文本测量拒绝的问题：按当前高度缓存可容纳两行的最大可读字号，测量和绘制共用结果；超过两行容量仍回退原生。此项不改变系统提示或消息的来源、准入与仲裁。

系统模式提示单独采用单行合同：当前实际文字通道先缩至可读字号，仍不足则复用安全宽度边界及真实 `captionRoom` 二分求宽，不借普通 Toast 的两行准入。开孔偏移、目标高度和 fontScale 均进入测量；本地化短名称只在完整名称仍放不下时使用，无障碍描述保留全义。StaticLayout 与标题使用同一字体 / 语言回退、balanced break、无连字符和不含字体额外 padding，稳定输入复用测量。保留系统字形，不打包 Apple 字体或用 scaleX / 额外字距补宽。22G100 本地证据尚未确认此类状态标题的精确字重、字宽及基线，不称像素级字体等价。

后续文字显现复核发现，实验方案的完整宽度准入会在透明度已推进后突然显示整段文字；局部字号缩放和分档模糊还会叠加变化。当前保留同一内容透明度通道和可用区间裁切，字号保持物理尺寸，不等待整段文字完全放下；局部模糊按上文明确范围重新映射，避免与无证双轴字号缩放混用。裁切按相机保留区及左右文字通道计算，布局后刷新；连续计时仍复用原生 Chronometer，不因 tick 重播交接。

22G100 的 Home 接收确有正向纵向拉伸：`SBSAAcceptanceBounceBehaviorProvider._expandedBouncingContainerViewFrameForBounceStyle:unexpandedFrame:`（`0x1d8db0ae0`）普通方向临时 height+26、y−17，侧向 height+18、y−9；Stretching block 实际写入 body bounds / center。Home 返回的 bounce threshold 通过请求链触发，LayoutY 默认 .513 / 1，回弹延迟 .12。它不支持本项目原先“横向收束后再负向最多 1 dp / 180 ms”的额外 pulse，因此移除该附加项及高度借用，保留真实目标高度过渡和用户直接拖动。本轮依据 Up 的已证阶段恢复正向承接；点数到 dp 与非驻留 seed 的基础运动仍属 Android 映射，不能声称原生从无纵向变化。

已有外壳的内容替换不再借用 Home 入场进度或附加扩张脉冲；同尺寸只交接内容，真实尺寸变化从当前呈现继续。通知同 key 更新立即撤销旧点击，已批准画面只保留到原期限或新绑定接替，不提前退场、不因尚未批准的更新续期。隐私回调核验当前同 key 通知的原生策略，将对象更新与真正隐藏分开；锁屏、应用锁、行回收及不同身份仍撤销展示。真实窗口向已有岛体返回时保留独立接受轴，内容在既有 Accepted 时点后使用同一有限时钟的 slot/snapshot 通道；没有原生进度时不回退旧 catch 波。旧显示列表同时保留当时的文字裁切范围。这些仍属 A14 承接策略，不新增 iOS 原生事实。

## 音量条

`SBElasticHUDSettings.setDefaultValues`（`0x1d8b2d16c`）、`sizeForState:`（`0x1d88e4934`）及 `_animateFromState:toState:…`（`0x1d88e0514`）已选定分支复核。

| 状态 | 竖向点尺寸 | 横向点尺寸 | 比例 |
| --- | --- | --- | --- |
| Base | 47 × 148 | 209 × 32 | .3 |
| Initial | 47 × 148 | 209 × 32 | 1 |
| Compact | 7 × 134 | 238 × 5 | 1 |
| Interacting | 58 × 170 | 260 × 37 | 1 |
| Deflating | 47 × 148 | 209 × 32 | .75 |

比例之后按当前显示倍率对齐物理像素；Reduce Motion 比例为 1。Base 是收回基态，不是常驻条。初入形状 `.8/.9`、位置 `.4777/.8`；普通形状 `.408/1`，未指定独立位置时复制形状配置。Initial→Deflating 为 `2.75/.9`；Deflating→Base 形状 `.5/2.75`、位置 `1/.5`，后者另有 impulse .12。不能把 overdamped 配置送进仅支持欠阻尼的求解器。

默认 dismissalInterval=1、postTransition≈.7；deflation 定时器实际取 settlingDuration×.18。`_dismissalTimerFired:`（`0x1d88e1700`）检查 tracking 并重排，不能抄成无条件消失。原生按钮步长需查询音频系统；DONOR 演示的 16 档不是 Android 合同。

A14 开关 `system_volume_ios` 默认关闭，重启 SystemUI 生效。通过已核验的 HyperOS 1 音量插件 `MiuiVolumeDialogImpl` 装饰横竖屏收起控件：监听原生进度与 tracking，保留滑杆 listener、相对拖动、档位、流 / 路由、安全警告、辅助功能、展开及关闭；不新增 AudioManager 写入、不拦截物理按键。关闭 / detach 恢复所有持有属性并停止形态时钟及延迟任务。横屏沿用 Android 原生侧边位置和方向，展开保留原生控件。

当前仅移植确认的选定尺寸和初入 / 普通形态参数。点数到 dp、G4 绘制、深灰 / 白色材质、收细调度是 **APPROXIMATED**。窗口位置、出入场轨迹及触摸区域完整沿用安卓原生；收细过程中仅将条体绘制中心向原生所在侧的触摸区域外缘平移，仍在同一形态时钟内。原生窗口关闭与输入仍由系统控制，不声称完整复刻两通道出入场或原生 MaterialKit。尚待完整验证通话、远端路由、其他地区 ROM、旋转和辅助功能的运行矩阵。

进一步复核 `SBElasticSliderView._fractionToRegularMetrics`（`0x1d8be5f1c`）与 `_updateCornerRadius`（`0x1d8be5fa4`）：竖向 `f=clamp(4×(width−7)/40,0,1)`，圆角为 `width×((1−f)×.5+f×.3191)`。常规态不是完整胶囊，7–17 点之间已完成圆角比例过渡。A14 用共享 G4 曲率族表达同一圆角范围：边缘使用四分之一长度的曲率渐变，中间保留常曲率段；按该段的曲率半径换算肩部占用宽度，短轴半宽限幅。在收细的 regular-metrics 区间内，曲率平滑趋向圆形，Compact 用 Android 原生半圆端帽，避免四段 G4 肩部相接后顶端偏平。原生半径不再直接当成肩部宽度，不能将此映射称为 UIKit 的精确路径。

`_elasticGlyphCenterForDefaultGlyphCenter:`（`0x1d8be6520`）的竖向中心为 `x=width/2`、`y=(height−width)×(1−f)+(height−width/2)×f`。`glyphScaleForState:`（`0x1d88e4dc0`）为 Initial=1、Compact≈0、Base=.3、Deflating=.75、Interacting=58/47，再乘竖向 .768。形状与图标共用当前形态时钟并保留速度；图标复用原生路由 / 静音 drawable，颜色阈值和 A14 的更多入口仍属适配。

`transitionStateContextuallyForValueChangeEvent`（`0x1d88dee7c`）已核对：`_appearState=2` 时，非基础态的值变化选择 state 2 / Compact；基础态另按初次过渡标记选择 Initial 或 Compact。A14 将首次显示、显示期间的后续按键值变化与触摸 tracking 分开；后续音量键进入 / 保持细条，继续显示原生进度，不强制回粗条。这里的首次事件标记为 Android 事件适配。

原生 `_updateSliderTracking` 的返回分支保留拖动前的 Compact 状态：从细条开始拖动，松开回细条；从 Initial 开始则回 Initial。A14 复用原生 tracking 回调，更多入口放在条体下方，展开后完整恢复原生布局与材质。父容器在原生 attach 回调中晚建的背景也按实际对象保存 / 恢复，避免外围灰色矩形。

独立更多入口保留 Android 键盘与无障碍焦点能力，仅在接管时关闭 `focusableInTouchMode`，避免第一次 UP 只获取焦点、第二次才执行原生展开；退出时归还原值。它不新增二级面板，也不把此 Android 操作入口当作 iOS 原生元素。

原生开启背景模糊时，音量填充由 `MiuiVolumeSeekBarProgressView.setProgress` 的 Folme 驱动，SeekBar 同时存在另一条 200 ms 动画。A14 此时直接消费原生可见填充的进度 / 高度，避免两条值轨迹争用显示；非模糊分支继续跟随 SeekBar。材料刷新重申单一绘制所有权。深色未填充段与白色填充段分别裁切绘制，同一抗锯齿边缘只经过一次材质，避免深色底层透出暗边；不新增值动画或音量写入。

端点触摸接入 `MiuiVolumeSeekBar.onTouchEvent` 的具体重写，在相对轴变换前读取原始运动；基类字段的声明类型不能当作 Hook 所有者。含非零最小档位的原生流仍沿用系统边界。输入采用 .55 有理阻尼，起手捕获 initialStretch；当前范围按状态 .182 / .105 与轨道长度取整。宽度分母使用当前配置范围，高度增加 .73×|overflow|、中心移动 −.77×overflow，保留本地安全边界。抵达端点时 overflow=0，继续越界才拉伸；按键端点仍有独立有界速度反馈，松手与取消归还同一有限形态 clock。更多点隔离原生动态 tint，保持中性灰及既有 28 dp 展开入口。

## Duo 27.2 独立证据

此处为 iPhone19,4 / iOS 27.2 / 24B91020 的 SystemStatusUI 288.1.6.0.2，独立于 iOS 18 岛体参数。原始系统文件和反汇编私有留存。

`STUILayoutSettings.setDefaultValues`（`0x22482e2f8`）确认默认组合边长 47、ringDiameter 38、arcDotDiameter 4、arcDotSpacing 3、wifiSymbolSize 16、wifiSymbolWeight 4、wifiSymbolYOffset 1.33。`intrinsicContentSize`（`0x224871124`）缓存读取默认 47×47。这些配置与 UIKit 圆电池配置分属不同所有者；新资料已补齐普通状态栏 peer 消费链及四段 Circle，不把配置值直接当屏幕测量。

电量更新（`0x224807cf0`）将 capacity 除以 100，比例大于 .01 时保留，否则设零，再分别设置充电状态、比例和省电状态。`setChargePercent:`（`0x22486fe94`）使用 SwiftUI spring(response=.15, dampingFraction=.85, blendDuration=.25)；`setExpanded:`（`0x22486fbac`）使用 Spring(duration=.5,bounce=.3) 后接 interpolatingSpring(initialVelocity=0)。这些入口与参数为 **CONFIRMED**；Android 求解器转换、停止条件及状态分配仍属工程映射。blendDuration 不是总时长，展开参数不能覆盖中心图案切换。

Wi-Fi 与蜂窝按 data/style 变化增量更新，使用独立 active/inactive color；低数据模式为黄色、inactive alpha=.3，该值仅限此分支。A14 已有原生事件、来源身份快照、合并刷新及未知状态回退，无需增加轮询或状态框架。默认数据卡状态、耳机短提示及当前圆环渲染继续标为 Android 适配；当前切换时序见下方同版默认弹簧补充。

新增恢复的 `ArcLayout`（`0x224851878`）沿半径 r 放置点：θ=(dotDiameter+spacing)×(j−1.5)/r，x=midX+r×sinθ、y=midY+r×cosθ。A14 按原归一化间距与现有环坐标预计算四点，修正左右不对称；仍为四次绘制，不改变 Android 数据卡与强度状态。Wi-Fi 的组合资源身份 `wifi.statusbar.combined` 与固定矢量轮廓已恢复；CoreUI raw weight 不能直接解释为 SwiftUI bold 设置。Wi-Fi 构建器的 scale+opacity 已确认，默认动画的现代消费者见下方补充，不泛化为所有中心图形。

纯代码库补齐的主副项分支、Wi-Fi 条件字号和两遍布局、原始矢量、普通圆电池曲线及 A14 当前接入范围统一见 [Duo 专项](duo-status-bar.md#图形与兼容边界2026-11-06)。只迁移现有 Android 状态能表达的分支，不引入玻璃或为每次 1% 电量变化增加逐帧动画。已有主屏 / 锁屏短时视觉证据不等于全状态、原机轨迹或设备性能验收。

Duo 图形直接构造并按需共享原始 Path，不读取宿主图标或在绘制时解析资源。默认关闭额外加粗；Wi-Fi 沿用已解析 master 坐标。制式文字的 Android synthetic bold 跟随开关，百分比使用获许可的 Nunito 900 圆体子集；它是 Android 近似，不用新数字代码的 wght400 默认值替换未知的原生最终字重。圆环使用已证普通消费者的四段贝塞尔与 Android PathMeasure 适配，不称 RenderBox 测长或栅格化等价。

CompactRingLayout（`0x224890638`）按元素语义与实际尺寸两遍放置：ringSymbol 顶部锚点、辅助项沿主项实测底缘或固定 top、arc 居中；第二遍不继续累积基线。主副构建分支已取得，原生 Wi-Fi 默认设置 raw4 为 SwiftUI bold，不能与 CAR master key4 混用。A14 矢量框测量、字重选择和字体到墨迹的映射继续明确标为适配，未宣称关闭额外加粗即原版默认字重一比一。

## 2026-11-05 同版按压与音乐接受阶段

容器按压识别器实际 minimumPressDuration=0，.2 秒 timer 区分 TapAction / PressAction；它不是动画延时。Began 把宽高各乘 1.2 后置中并裁到系统边界，内容倍率来自裁切后的实际尺寸比。A14 使用现有 shell clock，以 .85/.694 的 Begin 配置作解析映射；UP/CANCEL 使用原回弹，打开应用不等待回弹。业务长按与自定义手势阈值保持。拖动接管必须区分新按压与旧回弹，不能吞掉 DOWN 至首次 MOVE 的真实位移。

Up Acceptance 的目标框相对当前容器为 x−8、y−17、w+16、h+26，即中心 Y 上移4。阶段原值为 Stretch .50/.714，120 ms 后重定向 Rebound .35/1，再80 ms进入 Accepted，宽 .513/.525、高 .513/1；这两个推进使用 timer，不等回弹完成 milestone。补核 Stretching 的描述复制 block（`0x1d8dafe88`）后，确认它读取已有容器的未扩张 bounds / center 再写入目标矩形；这不能证明应在安卓从相机种子展开时叠加独立 +26 高度通道。A14 现仅对已有岛体的真实窗口接收执行上述接受形变；非常驻相机 seed 只做既有有限边界展开，高度保持目标过渡，不附加接受鼓包。Accepted 不将整个 seed→Short/Long 的大位移改为低阻尼。基础运动、点数到 dp、既有容器上的分轴组合仍属 A14 映射；私有求解器未完整恢复。真实页面轨迹、相机安全与最终移窗保持，触发阈值和首次接受时刻并非已证等价。

## 音量可见性与弹性补充

`SBElasticSliderView._updateShadowMode`（0x1d8be647c）的 Regular/Interacting 使用 radius20、opacity.18，Compact 使用 radius5、opacity.30；状态消费者位于 0x1d88dfe9c。A14 映射到 Android RenderNode elevation/outline，而非每帧生成模糊位图；圆角阴影轮廓是 Android 外观适配，不等于 CoreMaterial。背景仅保留单一画材，未填充段提高亮度；无新增外轮廓黑色描边。原生 `elasticHUDBackground` 确有采样材质和模块继承，尚未完整迁移。

原生横屏使用独立横轴及 209×32 / 238×5 / 260×37 状态；A14 按现有产品的位置契约保留系统侧边竖向轨道，在横屏接入相同材质和交互。它不是将 iOS 顶部横条搬到 Android。旋转与展开仍由宿主重建及适配 owner 释放处理。

## Duo 紧凑状态动效补充

24B91020 的 Wi-Fi mode、airplane / cellularEnabled、蜂窝 bars 等 setter 通过 `withAnimation(Animation.default)`；Wi-Fi 强度 setter 自身无该包装。现代默认分支是 response .50、dampingFraction1、blendDuration0、delay0；Double 消费者使用 1/300 秒步长和1/600秒半步积分。A14 移除180ms两段淡出 / 淡入及空白中点，采用该现代弹簧参考，同一绘制失效链最多持有两个 glyph；反向接续当前呈现，蜂窝强度按可见性更新。Wi-Fi 的 scale(.00001, center)+opacity 有原始构建器证据，不泛化为耳机或飞机旋转。

宿主的默认动画兼容 gate、完整 retarget / merge、原生停止与 Reduce Motion 链仍未完全闭合；Android 的有限安全时限、精度收敛、动画比例和不可见停止归属本地生命周期。整体尺寸沿用一次整数像素映射，既有可见体量再加约 .50 dp；统一以 [Duo 大小说明](duo-status-bar.md#大小与颜色)为准，不另维护独立 dp 补偿。

默认窄条是独立的 A14 偏好，默认关闭；开启时直接准备 Compact 的 7×134、零图标倍率与靠边位置，不先播放 Base→Initial 再收细。实际触摸仍进入 Interacting，结束回 Compact，原生档位、展开入口、路由和窗口不变。

## 缺少窗口进度时的入场与材质重入

首次音乐返回缺少原生进度时，原实现把 progress=1 继续解释为整个 presentation 已完成，并在 transition 提前归零退出，岛体因此直接出现。现在仅窗口等待结束；外壳仍先建立正常入场种子并使用既有有限时钟。已有小岛则从当前位姿走真实尺寸 morph，不借回旧 catch pulse；同身份元数据更新不重新 prepareEntry。动画关闭 / 省电仍直接终态。原生窗口接受与 slot/snapshot 的所有权不变；此为 A14 缺失通道的适配，并非新恢复的 Apple 弹性参数。

音量 show factory 与调色回调可以在 attach 后重新写入父材质。Session 现和 Binding 一起在这些原生事件后重申单一背景 / 阴影所有权，保存最新原生对象，展开 / detach 时归还；不增加逐帧材质查询或第二动画器。保留 PlaybackInfo 的 A14 空值回退，即使 SDK 标为非空；用显式可空类型表达，不能为了消除编译诊断删除 RemoteException 后的保护。

## 音量长按边界（2026-11-06）

`_UIFluidSliderDiscreteButtonDriver._issueUpdateWithValue:`（0x18676bf74）选择竖向增加 1.06 / 减少 .92 的持有倍率；释放返回 1。`_UIFluidSliderSettings`（0x1868255d8）的 pressScale 使用 response .8 / damping .9；`_issueUpdate`（0x1861f69f8–0x1861f6ac0）将呈现倍率通过 `CGAffineTransformMakeScale(s,s)` 写入 trackTransform。A14 端点按键从一次触摸式越界脉冲改为该方向性等比缩放，接续同一有限形态时钟。反向、数值 / 档位范围变化、触摸接管及销毁清除边界状态。现复用宿主 `onVolumeChangedW(II)Z` 的 FROM_KEY / SHOW_UI 边界，松手由同一个已有回调送入当前弱引用 Session；AudioService 会清除媒体流的 PLAY_SOUND，不能用它作为必要条件。程序显示不启动端点按压；600 ms 到期仅作释放中断兜底。已核实加载版本及实体输入设备的采样中，松手后约 100–263 ms 已回弹，早于兜底；这证明实际释放接入，不证明完整离散驱动已迁移。两个插件样本的 updateExpandedH(ZZ)V 仅转调 (ZZZ)V，生产只挂后者，精确 ABI / 整批回退保持。

继续恢复的原生合同：同版默认 sliderStepCount=16；离散更新 response .3 / damping1、settle .6 / damping1（0x186824f90 / 0x186824fcc 对应原始常量 .3 / .6）；重复 Timer 每次继续 `_incrementValue`，未夹紧值通过 .55 阻力映射为越界，之后才叠加等比按压。A14 现将这条外观通道并入既有形态时钟：刚到边界不制造额外步，后续原生按键重复才累积未夹紧值；释放按 .6 回归，触摸接管保留正在退场的按钮位移。宽度取 min(.92×width,width−4)，高度增加 .73×|overflow|，−.77×overflow 位移再乘按压倍率，与同版变换顺序一致。Compact 两次越界的独立方程参考为 6.24391×147.19129、偏移 −5.43355，回归验证重复推进、当前速度接续和手势接管；纯按压缩放复用未缩放 Path，已稳定的轴略过指数 / 三角求值。16 步仅作为外观映射，Android 实际档位、重复频率和业务值仍由宿主管理；不是 iOS 音频路由或原生计时器的移植。设备完整观感、低端反向及快速中断继续按实测验收，不能由方程一致推断原机效果完全相同。

同版 `SBElasticSliderView.initWithFrame:`（0x1d8be5644）创建一个 shadowView 并使阴影路径跟随 bounds；选定初始化及模式更新未见第二个底部专用阴影。A14 保留单一 RenderNode 投影和 Compact 的 5/.30 映射，不叠加黑色描边或底部渐变。Android 投影与 iOS 的实际采样材质仍有外观差异。

## 内容模糊量化续修（2026-11-06）

同版已证 16.666 点模糊范围保持。原八档在 3.5 密度下单次半径跳变约 7.29 像素；缓存改为 64 档，最大量化误差小于 1 物理像素（密度 1–3.5 的 120 点揭示序列回归）。仍由同一个 contentLayer 与旧快照共享，终态清空 RenderEffect，旧显示列表按原所有权释放，不新增窗口、时钟或每帧效果对象。成本是首次实际切换准备更多有限效果对象；未测 GPU / 首切准备成本，不据此宣称帧率提升或完全解决卡顿。岛体参数 / 轨迹复用现有同版映射，整体 iOS 相似度仍需人工对照。
