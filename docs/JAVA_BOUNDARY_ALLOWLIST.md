# Java Boundary Allowlist

这些文件保持 Java，不继续迁移到 Kotlin。

## KEEP_JAVA_FRAMEWORK_ENTRY

| File | Reason |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` | libxposed `XposedModule` 入口。框架按类名发现并实例化。 |

## KEEP_JAVA_REFLECTION_ABI

| File | Reason |
|---|---|
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | LSPosed 派生的 GPL 反射 / Hook 核心。公开静态方法、vararg、数组、泛型和分配敏感缓存。 |

## KEEP_JAVA_VENDOR_OR_GENERATED

| File | Reason |
|---|---|
| `app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java` | Apache Commons Lang 3 第三方补丁。 |

## 已是 Kotlin 的 Installer

这些 installer 是 Kotlin `object`，并通过 `@JvmStatic` 保留 `MainModule.java` 调用点：

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
