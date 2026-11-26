package com.shrimpfarm.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREF_LANG = "app_language";
    /** 跟随系统。 */
    public static final String LANG_AUTO = "auto";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    public static Context setLocale(Context context) {
        return updateLocale(context, getSavedLang(context));
    }

    public static Context setLocale(Context context, String lang) {
        saveLang(context, lang);
        return updateLocale(context, lang);
    }

    public static String getSavedLang(Context context) {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString(PREF_LANG, LANG_AUTO);
    }

    /** 当前实际生效的语言：用户选过就用选的，跟随系统时按系统语言判断（中文算 zh，其余算 en）。 */
    public static String getEffectiveLang(Context context) {
        String saved = getSavedLang(context);
        if (LANG_ZH.equals(saved) || LANG_EN.equals(saved)) {
            return saved;
        }
        Locale system = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? LocaleList.getDefault().get(0)
                : Locale.getDefault();
        return system != null && "zh".equals(system.getLanguage()) ? LANG_ZH : LANG_EN;
    }

    private static void saveLang(Context context, String lang) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LANG, lang)
                .apply();
    }

    private static Context updateLocale(Context context, String lang) {
        Locale locale;
        if (LANG_AUTO.equals(lang)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locale = LocaleList.getDefault().get(0);
            } else {
                locale = Locale.getDefault();
            }
        } else if (LANG_EN.equals(lang)) {
            locale = Locale.US;
        } else {
            locale = Locale.CHINA;
        }

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.fontScale = 1.0f;
        Context newCtx = context.createConfigurationContext(config);
        android.util.DisplayMetrics dm = newCtx.getResources().getDisplayMetrics();
        dm.scaledDensity = dm.density;
        return newCtx;
    }
}
