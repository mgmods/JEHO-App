package com.Dramizo.Series.presentation.common;

import android.app.Activity;
import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.di.AppContainer;

public final class ContainerProvider {
    private ContainerProvider() {}
    public static AppContainer from(Activity activity) {
        return ((AuraLiveApp) activity.getApplication()).getContainer();
    }
}
