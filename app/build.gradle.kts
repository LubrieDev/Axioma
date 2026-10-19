import java.security.MessageDigest

plugins {
    id("com.android.application")
}

val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val selectedAbis = providers.gradleProperty("axiomaAbis").orElse("arm64-v8a,x86_64")
    .get().split(',').map { it.trim() }
require(selectedAbis.isNotEmpty() && selectedAbis.distinct().size == selectedAbis.size
    && selectedAbis.all { it in supportedAbis }) { "Unsupported or duplicate axiomaAbis." }
val buildId = providers.gradleProperty("axiomaBuildId").orElse("default").get()
val logicalBuildIds = supportedAbis + listOf("default", "universal", "tests")
require(buildId in logicalBuildIds + logicalBuildIds.map { "release-$it" }) { "Invalid axiomaBuildId." }
val testVariant = providers.gradleProperty("axiomaTestBuildType").orElse("debug").get()
require(testVariant in listOf("debug", "release")) { "Invalid axiomaTestBuildType." }
val releaseStore = providers.environmentVariable("AXIOMA_KEYSTORE_PATH").orNull
layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("artifacts/android/.build/$buildId/app"))

android {
    namespace = "org.axioma.calculator"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.2.13676358"
    testBuildType = testVariant

    defaultConfig {
        applicationId = "org.axioma.calculator"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.0"
        testInstrumentationRunner = "org.axioma.calculator.SmokeInstrumentation"

        ndk {
            abiFilters += selectedAbis
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-Wall", "-Wextra", "-Werror")
            }
        }
    }

    signingConfigs {
        if (releaseStore != null) {
            create("permanentRelease") {
                storeFile = file(releaseStore)
                storePassword = providers.environmentVariable("AXIOMA_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("AXIOMA_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("AXIOMA_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isDebuggable = false
            if (releaseStore != null) { signingConfig = signingConfigs.getByName("permanentRelease") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            buildStagingDirectory = rootProject.file("artifacts/android/.build/$buildId/native")
            version = "3.22.1"
        }
    }
}

// Java/NDK classpaths stay free of external libraries. MathJax is audited as assets.
tasks.register("verifyRendererAssets") {
    group = "verification"
    description = "Verify the pinned offline MathJax files against their recorded hashes."
    doLast {
        val root = file("src/main/assets/renderer/vendor")
        val manifest = groovy.json.JsonSlurper().parse(root.resolve("manifest.json")) as Map<*, *>
        val entries = manifest["files"] as List<*>
        val expectedPaths = mutableSetOf("manifest.json")
        for (entry in entries) {
            val record = entry as Map<*, *>
            val path = record["path"] as String
            check(!path.contains("..") && !path.startsWith("/")) { "Invalid renderer asset path: $path" }
            val asset = root.resolve(path)
            check(asset.isFile) { "Missing renderer asset: $path" }
            val hash = MessageDigest.getInstance("SHA-256").digest(asset.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash == record["sha256"]) { "Renderer asset hash mismatch: $path" }
            expectedPaths.add(path)
        }
        val actualPaths = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        check(actualPaths == expectedPaths) { "Unrecorded renderer assets detected." }
        println("Offline MathJax: ${entries.size} pinned assets verified")
    }
}
tasks.named("preBuild") { dependsOn("verifyRendererAssets") }

tasks.register("verifyOwnRuntime") {
    group = "verification"
    description = "Audit native/Java dependencies and the allowed presentation assets."
    dependsOn("verifyRendererAssets")
    doLast {
        for (name in listOf("debugRuntimeClasspath", "releaseRuntimeClasspath", "${testVariant}AndroidTestRuntimeClasspath")) {
            val external = configurations.getByName(name).incoming.resolutionResult.allComponents
                .map { it.id }
                .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
            check(external.isEmpty()) { "External dependencies in $name: $external" }
            println("$name: zero external Java/NDK dependencies; MathJax presentation assets audited separately")
        }
    }
}
