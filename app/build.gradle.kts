import java.util.Properties
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.WriteProperties

plugins {
    alias(libs.plugins.android.application)
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

val lastVersion = 204
val lastVersionName = "r14.20.7"

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
val preferenceJavaDir = layout.projectDirectory.dir("src/main/java")

val generatePreferenceArtifacts = tasks.register<Exec>("generatePreferenceArtifacts") {
    description = "Generates lazy preference pages, search index, and current preference catalog"
    group = "build"
    inputs.file(preferenceArtifactGenerator)
    inputs.dir(preferenceSourceDir)
    inputs.dir(preferenceJavaDir)
    outputs.dir(generatedPreferenceResourcesDir)
    outputs.dir(generatedPreferenceCatalogDir)
    commandLine(
        providers.environmentVariable("PYTHON").orElse("python").get(),
        preferenceArtifactGenerator.asFile.absolutePath,
        "--source-dir",
        preferenceSourceDir.asFile.absolutePath,
        "--output-dir",
        generatedPreferenceResourcesDir.get().asFile.absolutePath,
        "--catalog-output",
        generatedPreferenceCatalogDir.get().asFile.absolutePath,
        "--java-dir",
        preferenceJavaDir.asFile.absolutePath,
    )
}

android {
    namespace = "tv.withaibuild.customiuizer"
    compileSdk = 37

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
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        // Supported translations intentionally fall back to the base strings when incomplete.
        warning += "MissingTranslation"
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
    sourceSets.getByName("main").res.directories.add(
        generatedPreferenceResourcesDir.get().asFile.absolutePath
    )
    sourceSets.getByName("main").kotlin.directories.add(
        generatedPreferenceCatalogDir.get().asFile.absolutePath
    )
}

val buildProvenanceDir = layout.buildDirectory.dir("generated/assets/build-provenance")

val writeDebugBuildProvenance = tasks.register<WriteProperties>("writeDebugBuildProvenance") {
    description = "Writes build-provenance.properties for the debug variant"
    group = "build"
    destinationFile.set(buildProvenanceDir.map { it.dir("debug").file("build-provenance.properties") })
    properties(
        mapOf(
            "revision" to buildRevision,
            "versionName" to lastVersionName,
            "versionCode" to lastVersion.toString(),
            "buildType" to "debug",
        )
    )
}

val writeDevelopBuildProvenance = tasks.register<WriteProperties>("writeDevelopBuildProvenance") {
    description = "Writes build-provenance.properties for the develop variant"
    group = "build"
    destinationFile.set(buildProvenanceDir.map { it.dir("develop").file("build-provenance.properties") })
    properties(
        mapOf(
            "revision" to buildRevision,
            "versionName" to lastVersionName,
            "versionCode" to lastVersion.toString(),
            "buildType" to "develop",
        )
    )
}

val writeReleaseBuildProvenance = tasks.register<WriteProperties>("writeReleaseBuildProvenance") {
    description = "Writes build-provenance.properties for the release variant"
    group = "build"
    destinationFile.set(buildProvenanceDir.map { it.dir("release").file("build-provenance.properties") })
    properties(
        mapOf(
            "revision" to buildRevision,
            "versionName" to lastVersionName,
            "versionCode" to lastVersion.toString(),
            "buildType" to "release",
        )
    )
}

android.sourceSets.getByName("debug").assets.directories.add(
    buildProvenanceDir.get().dir("debug").asFile.absolutePath
)
android.sourceSets.getByName("develop").assets.directories.add(
    buildProvenanceDir.get().dir("develop").asFile.absolutePath
)
android.sourceSets.getByName("release").assets.directories.add(
    buildProvenanceDir.get().dir("release").asFile.absolutePath
)

afterEvaluate {
    tasks.named("preBuild").configure {
        dependsOn(generatePreferenceArtifacts)
    }
    tasks.named("mergeDebugAssets").configure {
        dependsOn(writeDebugBuildProvenance)
    }
    tasks.named("mergeDevelopAssets").configure {
        dependsOn(writeDevelopBuildProvenance)
    }
    tasks.named("mergeReleaseAssets").configure {
        dependsOn(writeReleaseBuildProvenance)
    }

    // Lint, model and analysis tasks read the generated assets directory, so they
    // must run after the per-variant provenance file has been written.
    tasks.configureEach {
        if (name.startsWith("write") || !name.contains("Lint", ignoreCase = true)) {
            return@configureEach
        }
        val writeTask = when {
            name.contains("Debug") -> writeDebugBuildProvenance
            name.contains("Develop") -> writeDevelopBuildProvenance
            name.contains("Release") -> writeReleaseBuildProvenance
            else -> null
        }
        writeTask?.let { dependsOn(it) }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.androidResources.localeFilters.addAll(supportedLocales)
        variant.outputs.forEach { output ->
            val suffix = when (variant.name) {
                "debug" -> "-debug"
                "release" -> if (officialRelease) "" else "-unsigned"
                "develop" -> if (officialRelease) "-develop" else "-develop-unsigned"
                else -> ""
            }
            output.outputFileName.set("CustoMIUIzer-A14-$lastVersionName$suffix.apk")
        }
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
    implementation(libs.dexkit)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
}
