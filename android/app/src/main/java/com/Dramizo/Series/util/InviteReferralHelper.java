package com.Dramizo.Series.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.Dramizo.Series.AuraLiveApp;
import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.R;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

/**
 * Play Store install-referrer + deep-link invite / room joins.
 * Share room: {@code /open/room/{id}} → app if installed, else Play with referrer;
 * after install the referrer opens the same room post-login.
 */
public final class InviteReferralHelper {
    private static final String TAG = "InviteReferral";
    private static final String PREFS = "invite_referral";
    private static final String KEY_PENDING = "pending_code";
    private static final String KEY_PENDING_ROOM = "pending_room_id";
    private static final String KEY_REFERRER_DONE = "referrer_fetched";
    private static final String KEY_PROMPT_DONE = "invite_prompt_done";

    public static final String EXTRA_PENDING_INVITE = "pending_invite_code";
    public static final String EXTRA_OPEN_INVITE = "open_invite";
    public static final String EXTRA_PENDING_ROOM = "pending_room_id";

    private InviteReferralHelper() {}

    public static String playStoreUrl(@Nullable String code) {
        StringBuilder url = new StringBuilder("https://play.google.com/store/apps/details?id=")
                .append(BuildConfig.APPLICATION_ID);
        String clean = normalizeCode(code);
        if (clean != null) {
            String referrer = "utm_source=invite&utm_medium=share&utm_content=" + clean;
            url.append("&referrer=").append(Uri.encode(referrer));
        }
        return url.toString();
    }

    /** Universal / App Link that opens the room (or Play Store when app missing). */
    public static String roomOpenUrl(@Nullable String roomId) {
        String id = normalizeRoomId(roomId);
        if (id == null) return ApiOrigin.origin() + "/open/room";
        return ApiOrigin.origin() + "/open/room/" + Uri.encode(id);
    }

    public static String roomPlayStoreUrl(@Nullable String roomId) {
        StringBuilder url = new StringBuilder("https://play.google.com/store/apps/details?id=")
                .append(BuildConfig.APPLICATION_ID);
        String id = normalizeRoomId(roomId);
        if (id != null) {
            String referrer = "utm_source=room&utm_medium=share&utm_content=" + id;
            url.append("&referrer=").append(Uri.encode(referrer));
        }
        return url.toString();
    }

    public static String inviteDeepLink(@Nullable String code) {
        String clean = normalizeCode(code);
        if (clean == null) return ApiOrigin.origin() + "/open/invite";
        return ApiOrigin.origin() + "/open/invite/" + Uri.encode(clean);
    }

    public static String shareMessage(Context context, @Nullable String code) {
        String app = context != null
                ? context.getString(R.string.app_name)
                : "JEHO CHAT";
        String clean = normalizeCode(code);
        if (clean == null) clean = "----";
        return "انضم إليّ على " + app + "!\n"
                + "رمز دعوتي: " + clean + "\n"
                + inviteDeepLink(clean);
    }

    public static void savePendingCode(Context context, @Nullable String code) {
        String clean = normalizeCode(code);
        if (clean == null || context == null) return;
        prefs(context).edit()
                .putString(KEY_PENDING, clean)
                .putBoolean(KEY_PROMPT_DONE, false)
                .apply();
    }

    public static void savePendingRoom(Context context, @Nullable String roomId) {
        String id = normalizeRoomId(roomId);
        if (id == null || context == null) return;
        prefs(context).edit().putString(KEY_PENDING_ROOM, id).apply();
    }

    @Nullable
    public static String peekPendingRoom(Context context) {
        if (context == null) return null;
        return normalizeRoomId(prefs(context).getString(KEY_PENDING_ROOM, null));
    }

    @Nullable
    public static String takePendingRoom(Context context) {
        String id = peekPendingRoom(context);
        if (context != null) {
            prefs(context).edit().remove(KEY_PENDING_ROOM).apply();
        }
        return id;
    }

    @Nullable
    public static String peekPendingCode(Context context) {
        if (context == null) return null;
        return normalizeCode(prefs(context).getString(KEY_PENDING, null));
    }

    /** True when a pending invite should open Invitation once after login. */
    public static boolean shouldAutoOpenInvite(Context context) {
        if (context == null) return false;
        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_PROMPT_DONE, false)) return false;
        return normalizeCode(p.getString(KEY_PENDING, null)) != null;
    }

    public static void markInvitePrompted(Context context) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_PROMPT_DONE, true).apply();
    }

    /** Returns and clears the pending code (after successful bind or cancel). */
    @Nullable
    public static String takePendingCode(Context context) {
        String code = peekPendingCode(context);
        if (context != null) {
            prefs(context).edit().remove(KEY_PENDING).apply();
        }
        return code;
    }

    public static void clearPendingCode(Context context) {
        if (context == null) return;
        prefs(context).edit().remove(KEY_PENDING).apply();
    }

    /** Parse invite code from app-link / custom-scheme URI. */
    @Nullable
    public static String parseCodeFromUri(@Nullable Uri data) {
        if (data == null) return null;
        String scheme = data.getScheme() != null ? data.getScheme() : "";
        String host = data.getHost() != null ? data.getHost() : "";
        String path = data.getPath() != null ? data.getPath() : "";

        String q = data.getQueryParameter("code");
        if (q == null) q = data.getQueryParameter("invite");
        if (q == null) q = data.getQueryParameter("utm_content");
        String fromQuery = normalizeCode(q);
        if (fromQuery != null) return fromQuery;

        if ("invite".equalsIgnoreCase(host)
                || "hamslive".equalsIgnoreCase(scheme)
                || "jehochat".equalsIgnoreCase(scheme)) {
            String seg = data.getLastPathSegment();
            if (seg != null && !"invite".equalsIgnoreCase(seg)) {
                String fromSeg = normalizeCode(seg);
                if (fromSeg != null) return fromSeg;
            }
        }

        if (path.startsWith("/open/invite")) {
            String rest = path.length() > "/open/invite/".length()
                    ? path.substring("/open/invite/".length()).split("[/?#]")[0]
                    : "";
            return normalizeCode(rest);
        }
        return null;
    }

    @Nullable
    public static String parseCodeFromReferrer(@Nullable String referrer) {
        if (TextUtils.isEmpty(referrer)) return null;
        try {
            Uri uri = Uri.parse("https://local/?" + referrer);
            String source = uri.getQueryParameter("utm_source");
            // Room joins use utm_source=room — don't treat room UUID as invite code.
            if ("room".equalsIgnoreCase(source)) return null;
            String content = uri.getQueryParameter("utm_content");
            if (content == null) content = uri.getQueryParameter("invite");
            if (content == null) content = uri.getQueryParameter("code");
            String parsed = normalizeCode(content);
            if (parsed != null) return parsed;
            if (referrer.contains("=")) {
                for (String part : referrer.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq <= 0) continue;
                    String key = part.substring(0, eq).trim();
                    String val = part.substring(eq + 1).trim();
                    if ("utm_content".equalsIgnoreCase(key)
                            || "invite".equalsIgnoreCase(key)
                            || "code".equalsIgnoreCase(key)) {
                        return normalizeCode(Uri.decode(val));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    public static String parseRoomFromReferrer(@Nullable String referrer) {
        if (TextUtils.isEmpty(referrer)) return null;
        try {
            Uri uri = Uri.parse("https://local/?" + referrer);
            String source = uri.getQueryParameter("utm_source");
            String content = uri.getQueryParameter("utm_content");
            if (content == null) content = uri.getQueryParameter("room");
            if (content == null) content = uri.getQueryParameter("roomId");
            if ("room".equalsIgnoreCase(source) || content != null) {
                String id = normalizeRoomId(content);
                if (id != null) return id;
            }
            if (referrer.contains("=")) {
                for (String part : referrer.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq <= 0) continue;
                    String key = part.substring(0, eq).trim();
                    String val = part.substring(eq + 1).trim();
                    if ("room".equalsIgnoreCase(key) || "roomId".equalsIgnoreCase(key)
                            || ("utm_content".equalsIgnoreCase(key)
                            && referrer.toLowerCase().contains("utm_source=room"))) {
                        return normalizeRoomId(Uri.decode(val));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** One-shot Play Install Referrer read (safe to call from Application). */
    public static void captureInstallReferrer(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        if (p.getBoolean(KEY_REFERRER_DONE, false)) return;
        if (peekPendingCode(app) != null || peekPendingRoom(app) != null) {
            p.edit().putBoolean(KEY_REFERRER_DONE, true).apply();
            return;
        }

        final InstallReferrerClient client = InstallReferrerClient.newBuilder(app).build();
        try {
            client.startConnection(new InstallReferrerStateListener() {
                @Override
                public void onInstallReferrerSetupFinished(int responseCode) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            ReferrerDetails details = client.getInstallReferrer();
                            String raw = details != null ? details.getInstallReferrer() : null;
                            String code = parseCodeFromReferrer(raw);
                            String room = parseRoomFromReferrer(raw);
                            if (code != null) {
                                Log.i(TAG, "Install referrer invite captured");
                                savePendingCode(app, code);
                            } else if (room != null) {
                                Log.i(TAG, "Install referrer room captured");
                                savePendingRoom(app, room);
                            } else {
                                Log.d(TAG, "Install referrer present but no invite/room: " + raw);
                            }
                        }
                    } catch (RemoteException e) {
                        Log.w(TAG, "Referrer read failed", e);
                    } finally {
                        prefs(app).edit().putBoolean(KEY_REFERRER_DONE, true).apply();
                        try {
                            client.endConnection();
                        } catch (Exception ignored) {
                        }
                    }
                }

                @Override
                public void onInstallReferrerServiceDisconnected() {
                    // no-op; Play may reconnect later — we only try once per install flag
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Referrer client failed", e);
            p.edit().putBoolean(KEY_REFERRER_DONE, true).apply();
        }
    }

    @Nullable
    private static String normalizeCode(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || "----".equals(s)) return null;
        // Strip accidental path/query leftovers
        int cut = s.indexOf('/');
        if (cut >= 0) s = s.substring(0, cut);
        cut = s.indexOf('?');
        if (cut >= 0) s = s.substring(0, cut);
        s = s.trim();
        if (s.length() < 2 || s.length() > 64) return null;
        return s;
    }

    @Nullable
    private static String normalizeRoomId(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        int cut = s.indexOf('?');
        if (cut >= 0) s = s.substring(0, cut);
        cut = s.indexOf('#');
        if (cut >= 0) s = s.substring(0, cut);
        s = s.trim();
        if (s.length() < 8 || s.length() > 64) return null;
        if (!s.matches("(?i)[0-9a-f\\-]{8,}")) return null;
        return s;
    }

    private static SharedPreferences prefs(Context context) {
        Context storage = AuraLiveApp.storageContext(context);
        if (storage == null) storage = context.getApplicationContext();
        return storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
