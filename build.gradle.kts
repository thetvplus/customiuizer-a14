import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP's built-in Kotlin otherwise resolves KGP 2.2.10 (CVE-2026-53914).
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        // Host build tools only: these constraints do not enter the APK.
        constraints {
            add("classpath", "org.apache.commons:commons-lang3:3.20.0") {
                because("CVE-2025-48924; use the already validated Commons version")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("CVE-2024-29371")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("CVE-2021-33813")
            }
            add("classpath", "org.bouncycastle:bcprov-jdk18on:1.86") {
                because("Bouncy Castle security fixes through CVE-2026-13506")
            }
            add("classpath", "org.bouncycastle:bcpkix-jdk18on:1.86") {
                because("Keep the Bouncy Castle modules aligned")
            }
            add("classpath", "org.bouncycastle:bcutil-jdk18on:1.86") {
                because("Keep the Bouncy Castle modules aligned")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    toolchainDownloadUrls.empty()
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
