cmake_minimum_required(VERSION 3.22.1)
project(bergamot_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(USE_WASM_COMPATIBLE_SOURCES OFF CACHE BOOL "" FORCE)
set(USE_WASM_COMPATIBLE_SOURCE OFF CACHE BOOL "" FORCE)
set(COMPILE_WASM OFF CACHE BOOL "" FORCE)
set(USE_THREADS OFF CACHE BOOL "" FORCE)
set(USE_DOXYGEN OFF CACHE BOOL "" FORCE)
set(COMPILE_TESTS OFF CACHE BOOL "" FORCE)
set(COMPILE_CUDA OFF CACHE BOOL "" FORCE)
set(COMPILE_LIBRARY_ONLY ON CACHE BOOL "" FORCE)
set(SSPLIT_COMPILE_LIBRARY_ONLY ON CACHE BOOL "" FORCE)
set(SSPLIT_USE_INTERNAL_PCRE2 ON CACHE BOOL "" FORCE)
set(USE_MKL OFF CACHE BOOL "" FORCE)
set(USE_FBGEMM OFF CACHE BOOL "" FORCE)
set(COMPILE_CPU ON CACHE BOOL "" FORCE)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(GIT_SUBMODULE OFF CACHE BOOL "" FORCE)
set(COMPILE_BERT OFF CACHE BOOL "" FORCE)

# NDK FindThreads often fails during host-side configure; force pthreads.
set(CMAKE_THREAD_LIBS_INIT "-pthread")
set(CMAKE_HAVE_THREADS_LIBRARY 1)
set(CMAKE_USE_WIN32_THREADS_INIT 0)
set(CMAKE_USE_PTHREADS_INIT 1)
set(THREADS_PREFER_PTHREAD_FLAG ON)

# Marian defaults BUILD_ARCH=native (-march=native), which NDK clang rejects.
# ARM uses Ruy (+sse2neon); x86_64 uses intgemm — matching Firefox on-device.
if(ANDROID)
  if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(BUILD_ARCH "armv8-a" CACHE STRING "Compile for this CPU architecture." FORCE)
    set(USE_INTGEMM OFF CACHE BOOL "" FORCE)
    set(USE_RUY ON CACHE BOOL "" FORCE)
    set(USE_RUY_SGEMM ON CACHE BOOL "" FORCE)
    set(USE_SIMD_UTILS ON CACHE BOOL "" FORCE)
  elseif(ANDROID_ABI STREQUAL "x86_64")
    set(BUILD_ARCH "x86-64" CACHE STRING "Compile for this CPU architecture." FORCE)
    set(USE_INTGEMM ON CACHE BOOL "" FORCE)
    add_compile_options(-msse4.2 -include immintrin.h)
  else()
    message(FATAL_ERROR "Unsupported ANDROID_ABI=${ANDROID_ABI}")
  endif()
endif()

# bergamot/src/main/cpp → repo root is four levels up.
set(BERGAMOT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/bergamot-translator")

add_subdirectory("${BERGAMOT_ROOT}" "${CMAKE_BINARY_DIR}/bergamot-translator")

add_library(bergamot_jni SHARED bergamot_jni.cpp)

target_include_directories(bergamot_jni PRIVATE
    "${BERGAMOT_ROOT}/src"
)

target_link_libraries(bergamot_jni
    bergamot-translator
    android
    log
)

target_compile_options(bergamot_jni PRIVATE -O3 -fvisibility=hidden)
