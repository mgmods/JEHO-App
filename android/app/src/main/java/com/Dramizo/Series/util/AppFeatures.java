package com.Dramizo.Series.util;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

/** Pulls dashboard feature flags into local session cache. */
public final class AppFeatures {
    private static final java.util.concurrent.atomic.AtomicLong LAST_REFRESH_AT =
            new java.util.concurrent.atomic.AtomicLong(-1L);
    private static final java.util.concurrent.atomic.AtomicBoolean REFRESH_IN_FLIGHT =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final long REFRESH_INTERVAL_MS = 60_000L;

    private AppFeatures() {}

    public static void refresh(AppContainer container) {
        if (container == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        long previous = LAST_REFRESH_AT.get();
        if (previous >= 0L && now - previous < REFRESH_INTERVAL_MS) return;
        if (!LAST_REFRESH_AT.compareAndSet(previous, now)) return;
        if (!REFRESH_IN_FLIGHT.compareAndSet(false, true)) return;
        try {
            Result<MiscDtos.FeaturesDto> r = ApiCall.execute(container.getConfigApi().features());
            if (!r.success || r.data == null) return;
            container.getSessionManager().setFemaleOnlyVoiceHostsFromServer(r.data.femaleOnlyVoiceHosts);
            container.getSessionManager().setMicWithoutHostApprovalFromServer(r.data.micWithoutHostApproval);
            container.getSessionManager().setGiftSoundsEnabledFromServer(r.data.giftSoundsEnabled);
            container.getSessionManager().setTasksEnabledFromServer(r.data.tasksEnabled);
            container.getSessionManager().setChatPromoFilterFromServer(r.data.chatPromoFilterEnabled);
            if (r.data.extraKeywords != null && !r.data.extraKeywords.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.data.extraKeywords.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(r.data.extraKeywords.get(i));
                }
                container.getSessionManager().setChatExtraKeywordsFromServer(sb.toString());
            } else {
                container.getSessionManager().setChatExtraKeywordsFromServer("");
            }
            // Silent media cache sync (game covers / static icons).
            MediaAssetSync.applyServerEpoch(container.getAppContext(), r.data.mediaAssetEpoch);
        } finally {
            REFRESH_IN_FLIGHT.set(false);
        }
    }
}
