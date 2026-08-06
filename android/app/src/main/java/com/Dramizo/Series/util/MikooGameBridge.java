package com.Dramizo.Series.util;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.SlotGameDtos;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Mikoo / BaiShun WebView bridge — exposes {@code NativeBridge} and {@code gameBridge}
 * like the original Mikoo {@code BSGameWebDialog}.
 */
public final class MikooGameBridge {
    public interface Callbacks {
        void onDestroy();
        void onRecharge();
        void onLoaded();
    }

    private final WebView webView;
    private final SlotGameDtos.SessionDto session;
    private final Callbacks callbacks;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());

    public MikooGameBridge(WebView webView, SlotGameDtos.SessionDto session, Callbacks callbacks) {
        this.webView = webView;
        this.session = session;
        this.callbacks = callbacks;
    }

    public void attach() {
        webView.removeJavascriptInterface("NativeBridge");
        webView.removeJavascriptInterface("gameBridge");
        webView.removeJavascriptInterface("AuraBridge");
        webView.removeJavascriptInterface("JehoGameProbe");
        webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");
        webView.addJavascriptInterface(new GameBridge(), "gameBridge");
        webView.addJavascriptInterface(new ProbeBridge(), "JehoGameProbe");
        GameProbeLog.i("BRIDGE.attach", "NativeBridge+gameBridge+JehoGameProbe"
                + " game=" + (session != null ? session.gameId : "?")
                + " bal=" + currentBalance()
                + " user=" + (session != null ? session.userId : "?")
                + " code=" + (session != null ? session.code : "?"));
    }

    public void detach() {
        webView.removeJavascriptInterface("NativeBridge");
        webView.removeJavascriptInterface("gameBridge");
    }

    /** Notify game after wallet recharge (Mikoo pattern). */
    public void notifyWalletUpdate() {
        if (session == null || session.userId == null) return;
        long bal = Math.max(0L, session.balance);
        JsonObject payload = new JsonObject();
        payload.addProperty("userId", session.userId);
        payload.addProperty("openId", session.userId);
        payload.addProperty("balance", bal);
        payload.addProperty("coin", bal);
        payload.addProperty("gold", bal);
        payload.addProperty("money", bal);
        payload.addProperty("userMoney", bal);
        runJs("walletUpdate", payload);
    }

    /** Live coin balance for Cocos / BaiShun that call NativeBridge.getBalance. */
    public long currentBalance() {
        return session != null ? Math.max(0L, session.balance) : 0L;
    }

    public void setBalance(long coins) {
        if (session != null) session.balance = Math.max(0L, coins);
    }

    private void runJs(String fn, Object payload) {
        if (fn == null || fn.isEmpty()) return;
        String json = payload instanceof JsonObject
                ? payload.toString()
                : gson.toJson(payload);
        String script = fn + "(" + json + ")";
        GameProbeLog.i("NATIVE→JS", fn + " " + json);
        main.post(() -> {
            try {
                webView.evaluateJavascript(script, null);
            } catch (Exception ignored) {
                try {
                    webView.loadUrl("javascript:" + script);
                } catch (Exception ignored2) {
                }
            }
        });
    }

    private void replyNativeConfig(String jsCallback, JsonObject config) {
        if (jsCallback == null || jsCallback.isEmpty()) return;
        final String json = config.toString();
        final String callback = jsCallback;
        GameProbeLog.i("REPLY.nativeConfig", callback + " " + json);
        main.post(() -> {
            String script = callback + "(" + json + ")";
            try {
                webView.evaluateJavascript(script, null);
            } catch (Exception ignored) {
                try {
                    webView.loadUrl("javascript:" + script);
                } catch (Exception ignored2) {
                }
            }
        });
    }

    private void replyToGame(String callBackId, String callName, Object callParam) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("code", 0);
        if (callBackId != null && !callBackId.isEmpty()) {
            envelope.addProperty("callBackId", callBackId);
        }
        if (callName != null && !callName.isEmpty()) {
            envelope.addProperty("callName", callName);
        }
        envelope.add("callParam", gson.toJsonTree(callParam != null ? callParam : new JsonObject()));
        final String json = envelope.toString();
        GameProbeLog.i("REPLY.sendMessageByClient", callName + " id=" + callBackId + " " + json);
        main.post(() -> {
            try {
                webView.evaluateJavascript(
                        "(function(){try{if(typeof sendMessageByClient==='function')"
                                + "{sendMessageByClient(" + json + ");}}catch(e){}})();",
                        null);
            } catch (Exception ignored) {
            }
        });
    }

    private JsonObject buildConfig() {
        JsonObject root = new JsonObject();
        if (session == null) return root;
        String nick = safeDisplayName(session.displayName, null);
        root.addProperty("appChannel", nullToEmpty(session.appChannel));
        root.addProperty("appId", session.appId);
        root.addProperty("userId", nullToEmpty(session.userId));
        root.addProperty("code", nullToEmpty(session.code));
        root.addProperty("roomId", nullToEmpty(session.roomId));
        root.addProperty("gameMode", session.gameMode != null ? session.gameMode : "2");
        root.addProperty("language", session.language != null ? session.language : "2");
        root.addProperty("gsp", session.gsp);
        root.addProperty("balance", session.balance);
        root.addProperty("coin", session.balance);
        root.addProperty("userMoney", session.balance);
        root.addProperty("nickname", nick);
        root.addProperty("nickName", nick);
        root.addProperty("name", nick);
        root.addProperty("avatar", nullToEmpty(session.avatarUrl));
        root.addProperty("headImg", nullToEmpty(session.avatarUrl));
        JsonObject gameConfig = new JsonObject();
        // Exact Mikoo BSGameWebDialog NativeBridge.getConfig:
        // sceneMode=0 → engine draws half-scene (room stays visible); host gives full WebView.
        gameConfig.addProperty("sceneMode", 0);
        gameConfig.addProperty("halfScreen", true);
        gameConfig.addProperty("currencyIcon", nullToEmpty(session.currencyIcon));
        gameConfig.addProperty("balance", session.balance);
        gameConfig.addProperty("coin", session.balance);
        gameConfig.addProperty("userMoney", session.balance);
        gameConfig.addProperty("nickname", nick);
        gameConfig.addProperty("nickName", nick);
        root.add("gameConfig", gameConfig);
        // Some BaiShun builds read the nested object under this alias.
        root.add("BSGameConfig", gameConfig);
        return root;
    }

    private JsonObject buildVersionApis() {
        JsonObject root = new JsonObject();
        JsonArray apis = new JsonArray();
        apis.add("getConfig");
        apis.add("gameRecharge");
        apis.add("destroy");
        apis.add("gameLoaded");
        root.add("apis", apis);
        return root;
    }

    private static String nullToEmpty(@Nullable String v) {
        return v != null ? v : "";
    }

    /** Never show internal UUID as in-game nickname. */
    public static boolean looksLikeUuid(@Nullable String v) {
        if (v == null) return false;
        String s = v.trim();
        if (s.isEmpty()) return false;
        return s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
                || s.matches("(?i)^[0-9a-f-]{20,}$");
    }

    public static String safeDisplayName(@Nullable String displayName, @Nullable String fallback) {
        String n = displayName != null ? displayName.trim() : "";
        if (!n.isEmpty() && !looksLikeUuid(n)) return n;
        String f = fallback != null ? fallback.trim() : "";
        if (!f.isEmpty() && !looksLikeUuid(f)) return f;
        return "Player";
    }

    private void parseAndDispatch(String json) {
        if (json == null || json.isEmpty()) return;
        GameProbeLog.bridge("gameBridge", "dispatch", json);
        try {
            JSONObject obj = new JSONObject(json);
            String callName = obj.optString("callName", "");
            String callBackId = obj.optString("callBackId", obj.optString("jsCallback", ""));
            switch (callName) {
                case "getConfig":
                    replyToGame(callBackId, callName, buildConfig());
                    break;
                case "versionSupportApis":
                    replyToGame(callBackId, callName, buildVersionApis());
                    break;
                case "getMeshData":
                    replyToGame(callBackId, callName, new JsonObject());
                    break;
                case "gameRecharge":
                    main.post(() -> {
                        if (callbacks != null) callbacks.onRecharge();
                    });
                    replyToGame(callBackId, callName, new JsonObject());
                    break;
                case "destroy":
                    main.post(() -> {
                        if (callbacks != null) callbacks.onDestroy();
                    });
                    replyToGame(callBackId, callName, new JsonObject());
                    break;
                case "gameLoaded":
                    main.post(() -> {
                        if (callbacks != null) callbacks.onLoaded();
                    });
                    replyToGame(callBackId, callName, new JsonObject());
                    break;
                case "nextGameRound":
                case "setInterruptTouchEvent":
                case "setGameSetting":
                    replyToGame(callBackId, callName, new JsonObject());
                    break;
                default:
                    GameProbeLog.w("BRIDGE.unknownCall", callName + " raw=" + json);
                    if (!callBackId.isEmpty()) {
                        replyToGame(callBackId, callName, new JsonObject());
                    }
                    break;
            }
        } catch (Exception e) {
            GameProbeLog.e("BRIDGE.parseFail", e.getMessage() + " raw=" + json);
        }
    }

    private void dispatchNativeParams(String params) {
        if (params == null || params.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(params);
            String callName = obj.optString("callName", "");
            String callBackId = obj.optString("callBackId", obj.optString("jsCallback", ""));
            if ("getConfig".equals(callName) || (!callName.isEmpty() && callBackId.isEmpty())) {
                if ("getConfig".equals(callName) || obj.has("jsCallback")) {
                    replyNativeConfig(callBackId, buildConfig());
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        parseAndDispatch(params);
    }

    public final class ProbeBridge {
        @JavascriptInterface
        public void log(String msg) {
            GameProbeLog.js("probe", msg);
        }
    }

    public final class NativeBridge {
        @JavascriptInterface
        public String getBalance() {
            String bal = String.valueOf(currentBalance());
            GameProbeLog.bridge("NativeBridge", "getBalance", bal);
            return bal;
        }

        @JavascriptInterface
        public String getUserMoney() {
            return getBalance();
        }

        @JavascriptInterface
        public void getConfig(String params) {
            GameProbeLog.bridge("NativeBridge", "getConfig", params);
            replyMeshCallback(params, buildConfig());
        }

        @JavascriptInterface
        public void versionSupportApis(String params) {
            GameProbeLog.bridge("NativeBridge", "versionSupportApis", params);
            replyMeshCallback(params, buildVersionApis());
        }

        @JavascriptInterface
        public void getMeshData(String params) {
            GameProbeLog.bridge("NativeBridge", "getMeshData", params);
            replyMeshCallback(params, new JsonObject());
        }

        @JavascriptInterface
        public void destroy(String params) {
            GameProbeLog.bridge("NativeBridge", "destroy", params);
            replyMeshCallback(params, new JsonObject());
            main.post(() -> {
                if (callbacks != null) callbacks.onDestroy();
            });
        }

        @JavascriptInterface
        public void gameLoaded(String params) {
            GameProbeLog.bridge("NativeBridge", "gameLoaded", params);
            replyMeshCallback(params, new JsonObject());
            main.post(() -> {
                if (callbacks != null) callbacks.onLoaded();
            });
        }

        @JavascriptInterface
        public void gameRecharge(String params) {
            GameProbeLog.bridge("NativeBridge", "gameRecharge", params);
            replyMeshCallback(params, new JsonObject());
            main.post(() -> {
                if (callbacks != null) callbacks.onRecharge();
            });
        }

        /** BaiShun MeshH5 expects window[jsCallback](data). */
        private void replyMeshCallback(String params, JsonObject payload) {
            try {
                JSONObject obj = new JSONObject(params != null ? params : "{}");
                String jsCallback = obj.optString("jsCallback", obj.optString("callBackId", ""));
                if (!jsCallback.isEmpty()) {
                    replyNativeConfig(jsCallback, payload);
                } else {
                    String callName = obj.optString("callName", "");
                    replyToGame(obj.optString("callBackId", ""), callName, payload);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public final class GameBridge {
        @JavascriptInterface
        public void sendMessageByJs(String json) {
            GameProbeLog.bridge("gameBridge", "sendMessageByJs", json);
            parseAndDispatch(json);
        }
    }

    public static boolean isMikooSlot(@Nullable String mode, @Nullable String url) {
        if (mode != null && "mikoo_slot".equalsIgnoreCase(mode)) return true;
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("/games/mikoo/") || lower.contains("mikoo_slot");
    }

    public static String extractGameId(@Nullable String url) {
        if (url == null) return null;
        String lower = url.toLowerCase(Locale.US);
        int idx = lower.indexOf("/games/mikoo/");
        if (idx < 0) return null;
        String tail = url.substring(idx + "/games/mikoo/".length());
        int slash = tail.indexOf('/');
        return slash > 0 ? tail.substring(0, slash) : tail;
    }

    public static String appendSessionParams(String url, SlotGameDtos.SessionDto session) {
        if (url == null || url.isEmpty() || session == null) return url;
        Uri.Builder builder = Uri.parse(url).buildUpon();
        builder.appendQueryParameter("appChannel", nullToEmpty(session.appChannel));
        builder.appendQueryParameter("appId", String.valueOf(session.appId));
        builder.appendQueryParameter("userId", nullToEmpty(session.userId));
        builder.appendQueryParameter("code", nullToEmpty(session.code));
        if (session.roomId != null && !session.roomId.isEmpty()) {
            builder.appendQueryParameter("roomId", session.roomId);
        }
        builder.appendQueryParameter("gameMode", session.gameMode != null ? session.gameMode : "2");
        builder.appendQueryParameter("language", session.language != null ? session.language : "2");
        builder.appendQueryParameter("gsp", String.valueOf(session.gsp));
        builder.appendQueryParameter("balance", String.valueOf(Math.max(0L, session.balance)));
        builder.appendQueryParameter("coin", String.valueOf(Math.max(0L, session.balance)));
        builder.appendQueryParameter("userMoney", String.valueOf(Math.max(0L, session.balance)));
        String nick = safeDisplayName(session.displayName, null);
        if (!nick.isEmpty()) {
            builder.appendQueryParameter("nickname", nick);
        }
        // Mikoo room sessions do not force adaption=0; sceneMode=0 handles half-UI.
        if (session.gameType > 0) {
            builder.appendQueryParameter("gameType", String.valueOf(session.gameType));
        }
        if (session.routeUrl != null && !session.routeUrl.isEmpty()) {
            // BaiShun DOMAIN must be origin only — path like /games/route/ breaks
            // jeho-force-local WebSocket host rewrite (LOCAL_WS becomes .../games/route).
            String domain = session.routeUrl;
            try {
                Uri d = Uri.parse(domain);
                if (d.getScheme() != null && d.getHost() != null) {
                    domain = d.getScheme() + "://" + d.getHost();
                    if (d.getPort() > 0) domain += ":" + d.getPort();
                    domain += "/";
                }
            } catch (Exception ignored) {
            }
            builder.appendQueryParameter("DOMAIN", domain);
        } else if (session.containerUrl != null && !session.containerUrl.isEmpty()) {
            // Hash games use WSS container; BaiShun prefers HTTP route base when provided.
            builder.appendQueryParameter("DOMAIN", session.containerUrl);
        }
        return builder.build().toString();
    }

    public static void openWallet(Activity activity) {
        com.Dramizo.Series.util.BalanceRedirect.openRecharge(activity);
    }
}
