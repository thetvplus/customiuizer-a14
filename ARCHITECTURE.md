# A14 架构

当前 production 运行时，不是阶段历史。

## 调用链

```text
LSPosed / libxposed
  → MainModule
  → PreferenceBootstrap
  → ProcessRouter
  → Installer
  → FeatureSpec / FeatureInstallRegistry
  → ROM contract / resolver
  → Hook / Controller
  → owned runtime state
```

`MainModule` 在 `onPackageReady` / `onSystemServerStarting` 中按包名分发。
`PreferenceBootstrap` 准备进程内偏好快照。快照未就绪时不安装业务 Hook。
`ProcessRouter` 把包名和进程名解析成 `ProcessScope`。
各 `*Installer` 只安装本进程相关 Feature。
`FeatureInstallRegistry` 按稳定 Feature ID 保证每进程安装一次。
preference 更新只改状态，不重复安装已存在 Hook。

灵动额头正式路径是 ROM StrongToast 原地 reshape，加上 `DynamicIslandStatusBarFade`。
没有独立模块窗口 Host。

## 平台

- HyperOS 1 / Android 14 / SDK 34
- `minSdk=34`、`targetSdk=34`
- API 101 最低生产路径
- API 102 经能力探测启用，缺失时安全降级，不出现在 API 101 必经签名和初始化路径
- `module.prop`：`minApiVersion=101`、`targetApiVersion=102`、`staticScope=true`
- 动态应用作用域只在支持的 LSPosed API 上按需请求

## 生命周期与失败

- 关闭功能不创建业务 Hook、Receiver、Observer 或任务。
- 注册绑定进程级或实例级所有者；stale / replace / release 路径完整。
- 不静态强持有 Activity、View 或短生命周期 controller。
- 普通异常局部隔离；`OutOfMemoryError`、`ThreadDeath`、`VirtualMachineError` 继续抛出。

## 热路径与缓存

热路径禁止磁盘 I/O、DexKit、重复反射、同步 Binder、Regex 重建、临时集合链、无界缓存和日志洪泛。
热路径只读预计算、不可变、原子或有界状态。
反射缓存按 ClassLoader 隔离且有界。

## JVM 边界

默认保留 Java：

- `MainModule.java`
- `XposedHelpers.java`
- `MemberUtilsX.java`

详见 [docs/JAVA_BOUNDARY_ALLOWLIST.md](docs/JAVA_BOUNDARY_ALLOWLIST.md)。
