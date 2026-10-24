# Java 调用边界

这些文件默认保持 Java，以保护入口发现、Java/Kotlin ABI 和第三方来源。现代语言能力不要求迁移文件；确需局部迁移时单独给出行为等价、字节码 / R8 类加载验证与回退单位，和功能变化分开。共同决策规则见 [AGENTS.md](../AGENTS.md)。

## 框架入口

| File | Reason |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` | libxposed `XposedModule` 入口。框架按类名发现并实例化。 |

## 反射与调用契约

| File | Reason |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | LSPosed 派生的 GPL 反射 / Hook 核心。公开静态方法、vararg、数组、泛型和分配敏感缓存。 |

## 第三方补丁

| File | Reason |
|---|---|
| `app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java` | Apache Commons Lang 3 第三方补丁。 |

## Kotlin Installer 的 Java 入口

当前 [Installer](../app/src/main/java/tv/withaibuild/customiuizer/installers/) 使用 Kotlin `object`；`MainModule.java` 的既有静态调用依靠 `@JvmStatic` 保持 ABI。具体成员以源码为准，不另复制易过期的 Installer 文件清单。
