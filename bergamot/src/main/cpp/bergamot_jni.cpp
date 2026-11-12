#include <jni.h>
#include <android/log.h>

#include <exception>
#include <memory>
#include <string>
#include <vector>

#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "translator/translation_model.h"

using marian::bergamot::BlockingService;
using marian::bergamot::Response;
using marian::bergamot::ResponseOptions;
using marian::bergamot::TranslationModel;
using marian::bergamot::parseOptionsFromString;

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "BergamotNative", __VA_ARGS__)

namespace {

struct ModelHandle {
    std::shared_ptr<TranslationModel> model;
};

BlockingService& sharedService() {
    static BlockingService service(BlockingService::Config{});
    return service;
}

ResponseOptions defaultOptions() {
    ResponseOptions options;
    options.qualityScores = false;
    options.alignment = false;
    options.HTML = false;
    options.sentenceMappings = false;
    return options;
}

void throwRuntime(JNIEnv* env, const char* message) {
    if (env->ExceptionCheck()) {
        return;
    }
    jclass clazz = env->FindClass("java/lang/RuntimeException");
    if (clazz == nullptr) {
        return;
    }
    env->ThrowNew(clazz, message != nullptr ? message : "Bergamot translation failed");
    env->DeleteLocalRef(clazz);
}

std::vector<std::string> readStrings(JNIEnv* env, jobjectArray input) {
    const jsize count = env->GetArrayLength(input);
    std::vector<std::string> out;
    out.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto value = reinterpret_cast<jstring>(env->GetObjectArrayElement(input, i));
        if (value == nullptr) {
            out.emplace_back();
            continue;
        }
        const char* utf = env->GetStringUTFChars(value, nullptr);
        if (utf == nullptr) {
            // CheckJNI aborts if ReleaseStringUTFChars is called with a null pointer.
            env->DeleteLocalRef(value);
            return {};
        }
        out.emplace_back(utf);
        env->ReleaseStringUTFChars(value, utf);
        env->DeleteLocalRef(value);
    }
    return out;
}

jobjectArray toStringArray(JNIEnv* env, const std::vector<Response>& responses) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(responses.size()), stringClass, nullptr);
    for (size_t i = 0; i < responses.size(); ++i) {
        jstring value = env->NewStringUTF(responses[i].target.text.c_str());
        env->SetObjectArrayElement(out, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return out;
}

ModelHandle* asHandle(jlong handle) {
    return reinterpret_cast<ModelHandle*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_serifu_bergamot_BergamotNative_nativeLoad(
    JNIEnv* env,
    jclass,
    jstring configYaml
) {
    if (configYaml == nullptr) {
        return 0;
    }
    const char* yamlChars = env->GetStringUTFChars(configYaml, nullptr);
    if (yamlChars == nullptr) {
        return 0;
    }
    std::string yaml(yamlChars);
    env->ReleaseStringUTFChars(configYaml, yamlChars);

    try {
        auto options = parseOptionsFromString(yaml, /*validate=*/false);
        auto handle = std::make_unique<ModelHandle>();
        handle->model = std::make_shared<TranslationModel>(options, /*replicas=*/1);
        return reinterpret_cast<jlong>(handle.release());
    } catch (const std::exception& e) {
        LOGE("nativeLoad failed: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("nativeLoad failed: unknown C++ exception");
        return 0;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_serifu_bergamot_BergamotNative_nativeTranslate(
    JNIEnv* env,
    jclass,
    jlong handle,
    jobjectArray texts
) {
    auto* modelHandle = asHandle(handle);
    if (modelHandle == nullptr || modelHandle->model == nullptr || texts == nullptr) {
        throwRuntime(env, "Bergamot model is not loaded");
        return nullptr;
    }
    try {
        auto sources = readStrings(env, texts);
        if (env->ExceptionCheck()) {
            return nullptr;
        }
        std::vector<ResponseOptions> options(sources.size(), defaultOptions());
        auto responses = sharedService().translateMultiple(
            modelHandle->model,
            std::move(sources),
            options
        );
        return toStringArray(env, responses);
    } catch (const std::exception& error) {
        LOGE("nativeTranslate failed: %s", error.what());
        throwRuntime(env, error.what());
        return nullptr;
    } catch (...) {
        LOGE("nativeTranslate failed: unknown exception");
        throwRuntime(env, "Bergamot translation failed");
        return nullptr;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_app_serifu_bergamot_BergamotNative_nativeTranslatePivot(
    JNIEnv* env,
    jclass,
    jlong firstHandle,
    jlong secondHandle,
    jobjectArray texts
) {
    auto* first = asHandle(firstHandle);
    auto* second = asHandle(secondHandle);
    if (first == nullptr || second == nullptr ||
        first->model == nullptr || second->model == nullptr ||
        texts == nullptr) {
        throwRuntime(env, "Bergamot pivot model is not loaded");
        return nullptr;
    }
    try {
        auto sources = readStrings(env, texts);
        if (env->ExceptionCheck()) {
            return nullptr;
        }
        std::vector<ResponseOptions> options(sources.size(), defaultOptions());
        auto responses = sharedService().pivotMultiple(
            first->model,
            second->model,
            std::move(sources),
            options
        );
        return toStringArray(env, responses);
    } catch (const std::exception& error) {
        LOGE("nativeTranslatePivot failed: %s", error.what());
        throwRuntime(env, error.what());
        return nullptr;
    } catch (...) {
        LOGE("nativeTranslatePivot failed: unknown exception");
        throwRuntime(env, "Bergamot pivot translation failed");
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_app_serifu_bergamot_BergamotNative_nativeFree(
    JNIEnv*,
    jclass,
    jlong handle
) {
    delete asHandle(handle);
}
