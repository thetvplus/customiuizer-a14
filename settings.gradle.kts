pluginManagement {
    val useChinaMirrors = settings.providers.gradleProperty("useChinaMirrors")
        .map { it == "true" }
        .getOrElse(false)

    repositories {
        if (useChinaMirrors) {
            // Aliyun Gradle plugin mirror: Gradle plugins only (Android / Kotlin).
            maven("https://maven.aliyun.com/repository/gradle-plugin/") {
                content {
                    includeGroupByRegex("""com\.android\..*""")
                    includeGroupByRegex("""org\.jetbrains\..*""")
                }
            }
            // Huawei Maven mirror: everything else that normally comes from Maven Central.
            maven("https://mirrors.huaweicloud.com/repository/maven/") {
                content {
                    includeGroupByRegex(""".*""")
                }
            }
        } else {
            // Official repositories only. This is the default for non-China builds.
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val useChinaMirrors = settings.providers.gradleProperty("useChinaMirrors")
        .map { it == "true" }
        .getOrElse(false)

    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Stable libxposed artifacts must have the same upstream in both modes.
        exclusiveContent {
            forRepository {
                mavenCentral { name = "LibXposedCentral" }
            }
            filter { includeGroup("io.github.libxposed") }
        }

        if (useChinaMirrors) {
            // Aliyun Google mirror: AndroidX / Google / core Android artifacts.
            maven("https://maven.aliyun.com/repository/google/") {
                content {
                    includeGroupByRegex("""com\.android\..*""")
                    includeGroupByRegex("""androidx\..*""")
                    includeGroupByRegex("""com\.google\..*""")
                }
            }
            // Huawei Maven mirror: the rest of Maven Central.
            maven("https://mirrors.huaweicloud.com/repository/maven/") {
                content {
                    excludeGroup("io.github.libxposed")
                    includeGroupByRegex(""".*""")
                }
            }
        } else {
            // Official repositories only. This is the default for non-China builds.
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "CustoMIUIzer-A14"
include(":app")
