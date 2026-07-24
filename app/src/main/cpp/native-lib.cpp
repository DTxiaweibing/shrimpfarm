#include <jni.h>
#include <android/log.h>
#include <time.h>
#include <cstring>
#include <cstdlib>

#define LOG_TAG "NativeWatermark"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// XOR-obfuscated "非官方正版" (UTF-8: E9 9D 9E E5 AE 98 E6 96 B9 E6 AD A3 E7 89 88)
// key "NM3LOALN" (4E 4D 33 4C 30 41 4C 4E)
static const unsigned char WATERMARK_ENC[] = {
    0xA7, 0xD0, 0xAD, 0xA9, 0x9E, 0xD9, 0xAA, 0xD8,
    0xF7, 0xAB, 0x9E, 0xEF, 0xD7, 0xC8, 0xC4
};
static const char XOR_KEY[] = { 'N', 'M', '3', 'L', '0', 'A', 'L', 'N', 0 };
static const int ENC_LEN = sizeof(WATERMARK_ENC) / sizeof(WATERMARK_ENC[0]);

static const int TRIGGER_DAYS = 50;
static const float WATERMARK_TEXT_SIZE_DP = 36;
static const float WATERMARK_LINE_SPACING_DP = 200;

static void decrypt_watermark(char *out) {
    int klen = strlen(XOR_KEY);
    for (int i = 0; i < ENC_LEN; i++) {
        out[i] = WATERMARK_ENC[i] ^ XOR_KEY[i % klen];
    }
    out[ENC_LEN] = '\0';
}

static int parse_date(const char *date_str, struct tm *tm_out) {
    int y, m, d;
    if (sscanf(date_str, "%d/%d/%d", &y, &m, &d) == 3) goto ok;
    if (sscanf(date_str, "%d-%d-%d", &y, &m, &d) == 3) goto ok;
    if (sscanf(date_str, "%d.%d.%d", &y, &m, &d) == 3) goto ok;
    return -1;
ok:
    tm_out->tm_year = y - 1900;
    tm_out->tm_mon = m - 1;
    tm_out->tm_mday = d;
    tm_out->tm_hour = 0;
    tm_out->tm_min = 0;
    tm_out->tm_sec = 0;
    tm_out->tm_isdst = -1;
    return 0;
}

static int days_since_stocking(const char *stocking_date) {
    struct tm stock_tm;
    memset(&stock_tm, 0, sizeof(stock_tm));
    if (parse_date(stocking_date, &stock_tm) != 0) return 0;

    time_t stock_time = mktime(&stock_tm);
    if (stock_time == (time_t)-1) return 0;

    time_t now_time = time(NULL);
    if (now_time == (time_t)-1) return 0;

    double diff = difftime(now_time, stock_time);
    int days = (int)(diff / (60 * 60 * 24));
    return days > 0 ? days : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_shrimpfarm_app_WatermarkNative_shouldShowWatermark(
    JNIEnv *env, jclass clazz, jstring stocking_date) {
    const char *date_str = env->GetStringUTFChars(stocking_date, NULL);
    if (date_str == NULL) return JNI_FALSE;
    int days = days_since_stocking(date_str);
    env->ReleaseStringUTFChars(stocking_date, date_str);
    return days >= TRIGGER_DAYS ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_shrimpfarm_app_WatermarkNative_renderWatermark(
    JNIEnv *env, jclass clazz, jobject canvas, jint width, jint height, jobject paint) {
    if (canvas == NULL || paint == NULL) return;

    char text_buf[32];
    decrypt_watermark(text_buf);
    jstring watermark_text = env->NewStringUTF(text_buf);
    if (watermark_text == NULL) return;

    jclass canvas_cls = env->FindClass("android/graphics/Canvas");
    jclass paint_cls = env->FindClass("android/graphics/Paint");

    jmethodID save_mid = env->GetMethodID(canvas_cls, "save", "()I");
    jmethodID rotate_mid = env->GetMethodID(canvas_cls, "rotate", "(FFF)V");
    jmethodID draw_text_mid = env->GetMethodID(canvas_cls, "drawText",
        "(Ljava/lang/String;FFLandroid/graphics/Paint;)V");
    jmethodID restore_mid = env->GetMethodID(canvas_cls, "restore", "()V");

    if (!save_mid || !rotate_mid || !draw_text_mid || !restore_mid) {
        LOGE("Failed to get Canvas method IDs");
        return;
    }

    float density = 3.0f;
    float text_size = WATERMARK_TEXT_SIZE_DP * density;
    float step = WATERMARK_LINE_SPACING_DP * density;

    jmethodID set_text_size_mid = env->GetMethodID(paint_cls, "setTextSize", "(F)V");
    if (set_text_size_mid) {
        env->CallVoidMethod(paint, set_text_size_mid, text_size);
    }

    float diagonal = (float)((width + height) * 1.2);
    int count = 0;

    for (float y = -diagonal; y < diagonal; y += step) {
        int row = (int)((y + diagonal) / step);
        float x_start = -diagonal + ((row % 2 == 0) ? step * 0.5f : 0);
        for (float x = x_start; x < diagonal; x += step) {
            env->CallIntMethod(canvas, save_mid);
            env->CallVoidMethod(canvas, rotate_mid, -45.0f, x, y);
            env->CallVoidMethod(canvas, draw_text_mid, watermark_text, x, y, paint);
            env->CallVoidMethod(canvas, restore_mid);
            count++;
        }
    }

    env->DeleteLocalRef(watermark_text);
    LOGI("Rendered %d watermark tiles", count);
}

// XOR-obfuscated "ShrimpFarm2024!!" (ASCII: 53 68 72 69 6D 70 46 61 72 6D 32 30 32 34 21 21)
// key "NM3L0ALN" (4E 4D 33 4C 30 41 4C 4E)
static const unsigned char ROOT_KEY_ENC[] = {
    0x1D, 0x25, 0x41, 0x25, 0x5D, 0x31, 0x0A, 0x2F,
    0x3C, 0x20, 0x01, 0x7C, 0x02, 0x75, 0x6D, 0x6F
};
static const int ROOT_KEY_LEN = sizeof(ROOT_KEY_ENC) / sizeof(ROOT_KEY_ENC[0]);

extern "C" JNIEXPORT jstring JNICALL
Java_com_shrimpfarm_app_WatermarkNative_getRootKey(JNIEnv *env, jclass clazz) {
    char buf[32];
    int klen = strlen(XOR_KEY);
    for (int i = 0; i < ROOT_KEY_LEN; i++) {
        buf[i] = ROOT_KEY_ENC[i] ^ XOR_KEY[i % klen];
    }
    buf[ROOT_KEY_LEN] = '\0';
    return env->NewStringUTF(buf);
}

// XOR-obfuscated official APK signing fingerprint (SHA-256 hex)
// key "NM3L0ALN" (4E 4D 33 4C 30 41 4C 4E)
static const unsigned char FINGERPRINT_ENC[] = {
    0x2A, 0x7B, 0x0B, 0x2D, 0x53, 0x72, 0x28, 0x76,
    0x7E, 0x7D, 0x57, 0x2D, 0x05, 0x76, 0x2E, 0x28,
    0x2B, 0x2C, 0x03, 0x2E, 0x00, 0x73, 0x2A, 0x78,
    0x77, 0x7F, 0x57, 0x7A, 0x54, 0x75, 0x7C, 0x78,
    0x77, 0x7B, 0x56, 0x29, 0x07, 0x74, 0x7A, 0x7B,
    0x2D, 0x2E, 0x01, 0x2F, 0x08, 0x72, 0x7A, 0x2F,
    0x2A, 0x7A, 0x05, 0x7B, 0x07, 0x72, 0x2A, 0x2C,
    0x77, 0x7D, 0x56, 0x28, 0x51, 0x22, 0x7B, 0x78,
};
static const int FINGERPRINT_LEN = sizeof(FINGERPRINT_ENC) / sizeof(FINGERPRINT_ENC[0]);

extern "C" JNIEXPORT jstring JNICALL
Java_com_shrimpfarm_app_WatermarkNative_getOfficialFingerprint(JNIEnv *env, jclass clazz) {
    char buf[128];
    int klen = strlen(XOR_KEY);
    for (int i = 0; i < FINGERPRINT_LEN; i++) {
        buf[i] = FINGERPRINT_ENC[i] ^ XOR_KEY[i % klen];
    }
    buf[FINGERPRINT_LEN] = '\0';
    return env->NewStringUTF(buf);
}
