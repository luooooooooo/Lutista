#include <jni.h>

// Stub JNI bridge for non-arm64 ABIs

static jlong nativeInit(JNIEnv* env, jobject thiz, jobject shaderDataMap) { return 0; }
static void nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {}
static jboolean nativeIsAvailable(JNIEnv* env, jobject thiz, jlong handle) { return JNI_FALSE; }
static void nativeUploadLut(JNIEnv* env, jobject thiz, jlong handle, jfloatArray lutData, jint lutSize, jint lutSlot) {}
static void nativeUploadCurves(JNIEnv* env, jobject thiz, jlong handle, jfloatArray curveData) {}
static jbyteArray nativeProcessImage(JNIEnv* env, jobject thiz, jlong handle, jbyteArray imageData, jint width, jint height, jfloatArray params) { return nullptr; }

static const char* VULKAN_GPU_ENGINE_CLASS = "cn/alittlecookie/lutista/gpu/VulkanGpuEngine";

static JNINativeMethod g_methods[] = {
    {"nativeInit",           "(Ljava/util/Map;)J",    (void*)nativeInit},
    {"nativeDestroy",        "(J)V",                  (void*)nativeDestroy},
    {"nativeIsAvailable",    "(J)Z",                  (void*)nativeIsAvailable},
    {"nativeUploadLut",      "(J[FII)V",              (void*)nativeUploadLut},
    {"nativeUploadCurves",   "(J[F)V",                (void*)nativeUploadCurves},
    {"nativeProcessImage",   "(J[BII[F)[B",           (void*)nativeProcessImage},
};

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass cls = env->FindClass(VULKAN_GPU_ENGINE_CLASS);
    if (cls == nullptr) return JNI_ERR;
    env->RegisterNatives(cls, g_methods, sizeof(g_methods) / sizeof(g_methods[0]));
    return JNI_VERSION_1_6;
}
