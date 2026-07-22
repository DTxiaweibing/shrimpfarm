#include <jni.h>
#include <android/log.h>
#include <time.h>
#include <cstring>
#include <cstdlib>

#define LOG_TAG "NativeWatermark"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// XOR-obfuscated "非官方正版"
static const char WATERMARK_ENC[] = {
    0x3c, 0x02, 0x42, 0x74, 0x02, 0x47, 0x74, 0x01,
    0x0c, 0x6c, 0x11, 0x43, 0x23, 0x6a, 0x11, 0x41,
    0x22, 0x6d, 0x00
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
    jmethodID set_alpha_mid = env->GetMethodID(paint_cls, "setAlpha", "(I)V");

    if (!save_mid || !rotate_mid || !draw_text_mid || !restore_mid) {
        LOGE("Failed to get Canvas method IDs");
        return;
    }

    float density = 3.0f;
    float text_size = WATERMARK_TEXT_SIZE_DP * density;
    float line_spacing = WATERMARK_LINE_SPACING_DP * density;

    jmethodID set_text_size_mid = env->GetMethodID(paint_cls, "setTextSize", "(F)V");
    if (set_text_size_mid) {
        env->CallVoidMethod(paint, set_text_size_mid, text_size);
    }

    float text_y = -40.0f * density;
    int count = 0;

    while (text_y < height + 100 * density) {
        env->CallIntMethod(canvas, save_mid);
        env->CallVoidMethod(canvas, rotate_mid, -45.0f, line_spacing / 2.0f, text_y);
        env->CallVoidMethod(canvas, draw_text_mid, watermark_text, 8.0f * density, text_y, paint);
        env->CallVoidMethod(canvas, restore_mid);
        text_y += line_spacing;
        count++;
    }

    env->DeleteLocalRef(watermark_text);
    LOGI("Rendered %d watermark lines, days >= %d", count, TRIGGER_DAYS);
}
