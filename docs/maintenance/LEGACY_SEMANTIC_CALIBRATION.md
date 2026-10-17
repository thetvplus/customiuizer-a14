# 遗留功能语义契约

当前 A14 源码是实现事实基准。上游 [MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)（`d8f162653c413674f36a7d8d2b05cbd8543cf40c`）仅用于核对继承功能的用户语义，不能覆盖明确的 A14 产品差异。

比较单位是一项用户功能：偏好 → 选项 → 安装开关 → 进程 → Hook → 数据来源 → 格式 / 放置 → 更新与释放。编译成功不等于语义保持。原始差异可由当前代码重新生成：

```powershell
python tools/legacy_semantic_compare.py --upstream <MonwF-customiuizer路径>
```

## 需保留的行为

| 功能 | A14 当前契约 | 回归入口 |
| --- | --- | --- |
| `system_nolightuponcharges` | 1=原生；2=充电不唤醒且不显示充电动画；3=原生唤醒且不显示充电动画 | `NoLightUpOnChargeContractTest` |
| 充电唤醒抑制 | 只有选项2拦截 POWER、PLUGGED、RAPID / WIRELESS 充电原因；2→3后不继续阻止唤醒 | 同上 |
| 电池详情仅充电时显示 | ChargeUtils 存在而电量状态缺失时隐藏；类缺失时保留上游回退，不清除已解析类导致下轮错误显示 | `DeviceInfoChargeVisibilityTest` |
| 温度 / 电池字号0 | 保留适合当前行高的原生字号；不还原上游固定字号 | DeviceInfo 与字号契约测试 |
| 网速样式3 | A14 单行布局，不能套用上游假双行行为 | 详细网速与 Feature wiring 测试 |
| Launcher | 保持手势 `action != 1`、文件夹列数、最近任务模糊、自由窗口、图标和标题字号的开关与范围 | Launcher 契约测试 |

充电选项3为 `NO_LIGHT_OPTION_3 = INTENTIONAL_A14_PRODUCT_DIVERGENCE`，不是上游选项3恢复。温度来源、状态栏高度、字号默认值及偏移范围按当前 XML / 资源 / 配置读取，不能把旧清单中的计数或上游默认值当成现状。

## 判断与取证

- 当前 UI / 偏好仍存在但运行不可达，才是待确认的 dead feature；上游有而 A14 已移除的项目是产品差异。
- 有上游语义而目标 ROM 方法缺失或未证实，是 ROM 兼容缺口，不猜测目标；具体网速 / 锁屏 / 通知边界见 [COMPATIBILITY.md](../../COMPATIBILITY.md)。
- XML 省略 `defaultValue="false"` 与显式 false 不能直接算行为差异。
- 机械迁移与功能改变分开，保持 Backup V2、USB 默认用途、匹配重启、搜索路由、分页面、API 101/102、FeatureRegistry、偏好契约与内存释放规则。

历史批次状态、键数、已移除项目数和中间修复清单已归入 Git 历史；本页维护仍有效的契约，具体当前偏好目录由构建生成。
