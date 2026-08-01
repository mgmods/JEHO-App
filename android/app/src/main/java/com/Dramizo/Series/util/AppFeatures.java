package com.Dramizo.Series.util;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

/** Pulls dashboard feature flags into local session cache. */
public final class AppFeatures {
    private AppFeatures() {}

    public static void refresh(AppContainer container) {
        if (container == null) return;
        Result<MiscDtos.FeaturesDto> r = ApiCall.execute(container.getConfigApi().features());
        if (!r.success || r.data == null) return;
        container.getSessionManager().setFemaleOnlyVoiceHostsFromServer(r.data.femaleOnlyVoiceHosts);
        container.getSessionManager().setMicWithoutHostApprovalFromServer(r.data.micWithoutHostApproval);
        container.getSessionManager().setGiftSoundsEnabledFromServer(r.data.giftSoundsEnabled);
    }
}
