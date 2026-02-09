package com.example.rotaoptjv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;

public class PreferencesManager {
    private static final String LAST_RESET_DATE_KEY = "lastResetDate";
    private static final String[] PREF_FILES_TO_RESET = {
            "VisitedStatusPrefs",
            "RouteSegmentsPrefs"
    };

    public static void checkAndResetIfNeeded(Context context) {
        SharedPreferences mainPrefs = context.getSharedPreferences("AppMainPrefs", Context.MODE_PRIVATE);

        // Bugünün tarihini al (sadece gün bazında)
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayMillis = today.getTimeInMillis();

        // Son sıfırlama tarihini al
        long lastResetDate = mainPrefs.getLong(LAST_RESET_DATE_KEY, 0);

        // Eğer bugün henüz sıfırlanmadıysa sıfırla
        if (lastResetDate < todayMillis) {
            resetAllPreferences(context);

            // Son sıfırlama tarihini güncelle
            mainPrefs.edit()
                    .putLong(LAST_RESET_DATE_KEY, todayMillis)
                    .apply();
        }
    }

    private static void resetAllPreferences(Context context) {
        for (String prefFileName : PREF_FILES_TO_RESET) {
            SharedPreferences prefs = context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
    }

    // Her activity'de kullanmak için yardımcı metodlar
    public static SharedPreferences getVisitedStatusPrefs(Context context) {
        checkAndResetIfNeeded(context);
        return context.getSharedPreferences("VisitedStatusPrefs", Context.MODE_PRIVATE);
    }

    public static SharedPreferences getRouteSegmentsPrefs(Context context) {
        checkAndResetIfNeeded(context);
        return context.getSharedPreferences("RouteSegmentsPrefs", Context.MODE_PRIVATE);
    }
}