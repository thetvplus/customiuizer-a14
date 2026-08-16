# A14 架构

## 控制面

```text
User goal
  ↓
ChatGPT task contract
  ↓
Devin implementation + build
  ↓
A14 verification gates
  ↓
ChatGPT final diff review
  ↓
same-task fixes
```

## 运行时结构

```text
Preference state
      ↓
FeatureDefinition / process routing
      ↓
Installer
      ↓
HyperOS 1 / Android 14 target resolution
      ↓
Hook / Controller
      ↓
Owned registrations and prepared state
```

## Feature 生命周期

- 只在相关进程初始化；
- 功能关闭不创建业务 Hook、Receiver、Observer 或任务；
- Feature 每进程只安装一次；
- preference 更新只更新状态，不重复安装；
- 注册绑定进程级或实例级所有者；
- stale、replace、release 路径完整。

## 热路径

禁止：

- 磁盘 I/O；
- DexKit；
- 重复反射；
- 同步 Binder；
- Regex 重建；
- 临时集合链；
- 无界缓存；
- 高频格式化和日志洪泛。

热路径只读预计算、不可变、原子或有界状态。

## API 101/102

API 101 是生产最低路径。API 102 专属能力：

- 位于隔离边界；
- 通过能力探测启用；
- 不出现在 API 101 必经类型签名和初始化路径；
- 缺失时安全降级；
- 有独立测试或静态门禁。

## JVM 边界

默认保留：

- `MainModule.java`
- `XposedHelpers.java`
- `MemberUtilsX.java`

改变这些文件必须由专门任务证明 JVM、反射、R8 和框架兼容性。
