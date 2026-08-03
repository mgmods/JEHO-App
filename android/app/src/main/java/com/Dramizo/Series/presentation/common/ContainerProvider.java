package com.Dramizo.Series.presentation.common;

import android.app.Activity;
import android.content.Context;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.di.AppContainer;

public final class ContainerProvider {
    private ContainerProvider() {}

    public static AppContainer from(Activity activity) {
        return from((Context) activity);
    }

    public static AppContainer from(Context context) {
        Context app = context.getApplicationContext();
        return ((AuraLiveApp) app).getContainer();
    }
}
