# 文档维护规范

代码是实现事实来源。文档保留用途、关键合同、操作方法和未决边界；开发过程由 Git 保存。

## 单一事实来源

| 内容 | 权威来源 |
| --- | --- |
| 工程判断、授权与基础约束 | [AGENTS](../AGENTS.md) |
| 入口、名称、顺序、条件 | [app/settings](../app/settings)、资源及实际控制器；[功能地图](FEATURE_MAP.md)与[简版](FEATURE_MAP_SIMPLE.md)为同源生成视图 |
| 路由、安装、所有权 | 当前源码 / Feature catalog；跨功能合同在 [ARCHITECTURE](../ARCHITECTURE.md)，进程矩阵只是生成视图 |
| 功能行为、逆向映射 | [总目录](README.md)中的对应专题；保留固定样本、结论、未知项和必要来源 |
| 支持范围 / 当前待验 | [COMPATIBILITY](../COMPATIBILITY.md) / [原路线图](maintenance/A14_REFINEMENT_ROADMAP.md) |
| 环境、协作、生成 / 工具参数 | [DEVELOPMENT](DEVELOPMENT.md) / [TOOLS](TOOLS.md)及 CLI `--help` |
| 选测、证据复用与停止条件 / 性能测量 | [TESTING](TESTING.md#执行分级) / [OPTIMIZATION_GATES](OPTIMIZATION_GATES.md) |
| 正式签名、发行与恢复 / 版本历史 | [RELEASE](RELEASE.md) / 双语 [CHANGELOG](../CHANGELOG.md) 与 [CHANGELOG_EN](../CHANGELOG_EN.md) |

其他页面直接链接权威来源，不复制完整合同。根 README 只介绍产品、安装、下载和开发入口；[docs/README](README.md)是唯一文档总目录，AGENTS 和 Skills 只提供按需路由。

## 更新与删除

- 页面、键、名称或路由变化时用 `python tools/generate_feature_map.py` 更新地图，不手写第二份功能清单。
- 只更新实际受影响的合同；路线图保留当前决定、未决问题和证据入口，CHANGELOG 只保留正式版本的有效变化及必要的未发布用户变化。
- 删除重复说明、过期计划与已被替代的过程记录，不搬到新档案。需要追溯时引用固定提交与历史路径；保留发布恢复、兼容和逆向复现所需依据。
- 删除 / 移动前检查 Markdown、源码注释、生成器、测试、工作流及模板消费者，一并修复引用，不留纯跳转空壳。
- 第三方 Skills 保留来源与许可；按实际消费者判断资源用途，不仅凭未进入 CI 就删除工具。

## 写作与验证

- 简短、直接、可检索，使用真实路径、符号和设置键；不逐段复述源码。稳定文件名不因文字整理改动。
- 中文标题，技术专名保留原名；英文 README / CHANGELOG 保持英文。沿用 README / AGENTS / SKILL 等约定，不复制外部文档译文。
- 区分已实现、源码 / 构建验证、静态样本、实机结果和待验；推断明确标注，性能结论遵循测量合同。
- 公开内容按 [AGENTS 的证据边界](../AGENTS.md#验证发布与证据)脱敏，原始 ROM、截图、日志和采样留在忽略目录或仓库外。
- 按 [Quick 文档范围](TESTING.md#执行分级)运行本地链接、锚点、图片与必要生成物检查；工具或构建合同实际变化才补相应测试。不将本地链接通过称为外网或设备验证。
