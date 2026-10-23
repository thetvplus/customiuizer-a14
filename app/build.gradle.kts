import java.util.Properties
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.WriteProperties
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import com.android.build.gradle.internal.tasks.OptimizeResourcesTask
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application)
}

abstract class GeneratePreferenceArtifacts : Exec() {
    @get:OutputDirectory
    abstract val resourcesOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val catalogOutputDirectory: DirectoryProperty
}

abstract class WriteBuildProvenance : WriteProperties() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

kotlin {
    compilerOptions {
        // Align stable language/API with compiler and stdlib 2.4.
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

val officialRelease = (project.findProperty("officialRelease")?.toString()?.toBoolean() ?: false)

val keystorePropertiesPath =
    providers.gradleProperty("customiuizerA14KeystoreProperties").orNull
        ?: providers.environmentVariable("CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES").orNull

val keystorePropertiesFile = keystorePropertiesPath?.let(::file)

val keystoreProperties = Properties()
if (officialRelease) {
    if (keystorePropertiesFile == null || !keystorePropertiesFile.isFile) {
        throw GradleException(
            "officialRelease=true but customiuizerA14KeystoreProperties property or CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES env var must point to an existing keystore.properties file"
        )
    }
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val missing = required.filter { keystoreProperties.getProperty(it).isNullOrEmpty() }
    if (missing.isNotEmpty()) {
        throw GradleException("officialRelease=true but keystore.properties is missing: ${missing.joinToString(", ")}")
    }
    val storeFileProp = keystoreProperties.getProperty("storeFile")!!
    val storeFile = file(storeFileProp)
    if (!storeFile.isFile) {
        throw GradleException("officialRelease=true but keystore file does not exist: $storeFile")
    }
}

val lastVersion = 248
val lastVersionName = "r14.25.3"

fun resolveBuildRevision(): String {
    val prop = project.findProperty("buildRevision")?.toString()
    val env = providers.environmentVariable("CUSTOMIUIZER_BUILD_REVISION").orNull
    val explicit = prop ?: env
    if (!explicit.isNullOrBlank()) {
        require(explicit.matches(Regex("^[0-9a-fA-F]{8}$"))) {
            "buildRevision must be an 8-character hex SHA, got: $explicit"
        }
        return explicit.lowercase()
    }
    if (project.findProperty("requireBuildRevision")?.toString()?.toBoolean() == true) {
        throw GradleException(
            "buildRevision must be provided via -PbuildRevision=... or CUSTOMIUIZER_BUILD_REVISION"
        )
    }
    val git = providers.exec {
        workingDir(rootDir)
        commandLine("git", "rev-parse", "--short=8", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { output ->
        output.trim().takeIf { it.matches(Regex("^[0-9a-fA-F]{8}$")) }?.lowercase()
    }.getOrNull()
    if (git == null) {
        throw GradleException("Unable to determine buildRevision from git")
    }
    return git
}

val buildRevision = resolveBuildRevision()
val supportedLocales = setOf(
    "ru-rRU",
    "zh-rCN",
    "zh-rTW",
    "ja-rJP",
    "vi-rVN",
    "cs-rCZ",
    "pt-rBR",
    "tr-rTR",
    "es-rES",
)

val preferenceSourceDir = layout.projectDirectory.dir("src/main/res/xml")
val preferenceArtifactGenerator = rootProject.layout.projectDirectory.file("tools/generate_preference_artifacts.py")
val generatedPreferenceResourcesDir = layout.buildDirectory.dir("generated/res/preference-artifacts/main")
val generatedPreferenceCatalogDir = layout.buildDirectory.dir("generated/source/preference-catalog/main")
// FeatureSpec preferenceKey declarations live here. A full-tree parity test prevents
// an out-of-package declaration from silently disappearing from the catalog.
val preferenceFeatureDir = layout.projectDirectory.dir("src/main/java/tv/withaibuild/customiuizer/mods/utils/feature")
val preferenceFeatureSources = fileTree(preferenceFeatureDir) { include("**/*.kt") }

val generatePreferenceArtifacts = tasks.register<GeneratePreferenceArtifacts>("generatePreferenceArtifacts") {
    description = "Generates lazy preference pages, search index, and current preference catalog"
    group = "build"
    inputs.file(preferenceArtifactGenerator)
    inputs.dir(preferenceSourceDir)
    inputs.files(preferenceFeatureSources).withPathSensitivity(PathSensitivity.RELATIVE)
    resourcesOutputDirectory.set(generatedPreferenceResourcesDir)
    catalogOutputDirectory.set(generatedPreferenceCatalogDir)
    commandLine(
        providers.environmentVariable("PYTHON").orElse("python").get(),
        preferenceArtifactGenerator.asFile.absolutePath,
        "--source-dir",
        preferenceSourceDir.asFile.absolutePath,
        "--output-dir",
        resourcesOutputDirectory.get().asFile.absolutePath,
        "--catalog-output",
        catalogOutputDirectory.get().asFile.absolutePath,
        "--java-dir",
        preferenceFeatureDir.asFile.absolutePath,
    )
}

android {
    namespace = "tv.withaibuild.customiuizer"
    compileSdk = 37
    // Keep CI's SDK package pin aligned with the tool actually used by AGP.
    buildToolsVersion = "36.0.0"

    signingConfigs {
        if (officialRelease) {
            create("v2") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "tv.withaibuild.customiuizer.r14"
        minSdk = 34
        //noinspection OldTargetApi,ExpiredTargetSdkVersion
        targetSdk = 34
        versionCode = lastVersion
        versionName = lastVersionName
        buildConfigField("String", "BUILD_REVISION", "\"$buildRevision\"")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    val officialSigning = if (officialRelease) signingConfigs.getByName("v2") else null
    buildTypes {
        create("develop") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            signingConfig = officialSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo {
                // Keep release APKs reproducible; the Git revision is represented by the tag.
                include = false
            }
            signingConfig = officialSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-debug"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/androidx.*.version",
                "**.kotlin_builtins",
                "**.kotlin_metadata",
                // JVM coroutine-agent replacement bytecode cannot run in an ART APK.
                "DebugProbesKt.bin",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        // These Open With samples need seekable asset FDs. Match only their names,
        // leaving licenses and other text/ZIP entries compressed.
        noCompress += listOf("test3.txt", "test4.zip")
    }
    lint {
        // Supported translations intentionally fall back to the base strings when incomplete.
        warning += "MissingTranslation"
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
            }
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

val buildProvenanceDir = layout.buildDirectory.dir("generated/assets/build-provenance")
val releaseSourceCommit = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { value ->
    value.trim().also { require(it.matches(Regex("^[0-9a-f]{40}$"))) { "Release source commit must be a full SHA" } }
}

val writeDebugBuildProvenance = tasks.register<WriteBuildProvenance>("writeDebugBuildProvenance") {
    description = "Writes build-provenance.properties for the debug variant"
    group = "build"
    outputDirectory.set(buildProvenanceDir.map { it.dir("debug") })
    destinationFile.set(outputDirectory.file("build-provenance.properties"))
    properties(
        mapOf(
            "revision" to buildRevision,
            "versionName" to lastVersionName,
            "versionCode" to lastVersion.toString(),
            "buildType" to "debug",
        )
    )
}

val writeDevelopBuildProvenance = tasks.register<WriteBuildProvenance>("writeDevelopBuildProvenance") {
    description = "Writes build-provenance.properties for the develop variant"
    group = "build"
    outputDirectory.set(buildProvenanceDir.map { it.dir("develop") })
    destinationFile.set(outputDirectory.file("build-provenance.properties"))
    properties(
        mapOf(
            "revision" to buildRevision,
            "versionName" to lastVersionName,
            "versionCode" to lastVersion.toString(),
            "buildType" to "develop",
        )
    )
}

val writeReleaseBuildProvenance = tasks.register<WriteBuildProvenance>("writeReleaseBuildProvenance") {
    description = "Writes build-provenance.properties for the release variant"
    group = "build"
    outputDirectory.set(buildProvenanceDir.map { it.dir("release") })
    destinationFile.set(outputDirectory.file("build-provenance.properties"))
    properties(
        mapOf(
            "revision" to buildRevision,
            "sourceCommit" to releaseSourceCommit,
            "versionName" to lastVersionName,
            "versionCode" to lastVersion.toString(),
            "buildType" to "release",
        )
    )
}

androidComponents {
    onVariants(selector().all()) { variant ->
        // AGP wires every consuming task (including lint and compilation) to the
        // generators through their declared outputs, without task-name matching.
        variant.sources.res!!.addGeneratedSourceDirectory(
            generatePreferenceArtifacts, GeneratePreferenceArtifacts::resourcesOutputDirectory
        )
        variant.sources.kotlin!!.addGeneratedSourceDirectory(
            generatePreferenceArtifacts, GeneratePreferenceArtifacts::catalogOutputDirectory
        )
        val writeProvenance = when (variant.name) {
            "debug" -> writeDebugBuildProvenance
            "develop" -> writeDevelopBuildProvenance
            "release" -> writeReleaseBuildProvenance
            else -> error("Build provenance is not configured for ${variant.name}")
        }
        variant.sources.assets!!.addGeneratedSourceDirectory(
            writeProvenance, WriteBuildProvenance::outputDirectory
        )
        // Standalone lint does not inherit generated-assets task dependencies.
        variant.lifecycleTasks.registerPreBuild(writeProvenance)
        variant.androidResources.localeFilters.addAll(supportedLocales)
        variant.outputs.forEach { output ->
            val suffix = when (variant.name) {
                "debug" -> "-debug"
                "release" -> if (officialRelease) "" else "-unsigned"
                "develop" -> "-${buildRevision.take(6)}" + if (officialRelease) "-develop" else "-develop-unsigned"
                else -> ""
            }
            output.outputFileName.set("CustoMIUIzer-A14-$lastVersionName$suffix.apk")
        }
    }
}

// R8's proto-to-binary conversion rebuilds the table and drops link-time compact
// encoding. Apply it to the existing optimized resource output, before APK signing.
// This uses the pinned AGP task and its own AAPT2 binary; check it when upgrading AGP.
tasks.withType<OptimizeResourcesTask>().configureEach {
    inputs.property("compactResourceEntries", true)
    doLast {
        val resourceTask = this as OptimizeResourcesTask
        val outputDir = resourceTask.optimizedProcessedRes.get().asFile
        val executableName = if (System.getProperty("os.name").startsWith("Windows")) "aapt2.exe" else "aapt2"
        val executable = resourceTask.aapt2.binaryDirectory.singleFile.resolve(executableName)
        check(executable.isFile) { "AGP AAPT2 binary not found" }
        var compacted = 0
        outputDir.walkTopDown().filter { it.isFile && (it.extension == "ap_" || it.extension == "apk") }.forEach { input ->
            val temporary = input.resolveSibling("${input.name}.compact")
            try {
                val exit = ProcessBuilder(executable.absolutePath, "optimize", "--enable-compact-entries",
                    "-o", temporary.absolutePath, input.absolutePath).inheritIO().start().waitFor()
                check(exit == 0) { "Compact resource encoding failed for ${input.name}: $exit" }
                Files.move(temporary.toPath(), input.toPath(), StandardCopyOption.REPLACE_EXISTING)
                compacted++
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        }
        check(compacted > 0) { "AGP optimized resource package not found" }
    }
}

dependencies {
    compileOnly(files("lib/framework.jar"))
    compileOnly(libs.libxposed.api)
    testImplementation(libs.libxposed.api)

    implementation(libs.libxposed.service)
    implementation(libs.commons.lang3)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.appcompat)
    constraints {
        implementation(libs.androidx.fragment.ktx) {
            because("Keep the transitive Fragment KTX companion aligned with Fragment")
        }
    }
    implementation(libs.dexkit)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
