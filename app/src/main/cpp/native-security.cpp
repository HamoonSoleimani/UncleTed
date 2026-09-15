#include <jni.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <cstring>
#include <android/log.h>

#define TAG "UncleTed-NativeSecurity"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#ifndef PR_SET_TAGGED_ADDR_CTRL
#define PR_SET_TAGGED_ADDR_CTRL 55
#endif

#ifndef PR_GET_TAGGED_ADDR_CTRL
#define PR_GET_TAGGED_ADDR_CTRL 56
#endif

#ifndef PR_TAGGED_ADDR_ENABLE
#define PR_TAGGED_ADDR_ENABLE (1UL << 0)
#endif

#ifndef PR_MTE_TCF_SHIFT
#define PR_MTE_TCF_SHIFT 1
#endif

#ifndef PR_MTE_TCF_SYNC
#define PR_MTE_TCF_SYNC (1UL << PR_MTE_TCF_SHIFT)
#endif

#ifndef PR_MTE_TAG_SHIFT
#define PR_MTE_TAG_SHIFT 3
#endif

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_applyProcessHardening(
    JNIEnv* env,
    jobject /* this */
) {
    // 1. Disable process dumpable flag to prevent /proc/$PID/mem exfiltration and unprivileged ptrace
    if (prctl(PR_SET_DUMPABLE, 0) != 0) {
        LOGE("Failed enforcing PR_SET_DUMPABLE=0");
        return JNI_FALSE;
    }
    LOGI("Process hardened: PR_SET_DUMPABLE=0 enforced.");

    // 2. Enforce Synchronous MTE (Memory Tagging Extension) mode in userspace if hardware-supported
    unsigned long mask = PR_TAGGED_ADDR_ENABLE | PR_MTE_TCF_SYNC | (0xfffe << PR_MTE_TAG_SHIFT);
    if (prctl(PR_SET_TAGGED_ADDR_CTRL, mask, 0, 0, 0) == 0) {
        LOGI("Hardware Memory Tagging (MTE) enforced in SYNCHRONOUS mode.");
    } else {
        LOGI("Hardware MTE not supported by kernel/silicon; skipping MTE control prctl.");
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_isMteActive(
    JNIEnv* env,
    jobject /* this */
) {
    int ret = prctl(PR_GET_TAGGED_ADDR_CTRL, 0, 0, 0, 0);
    if (ret < 0) {
        return JNI_FALSE;
    }
    if ((ret & PR_MTE_TCF_SYNC) != 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_secureZeroMemory(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray buffer
) {
    if (!buffer) return;

    jsize len = env->GetArrayLength(buffer);
    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);

    if (bytes != nullptr) {
        // Volatile pointer loop to prevent compiler dead-store elimination (DSE)
        volatile unsigned char* p = reinterpret_cast<volatile unsigned char*>(bytes);
        for (jsize i = 0; i < len; ++i) {
            p[i] = 0;
        }
        env->ReleaseByteArrayElements(buffer, bytes, 0);
    }
}

} // extern "C"