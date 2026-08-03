package com.Dramizo.Series.presentation.settings;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.util.LocaleHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<AuthDtos.UserDto>> blocked = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loggedOut = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public SettingsViewModel(AppContainer c) { this.c = c; }

    public LiveData<List<AuthDtos.UserDto>> getBlocked() { return blocked; }
    public LiveData<Boolean> getLoggedOut() { return loggedOut; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getMessage() { return message; }

    public boolean isDarkMode() { return c.getSessionManager().isDarkMode(); }
    public boolean isMuteMessageNotifications() {
        return c.getSessionManager().isMuteMessageNotifications();
    }
    public String getLanguage() { return c.getSessionManager().getLanguage(); }

    public void setDarkMode(boolean enabled) {
        c.getSessionManager().setDarkMode(enabled);
        AppCompatDelegate.setDefaultNightMode(enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public void setMuteMessageNotifications(boolean muted) {
        c.getSessionManager().setMuteMessageNotifications(muted);
    }

    public boolean isMuteCelebrationPopups() {
        return c.getSessionManager().isMuteCelebrationPopups();
    }

    public void setMuteCelebrationPopups(boolean muted) {
        c.getSessionManager().setMuteCelebrationPopups(muted);
    }

    public boolean isFriendsOnlyMessages() {
        return c.getSessionManager().isFriendsOnlyMessages();
    }

    public void setFriendsOnlyMessages(boolean enabled) {
        c.getSessionManager().setFriendsOnlyMessages(enabled);
    }

    public void setLanguage(String lang) {
        applyLanguage(c.getAppContext(), lang);
        c.getSessionManager().setLanguage(LocaleHelper.normalizeTag(lang));
    }

    /** Persist + apply app locales (usable from LanguageActivity without ViewModel). */
    public static void applyLanguage(android.content.Context ctx, String lang) {
        String normalized = LocaleHelper.normalizeTag(lang);
        if (ctx != null) {
            android.content.Context app = ctx.getApplicationContext();
            try {
                app.getSharedPreferences("auralive_lang", android.content.Context.MODE_PRIVATE)
                        .edit().putString("language", normalized).apply();
            } catch (Exception ignored) {
            }
            try {
                if (app instanceof com.Dramizo.Series.AuraLiveApp) {
                    ((com.Dramizo.Series.AuraLiveApp) app).getContainer()
                            .getSessionManager().setLanguage(normalized);
                }
            } catch (Exception ignored) {
            }
        }
        AppCompatDelegate.setApplicationLocales(
                "system".equals(normalized)
                        ? LocaleListCompat.getEmptyLocaleList()
                        : LocaleListCompat.forLanguageTags(normalized));
    }

    public static String displayLanguageLabel(android.content.Context ctx, String tag) {
        String n = LocaleHelper.normalizeTag(tag);
        if ("system".equals(n)) return ctx.getString(R.string.system_language);
        if ("ar".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_ar);
        if ("en".equalsIgnoreCase(n) || n.toLowerCase(java.util.Locale.US).startsWith("en")) {
            return ctx.getString(R.string.lang_name_en);
        }
        if ("fr".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_fr);
        if ("tr".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_tr);
        if ("de".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_de);
        if ("es".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_es);
        if ("pt-BR".equalsIgnoreCase(n) || "pt".equalsIgnoreCase(n)) {
            return ctx.getString(R.string.lang_name_pt);
        }
        if ("ru".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_ru);
        if ("id".equalsIgnoreCase(n) || "in".equalsIgnoreCase(n)) {
            return ctx.getString(R.string.lang_name_id);
        }
        if ("hi".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_hi);
        if ("ur".equalsIgnoreCase(n)) return ctx.getString(R.string.lang_name_ur);
        if ("zh-CN".equalsIgnoreCase(n) || "zh".equalsIgnoreCase(n)) {
            return ctx.getString(R.string.lang_name_zh);
        }
        return n;
    }

    public void loadBlocked() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<AuthDtos.UserDto>> r = c.getUserRepository().blocked();
            if (r.success && r.data != null && r.data.items != null) blocked.postValue(r.data.items);
            else error.postValue(r.error != null ? r.error : c.getAppContext().getString(R.string.settings_block_list_load_failed));
        });
    }

    public void unblock(String userId) {
        if (userId == null) return;
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getUserRepository().unblock(userId);
            if (r.success) {
                message.postValue(c.getAppContext().getString(R.string.settings_unblocked));
                List<AuthDtos.UserDto> current = blocked.getValue();
                if (current != null) {
                    List<AuthDtos.UserDto> next = new ArrayList<>();
                    for (AuthDtos.UserDto u : current) {
                        if (u != null && !userId.equals(u.id)) next.add(u);
                    }
                    blocked.postValue(next);
                }
            } else {
                error.postValue(r.error != null ? r.error : c.getAppContext().getString(R.string.settings_unblock_failed));
            }
        });
    }

    public void logout() {
        c.getIoExecutor().execute(() -> {
            c.getNotificationRepository().unregisterDevice();
            c.getAuthRepository().logout();
            loggedOut.postValue(true);
        });
    }
}
