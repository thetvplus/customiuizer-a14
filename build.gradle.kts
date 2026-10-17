import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm

plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    toolchainDownloadUrls.empty()
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
