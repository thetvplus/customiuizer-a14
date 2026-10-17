# AGENTS.md — CustoMIUIzer A14

共同工程规则，适用于 ChatGPT、Cursor、Devin、Codex 和其它 Agent。
不要为每个 Agent 复制一份几乎相同的文档。

## 1. 产品边界

- HyperOS 1 / Android 14 / SDK 34。
- `applicationId`：`tv.withaibuild.customiuizer.r14`
- `minSdk=34`、`targetSdk=34`、ABI `arm64-v8a`
- libxposed API 101 为最低运行基线，API 102 为隔离能力。
- API 102 专属类型不得进入 API 101 必经生产路径。
- 不支持 Android 13、Android 15 或 Android 16。
- A14 独立版本、签名、APK、发布和兼容策略。

## 2. 优先级

```text
correctness
> compatibility
> lifecycle
> stability
> maintainability
> memory/performance
> repository cleanliness
> elegance
```

工程风格：高性能、高稳定、高兼容、低内存、低占用。
少层、少状态、少框架、少重复、少魔法、少后台行为。直接、明确、有界、可验证。

不为微优化增加框架。不重做 PrefMap、ResourceHooks、FeatureRegistry、ProcessRouter、PreferenceBootstrap。不重新设计 Dynamic Island 视觉。不把整个 Java 树 Kotlin 化。不用 Compose 或新 DI。

## 3. Git

- `main` 是唯一长期稳定生产线。
- 从 exact SHA 建工作分支，例如 `cursor/...` 或 `devin/...`。
- 禁止 force push、破坏性 reset、rebase 已公开历史、无差别 clean。
- 不为 Review 建平行分支。不覆盖未知工作区修改。
- 普通任务可以 commit；push / merge main / tag / release 必须由当前任务合同明确授权。
- 不相信 Agent 自报 PASS。客观门禁和 diff 审查才算数。

## 4. 运行时

- 无关进程不初始化无关 Feature。
- Feature 同一进程只安装一次。
- preference 变化不得把已安装 Hook 重置为未安装。
- 关闭功能不得创建业务 Hook、Receiver、Observer 或任务。
- Hook 时序、参数改写和 `chain.proceed()` 次数必须保持。
- 回调最外层使用项目既有安全边界；普通异常局部隔离。
- `OutOfMemoryError`、`ThreadDeath`、`VirtualMachineError` 不得吞掉。
- Receiver / Observer / listener / controller 必须绑定所有者，并有替换、失效、释放闭环。
- 不静态强持有 Activity、View 或短生命周期 controller。
- 反射、DexKit、磁盘 I/O 和同步 Binder 留在冷路径。
- 热路径只读已准备好的不可变或原子状态。
- 缓存有界、按 ClassLoader 隔离。

## 5. JVM

- 构建运行时为 JDK 25。`JAVA_HOME` 指向 JDK 根目录，不得指向 `bin`。
- Android 产物 `sourceCompatibility` / `targetCompatibility` 保持 17。
- 不得为了绕过环境错误降级到 JDK 17。
- 默认保留 Java：`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java`。
- Java→Kotlin 必须小批量、行为等价，并与功能变化分开。

## 6. 验证

开发中：

```powershell
python tools/verify.py fast --changed
```

收口：

```powershell
python tools/verify.py full
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
```

工具目录改动时再跑 Python 工具测试。失败在原任务内修复。不得通过删测试、降断言或吞异常制造通过。

## 7. 构建与发布

- Debug / develop 构建不得冒充正式版。
- 正式 Release 仅在用户明确要求、仓库外签名配置有效时执行。
- 禁止提交 APK、keystore、密码或本地签名配置。
- GitHub Actions 不得硬编码本机路径，不得使用正式 keystore。
- ROM 样本、trace、mapping、profiler 数据不得入库。
- 版本名与 `CHANGELOG` 必须同步；`versionCode` 必须单调增加。

## 8. ROM 证据

- 静态扫描不能替代目标 HyperOS ROM 实机验证。
- 无实机证据不得修改成熟热路径基础设施。
- 候选缺陷必须可复现，并有内存 / 线程 / 日志 / CPU 证据。
- “这里可以 cache” 不构成缺陷。
