#include <jni.h>
#include <android/log.h>
#include "VulkanEngine.h"
#include <vector>
#include <string>
#include <cstring>

#define LOG_TAG "VulkanJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* g_jvm = nullptr;

// ── Native method implementations ──

static jlong nativeInit(JNIEnv* env, jobject thiz, jobject shaderDataMap) {
    std::map<std::string, std::vector<uint8_t>> shaderMap;

    jclass mapClass = env->FindClass("java/util/Map");
    jmethodID entrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
    jobject entrySet = env->CallObjectMethod(shaderDataMap, entrySetMethod);

    jclass setClass = env->FindClass("java/util/Set");
    jmethodID iteratorMethod = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
    jobject iterator = env->CallObjectMethod(entrySet, iteratorMethod);

    jclass iteratorClass = env->FindClass("java/util/Iterator");
    jmethodID hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
    jmethodID nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");

    jclass entryClass = env->FindClass("java/util/Map$Entry");
    jmethodID getKeyMethod = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
    jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

    while (env->CallBooleanMethod(iterator, hasNextMethod)) {
        jobject entry = env->CallObjectMethod(iterator, nextMethod);
        jstring key = (jstring)env->CallObjectMethod(entry, getKeyMethod);
        jbyteArray value = (jbyteArray)env->CallObjectMethod(entry, getValueMethod);

        const char* keyStr = env->GetStringUTFChars(key, nullptr);
        jbyte* data = env->GetByteArrayElements(value, nullptr);
        jsize len = env->GetArrayLength(value);

        shaderMap[keyStr] = std::vector<uint8_t>(data, data + len);

        env->ReleaseByteArrayElements(value, data, JNI_ABORT);
        env->ReleaseStringUTFChars(key, keyStr);
        env->DeleteLocalRef(entry);
        env->DeleteLocalRef(key);
        env->DeleteLocalRef(value);
    }

    auto* engine = new VulkanEngine();
    if (!engine->init(shaderMap)) {
        LOGE("Vulkan engine init failed");
        delete engine;
        return 0;
    }
    LOGI("Vulkan engine initialized");
    return reinterpret_cast<jlong>(engine);
}

static void nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<VulkanEngine*>(handle);
    if (engine) {
        engine->destroy();
        delete engine;
    }
}

static jboolean nativeIsAvailable(JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<VulkanEngine*>(handle);
    return engine != nullptr && engine->isAvailable();
}

static void nativeUploadLut(JNIEnv* env, jobject thiz, jlong handle,
                             jfloatArray lutData, jint lutSize, jint lutSlot) {
    auto* engine = reinterpret_cast<VulkanEngine*>(handle);
    if (!engine || !engine->isAvailable()) return;
    jfloat* data = env->GetFloatArrayElements(lutData, nullptr);
    engine->uploadLut(reinterpret_cast<const float*>(data),
                      static_cast<uint32_t>(lutSize),
                      static_cast<int>(lutSlot));
    env->ReleaseFloatArrayElements(lutData, data, JNI_ABORT);
}

// nativeProcessImage:
//   imageData: ByteArray (RGBA8, length = width * height * 4)
//   params:    FloatArray (24 floats: filter params, width/height from JNI args)
//   Returns:   ByteArray (RGBA8 result) or null on failure
static jbyteArray nativeProcessImage(JNIEnv* env, jobject thiz, jlong handle,
                                      jbyteArray imageData, jint width, jint height,
                                      jfloatArray params) {
    auto* engine = reinterpret_cast<VulkanEngine*>(handle);
    if (!engine || !engine->isAvailable()) return nullptr;

    // Get input RGBA8 bytes
    jbyte* data = env->GetByteArrayElements(imageData, nullptr);
    jfloat* paramArr = env->GetFloatArrayElements(params, nullptr);

    // Build FilterParams from the float array
    FilterParams fp{};
    int idx = 0;
    fp.lutStrength         = paramArr[idx++];
    fp.lut2Strength        = paramArr[idx++];
    fp.lutSize             = paramArr[idx++];
    fp.lut2Size            = paramArr[idx++];
    fp.exposure            = paramArr[idx++];
    fp.contrast            = paramArr[idx++];
    fp.highlights          = paramArr[idx++];
    fp.shadows             = paramArr[idx++];
    fp.saturation          = paramArr[idx++];
    fp.colorTemperature    = paramArr[idx++];
    fp.tint                = paramArr[idx++];
    fp.vignetteStrength    = paramArr[idx++];
    fp.grainStrength       = paramArr[idx++];
    fp.grainSeed           = paramArr[idx++];
    fp.shadowThreshold     = paramArr[idx++];
    fp.highlightThreshold  = paramArr[idx++];
    fp.shadowGrainRatio    = paramArr[idx++];
    fp.midtoneGrainRatio   = paramArr[idx++];
    fp.highlightGrainRatio = paramArr[idx++];
    fp.shadowSizeRatio     = paramArr[idx++];
    fp.highlightSizeRatio  = paramArr[idx++];
    fp.enableLut           = static_cast<int>(paramArr[idx++]);
    fp.enableLut2          = static_cast<int>(paramArr[idx++]);
    fp.enableGrain         = static_cast<int>(paramArr[idx++]);
    fp.width               = static_cast<int>(width);
    fp.height              = static_cast<int>(height);
    fp._pad0               = 0.0f;
    fp._pad1               = 0.0f;

    bool success = engine->processImage(reinterpret_cast<uint8_t*>(data),
                                         static_cast<uint32_t>(width),
                                         static_cast<uint32_t>(height), fp);

    env->ReleaseFloatArrayElements(params, paramArr, JNI_ABORT);

    if (success) {
        // Create output byte array and copy result
        jsize dataSize = width * height * 4;
        jbyteArray result = env->NewByteArray(dataSize);
        env->SetByteArrayRegion(result, 0, dataSize, data);
        env->ReleaseByteArrayElements(imageData, data, JNI_ABORT);
        return result;
    } else {
        env->ReleaseByteArrayElements(imageData, data, JNI_ABORT);
        return nullptr;
    }
}

// ── Method table and JNI_OnLoad ──

static const char* VULKAN_GPU_ENGINE_CLASS = "cn/alittlecookie/lut2photo_n/gpu/VulkanGpuEngine";

static JNINativeMethod g_methods[] = {
    {"nativeInit",           "(Ljava/util/Map;)J",      (void*)nativeInit},
    {"nativeDestroy",        "(J)V",                    (void*)nativeDestroy},
    {"nativeIsAvailable",    "(J)Z",                    (void*)nativeIsAvailable},
    {"nativeUploadLut",      "(J[FII)V",                (void*)nativeUploadLut},
    {"nativeProcessImage",   "(J[BII[F)[B",             (void*)nativeProcessImage},
};

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }

    jclass cls = env->FindClass(VULKAN_GPU_ENGINE_CLASS);
    if (cls == nullptr) {
        LOGE("JNI_OnLoad: FindClass failed for %s", VULKAN_GPU_ENGINE_CLASS);
        return JNI_ERR;
    }

    int methodCount = sizeof(g_methods) / sizeof(g_methods[0]);
    if (env->RegisterNatives(cls, g_methods, methodCount) < 0) {
        LOGE("JNI_OnLoad: RegisterNatives failed");
        return JNI_ERR;
    }

    LOGI("JNI_OnLoad: registered %d native methods", methodCount);
    return JNI_VERSION_1_6;
}
