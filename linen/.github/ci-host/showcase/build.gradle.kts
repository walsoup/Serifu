plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.walsoup.linen.showcase"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.walsoup.linen.showcase"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
  }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(project(":design"))
  implementation("androidx.activity:activity-compose:1.13.0")
}
