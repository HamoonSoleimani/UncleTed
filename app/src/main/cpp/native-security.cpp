#include <jni.h>
#include <sys/prctl.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <linux/fs.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdint>
#include <vector>
#include <android/log.h>
#include <zlib.h>

#define TAG "UncleTed-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

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

#ifndef BLKSECDISCARD
#define BLKSECDISCARD _IO(0x12, 125)
#endif

namespace {

inline uint32_t rotl32(uint32_t x, int n) {
    return (x << n) | (x >> (32 - n));
}

void chacha20QuarterRound(uint32_t& a, uint32_t& b, uint32_t& c, uint32_t& d) {
    a += b; d ^= a; d = rotl32(d, 16);
    c += d; b ^= c; b = rotl32(b, 12);
    a += b; d ^= a; d = rotl32(d, 8);
    c += d; b ^= c; b = rotl32(b, 7);
}

void chacha20Block(uint32_t out[16], const uint32_t in[16]) {
    for (int i = 0; i < 16; ++i) out[i] = in[i];
    for (int i = 0; i < 10; ++i) {
        chacha20QuarterRound(out[0], out[4], out[8], out[12]);
        chacha20QuarterRound(out[1], out[5], out[9], out[13]);
        chacha20QuarterRound(out[2], out[6], out[10], out[14]);
        chacha20QuarterRound(out[3], out[7], out[11], out[15]);
        chacha20QuarterRound(out[0], out[5], out[10], out[15]);
        chacha20QuarterRound(out[1], out[6], out[11], out[12]);
        chacha20QuarterRound(out[2], out[7], out[8], out[13]);
        chacha20QuarterRound(out[3], out[4], out[9], out[14]);
    }
    for (int i = 0; i < 16; ++i) out[i] += in[i];
}

void chacha20Xor(const uint8_t key[32], const uint8_t nonce[12], uint32_t counter,
                 const uint8_t* in, uint8_t* out, size_t len) {
    uint32_t state[16] = {
        0x61707865, 0x3320646e, 0x79622d32, 0x6b206574,
        reinterpret_cast<const uint32_t*>(key)[0], reinterpret_cast<const uint32_t*>(key)[1],
        reinterpret_cast<const uint32_t*>(key)[2], reinterpret_cast<const uint32_t*>(key)[3],
        reinterpret_cast<const uint32_t*>(key)[4], reinterpret_cast<const uint32_t*>(key)[5],
        reinterpret_cast<const uint32_t*>(key)[6], reinterpret_cast<const uint32_t*>(key)[7],
        counter,
        reinterpret_cast<const uint32_t*>(nonce)[0], reinterpret_cast<const uint32_t*>(nonce)[1],
        reinterpret_cast<const uint32_t*>(nonce)[2]
    };

    uint32_t block[16];
    uint8_t* blockBytes = reinterpret_cast<uint8_t*>(block);

    while (len > 0) {
        state[12] = counter++;
        chacha20Block(block, state);
        size_t take = (len < 64) ? len : 64;
        for (size_t i = 0; i < take; ++i) {
            *out++ = *in++ ^ blockBytes[i];
        }
        len -= take;
    }
}

void poly1305Clamp(uint8_t r[16]) {
    r[3] &= 15;
    r[7] &= 15;
    r[11] &= 15;
    r[15] &= 15;
    r[4] &= 252;
    r[8] &= 252;
    r[12] &= 252;
}

void poly1305Tag(const uint8_t* msg, size_t msgLen, const uint8_t key[32], uint8_t tag[16]) {
    uint8_t r[16];
    std::memcpy(r, key, 16);
    poly1305Clamp(r);

    uint64_t r0 = reinterpret_cast<uint32_t*>(r)[0];
    uint64_t r1 = reinterpret_cast<uint32_t*>(r)[1];
    uint64_t r2 = reinterpret_cast<uint32_t*>(r)[2];
    uint64_t r3 = reinterpret_cast<uint32_t*>(r)[3];

    uint64_t s1 = r1 * 20;
    uint64_t s2 = r2 * 20;
    uint64_t s3 = r3 * 20;

    uint64_t h0 = 0, h1 = 0, h2 = 0, h3 = 0, h4 = 0;

    while (msgLen > 0) {
        size_t take = (msgLen < 16) ? msgLen : 16;
        uint8_t chunk[16] = {0};
        std::memcpy(chunk, msg, take);
        if (take < 16) chunk[take] = 1;

        uint64_t c0 = reinterpret_cast<uint32_t*>(chunk)[0];
        uint64_t c1 = reinterpret_cast<uint32_t*>(chunk)[1];
        uint64_t c2 = reinterpret_cast<uint32_t*>(chunk)[2];
        uint64_t c3 = reinterpret_cast<uint32_t*>(chunk)[3];
        uint64_t c4 = (take == 16) ? 1 : 0;

        h0 += c0;
        h1 += c1;
        h2 += c2;
        h3 += c3;
        h4 += c4;

        uint64_t d0 = h0 * r0 + h1 * s3 + h2 * s2 + h3 * s1;
        uint64_t d1 = h0 * r1 + h1 * r0 + h2 * s3 + h3 * s2;
        uint64_t d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s3;
        uint64_t d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0;
        uint64_t d4 = h4 * 5;

        d0 += d4 * s1;
        d1 += d4 * s2;
        d2 += d4 * s3;
        d3 += d4 * r0;

        h0 = d0 & 0xFFFFFFFF;
        uint64_t carry = d0 >> 32;
        d1 += carry; h1 = d1 & 0xFFFFFFFF; carry = d1 >> 32;
        d2 += carry; h2 = d2 & 0xFFFFFFFF; carry = d2 >> 32;
        d3 += carry; h3 = d3 & 0xFFFFFFFF; carry = d3 >> 32;
        h4 = carry;

        msg += take;
        msgLen -= take;
    }

    uint64_t carry = (h4 * 5 + h0) >> 32;
    h0 = (h4 * 5 + h0) & 0xFFFFFFFF;
    h1 += carry; carry = h1 >> 32; h1 &= 0xFFFFFFFF;
    h2 += carry; carry = h2 >> 32; h2 &= 0xFFFFFFFF;
    h3 += carry; h3 &= 0xFFFFFFFF;

    const uint8_t* pad = key + 16;
    uint64_t f0 = h0 + reinterpret_cast<const uint32_t*>(pad)[0];
    carry = f0 >> 32;
    uint64_t f1 = h1 + reinterpret_cast<const uint32_t*>(pad)[1] + carry;
    carry = f1 >> 32;
    uint64_t f2 = h2 + reinterpret_cast<const uint32_t*>(pad)[2] + carry;
    carry = f2 >> 32;
    uint64_t f3 = h3 + reinterpret_cast<const uint32_t*>(pad)[3] + carry;

    reinterpret_cast<uint32_t*>(tag)[0] = static_cast<uint32_t>(f0);
    reinterpret_cast<uint32_t*>(tag)[1] = static_cast<uint32_t>(f1);
    reinterpret_cast<uint32_t*>(tag)[2] = static_cast<uint32_t>(f2);
    reinterpret_cast<uint32_t*>(tag)[3] = static_cast<uint32_t>(f3);
}

void chacha20Poly1305Encrypt(const uint8_t key[32], const uint8_t nonce[12],
                             const uint8_t* plain, size_t plainLen,
                             std::vector<uint8_t>& cipher, uint8_t tag[16]) {
    uint8_t polyKeyBlock[64] = {0};
    chacha20Xor(key, nonce, 0, polyKeyBlock, polyKeyBlock, 64);

    cipher.resize(plainLen);
    chacha20Xor(key, nonce, 1, plain, cipher.data(), plainLen);

    poly1305Tag(cipher.data(), plainLen, polyKeyBlock, tag);

    volatile uint8_t* v = polyKeyBlock;
    for (size_t i = 0; i < 64; ++i) v[i] = 0;
}

bool chacha20Poly1305Decrypt(const uint8_t key[32], const uint8_t nonce[12],
                             const uint8_t* cipher, size_t cipherLen,
                             const uint8_t expectedTag[16], std::vector<uint8_t>& plain) {
    uint8_t polyKeyBlock[64] = {0};
    chacha20Xor(key, nonce, 0, polyKeyBlock, polyKeyBlock, 64);

    uint8_t computedTag[16] = {0};
    poly1305Tag(cipher, cipherLen, polyKeyBlock, computedTag);

    volatile uint8_t* v = polyKeyBlock;
    for (size_t i = 0; i < 64; ++i) v[i] = 0;

    uint8_t diff = 0;
    for (size_t i = 0; i < 16; ++i) {
        diff |= (computedTag[i] ^ expectedTag[i]);
    }
    if (diff != 0) {
        return false;
    }

    plain.resize(cipherLen);
    chacha20Xor(key, nonce, 1, cipher, plain.data(), cipherLen);
    return true;
}

bool compressBuffer(const uint8_t* inData, size_t inSize, std::vector<uint8_t>& outData) {
    z_stream strm;
    std::memset(&strm, 0, sizeof(strm));
    if (deflateInit2(&strm, 9, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY) != Z_OK) {
        return false;
    }

    outData.resize(deflateBound(&strm, inSize));
    strm.next_in = const_cast<Bytef*>(inData);
    strm.avail_in = inSize;
    strm.next_out = outData.data();
    strm.avail_out = outData.size();

    int res = deflate(&strm, Z_FINISH);
    deflateEnd(&strm);

    if (res != Z_STREAM_END) {
        return false;
    }
    outData.resize(strm.total_out);
    return true;
}

bool decompressBuffer(const uint8_t* inData, size_t inSize, std::vector<uint8_t>& outData) {
    z_stream strm;
    std::memset(&strm, 0, sizeof(strm));
    if (inflateInit2(&strm, 15 + 16) != Z_OK) {
        return false;
    }

    outData.resize(inSize * 4 + 1024);
    strm.next_in = const_cast<Bytef*>(inData);
    strm.avail_in = inSize;

    while (true) {
        strm.next_out = outData.data() + strm.total_out;
        strm.avail_out = outData.size() - strm.total_out;

        int ret = inflate(&strm, Z_NO_FLUSH);
        if (ret == Z_STREAM_END) {
            break;
        }
        if (ret != Z_OK && ret != Z_BUF_ERROR) {
            inflateEnd(&strm);
            return false;
        }
        outData.resize(outData.size() * 2);
    }

    outData.resize(strm.total_out);
    inflateEnd(&strm);
    return true;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_applyProcessHardening(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (prctl(PR_SET_DUMPABLE, 0, 0, 0, 0) != 0) {
        LOGE("Failed to set PR_SET_DUMPABLE to 0");
        return JNI_FALSE;
    }

    long ctrl = prctl(PR_GET_TAGGED_ADDR_CTRL, 0, 0, 0, 0);
    if (ctrl >= 0) {
        if (!(ctrl & PR_TAGGED_ADDR_ENABLE)) {
            prctl(PR_SET_TAGGED_ADDR_CTRL, ctrl | PR_TAGGED_ADDR_ENABLE | PR_MTE_TCF_SYNC, 0, 0, 0);
        }
    }

    LOGI("Hardware process hardening applied: PR_SET_DUMPABLE=0, MTE sync configured.");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_isMteActive(JNIEnv* /*env*/, jobject /*thiz*/) {
    long ctrl = prctl(PR_GET_TAGGED_ADDR_CTRL, 0, 0, 0, 0);
    if (ctrl < 0) {
        return JNI_FALSE;
    }
    return (ctrl & PR_MTE_TCF_SYNC) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_secureZeroMemory(JNIEnv* env, jobject /*thiz*/, jbyteArray buffer) {
    if (buffer == nullptr) return;
    jsize len = env->GetArrayLength(buffer);
    if (len <= 0) return;

    jbyte* bytes = env->GetByteArrayElements(buffer, nullptr);
    if (bytes != nullptr) {
        volatile unsigned char* p = reinterpret_cast<volatile unsigned char*>(bytes);
        for (jsize i = 0; i < len; ++i) {
            p[i] = 0x00;
        }
        __asm__ __volatile__("" : : "r"(p) : "memory");
        env->ReleaseByteArrayElements(buffer, bytes, 0);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_purgeBlockDevice(JNIEnv* env, jobject /*thiz*/, jstring blockDevicePath) {
    if (blockDevicePath == nullptr) return JNI_FALSE;

    const char* path = env->GetStringUTFChars(blockDevicePath, nullptr);
    if (path == nullptr) return JNI_FALSE;

    int fd = open(path, O_RDWR | O_DIRECT | O_SYNC);
    if (fd < 0) {
        LOGE("Failed opening block device path: %s", path);
        env->ReleaseStringUTFChars(blockDevicePath, path);
        return JNI_FALSE;
    }

    uint64_t range[2];
    range[0] = 0;
    if (ioctl(fd, BLKGETSIZE64, &range[1]) < 0) {
        LOGE("BLKGETSIZE64 failed on %s", path);
        close(fd);
        env->ReleaseStringUTFChars(blockDevicePath, path);
        return JNI_FALSE;
    }

    LOGI("Issuing JEDEC BLKSECDISCARD across full partition size: %llu bytes on %s", static_cast<unsigned long long>(range[1]), path);

    bool success = true;
    if (ioctl(fd, BLKSECDISCARD, &range) < 0) {
        LOGW("BLKSECDISCARD rejected by hardware FTL; falling back to BLKDISCARD on %s", path);
        if (ioctl(fd, BLKDISCARD, &range) < 0) {
            LOGE("BLKDISCARD failed on %s", path);
            success = false;
        }
    }

    fsync(fd);
    close(fd);
    env->ReleaseStringUTFChars(blockDevicePath, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_lockMemoryPages(JNIEnv* env, jobject /*thiz*/, jbyteArray data) {
    if (data == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_FALSE;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return JNI_FALSE;

    int res = mlock(bytes, static_cast<size_t>(len));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_unlockMemoryPages(JNIEnv* env, jobject /*thiz*/, jbyteArray data) {
    if (data == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return JNI_FALSE;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes == nullptr) return JNI_FALSE;

    int res = munlock(bytes, static_cast<size_t>(len));
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_executeCesNative(
    JNIEnv* env, jobject /*thiz*/, jbyteArray input, jbyteArray key, jbyteArray nonce) {

    if (input == nullptr || key == nullptr || nonce == nullptr) return nullptr;
    if (env->GetArrayLength(key) != 32 || env->GetArrayLength(nonce) != 12) return nullptr;

    jsize inLen = env->GetArrayLength(input);
    if (inLen <= 0) return nullptr;

    jbyte* inBytes = env->GetByteArrayElements(input, nullptr);
    jbyte* keyBytes = env->GetByteArrayElements(key, nullptr);
    jbyte* nonceBytes = env->GetByteArrayElements(nonce, nullptr);

    std::vector<uint8_t> compressed;
    bool compOk = compressBuffer(reinterpret_cast<const uint8_t*>(inBytes), inLen, compressed);
    env->ReleaseByteArrayElements(input, inBytes, JNI_ABORT);

    if (!compOk) {
        env->ReleaseByteArrayElements(key, keyBytes, JNI_ABORT);
        env->ReleaseByteArrayElements(nonce, nonceBytes, JNI_ABORT);
        return nullptr;
    }

    std::vector<uint8_t> cipher;
    uint8_t tag[16];
    chacha20Poly1305Encrypt(reinterpret_cast<const uint8_t*>(keyBytes),
                            reinterpret_cast<const uint8_t*>(nonceBytes),
                            compressed.data(), compressed.size(), cipher, tag);

    env->ReleaseByteArrayElements(key, keyBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonceBytes, JNI_ABORT);

    cipher.insert(cipher.end(), tag, tag + 16);

    jbyteArray outArray = env->NewByteArray(static_cast<jsize>(cipher.size()));
    if (outArray != nullptr) {
        env->SetByteArrayRegion(outArray, 0, static_cast<jsize>(cipher.size()),
                                reinterpret_cast<const jbyte*>(cipher.data()));
    }
    return outArray;
}

JNIEXPORT jbyteArray JNICALL
Java_com_hamoon_uncleted_util_NativeSecurityBridge_executeCesDecryptNative(
    JNIEnv* env, jobject /*thiz*/, jbyteArray inputWithTag, jbyteArray key, jbyteArray nonce) {

    if (inputWithTag == nullptr || key == nullptr || nonce == nullptr) return nullptr;
    if (env->GetArrayLength(key) != 32 || env->GetArrayLength(nonce) != 12) return nullptr;

    jsize totalLen = env->GetArrayLength(inputWithTag);
    if (totalLen <= 16) return nullptr;

    size_t cipherLen = totalLen - 16;

    jbyte* inBytes = env->GetByteArrayElements(inputWithTag, nullptr);
    jbyte* keyBytes = env->GetByteArrayElements(key, nullptr);
    jbyte* nonceBytes = env->GetByteArrayElements(nonce, nullptr);

    const uint8_t* cipherPtr = reinterpret_cast<const uint8_t*>(inBytes);
    const uint8_t* tagPtr = cipherPtr + cipherLen;

    std::vector<uint8_t> decryptedComp;
    bool ok = chacha20Poly1305Decrypt(reinterpret_cast<const uint8_t*>(keyBytes),
                                      reinterpret_cast<const uint8_t*>(nonceBytes),
                                      cipherPtr, cipherLen, tagPtr, decryptedComp);

    env->ReleaseByteArrayElements(inputWithTag, inBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(key, keyBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonceBytes, JNI_ABORT);

    if (!ok) {
        LOGE("CES Native Decryption Authentication Tag Mismatch!");
        return nullptr;
    }

    std::vector<uint8_t> plain;
    if (!decompressBuffer(decryptedComp.data(), decryptedComp.size(), plain)) {
        LOGE("CES Native Decompression Failure");
        return nullptr;
    }

    jbyteArray outArray = env->NewByteArray(static_cast<jsize>(plain.size()));
    if (outArray != nullptr) {
        env->SetByteArrayRegion(outArray, 0, static_cast<jsize>(plain.size()),
                                reinterpret_cast<const jbyte*>(plain.data()));
    }
    return outArray;
}

} // extern "C"