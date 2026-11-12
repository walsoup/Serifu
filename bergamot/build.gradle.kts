plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.serifu.bergamot"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("proguard-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fvisibility=hidden")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DUSE_WASM_COMPATIBLE_SOURCES=OFF",
                    "-DUSE_WASM_COMPATIBLE_SOURCE=OFF",
                    "-DCOMPILE_WASM=OFF",
                    "-DUSE_THREADS=OFF",
                    "-DSSPLIT_USE_INTERNAL_PCRE2=ON",
                    "-DSSPLIT_COMPILE_LIBRARY_ONLY=ON",
                    "-DUSE_DOXYGEN=OFF",
                    "-DCOMPILE_TESTS=OFF",
                    "-DCOMPILE_CUDA=OFF",
                    "-DCOMPILE_LIBRARY_ONLY=ON",
                    "-DUSE_MKL=OFF",
                    "-DUSE_FBGEMM=OFF",
                    // USE_INTGEMM / USE_RUY are set per-ABI in CMakeLists.txt
                    "-DCOMPILE_CPU=ON",
                    "-DGIT_SUBMODULE=OFF",
                )
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        debug {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "**/libopenblas.so",
                "**/libintgemm.so",
            )
        }
    }
}
