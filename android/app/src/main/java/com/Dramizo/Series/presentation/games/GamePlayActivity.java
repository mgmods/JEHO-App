package com.Dramizo.Series.presentation.games;

import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.MikooGameBridge;
import com.Dramizo.Series.util.MikooHashBridge;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.api.SlotGameApi;
import com.Dramizo.Series.data.remote.api.WalletApi;
import com.Dramizo.Series.data.remote.dto.SlotGameDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.GameUrls;
import com.bumptech.glide.Glide;

import java.util.Locale;

/**
 * External games-tab host: immersive black stage, WebView sized to Cocos design
 * aspect (~750×1334). Balance stays inside the game UI (Mikoo style).
 */
public class GamePlayActivity extends ThemedActivity {
    public static final String EXTRA_URL = "play_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_COVER_URL = "cover_url";
    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_ROOM_ID = "room_id";

    /** Typical Mikoo portrait canvas (crash/luck-car/megaways). */
    private static final float DESIGN_W = 750f;
    private static final float DESIGN_H = 1334f;
    /** Fishing Cocos build is landscape (see fishing/src/settings.js). */
    private static final float FISHING_W = 1334f;
    private static final float FISHING_H = 750f;

    private WebView web;
    private View loadingOverlay;
    private View gameStage;
    private ProgressBar progress;
    private String pendingUrl;
    private boolean triedRemoteFallback;
    private String mode;
    private String gameId;
    private String roomId;
    private SlotGameDtos.SessionDto slotSession;
    private MikooGameBridge mikooBridge;
    private MikooHashBridge hashBridge;
    private SlotGameApi slotApi;
    private WalletApi walletApi;
    private boolean destroyed;
    private boolean gameReady;
    private boolean finishingAfterAd;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GameAdsHelper gameAds;

    private final Runnable heartbeatSlot = new Runnable() {
        @Override
        public void run() {
            if (destroyed || slotSession == null || slotSession.sessionId == null || slotApi == null) return;
            ContainerProvider.from(GamePlayActivity.this).getIoExecutor().execute(() ->
                    ApiCall.execute(slotApi.heartbeat(slotSession.sessionId)));
            handler.postDelayed(this, 15_000);
        }
    };

    private final Runnable loadingWatchdog = () -> {
        if (destroyed || gameReady) return;
        Toast.makeText(this, "اللعبة تأخذ وقتاً… تحقق من الاتصال", Toast.LENGTH_LONG).show();
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveWindow();
        setContentView(R.layout.activity_game_play);

        AppContainer c = ContainerProvider.from(this);
        slotApi = c.getSlotGameApi();
        walletApi = c.getWalletApi();
        gameAds = GameAdsHelper.get(this);
        gameAds.refreshConfig(this);
        gameAds.preload(this);

        String rawUrl = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String coverUrl = getIntent().getStringExtra(EXTRA_COVER_URL);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        pendingUrl = GameUrls.resolve(this, rawUrl);

        if (gameId == null || gameId.isEmpty()) {
            gameId = MikooGameBridge.extractGameId(pendingUrl);
        }
        if (mode == null || mode.isEmpty()) {
            mode = MikooGameBridge.isMikooSlot(null, pendingUrl) ? "mikoo_slot" : null;
        }

        TextView tv = findViewById(R.id.tvTitle);
        ImageView back = findViewById(R.id.btnBack);
        ImageView cover = findViewById(R.id.imgGameCover);
        progress = findViewById(R.id.progressGame);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        gameStage = findViewById(R.id.gameStage);
        web = findViewById(R.id.webView);
        View matchOverlay = findViewById(R.id.matchOverlay);
        if (matchOverlay != null) matchOverlay.setVisibility(View.GONE);

        if (tv != null) tv.setText(title != null ? title : getString(R.string.nav_games));
        boolean mikoo = "mikoo_slot".equalsIgnoreCase(mode) || MikooGameBridge.isMikooSlot(mode, pendingUrl);
        // Keep close above status bar notch.
        int topPad = Math.max(dp(10), statusBarInset());
        if (mikoo) {
            back.setVisibility(View.GONE);
        }
        back.setOnClickListener(v -> requestClose());
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) back.getLayoutParams();
        lp.topMargin = topPad;
        back.setLayoutParams(lp);

        TextView btnReward = findViewById(R.id.btnGameRewardAd);
        if (btnReward != null) {
            btnReward.setVisibility(View.GONE);
            ViewGroup.MarginLayoutParams rlp = (ViewGroup.MarginLayoutParams) btnReward.getLayoutParams();
            rlp.topMargin = topPad;
            btnReward.setLayoutParams(rlp);
            handler.postDelayed(() -> {
                if (destroyed) return;
                if (gameAds != null && gameAds.isRewardedEnabled()) {
                    int coins = gameAds.rewardedCoins();
                    if (coins > 0) {
                        btnReward.setText(getString(R.string.game_reward_ad) + " +" + coins);
                    }
                    btnReward.setVisibility(View.VISIBLE);
                }
            }, 1_200L);
            btnReward.setOnClickListener(v -> {
                if (gameAds == null) return;
                gameAds.showRewardedForCoins(this, new GameAdsHelper.RewardUiCallback() {
                    @Override
                    public void onPreparing() {
                        Toast.makeText(GamePlayActivity.this, R.string.loading, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResult(boolean credited, int coins, long balance, @Nullable String message) {
                        GameAdsHelper.toastReward(GamePlayActivity.this, credited, coins, message);
                    }
                });
            });
        }

        if (coverUrl != null && !coverUrl.isEmpty()) {
            Glide.with(this)
                    .load(AssetCatalog.absoluteUrl(coverUrl))
                    .placeholder(ImagePlaceholder.game())
                    .error(ImagePlaceholder.game())
                    .into(cover);
        }

        // Size after first layout pass so design aspect matches real viewport.
        boolean fishing = "fishing".equalsIgnoreCase(gameId);
        gameStage.post(() -> layoutGameStage(mikoo || isDiceOrWheel(pendingUrl), fishing));
        configureWebView(mikoo);
        showLoading(true);
        handler.postDelayed(loadingWatchdog, 45_000);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                // Keep splash until gameLoaded for Mikoo — HTML progress alone is not "ready".
                if (!mikoo && progress != null && newProgress >= 100) {
                    showLoading(false);
                }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                injectForceLocalHosts(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectForceLocalHosts(view);
                injectCanvasFit(view);
                if (slotSession != null) {
                    injectRechargeHook(view);
                    injectBalanceSync(view);
                }
                if (!mikoo) {
                    showLoading(false);
                } else {
                    // Hash games rarely call NativeBridge.gameLoaded — don't stay stuck forever.
                    handler.postDelayed(() -> {
                        if (!destroyed && !gameReady) showLoading(false);
                    }, 2800);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && isExternalBaiShunHost(request.getUrl())) {
                    Uri u = request.getUrl();
                    String path = u != null && u.getPath() != null ? u.getPath() : "";
                    String body;
                    if (path.contains("get_addr")) {
                        String slug = gameId != null && !gameId.isEmpty() ? gameId : "cleopatra-slot";
                        String origin = com.Dramizo.Series.util.ApiOrigin.origin();
                        String wsOrigin = origin.replace("https://", "wss://").replace("http://", "ws://");
                        body = "{\"code\":200,\"data\":{"
                                + "\"http_addr\":\"" + origin + "/games/route/\","
                                + "\"ws_addr\":\"" + wsOrigin + "/games/ws/" + slug + "\"}}";
                    } else {
                        body = "{\"code\":200,\"data\":true}";
                    }
                    return new WebResourceResponse(
                            "application/json",
                            "utf-8",
                            new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && !request.isForMainFrame()) return;
                fallbackIfNeeded(view);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                fallbackIfNeeded(view);
            }
        });

        if (isDiceOrWheel(pendingUrl)) {
            web.setBackgroundColor(0xFF0A0E27);
            web.addJavascriptInterface(new AuraBridge(), "AuraBridge");
            pendingUrl = appendGameAuth(pendingUrl);
            web.loadUrl(pendingUrl);
            return;
        }

        if (mikoo && gameId != null) {
            startMikooSession();
            return;
        }

        web.addJavascriptInterface(new AuraBridge(), "AuraBridge");
        pendingUrl = appendGameAuth(pendingUrl);
        web.loadUrl(pendingUrl);
    }

    private void applyImmersiveWindow() {
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Exception ignored) {
        }
    }

    /** Letterbox Mikoo design canvas so UI is not cropped on tall phones. */
    private void layoutGameStage(boolean aspectFit) {
        layoutGameStage(aspectFit, false);
    }

    private void layoutGameStage(boolean aspectFit, boolean fishingLandscape) {
        if (gameStage == null) return;
        View root = findViewById(android.R.id.content);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        if (root != null && root.getWidth() > 0) {
            screenW = root.getWidth();
            screenH = root.getHeight();
        }
        FrameLayout.LayoutParams lp;
        if (aspectFit) {
            float designW = fishingLandscape ? FISHING_W : DESIGN_W;
            float designH = fishingLandscape ? FISHING_H : DESIGN_H;
            float scale = Math.min(screenW / designW, screenH / designH);
            int w = Math.max(1, Math.round(designW * scale));
            int h = Math.max(1, Math.round(designH * scale));
            lp = new FrameLayout.LayoutParams(w, h, android.view.Gravity.CENTER);
        } else {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER);
        }
        gameStage.setLayoutParams(lp);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(boolean mikoo) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // Wide-viewport overview mode breaks Cocos canvas sizing → stuck loading / ugly scale.
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(!mikoo);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 JEHO CHAT/1.0");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }
        web.setBackgroundColor(Color.BLACK);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (progress != null) {
            progress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (!show) {
            gameReady = true;
            handler.removeCallbacks(loadingWatchdog);
        }
    }

    private void markGameReady() {
        showLoading(false);
        injectForceLocalHosts(web);
        injectCanvasFit(web);
        injectRechargeHook(web);
        injectBalanceSync(web);
    }

    private void injectBalanceSync(WebView view) {
        if (view == null || slotSession == null) return;
        long bal = Math.max(0L, slotSession.balance);
        String uid = slotSession.userId != null ? slotSession.userId : "";
        // Push coins locally — do NOT call walletUpdate early (it ReqBalance's WS and
        // used to wipe UI to 0 before server auth finished).
        String js = "(function(){try{var b=" + bal + ";var uid='" + uid.replace("'", "") + "';"
                + "function push(){try{"
                + "if(window.androidJsObj){"
                + "try{if(androidJsObj.getBalance)b=Number(androidJsObj.getBalance())||b;}catch(e){}"
                + "try{if(androidJsObj.getUserMoney)b=Number(androidJsObj.getUserMoney())||b;}catch(e){}"
                + "}"
                + "if(window.NativeBridge){"
                + "try{if(NativeBridge.getBalance)b=Number(NativeBridge.getBalance())||b;}catch(e){}"
                + "}"
                + "try{if(window.PlayerData&&PlayerData.SetCoins)PlayerData.SetCoins(b);}catch(e){}"
                + "try{if(window.CoinSafe){CoinSafe.coin=b;CoinSafe.balance=b;}}catch(e){}"
                + "if(typeof SelfBalance==='function')SelfBalance({balance:b,coin:b,gold:b,userMoney:b,userId:uid});"
                + "}catch(e){}}push();setTimeout(push,1200);setTimeout(push,2800);"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void startMikooSession() {
        showLoading(true);
        SlotGameDtos.StartRequest req = new SlotGameDtos.StartRequest();
        req.gameId = gameId;
        req.roomId = roomId;
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<SlotGameDtos.SessionDto> r = ApiCall.execute(slotApi.startSession(req));
            if (destroyed) return;
            runOnUiThread(() -> {
                if (!r.success || r.data == null) {
                    showLoading(false);
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                slotSession = r.data;
                if (slotSession.balance <= 0) {
                    Toast.makeText(this,
                            "رصيدك 0 — اشحن كوينز للعب",
                            Toast.LENGTH_LONG).show();
                    MikooGameBridge.openWallet(GamePlayActivity.this);
                }
                hashBridge = new MikooHashBridge(slotSession, new MikooHashBridge.Callbacks() {
                    @Override
                    public void onClose() {
                        requestClose();
                    }

                    @Override
                    public void onRecharge() {
                        Toast.makeText(GamePlayActivity.this,
                                "رصيد غير كافٍ — افتح الشحن",
                                Toast.LENGTH_SHORT).show();
                        MikooHashBridge.openWallet(GamePlayActivity.this);
                    }
                });
                MikooHashBridge.attach(web, hashBridge);

                mikooBridge = new MikooGameBridge(web, slotSession, new MikooGameBridge.Callbacks() {
                    @Override
                    public void onDestroy() {
                        requestClose();
                    }

                    @Override
                    public void onRecharge() {
                        Toast.makeText(GamePlayActivity.this,
                                "رصيد غير كافٍ — افتح الشحن",
                                Toast.LENGTH_SHORT).show();
                        MikooGameBridge.openWallet(GamePlayActivity.this);
                    }

                    @Override
                    public void onLoaded() {
                        markGameReady();
                    }
                });
                mikooBridge.attach();
                pendingUrl = MikooGameBridge.appendSessionParams(pendingUrl, slotSession);
                handler.post(heartbeatSlot);
                // Bridges must exist before first document load.
                web.loadUrl(pendingUrl);
            });
        });
    }

    /** Block leftover BaiShun/jieyou hosts — everything must hit api.adnova.bbs.tr. */
    private static boolean isExternalBaiShunHost(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        return h.endsWith("jieyou.shop")
                || h.endsWith("sruner.com")
                || h.endsWith("zkruner.com");
    }

    /** Rewrite WebSocket/fetch/XHR that still target jieyou onto JEHO. */
    private void injectForceLocalHosts(WebView view) {
        if (view == null) return;
        String origin = com.Dramizo.Series.util.ApiOrigin.origin();
        String wsOrigin = origin.replace("https://", "wss://").replace("http://", "ws://");
        String js = "(function(){try{"
                + "if(window.__jehoForceLocal)return;window.__jehoForceLocal=1;"
                + "var LOCAL='" + origin + "',LOCAL_WS='" + wsOrigin + "';"
                + "var BAD=/(jieyou\\.shop|sruner\\.com|zkruner\\.com)/i;"
                + "function rewrite(u){if(u==null)return u;var s=String(u);if(!BAD.test(s))return s;"
                + "try{var a=document.createElement('a');a.href=s;"
                + "var path=(a.pathname||'/')+(a.search||'')+(a.hash||'');"
                + "return /^wss?:/i.test(s)?(LOCAL_WS+path):(LOCAL+path);"
                + "}catch(e){return s.replace(/^https?:\\/\\/[^/]+/i,LOCAL).replace(/^wss?:\\/\\/[^/]+/i,LOCAL_WS);}}"
                + "try{var q=new URLSearchParams(location.search||'');var dom=q.get('DOMAIN')||q.get('domain');"
                + "if(dom&&!BAD.test(dom)){LOCAL=String(dom).replace(/\\/$/,'');"
                + "if(/^https?:/i.test(LOCAL))LOCAL_WS=LOCAL.replace(/^https:/i,'wss:').replace(/^http:/i,'ws:');}}"
                + "catch(e){}"
                + "var OW=window.WebSocket;if(OW){window.WebSocket=function(u,p){u=rewrite(u);"
                + "return p!==undefined?new OW(u,p):new OW(u);};"
                + "window.WebSocket.prototype=OW.prototype;"
                + "window.WebSocket.CONNECTING=OW.CONNECTING;window.WebSocket.OPEN=OW.OPEN;"
                + "window.WebSocket.CLOSING=OW.CLOSING;window.WebSocket.CLOSED=OW.CLOSED;}"
                + "if(window.fetch){var of=window.fetch.bind(window);window.fetch=function(i,n){"
                + "if(typeof i==='string')i=rewrite(i);"
                + "else if(i&&typeof Request!=='undefined'&&i instanceof Request)i=new Request(rewrite(i.url),i);"
                + "return of(i,n);};}"
                + "var XO=window.XMLHttpRequest;if(XO&&XO.prototype){var op=XO.prototype.open;"
                + "XO.prototype.open=function(){var a=Array.prototype.slice.call(arguments);"
                + "if(a.length>1)a[1]=rewrite(a[1]);return op.apply(this,a);};}"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    /** Only open recharge on insufficient — balance stays inside Cocos (Mikoo style). */
    private void injectRechargeHook(WebView view) {
        if (view == null) return;
        String js = "(function(){try{"
                + "if(window.__jehoEcoHook)return;window.__jehoEcoHook=1;"
                + "function jehoOpenCharge(){try{"
                + "if(window.androidJsObj&&androidJsObj.openChargePage)androidJsObj.openChargePage();"
                + "else if(window.NativeBridge&&NativeBridge.gameRecharge)NativeBridge.gameRecharge('{}');"
                + "else if(window.AuraBridge&&AuraBridge.openWallet)AuraBridge.openWallet();"
                + "}catch(e){}}"
                + "function jehoNeed(t){if(!t)return false;t=String(t);"
                + "return t.indexOf('Insufficient')>=0||t.indexOf('NeedRecharge')>=0"
                + "||t.indexOf('need_recharge')>=0||t.indexOf('\"Code\":2')>=0||t.indexOf('\"Code\": 2')>=0;}"
                + "var OW=window.WebSocket;if(OW&&!OW.__jehoEcoWrap){"
                + "function W(u,p){var ws=p!==undefined?new OW(u,p):new OW(u);"
                + "ws.addEventListener('message',function(ev){try{"
                + "var d=ev&&ev.data;if(typeof d==='string'&&jehoNeed(d))jehoOpenCharge();"
                + "}catch(e){}});return ws;}"
                + "W.prototype=OW.prototype;"
                + "W.CONNECTING=OW.CONNECTING;W.OPEN=OW.OPEN;"
                + "W.CLOSING=OW.CLOSING;W.CLOSED=OW.CLOSED;W.__jehoEcoWrap=1;window.WebSocket=W;}"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private void injectCanvasFit(WebView view) {
        if (view == null) return;
        String js = "(function(){try{"
                + "var d=document.documentElement,b=document.body;"
                + "if(d){d.style.height='100%';d.style.width='100%';d.style.margin='0';d.style.overflow='hidden';}"
                + "if(b){b.style.height='100%';b.style.width='100%';b.style.margin='0';"
                + "b.style.overflow='hidden';b.style.background='#000';}"
                + "var c=document.getElementById('GameCanvas')||document.querySelector('canvas');"
                + "if(c){c.style.width='100%';c.style.height='100%';c.style.display='block';}"
                + "var gc=document.getElementById('Cocos2dGameContainer');"
                + "if(gc){gc.style.width='100%';gc.style.height='100%';}"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
        injectDiceWheelFit(view);
    }

    private void refreshWalletAfterRecharge() {
        if (walletApi == null || slotSession == null) return;
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r = ApiCall.execute(walletApi.getWallet());
            if (destroyed || !r.success || r.data == null) return;
            long coins = Math.max(0L, r.data.coins);
            runOnUiThread(() -> {
                if (slotSession != null) slotSession.balance = coins;
                if (mikooBridge != null) mikooBridge.notifyWalletUpdate();
            });
        });
    }

    private void fallbackIfNeeded(WebView view) {
        if (triedRemoteFallback || pendingUrl == null) {
            showLoading(false);
            Toast.makeText(this, "تعذر فتح اللعبة", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pendingUrl.startsWith(GameUrls.ASSET_BASE)) {
            showLoading(false);
            Toast.makeText(this, "تعذر فتح اللعبة", Toast.LENGTH_SHORT).show();
            return;
        }
        String file = Uri.parse(pendingUrl).getLastPathSegment();
        if (file == null || file.isEmpty()) {
            showLoading(false);
            return;
        }
        triedRemoteFallback = true;
        pendingUrl = appendGameAuth(GameUrls.remote(file));
        view.loadUrl(pendingUrl);
    }

    private static boolean isDiceOrWheel(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("lucky-wheel") || lower.contains("dice.html");
    }

    private String appendGameAuth(String url) {
        if (url == null || url.isEmpty()) return url;
        Uri.Builder builder = Uri.parse(url).buildUpon();
        if (roomId != null && !roomId.isEmpty()) builder.appendQueryParameter("roomId", roomId);
        builder.appendQueryParameter("apiBase", com.Dramizo.Series.util.ApiOrigin.apiV1());
        if (isDiceOrWheel(url)) {
            builder.appendQueryParameter("embed", "1");
            builder.appendQueryParameter("v", "20260728c2");
        }
        return builder.build().toString();
    }

    private void injectDiceWheelFit(WebView view) {
        if (view == null || pendingUrl == null || !isDiceOrWheel(pendingUrl)) return;
        String js = "(function(){try{document.documentElement.classList.add('embed');"
                + "var c=document.querySelector('.game-container');if(!c)return;"
                + "function fit(){c.style.transform='none';c.style.width='100%';"
                + "var s=Math.min(1,window.innerHeight/Math.max(c.scrollHeight,1),"
                + "window.innerWidth/Math.max(c.scrollWidth,1));if(s<0.58)s=0.58;"
                + "c.style.transform='scale('+s+')';}fit();window.addEventListener('resize',fit);"
                + "}catch(e){}})();";
        try {
            view.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int statusBarInset() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) return getResources().getDimensionPixelSize(resId);
        return dp(24);
    }

    public class AuraBridge {
        @JavascriptInterface
        public String getAccessToken() {
            String token = ContainerProvider.from(GamePlayActivity.this)
                    .getSessionManager()
                    .getAccessToken();
            return token != null ? token : "";
        }

        @JavascriptInterface
        public void closeGame() {
            runOnUiThread(GamePlayActivity.this::requestClose);
        }

        @JavascriptInterface
        public void openWallet() {
            runOnUiThread(() -> MikooGameBridge.openWallet(GamePlayActivity.this));
        }
    }

    private void endSlotSession() {
        handler.removeCallbacks(heartbeatSlot);
        if (slotSession == null || slotSession.sessionId == null || slotApi == null) return;
        String sid = slotSession.sessionId;
        ContainerProvider.from(this).getIoExecutor().execute(() ->
                ApiCall.execute(slotApi.endSession(sid)));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveWindow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveWindow();
        if (slotSession != null) {
            refreshWalletAfterRecharge();
        }
    }

    @Override
    public void onBackPressed() {
        requestClose();
    }

    private void requestClose() {
        if (finishingAfterAd || isFinishing()) {
            finish();
            return;
        }
        finishingAfterAd = true;
        if (gameAds == null) {
            finish();
            return;
        }
        gameAds.maybeShowOnClose(this, () -> {
            finishingAfterAd = false;
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        endSlotSession();
        handler.removeCallbacks(loadingWatchdog);
        if (mikooBridge != null) mikooBridge.detach();
        if (hashBridge != null) MikooHashBridge.detach(web);
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
