plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.walsoup.linen"
  compileSdk = 37

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

composeCompiler {
  includeComposeMappingFile = false
}

dependencies {
  api(platform(libs.compose.bom))
  api(libs.compose.ui)
  api(libs.compose.material3)
  api(libs.compose.material.icons.core)
  api(libs.androidx.lifecycle.runtime.compose)
  api(libs.androidx.savedstate.compose)
  debugImplementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)
}
