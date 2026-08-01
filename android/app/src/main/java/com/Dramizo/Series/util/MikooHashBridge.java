package com.Dramizo.Series.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.SlotGameDtos;

/**
 * Hash-game bridge (7UpDown, Crash, etc.) — exposes {@code androidJsObj}
 * like Mikoo {@code v0} / {@code WebViewStatusDialog}.
 */
public final class MikooHashBridge {
    public interface Callbacks {
        void onClose();
        void onRecharge();
    }

    private final SlotGameDtos.SessionDto session;
    private final Callbacks callbacks;
    private final Handler main = new Handler(Looper.getMainLooper());

    public MikooHashBridge(@Nullable SlotGameDtos.SessionDto session, Callbacks callbacks) {
        this.session = session;
        this.callbacks = callbacks;
    }

    private void ui(Runnable r) {
        if (r == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
    }

    @JavascriptInterface
    public String getUid() {
        String v = (session == null || session.userId == null || session.userId.isEmpty())
                ? "0" : session.userId;
        GameProbeLog.bridge("androidJsObj", "getUid", v);
        return v;
    }

    @JavascriptInterface
    public String getTicket() {
        String v = (session == null || session.code == null) ? "" : session.code;
        GameProbeLog.bridge("androidJsObj", "getTicket", v);
        return v;
    }

    @JavascriptInterface
    public String getBalance() {
        String v = session == null ? "0" : String.valueOf(Math.max(0L, session.balance));
        GameProbeLog.bridge("androidJsObj", "getBalance", v);
        return v;
    }

    @JavascriptInterface
    public String getUserMoney() {
        return getBalance();
    }

    @JavascriptInterface
    public String getNickName() {
        String v = MikooGameBridge.safeDisplayName(
                session != null ? session.displayName : null, null);
        GameProbeLog.bridge("androidJsObj", "getNickName", v);
        return v;
    }

    @JavascriptInterface
    public String getHeadImage() {
        String v = (session == null || session.avatarUrl == null) ? "" : session.avatarUrl;
        GameProbeLog.bridge("androidJsObj", "getHeadImage", v);
        return v;
    }

    @JavascriptInterface
    public String getRoomId() {
        String v = (session == null || session.roomId == null || session.roomId.isEmpty())
                ? "0" : session.roomId;
        GameProbeLog.bridge("androidJsObj", "getRoomId", v);
        return v;
    }

    @JavascriptInterface
    public String getGameConfig() {
        String v = (session == null || session.hashGameConfig == null) ? "" : session.hashGameConfig;
        GameProbeLog.bridge("androidJsObj", "getGameConfig",
                v.length() > 200 ? v.substring(0, 200) + "…" : v);
        return v;
    }

    @JavascriptInterface
    public String getLanguage() {
        if (session == null || session.language == null) return "2";
        if ("1".equals(session.language)) return "en";
        return "ar";
    }

    @JavascriptInterface
    public String getAppName() {
        if (session == null || session.appChannel == null) return "jehochat";
        return session.appChannel;
    }

    @JavascriptInterface
    public String getAppVersion() {
        return "1.0";
    }

    @JavascriptInterface
    public void closeWin() {
        GameProbeLog.bridge("androidJsObj", "closeWin", null);
        if (callbacks != null) ui(callbacks::onClose);
    }

    @JavascriptInterface
    public void openChargePage() {
        GameProbeLog.bridge("androidJsObj", "openChargePage", null);
        if (callbacks != null) ui(callbacks::onRecharge);
    }

    @JavascriptInterface
    public void openPaymentPage() {
        GameProbeLog.bridge("androidJsObj", "openPaymentPage", null);
        if (callbacks != null) ui(callbacks::onRecharge);
    }

    public static void attach(android.webkit.WebView webView, MikooHashBridge bridge) {
        webView.removeJavascriptInterface("androidJsObj");
        webView.removeJavascriptInterface("JehoGameProbe");
        webView.addJavascriptInterface(bridge, "androidJsObj");
        webView.addJavascriptInterface(new Probe(), "JehoGameProbe");
        GameProbeLog.i("HASH.attach", "androidJsObj+JehoGameProbe"
                + " game=" + (bridge.session != null ? bridge.session.gameId : "?")
                + " bal=" + (bridge.session != null ? bridge.session.balance : 0));
    }

    public static void detach(android.webkit.WebView webView) {
        webView.removeJavascriptInterface("androidJsObj");
        webView.removeJavascriptInterface("JehoGameProbe");
    }

    public static void openWallet(Activity activity) {
        BalanceRedirect.openRecharge(activity);
    }

    public static final class Probe {
        @JavascriptInterface
        public void log(String msg) {
            GameProbeLog.js("probe", msg);
        }
    }
}
