// AGP 9.4.1 is the latest stable and supports compileSdk 36 (max API 37). It requires Gradle 9.6.0+.
// Gradle is pinned to 9.7.0, the newest release Kotlin 2.4.21 fully supports.
// Kotlin's table lists AGP through 9.3.1 as fully tested with 2.4.21; JetBrains allows newer AGP.
// AGP 9 has built-in Kotlin: do NOT apply org.jetbrains.kotlin.android (it fails the build).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Opt-in JVM screen renders. Absent, :app:testDebugUnitTest stays the existing unit-test task.
// Named to avoid AGP's screenshotTests source-set container.
val jvmScreenRenders = providers.gradleProperty("serifu.screenshots").orNull == "true"
val screenshotSkipNative = providers.gradleProperty("serifu.skipNative").orNull == "true"

/** Local signing values from `.env`. Missing file means the release build stays on the debug key. */
fun loadDotEnv(file: java.io.File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    return file.readLines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || "=" !in line) return@mapNotNull null
        val key = line.substringBefore("=").trim()
        val value = line.substringAfter("=").trim().removeSurrounding("\"")
        key to value
    }.toMap()
}

android {
    namespace = "app.walsoup.serifu"
    // The spec pins compileSdk 36. Compose 1.12.1, Navigation 2.10.2 and core-ktx 1.19.1
    // (the October 2026 stables) require compileSdk 37. targetSdk stays 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.walsoup.serifu"
        minSdk = 30
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0-alpha.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val dotEnv = loadDotEnv(rootProject.file(".env"))
    val releaseKeystore = dotEnv["KEYSTORE_FILE"]?.let { rootProject.file(it) }?.takeIf { it.isFile }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = dotEnv["KEYSTORE_PASSWORD"]
                keyAlias = dotEnv["KEY_ALIAS"]
                keyPassword = dotEnv["KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        debug {
            if (!screenshotSkipNative) {
                ndk {
                    abiFilters += listOf("arm64-v8a", "x86_64")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (!screenshotSkipNative) {
                ndk {
                    abiFilters += "arm64-v8a"
                }
            }
        }
    }

    // Experiment: smaller sideload APK, at the cost of extracting native libraries on install.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
    }

    if (jvmScreenRenders) {
        val screenshotSrc = layout.projectDirectory.dir("src/screenshot/java").asFile.absolutePath
        sourceSets.getByName("test").java.directories.add(screenshotSrc)
        sourceSets.getByName("test").kotlin.directories.add(screenshotSrc)
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        if (jvmScreenRenders) {
            unitTests.isReturnDefaultValues = true
            unitTests.all { test ->
                if (test.name != "testDebugUnitTest") return@all
                test.maxHeapSize = "2g"
                test.systemProperty("serifu.screenshot.dir", layout.buildDirectory.dir("outputs/screenshots").get().asFile.absolutePath)
                test.systemProperty("serifu.screenshot.expected", rootProject.file(".github/screenshots/expected.txt").absolutePath)
                test.systemProperty("roborazzi.test.record", "true")
                test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                test.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                )
                test.filter.setIncludePatterns("app.walsoup.serifu.screenshots.*")
                test.filter.isFailOnNoMatchingTests = true
            }
        }
    }
}

if (jvmScreenRenders) {
    tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
        finalizedBy("writeScreenGallery")
    }
    tasks.register("writeScreenGallery") {
        doLast {
            val exit = ProcessBuilder("python3", ".github/screenshots/gallery.py", "--allow-missing")
                .directory(rootProject.projectDir)
                .inheritIO()
                .start()
                .waitFor()
            if (exit != 0) error("gallery.py exited $exit")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":linen"))
    implementation(project(":bergamot"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.ocr.ja)
    implementation(libs.mlkit.ocr.zh)
    implementation(libs.mlkit.ocr.ko)
    implementation(libs.mlkit.ocr.latin)
    implementation(libs.mlkit.translate)
    implementation(libs.zstd.jni)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    if (jvmScreenRenders) {
        testImplementation(platform(libs.compose.bom))
        testImplementation(libs.compose.ui.test.junit4)
        testImplementation(libs.androidx.test.core)
        testImplementation(libs.roborazzi)
        testImplementation(libs.roborazzi.compose)
    }
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.uiautomator)
}
