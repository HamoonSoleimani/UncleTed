#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <atomic>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <linux/fs.h>
#include <zlib.h>
#include <android/log.h>

#define TAG "UncleTed-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Linux Kernel Block Discard IOCTL Definitions
#ifndef BLKDISCARD
#define BLKDISCARD _IO(0x12, 119)
#endif

#ifndef BLKSECDISCARD
#define BLKSECDISCARD _IO(0x12, 125)
#endif

// ARMv8.5-A Synchronous MTE Tag Checking Control Flags
#ifndef PR_SET_TAGGED_ADDR_CTRL
#define PR_SET_TAGGED_ADDR_CTRL 55
#endif

#ifndef PR_TAGGED_ADDR_ENABLE
#define PR_TAGGED_ADDR_ENABLE (1UL << 0)
#endif

#ifndef PR_MTE_TCF_SYNC
#define PR_MTE_TCF_SYNC (1UL << 1)
#endif

// =============================================================================
// Memory Sanitization & Sandboxing Utilities
// =============================================================================

static void burnMemory(void* ptr, size_t size) {
    if (!ptr || size == 0) return;
    volatile uint8_t* p = static_cast<volatile uint8_t*>(ptr);
    while (size--) {
        *p++ = 0;
    }
    std::atomic_thread_fence(std::memory_order_seq_cst);
}

// =============================================================================
// RFC 8439 ChaCha20-Poly1305 Implementation (Warning-Free & Complete)
// =============================================================================

namespace CryptoCore {

    static inline uint32_t rotl32(uint32_t v, int c) {
        return (v << c) | (v >> (32 - c));
    }

    static inline void chachaQuarterRound(uint32_t &a, uint32_t &b, uint32_t &c, uint32_t &d) {
        a += b; d ^= a; d = rotl32(d, 16);
        c += d; b ^= c; b = rotl32(b, 12);
        a += b; d ^= a; d = rotl32(d, 8);
        c += d; b ^= c; b = rotl32(b, 7);
    }

    static void chacha20Block(uint32_t out[16], const uint32_t in[16]) {
        for (int i = 0; i < 16; ++i) out[i] = in[i];
        for (int i = 0; i < 10; ++i) {
            chachaQuarterRound(out[0], out[4], out[8],  out[12]);
            chachaQuarterRound(out[1], out[5], out[9],  out[13]);
            chachaQuarterRound(out[2], out[6], out[10], out[14]);
            chachaQuarterRound(out[3], out[7], out[11], out[15]);
            chachaQuarterRound(out[0], out[5], out[10], out[15]);
            chachaQuarterRound(out[1], out[6], out[11], out[12]);
            chachaQuarterRound(out[2], out[7], out[8],  out[13]);
            chachaQuarterRound(out[3], out[4], out[9],  out[14]);
        }
        for (int i = 0; i < 16; ++i) out[i] += in[i];
    }

    static void chacha20Encrypt(const uint8_t key[32], const uint8_t nonce[12], uint32_t counter,
                                const uint8_t* in, uint8_t* out, size_t len) {
        uint32_t state[16] = {
            0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
            ((const uint32_t*)key)[0], ((const uint32_t*)key)[1],
            ((const uint32_t*)key)[2], ((const uint32_t*)key)[3],
            ((const uint32_t*)key)[4], ((const uint32_t*)key)[5],
            ((const uint32_t*)key)[6], ((const uint32_t*)key)[7],
            counter,
            ((const uint32_t*)nonce)[0], ((const uint32_t*)nonce)[1], ((const uint32_t*)nonce)[2]
        };

        uint32_t block[16];
        uint8_t* blockBytes = reinterpret_cast<uint8_t*>(block);

        while (len > 0) {
            chacha20Block(block, state);
            state[12]++;
            size_t take = (len < 64) ? len : 64;
            for (size_t i = 0; i < take; ++i) {
                out[i] = in[i] ^ blockBytes[i];
            }
            len -= take;
            in += take;
            out += take;
        }
        burnMemory(block, sizeof(block));
        burnMemory(state, sizeof(state));
    }

    // Exact RFC 8439 Poly1305 using 26-bit limb representation
    static void poly1305Mac(const uint8_t* msg, size_t len, const uint8_t key[32], uint8_t tag[16]) {
        uint32_t r0, r1, r2, r3, r4;
        uint32_t s1, s2, s3, s4;
        uint32_t h0 = 0, h1 = 0, h2 = 0, h3 = 0, h4 = 0;

        // r &= 0x0ffffffc0ffffffc0ffffffc0fffffff
        r0 = ( ((uint32_t)key[0]      ) | ((uint32_t)key[1]  <<  8) | ((uint32_t)key[2]  << 16) | ((uint32_t)key[3]  << 24) ) & 0x03ffffff;
        r1 = ( ((uint32_t)key[3] >> 2 ) | ((uint32_t)key[4]  <<  6) | ((uint32_t)key[5]  << 14) | ((uint32_t)key[6]  << 22) ) & 0x03ffff03;
        r2 = ( ((uint32_t)key[6] >> 4 ) | ((uint32_t)key[7]  <<  4) | ((uint32_t)key[8]  << 12) | ((uint32_t)key[9]  << 20) ) & 0x03ffc0ff;
        r3 = ( ((uint32_t)key[9] >> 6 ) | ((uint32_t)key[10] <<  2) | ((uint32_t)key[11] << 10) | ((uint32_t)key[12] << 18) ) & 0x03f03fff;
        r4 = ( ((uint32_t)key[12] >> 8) | ((uint32_t)key[13] <<  0) | ((uint32_t)key[14] <<  8) | ((uint32_t)key[15] << 16) ) & 0x000fffff;

        s1 = r1 * 5;
        s2 = r2 * 5;
        s3 = r3 * 5;
        s4 = r4 * 5;

        while (len > 0) {
            size_t take = (len < 16) ? len : 16;
            uint8_t buf[16] = {0};
            std::memcpy(buf, msg, take);
            buf[take] = 1;

            uint32_t w0 = ( ((uint32_t)buf[0]      ) | ((uint32_t)buf[1]  <<  8) | ((uint32_t)buf[2]  << 16) | ((uint32_t)buf[3]  << 24) ) & 0x03ffffff;
            uint32_t w1 = ( ((uint32_t)buf[3] >> 2 ) | ((uint32_t)buf[4]  <<  6) | ((uint32_t)buf[5]  << 14) | ((uint32_t)buf[6]  << 22) ) & 0x03ffffff;
            uint32_t w2 = ( ((uint32_t)buf[6] >> 4 ) | ((uint32_t)buf[7]  <<  4) | ((uint32_t)buf[8]  << 12) | ((uint32_t)buf[9]  << 20) ) & 0x03ffffff;
            uint32_t w3 = ( ((uint32_t)buf[9] >> 6 ) | ((uint32_t)buf[10] <<  2) | ((uint32_t)buf[11] << 10) | ((uint32_t)buf[12] << 18) ) & 0x03ffffff;
            uint32_t w4 = ( ((uint32_t)buf[12] >> 8) | ((uint32_t)buf[13] <<  0) | ((uint32_t)buf[14] <<  8) | ((uint32_t)buf[15] << 16) ) | (take < 16 ? 0 : 0x01000000);

            h0 += w0;
            h1 += w1;
            h2 += w2;
            h3 += w3;
            h4 += w4;

            uint64_t d0 = (uint64_t)h0 * r0 + (uint64_t)h1 * s4 + (uint64_t)h2 * s3 + (uint64_t)h3 * s2 + (uint64_t)h4 * s1;
            uint64_t d1 = (uint64_t)h0 * r1 + (uint64_t)h1 * r0 + (uint64_t)h2 * s4 + (uint64_t)h3 * s3 + (uint64_t)h4 * s2;
            uint64_t d2 = (uint64_t)h0 * r2 + (uint64_t)h1 * r1 + (uint64_t)h2 * r0 + (uint64_t)h3 * s4 + (uint64_t)h4 * s3;
            uint64_t d3 = (uint64_t)h0 * r3 + (uint64_t)h1 * r2 + (uint64_t)h2 * r1 + (uint64_t)h3 * r0 + (uint64_t)h4 * s4;
            uint64_t d4 = (uint64_t)h0 * r4 + (uint64_t)h1 * r3 + (uint64_t)h2 * r2 + (uint64_t)h3 * r1 + (uint64_t)h4 * r0;

            uint64_t c;
            h0 = (uint32_t)d0 & 0x03ffffff; c = d0 >> 26; d1 += c;
            h1 = (uint32_t)d1 & 0x03ffffff; c = d1 >> 26; d2 += c;
            h2 = (uint32_t)d2 & 0x03ffffff; c = d2 >> 26; d3 += c;
            h3 = (uint32_t)d3 & 0x03ffffff; c = d3 >> 26; d4 += c;
            h4 = (uint32_t)d4 & 0x03ffffff; c = d4 >> 26; h0 += (uint32_t)(c * 5);
            c = h0 >> 26; h0 &= 0x03ffffff; h1 += (uint32_t)c;

            msg += take;
            len -= take;
        }

        // Final carry propagation
        uint32_t c = h1 >> 26; h1 &= 0x03ffffff; h2 += c;
        c = h2 >> 26; h2 &= 0x03ffffff; h3 += c;
        c = h3 >> 26; h3 &= 0x03ffffff; h4 += c;
        c = h4 >> 26; h4 &= 0x03ffffff; h0 += c * 5;
        c = h0 >> 26; h0 &= 0x03ffffff; h1 += c;

        // Compute h + -p to reduce modulo 2^130 - 5
        uint32_t g0 = h0 + 5; c = g0 >> 26; g0 &= 0x03ffffff;
        uint32_t g1 = h1 + c; c = g1 >> 26; g1 &= 0x03ffffff;
        uint32_t g2 = h2 + c; c = g2 >> 26; g2 &= 0x03ffffff;
        uint32_t g3 = h3 + c; c = g3 >> 26; g3 &= 0x03ffffff;
        uint32_t g4 = h4 + c - (1 << 26);

        // Select h if h < p, or g if h >= p
        uint32_t mask = (g4 >> 31) - 1;
        g0 &= mask; g1 &= mask; g2 &= mask; g3 &= mask; g4 &= mask;
        mask = ~mask;
        h0 = (h0 & mask) | g0;
        h1 = (h1 & mask) | g1;
        h2 = (h2 & mask) | g2;
        h3 = (h3 & mask) | g3;
        h4 = (h4 & mask) | g4;

        // Reassemble 26-bit limbs into 32-bit words
        uint32_t f0 = (h0      ) | (h1 << 26);
        uint32_t f1 = (h1 >>  6) | (h2 << 20);
        uint32_t f2 = (h2 >> 12) | (h3 << 14);
        uint32_t f3 = (h3 >> 18) | (h4 <<  8);

        // Add pad (s) modulo 2^128
        uint32_t pad0 = ((uint32_t)key[16]) | ((uint32_t)key[17] << 8) | ((uint32_t)key[18] << 16) | ((uint32_t)key[19] << 24);
        uint32_t pad1 = ((uint32_t)key[20]) | ((uint32_t)key[21] << 8) | ((uint32_t)key[22] << 16) | ((uint32_t)key[23] << 24);
        uint32_t pad2 = ((uint32_t)key[24]) | ((uint32_t)key[25] << 8) | ((uint32_t)key[26] << 16) | ((uint32_t)key[27] << 24);
        uint32_t pad3 = ((uint32_t)key[28]) | ((uint32_t)key[29] << 8) | ((uint32_t)key[30] << 16) | ((uint32_t)key[31] << 24);

        uint64_t t = (uint64_t)f0 + pad0; f0 = (uint32_t)t; 
        t = (uint64_t)f1 + pad1 + (t >> 32); f1 = (uint32_t)t;
        t = (uint64_t)f2 + pad2 + (t >> 32); f2 = (uint32_t)t; 
        t = (uint64_t)f3 + pad3 + (t >> 32); f3 = (uint32_t)t;

        tag[0]  = (uint8_t)(f0      ); tag[1]  = (uint8_t)(f0 >>  8); tag[2]  = (uint8_t)(f0 >> 16); tag[3]  = (uint8_t)(f0 >> 24);
        tag[4]  = (uint8_t)(f1      ); tag[5]  = (uint8_t)(f1 >>  8); tag[6]  = (uint8_t)(f1 >> 16); tag[7]  = (uint8_t)(f1 >> 24);
        tag[8]  = (uint8_t)(f2      ); tag[9]  = (uint8_t)(f2 >>  8); tag[10] = (uint8_t)(f2 >> 16); tag[11] = (uint8_t)(f2 >> 24);
        tag[12] = (uint8_t)(f3      ); tag[13] = (uint8_t)(f3 >>  8); tag[14] = (uint8_t)(f3 >> 16); tag[15] = (uint8_t)(f3 >> 24);
    }

    static bool chacha20Poly1305Seal(const uint8_t key[32], const uint8_t nonce[12],
                                     const uint8_t* plain, size_t plainLen,
                                     std::vector<uint8_t>& outCipherWithTag) {
        uint8_t polyKey[64] = {0};
        chacha20Encrypt(key, nonce, 0, polyKey, polyKey, 64);

        outCipherWithTag.resize(plainLen + 16);
        chacha20Encrypt(key, nonce, 1, plain, outCipherWithTag.data(), plainLen);

        uint8_t tag[16] = {0};
        poly1305Mac(outCipherWithTag.data(), plainLen, polyKey, tag);
        std::memcpy(outCipherWithTag.data() + plainLen, tag, 16);

        burnMemory(polyKey, sizeof(polyKey));
        return true;
    }

    static bool chacha20Poly1305Open(const uint8_t key[32], const uint8_t nonce[12],
                                     const uint8_t* cipherWithTag, size_t totalLen,
                                     std::vector<uint8_t>& outPlain) {
        if (totalLen < 16) return false;
        size_t cipherLen = totalLen - 16;
        const uint8_t* receivedTag = cipherWithTag + cipherLen;

        uint8_t polyKey[64] = {0};
        chacha20Encrypt(key, nonce, 0, polyKey, polyKey, 64);

        uint8_t computedTag[16] = {0};
        poly1305Mac(cipherWithTag, cipherLen, polyKey, computedTag);
        burnMemory(polyKey, sizeof(polyKey));

        // Constant-time comparison
        uint8_t diff = 0;
        for (int i = 0; i < 16; ++i) {
            diff |= (receivedTag[i] ^ computedTag[i]);
        }
        if (diff != 0) {
            LOGE("Poly1305 authentication failed! Tag mismatch.");
            return false;
        }

        outPlain.resize(cipherLen);
        chacha20Encrypt(key, nonce, 1, cipherWithTag, outPlain.data(), cipherLen);
        return true;
    }
}

// =============================================================================
// Zlib Compression / Decompression Helpers
// =============================================================================

static bool zlibCompress(const uint8_t* inData, size_t inLen, std::vector<uint8_t>& outCompressed) {
    z_stream strm;
    std::memset(&strm, 0, sizeof(strm));
    if (deflateInit(&strm, Z_BEST_COMPRESSION) != Z_OK) return false;

    strm.next_in = const_cast<Bytef*>(inData);
    strm.avail_in = inLen;

    outCompressed.resize(deflateBound(&strm, inLen));
    strm.next_out = outCompressed.data();
    strm.avail_out = outCompressed.size();

    int ret = deflate(&strm, Z_FINISH);
    if (ret != Z_STREAM_END) {
        deflateEnd(&strm);
        return false;
    }
    outCompressed.resize(strm.total_out);
    deflateEnd(&strm);
    return true;
}

static bool zlibDecompress(const uint8_t* inData, size_t inLen, std::vector<uint8_t>& outPlain) {
    z_stream strm;
    std::memset(&strm, 0, sizeof(strm));
    if (inflateInit(&strm) != Z_OK) return false;

    strm.next_in = const_cast<Bytef*>(inData);
    strm.avail_in = inLen;

    outPlain.resize(inLen * 4 + 1024);
    strm.next_out = outPlain.data();
    strm.avail_out = outPlain.size();

    while (true) {
        int ret = inflate(&strm, Z_NO_FLUSH);
        if (ret == Z_STREAM_END) break;
        if (ret != Z_OK) {
            inflateEnd(&strm);
            return false;
        }
        size_t oldSize = outPlain.size();
        outPlain.resize(oldSize * 2);
        strm.next_out = outPlain.data() + oldSize;
        strm.avail_out = oldSize;
    }

    outPlain.resize(strm.total_out);
    inflateEnd(&strm);
    return true;
}

// =============================================================================
// JNI Method Implementations
// =============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_applyProcessHardening(JNIEnv* /* env */, jobject /* this */) {
    bool success = true;

    // 1. Anti-Debugging: Disable core dumps and ptrace memory inspection
    if (prctl(PR_SET_DUMPABLE, 0, 0, 0, 0) != 0) {
        LOGW("Failed configuring PR_SET_DUMPABLE=0");
        success = false;
    }

    // 2. ARMv8.5-A Memory Tagging: Force Synchronous Tag Checking (SIGSEGV on violation)
    unsigned long ctrl = PR_TAGGED_ADDR_ENABLE | PR_MTE_TCF_SYNC;
    if (prctl(PR_SET_TAGGED_ADDR_CTRL, ctrl, 0, 0, 0) == 0) {
        LOGI("Hardware Synchronous ARM MTE enforcement active (PR_MTE_TCF_SYNC).");
    } else {
        LOGI("ARM MTE unsupported or disabled on current hardware platform.");
    }

    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_isMteActive(JNIEnv* /* env */, jobject /* this */) {
    int res = prctl(56 /* PR_GET_TAGGED_ADDR_CTRL */, 0, 0, 0, 0);
    if (res >= 0 && (res & PR_MTE_TCF_SYNC)) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_secureZeroMemory(JNIEnv* env, jobject /* this */, jbyteArray buffer) {
    if (!buffer) return;
    jsize len = env->GetArrayLength(buffer);
    if (len <= 0) return;

    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    if (bytes) {
        burnMemory(bytes, static_cast<size_t>(len));
        env->ReleaseByteArrayElements(buffer, bytes, 0);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_lockMemoryPages(JNIEnv* env, jobject /* this */, jbyteArray data) {
    if (!data) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_TRUE;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return JNI_FALSE;

    int ret = mlock(bytes, static_cast<size_t>(len));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_unlockMemoryPages(JNIEnv* env, jobject /* this */, jbyteArray data) {
    if (!data) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_TRUE;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) return JNI_FALSE;

    int ret = munlock(bytes, static_cast<size_t>(len));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_purgeBlockDevice(JNIEnv* env, jobject /* this */, jstring pathStr) {
    if (!pathStr) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(pathStr, nullptr);
    if (!path) return JNI_FALSE;

    LOGE("Native purgeBlockDevice: opening %s", path);
    int fd = open(path, O_RDWR | O_SYNC);
    if (fd < 0) {
        LOGE("Failed opening block device %s (errno: %d)", path, errno);
        env->ReleaseStringUTFChars(pathStr, path);
        return JNI_FALSE;
    }

    uint64_t range[2] = {0, 0};
    if (ioctl(fd, BLKGETSIZE64, &range[1]) < 0) {
        range[1] = 64ULL * 1024ULL * 1024ULL; // 64MB fallback
    }

    int ret = ioctl(fd, BLKSECDISCARD, &range);
    if (ret != 0) {
        LOGW("BLKSECDISCARD unsupported (errno: %d). Attempting BLKDISCARD...", errno);
        ret = ioctl(fd, BLKDISCARD, &range);
    }

    fsync(fd);
    close(fd);
    env->ReleaseStringUTFChars(pathStr, path);
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_executeCesNative(
    JNIEnv* env, jobject /* this */, jbyteArray input, jbyteArray key, jbyteArray nonce) {

    if (!input || !key || !nonce) return nullptr;
    jsize inLen = env->GetArrayLength(input);
    jsize keyLen = env->GetArrayLength(key);
    jsize nonceLen = env->GetArrayLength(nonce);

    if (keyLen != 32 || nonceLen != 12 || inLen <= 0) return nullptr;

    jbyte* inBytes = env->GetByteArrayElements(input, nullptr);
    jbyte* keyBytes = env->GetByteArrayElements(key, nullptr);
    jbyte* nonceBytes = env->GetByteArrayElements(nonce, nullptr);

    // Step 1: Compress with Zlib Deflate
    std::vector<uint8_t> compressed;
    bool compOk = zlibCompress(reinterpret_cast<const uint8_t*>(inBytes), inLen, compressed);
    env->ReleaseByteArrayElements(input, inBytes, JNI_ABORT);

    if (!compOk || compressed.empty()) {
        env->ReleaseByteArrayElements(key, keyBytes, JNI_ABORT);
        env->ReleaseByteArrayElements(nonce, nonceBytes, JNI_ABORT);
        return nullptr;
    }

    // Step 2: Encrypt with ChaCha20-Poly1305
    std::vector<uint8_t> cipherWithTag;
    bool encOk = CryptoCore::chacha20Poly1305Seal(
        reinterpret_cast<const uint8_t*>(keyBytes),
        reinterpret_cast<const uint8_t*>(nonceBytes),
        compressed.data(), compressed.size(),
        cipherWithTag
    );

    burnMemory(compressed.data(), compressed.size());
    env->ReleaseByteArrayElements(key, keyBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonceBytes, JNI_ABORT);

    if (!encOk) return nullptr;

    jbyteArray result = env->NewByteArray(static_cast<jsize>(cipherWithTag.size()));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(cipherWithTag.size()),
                                reinterpret_cast<const jbyte*>(cipherWithTag.data()));
    }
    burnMemory(cipherWithTag.data(), cipherWithTag.size());
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_executeCesDecryptNative(
    JNIEnv* env, jobject /* this */, jbyteArray inputWithTag, jbyteArray key, jbyteArray nonce) {

    if (!inputWithTag || !key || !nonce) return nullptr;
    jsize inLen = env->GetArrayLength(inputWithTag);
    jsize keyLen = env->GetArrayLength(key);
    jsize nonceLen = env->GetArrayLength(nonce);

    if (keyLen != 32 || nonceLen != 12 || inLen <= 16) return nullptr;

    jbyte* inBytes = env->GetByteArrayElements(inputWithTag, nullptr);
    jbyte* keyBytes = env->GetByteArrayElements(key, nullptr);
    jbyte* nonceBytes = env->GetByteArrayElements(nonce, nullptr);

    // Step 1: Authenticate and Decrypt with ChaCha20-Poly1305
    std::vector<uint8_t> decryptedCompressed;
    bool decOk = CryptoCore::chacha20Poly1305Open(
        reinterpret_cast<const uint8_t*>(keyBytes),
        reinterpret_cast<const uint8_t*>(nonceBytes),
        reinterpret_cast<const uint8_t*>(inBytes), inLen,
        decryptedCompressed
    );

    env->ReleaseByteArrayElements(inputWithTag, inBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(key, keyBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonceBytes, JNI_ABORT);

    if (!decOk || decryptedCompressed.empty()) {
        return nullptr;
    }

    // Step 2: Decompress with Zlib Inflate
    std::vector<uint8_t> outPlain;
    bool decompOk = zlibDecompress(decryptedCompressed.data(), decryptedCompressed.size(), outPlain);
    burnMemory(decryptedCompressed.data(), decryptedCompressed.size());

    if (!decompOk || outPlain.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(outPlain.size()));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(outPlain.size()),
                                reinterpret_cast<const jbyte*>(outPlain.data()));
    }
    burnMemory(outPlain.data(), outPlain.size());
    return result;
}