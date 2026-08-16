# AGENTS.md — CustoMIUIzer A14 执行规则 v2

## 1. 身份与职责

用户与 ChatGPT 管理长期架构、优先级和最终代码审查。

Devin 负责：

- 按当前任务合同直接实现；
- 自行处理普通编译和测试失败；
- 在同一任务中修复；
- 运行 A14 门禁；
- 按需构建 APK；
- 输出最终 diff 和证据。

禁止创建独立 Review、Implement、Audit、Parity 或 HEAD 核对任务。

## 2. 平台边界

- HyperOS 1 / Android 14 / SDK 34；
- applicationId：`tv.withaibuild.customiuizer.r14`；
- `minSdk=34`、`targetSdk=34`；
- ABI：`arm64-v8a`；
- libxposed：API 101 最低运行基线，API 102 为目标能力；
- API 102 专属类型不得进入 API 101 必经生产路径；
- 不支持 Android 13、Android 15 或 Android 16；
- A14 独立版本、签名、APK、发布和兼容策略。

## 3. 控制权

```text
用户本轮明确要求
> 当前 active 任务合同
> AGENTS.md
> PROJECT.md / ARCHITECTURE.md / WORKFLOW.md
> 当前源码、测试、构建和日志证据
> Git 历史
```

旧文档已删除，不得从旧提交恢复旧执行流程。

## 4. 单任务闭环

```text
读取目标
→ 限定进程和功能
→ 定位真实调用链
→ 直接实现
→ 针对性验证
→ 修复
→ 完整验证
→ 按需构建
→ 最终报告
```

有明确路径后停止无边界审计。普通技术决策自行完成，不逐项向用户索要确认。

## 5. Git

任务开始：

```powershell
git branch --show-current
git status --short
git rev-parse HEAD
git fetch origin
```

- 长期文档不绑定分支；
- 普通任务不锁死 HEAD；
- 不为 Review 建平行分支；
- 不覆盖未知工作区修改；
- 禁止 force push、破坏性 reset 和无差别 clean；
- 当前任务分支可以正常 commit 和已授权 push；
- 最终记录 Base SHA、Final SHA、commits；
- 用户不再手工核对 HEAD。

## 6. A14 运行时约束

- 无关进程不初始化无关 Feature；
- Feature 同一进程只安装一次；
- preference 变化不得把已安装 Hook 重置为未安装；
- Hook 时序、参数改写和 `chain.proceed()` 次数必须保持；
- 回调最外层使用项目既有安全边界，普通异常局部隔离；
- `OutOfMemoryError` 不得吞掉；
- Receiver/Observer/listener/controller 注册必须绑定所有者；
- 多实例注册必须有替换、失效和释放闭环；
- 不静态强持有 Activity、View 或短生命周期 controller；
- 反射、DexKit、磁盘 I/O 和同步 Binder 留在冷路径；
- 热路径只读已准备好的不可变或原子状态；
- 缓存有界、按 ClassLoader 隔离；
- API 102 能力必须隔离，不得污染 API 101 主路径；
- 删除或改名必须核对 Manifest、R8、反射、DexKit、资源、动态入口和
  `META-INF/xposed`。

## 7. JVM 和语言边界

构建工具链固定为系统默认 JDK 25：

- `JAVA_HOME` 必须指向 JDK 根目录，禁止指向其 `bin` 子目录；
- Gradle daemon criteria 和 Java toolchain 均保持 Java 25；
- Android 产物的 `sourceCompatibility` / `targetCompatibility` 保持 17，
  不得误改为 Java 25 字节码；
- 不得为了绕过环境错误临时降级到 JDK 17；
- `tools/verify.py` 必须在执行 Gradle 前验证实际 Java 主版本为 25。

以下 Java 边界默认保留，除非任务明确证明可以安全改变：

- `MainModule.java`
- `XposedHelpers.java`
- `MemberUtilsX.java`

Java→Kotlin：

- 小批量；
- 行为等价；
- 迁移与功能变化原则上分开；
- 保持 JVM 签名、反射和框架入口；
- 利用 Kotlin 降低空指针和样板；
- 热路径避免隐式分配、装箱、多层 lambda 和复杂 DSL。

## 8. A13 关系

A13 可作为旧行为或功能语义参考，但不得：

- 将 Android 13 target 直接加入 A14；
- 用 A13 实机结果证明 A14；
- 为逐行一致牺牲 A14 的 SDK 34 和 HyperOS 1 架构；
- 建立跨仓库运行时依赖。

跨版本工作必须是 `PORT` 任务，并明确 API、ROM、生命周期和资源差异。

## 9. 验证

开发中：

```powershell
python tools/verify.py fast --changed
```

针对性测试：

```powershell
python tools/verify.py fast --tests <TestClassName>
```

收口：

```powershell
python tools/verify.py full
git diff --check
```

工具目录改动时补充：

```powershell
python -m compileall tools
python -m unittest discover -s tools/tests -p "test_*.py"
```

文档专用任务不跑 Android 编译。失败在原任务内修复，不通过删测试、降断言或吞异常
制造通过。

## 10. 构建

任务要求 Debug APK 时：

```powershell
.\gradlew.bat :app:assembleDebug
```

正式 Release 仅在用户明确要求、A14 仓库外签名配置有效时执行。不得：

- Debug 冒充 Release；
- 提交 APK、keystore、密码或本地签名配置；
- 自动创建 Release/Tag；
- 自动公开上传 APK。

## 11. EOL / Encoding

- Repository text canonical = UTF-8 without BOM + LF.
- `.bat/.cmd` checkout may use CRLF (see `.gitattributes`).
- `.gitattributes` is the authoritative EOL policy.
- `.editorconfig` guides editors and agents.
- Do not modify global/local `core.autocrlf` as a task workaround.
- Do not use dos2unix, unix2dos, IDE bulk formatters, or manual whole-file conversion during normal feature tasks.
- `tools/verify.py` owns EOL validation.
- Normal tasks must not repeatedly perform manual `git ls-files --eol` audits unless verify reports a violation.
- EOL-only differences are infrastructure, not feature changes.

## 12. 完成定义

- 用户目标已实际实现；
- 验收标准逐项有证据；
- A14 静态规则、编译、测试和 lint 按任务范围通过；
- API 101/102 边界未被破坏；
- 没有未解释改动；
- 最终 diff 已审查；
- 需要 APK 时给出路径、签名类型和 SHA-256；
- 实机状态明确分级；
- 最终报告只保留有决策价值的事实。
