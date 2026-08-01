package com.Dramizo.Series.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/** Applies the saved language tag to a Context, including regional locales. */
public final class LocaleHelper {
    private LocaleHelper() {}

    public static Context wrap(Context context, String language) {
        String lang = normalizeTag(language);
        Locale locale;
        if ("system".equals(lang)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locale = Resources.getSystem().getConfiguration().getLocales().get(0);
            } else {
                locale = Resources.getSystem().getConfiguration().locale;
            }
        } else {
            locale = Locale.forLanguageTag(lang);
        }
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        }
        config.locale = locale;
        res.updateConfiguration(config, res.getDisplayMetrics());
        return context;
    }

    public static String normalizeTag(String language) {
        if (language == null || language.trim().isEmpty()) return "ar";
        String tag = language.trim().replace('_', '-');
        if ("pt".equalsIgnoreCase(tag)) return "pt-BR";
        if ("zh".equalsIgnoreCase(tag)) return "zh-CN";
        if ("in".equalsIgnoreCase(tag)) return "id";
        return tag;
    }
}
