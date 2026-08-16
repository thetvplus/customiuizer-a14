# Java Boundary Allowlist

A14 保留的 Java 源文件边界。这些文件在 `P9` 之后保持 Java，不继续迁移到
Kotlin。

## 保留原因

### KEEP_JAVA_FRAMEWORK_ENTRY

| File | Reason |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` | libxposed `XposedModule` 入口点。框架按类名发现并实例化，迁到 Kotlin 会改变编译期类/签名，需要同步更新 Xposed manifest。保持 Java。 |

### KEEP_JAVA_REFLECTION_ABI

| File | Reason |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | LSPosed 派生的 GPL 反射/Hooking 核心。公开大量静态方法、vararg、数组、泛型和 nullability ABI，以及分配敏感的反射缓存。迁到 Kotlin 需要以 `@JvmStatic`、`@JvmOverloads`、`@JvmName`、`@Throws` 等完整保留，收益不明确。保持为稳定的 Java 反射 ABI 边界。 |

### KEEP_JAVA_VENDOR_OR_GENERATED

| File | Reason |
|---|---|
| `app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java` | Apache Commons Lang 3 第三方补丁，非项目自有代码，不参与迁移。 |

## 已迁移为 `.kt` 的 Installer

以下 installer 已转为 Kotlin `object` 并通过 `@JvmStatic` 保留 `MainModule.java`
中的 Java 调用点：

- `app/src/main/java/tv/withaibuild/customiuizer/installers/AndroidPackageInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/GuardProviderInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/InputMethodInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/MediaInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PermissionControllerInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PhoneInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PowerKeeperInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SettingsInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.kt`

## 当前状态

```text
MainModule.java          -> KEEP_JAVA_FRAMEWORK_ENTRY
XposedHelpers.java       -> KEEP_JAVA_REFLECTION_ABI
MemberUtilsX.java        -> KEEP_JAVA_VENDOR_OR_GENERATED
```

无 `MIGRATE_TO_KOTLIN`、`KEEP_JAVA_TEMPORARY_BLOCKER` 或 `UNCLASSIFIED` 项。
