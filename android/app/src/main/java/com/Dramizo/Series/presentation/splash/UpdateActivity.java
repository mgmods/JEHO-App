package com.Dramizo.Series.presentation.splash;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.R;

import java.util.Locale;

/** Full-screen branded update gate for dashboard-published Android releases. */
public final class UpdateActivity extends Activity {
    public static final String EXTRA_FORCE = "update_force";
    public static final String EXTRA_VERSION_CODE = "update_version_code";
    public static final String EXTRA_VERSION_NAME = "update_version_name";
    public static final String EXTRA_URL = "update_url";
    public static final String EXTRA_LINK_TYPE = "update_link_type";
    public static final String EXTRA_TITLE = "update_title";
    public static final String EXTRA_MESSAGE = "update_message";

    private static final int REQUEST_UNKNOWN_SOURCES = 7304;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean forceUpdate;
    private int targetVersionCode;
    private String targetVersionName = "";
    private String downloadUrl = "";
    private String linkType = "auto";
    private long downloadId = -1L;
    private boolean downloadRunning;
    private boolean waitingForInstallPermission;
    private boolean installerLaunched;
    private TextView statusText;
    private TextView progressLabel;
    private ProgressBar progressBar;
    private Button updateButton;
    private Button laterButton;
    private LinearLayout content;
    private final Runnable progressPoll = this::pollDownload;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (completed == downloadId) {
                    handler.removeCallbacks(progressPoll);
                    downloadRunning = false;
                    handleDownloadComplete();
                }
            }
        }
    };

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        forceUpdate = getIntent().getBooleanExtra(EXTRA_FORCE, true);
        targetVersionCode = getIntent().getIntExtra(EXTRA_VERSION_CODE, 0);
        targetVersionName = safe(getIntent().getStringExtra(EXTRA_VERSION_NAME));
        downloadUrl = safe(getIntent().getStringExtra(EXTRA_URL));
        linkType = safe(getIntent().getStringExtra(EXTRA_LINK_TYPE)).toLowerCase(Locale.ROOT);
        if (!"apk".equals(linkType) && !"external".equals(linkType) && !"auto".equals(linkType)) linkType = "auto";
        String title = safe(getIntent().getStringExtra(EXTRA_TITLE));
        String message = safe(getIntent().getStringExtra(EXTRA_MESSAGE));
        if (title.isEmpty()) title = isArabic() ? "يوجد تحديث جديد" : "A new update is available";
        if (message.isEmpty()) message = isArabic()
                ? "أصدرنا نسخة جديدة من JEHO مع تحسينات وإصلاحات مهمة. حدّث التطبيق للمتابعة."
                : "A new JEHO release is ready with important improvements and fixes. Update to continue.";

        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(Color.rgb(5, 9, 24));
            window.setNavigationBarColor(Color.rgb(5, 9, 24));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        buildUi(title, message);
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, filter);
    }

    private void buildUi(String title, String message) {
        final int ink = Color.rgb(14, 55, 44);
        final int muted = Color.rgb(94, 119, 111);
        final int mint = Color.rgb(18, 190, 151);
        final int paleMint = Color.rgb(211, 247, 235);
        final int lineMint = Color.rgb(177, 235, 220);

        // Edge-to-edge update gate: the same blocking screen is shown before any app area.
        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(Color.rgb(220, 249, 239));
            window.setNavigationBarColor(Color.WHITE);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 253, 251));
        root.setPadding(dp(22), dp(8), dp(22), dp(12));
        setContentView(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(2), dp(12), dp(2), dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        // Orbital-style illustration inspired by the supplied update-screen reference.
        android.widget.FrameLayout hero = new android.widget.FrameLayout(this);
        hero.setLayoutParams(params(-1, dp(330), 0, 0, 0, 4));
        android.widget.FrameLayout orbitOuter = new android.widget.FrameLayout(this);
        GradientDrawable outerCircle = new GradientDrawable();
        outerCircle.setShape(GradientDrawable.OVAL);
        outerCircle.setColor(Color.TRANSPARENT);
        outerCircle.setStroke(dp(2), Color.rgb(207, 243, 231));
        android.widget.FrameLayout.LayoutParams orbitParams =
                new android.widget.FrameLayout.LayoutParams(dp(270), dp(270), Gravity.CENTER);
        orbitOuter.setBackground(outerCircle);
        hero.addView(orbitOuter, orbitParams);

        android.widget.FrameLayout orbitInner = new android.widget.FrameLayout(this);
        GradientDrawable innerCircle = new GradientDrawable();
        innerCircle.setShape(GradientDrawable.OVAL);
        innerCircle.setColor(Color.TRANSPARENT);
        innerCircle.setStroke(dp(2), Color.rgb(171, 235, 216));
        orbitInner.setBackground(innerCircle);
        android.widget.FrameLayout.LayoutParams innerParams =
                new android.widget.FrameLayout.LayoutParams(dp(222), dp(222), Gravity.CENTER);
        hero.addView(orbitInner, innerParams);

        addOrbitDot(hero, Gravity.TOP | Gravity.CENTER_HORIZONTAL, mint, 14, 0, 0, 0, 18);
        addOrbitDot(hero, Gravity.LEFT | Gravity.CENTER_VERTICAL, mint, 14, 0, 0, 0, 0);
        addOrbitDot(hero, Gravity.RIGHT | Gravity.CENTER_VERTICAL, mint, 14, 0, 0, 0, 0);
        addOrbitDot(hero, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, Color.rgb(247, 205, 83), 10, 0, 0, 0, 18);

        TextView arrow = text("↑", 34, Color.WHITE, true);
        arrow.setGravity(Gravity.CENTER);
        GradientDrawable arrowBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(38, 207, 160), mint});
        arrowBg.setShape(GradientDrawable.OVAL);
        arrowBg.setShadowLayer(dp(12), 0, dp(5), 0x3032B995);
        arrow.setBackground(arrowBg);
        android.widget.FrameLayout.LayoutParams arrowParams =
                new android.widget.FrameLayout.LayoutParams(dp(68), dp(68), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        arrowParams.topMargin = dp(22);
        hero.addView(arrow, arrowParams);

        android.widget.FrameLayout logoHalo = new android.widget.FrameLayout(this);
        GradientDrawable haloBg = new GradientDrawable();
        haloBg.setShape(GradientDrawable.OVAL);
        haloBg.setColor(Color.WHITE);
        logoHalo.setBackground(haloBg);
        android.widget.FrameLayout.LayoutParams haloParams =
                new android.widget.FrameLayout.LayoutParams(dp(174), dp(174), Gravity.CENTER);
        logoHalo.setElevation(dp(5));
        hero.addView(logoHalo, haloParams);

        TextView appIcon = text("JEHO", 28, Color.WHITE, true);
        appIcon.setGravity(Gravity.CENTER);
        GradientDrawable appIconBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(39, 198, 139), Color.rgb(0, 135, 103)});
        appIconBg.setCornerRadius(dp(38));
        appIcon.setBackground(appIconBg);
        android.widget.FrameLayout.LayoutParams appIconParams =
                new android.widget.FrameLayout.LayoutParams(dp(126), dp(126), Gravity.CENTER);
        logoHalo.addView(appIcon, appIconParams);

        TextView badge = text(isArabic() ? "إصدار جديد" : "NEW VERSION", 13, ink, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(16), dp(8), dp(16), dp(8));
        badge.setBackground(rounded(paleMint, lineMint, dp(22)));
        body.addView(badge, params(-2, -2, 0, 0, 0, 18));

        TextView heading = text(isArabic() ? "هذا الإصدار أصبح قديماً" : "This version is outdated", 27, ink, true);
        heading.setGravity(Gravity.CENTER);
        heading.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        body.addView(heading, params(-1, -2, 0, 0, 0, 12));

        TextView copy = text(isArabic()
                ? "يرجى تحديث التطبيق إلى الإصدار الجديد للتمكن من المتابعة."
                : "Please update JEHO to the latest version to continue.",
                16, muted, false);
        copy.setGravity(Gravity.CENTER);
        copy.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        copy.setLineSpacing(dp(5), 1f);
        body.addView(copy, params(-1, -2, 0, 0, 0, 24));

        TextView version = text(isArabic()
                ? "إصدارك الحالي  " + installedVersionName()
                : "Your current version  " + installedVersionName(), 15, Color.rgb(39, 119, 91), true);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(18), dp(12), dp(18), dp(12));
        version.setBackground(rounded(Color.WHITE, lineMint, dp(28)));
        body.addView(version, params(-2, -2, 0, 0, 0, 14));

        TextView nextVersion = text((isArabic() ? "الإصدار الجديد  " : "Latest version  ")
                + (targetVersionName.isEmpty() ? "JEHO" : targetVersionName),
                13, muted, false);
        nextVersion.setGravity(Gravity.CENTER);
        body.addView(nextVersion, params(-1, -2, 0, 0, 0, 14));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(mint));
        progressBar.setVisibility(View.GONE);
        body.addView(progressBar, params(-1, dp(6), 0, 0, 0, 7));

        progressLabel = text("", 12, muted, false);
        progressLabel.setGravity(Gravity.CENTER);
        progressLabel.setVisibility(View.GONE);
        body.addView(progressLabel, params(-1, -2, 0, 0, 0, 8));

        statusText = text(isArabic() ? "حدّث التطبيق للمتابعة" : "Update the app to continue", 12, muted, false);
        statusText.setGravity(Gravity.CENTER);
        body.addView(statusText, params(-1, -2, 0, 0, 0, 12));

        // Fixed bottom action remains visible even on short displays or large font settings.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(0, dp(10), 0, dp(4));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        updateButton = button(isArabic() ? "تحديث من المتجر   ▣" : "Update now   ▣", true);
        updateButton.setOnClickListener(v -> beginUpdate());
        bottom.addView(updateButton, params(-1, dp(64), 0, 0, 0, 0));

        laterButton = button(isArabic() ? "لاحقاً" : "Later", false);
        laterButton.setVisibility(forceUpdate ? View.GONE : View.VISIBLE);
        laterButton.setOnClickListener(v -> continueWithoutUpdate());
        bottom.addView(laterButton, params(-1, dp(46), 0, 8, 0, 0));
    }

    private void addOrbitDot(android.widget.FrameLayout parent, int gravity, int color, int size,
                             int left, int top, int right, int bottom) {
        View dot = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
        android.widget.FrameLayout.LayoutParams p =
                new android.widget.FrameLayout.LayoutParams(dp(size), dp(size), gravity);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        parent.addView(dot, p);
    }

    private String installedVersionName() {
        try {
            return safe(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {
            return String.valueOf(BuildConfig.VERSION_CODE);
        }
    }

    private void beginUpdate() {
        if (downloadRunning) return;
        if (downloadUrl.isEmpty()) {
            showFailure(isArabic() ? "رابط التحديث غير موجود في إعدادات الداشبورد." : "No update URL is configured in the dashboard.");
            return;
        }
        Uri uri;
        try { uri = Uri.parse(downloadUrl); } catch (Exception error) {
            showFailure(isArabic() ? "تعذّر قراءة رابط التحديث." : "Could not read the update URL.");
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            showFailure(isArabic() ? "الرابط يجب أن يبدأ بـ http:// أو https://" : "The URL must start with http:// or https://");
            return;
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        boolean directApk = "apk".equals(linkType)
                || ("auto".equals(linkType) && path.endsWith(".apk"));
        if (!directApk) {
            try {
                // Open exactly the URL configured by the administrator; never substitute Play Store.
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                statusText.setText(isArabic()
                        ? "فتحنا الرابط الذي حدده الأدمن. ارجع إلى JEHO بعد إكمال التحديث."
                        : "Opened the administrator-configured link. Return to JEHO when finished.");
            } catch (Exception error) {
                showFailure(isArabic() ? "لا يوجد تطبيق مناسب لفتح هذا الرابط." : "No app is available to open this link.");
            }
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) {
            showFailure(isArabic() ? "خدمة التنزيل غير متاحة على الجهاز." : "Downloads are unavailable on this device.");
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("JEHO " + (targetVersionName.isEmpty() ? "update" : targetVersionName));
            request.setDescription(isArabic() ? "جارٍ تنزيل تحديث JEHO" : "Downloading JEHO update");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "JEHO-update-" + Math.max(1, targetVersionCode) + ".apk");
            downloadId = manager.enqueue(request);
            downloadRunning = true;
            updateButton.setEnabled(false);
            updateButton.setText(isArabic() ? "جارٍ التنزيل..." : "Downloading...");
            laterButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            progressLabel.setVisibility(View.VISIBLE);
            statusText.setText(isArabic() ? "اترك التطبيق مفتوحاً حتى يكتمل تنزيل الملف." : "Keep this screen open while the update downloads.");
            handler.post(progressPoll);
        } catch (Exception error) {
            showFailure(isArabic() ? "تعذّر بدء التنزيل. تحقق من الاتصال وحاول مجدداً." : "Could not start the download. Check your connection and retry.");
        }
    }

    private void pollDownload() {
        if (!downloadRunning || downloadId < 0) return;
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) return;
        Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(downloadId));
            if (cursor != null && cursor.moveToFirst()) {
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (total > 0) {
                    int pct = (int) Math.min(100, (done * 100L) / total);
                    progressBar.setProgress(pct);
                    progressLabel.setText(pct + "%  ·  " + formatBytes(done) + " / " + formatBytes(total));
                } else if (done > 0) {
                    progressLabel.setText(formatBytes(done));
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    handler.removeCallbacks(progressPoll);
                    downloadRunning = false;
                    showFailure(isArabic() ? "فشل تنزيل التحديث. تحقق من الرابط والإنترنت ثم أعد المحاولة." : "Download failed. Check the link and internet connection, then retry.");
                    return;
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    handler.removeCallbacks(progressPoll);
                    downloadRunning = false;
                    handleDownloadComplete();
                    return;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        handler.postDelayed(progressPoll, 500L);
    }

    private void handleDownloadComplete() {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Uri fileUri = manager == null || downloadId < 0 ? null : manager.getUriForDownloadedFile(downloadId);
        if (fileUri == null) {
            showFailure(isArabic() ? "اكتمل التنزيل لكن تعذّر فتح ملف التحديث." : "Download finished, but the APK could not be opened.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = true;
            statusText.setText(isArabic()
                    ? "اسمح لـ JEHO بتثبيت التحديث من هذا المصدر، ثم ارجع إلى التطبيق."
                    : "Allow JEHO to install this update from this source, then return here.");
            updateButton.setEnabled(true);
            updateButton.setText(isArabic() ? "السماح بالتثبيت" : "Allow installation");
            updateButton.setOnClickListener(v -> requestInstallPermission());
            updateButton.setVisibility(View.VISIBLE);
            return;
        }
        launchInstaller(fileUri);
    }

    private void requestInstallPermission() {
        if (Build.VERSION.SDK_INT < 26) {
            handleDownloadComplete();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_UNKNOWN_SOURCES);
        } catch (Exception error) {
            showFailure(isArabic() ? "افتح إعدادات الجهاز واسمح بتثبيت تطبيقات من هذا المصدر." : "Open system settings and allow installs from this source.");
        }
    }

    private void launchInstaller(Uri uri) {
        if (installerLaunched) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            installerLaunched = true;
            statusText.setText(isArabic()
                    ? "أكّد التثبيت في نافذة Android. لن تُحذف بيانات حسابك إذا كان توقيع النسخة مطابقاً."
                    : "Confirm installation in Android. Your account data stays if the signing certificate matches.");
        } catch (Exception error) {
            showFailure(isArabic() ? "تعذّر فتح مثبت Android لهذا الملف." : "Android could not open the installer for this file.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingForInstallPermission && Build.VERSION.SDK_INT >= 26
                && getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = false;
            handleDownloadComplete();
            return;
        }
        if (targetVersionCode > 0 && installedVersionCode() >= targetVersionCode) {
            Intent next = new Intent(this, SplashActivity.class);
            next.putExtra(SplashActivity.EXTRA_SKIP_UPDATE_CHECK_ONCE, true);
            next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(next);
            finish();
            return;
        }
        // If the user cancels Android's installer, allow reopening the downloaded APK.
        if (installerLaunched) {
            installerLaunched = false;
            updateButton.setEnabled(true);
            updateButton.setText(isArabic() ? "إعادة فتح التثبيت" : "Open installer again");
            updateButton.setOnClickListener(v -> handleDownloadComplete());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UNKNOWN_SOURCES) {
            if (Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) {
                waitingForInstallPermission = false;
                handleDownloadComplete();
            } else {
                showFailure(isArabic() ? "لم يتم السماح بالتثبيت بعد." : "Installation permission was not granted.");
            }
        }
    }

    private int installedVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= 28) return (int) getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ignored) { return 0; }
    }

    private void continueWithoutUpdate() {
        if (forceUpdate) return;
        startActivity(new Intent(this, SplashActivity.class)
                .putExtra(SplashActivity.EXTRA_SKIP_UPDATE_CHECK_ONCE, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void showFailure(String message) {
        statusText.setText(message);
        statusText.setTextColor(Color.rgb(255, 135, 165));
        updateButton.setEnabled(true);
        updateButton.setVisibility(View.VISIBLE);
        updateButton.setText(isArabic() ? "إعادة المحاولة" : "Try again");
        updateButton.setOnClickListener(v -> {
            statusText.setTextColor(Color.rgb(157, 170, 214));
            beginUpdate();
        });
        if (!forceUpdate) laterButton.setVisibility(View.VISIBLE);
    }

    @Override public void onBackPressed() {
        if (!forceUpdate) super.onBackPressed();
        // Mandatory update gate intentionally blocks back navigation.
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(primary ? 16 : 14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                primary ? new int[]{Color.rgb(104, 53, 255), Color.rgb(158, 69, 255)}
                        : new int[]{Color.rgb(15, 22, 49), Color.rgb(20, 29, 60)}));
        GradientDrawable buttonBg = (GradientDrawable) b.getBackground();
        buttonBg.setCornerRadius(dp(28));
        buttonBg.setStroke(dp(1), primary ? Color.rgb(188, 142, 255) : Color.rgb(63, 87, 153));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(left, dp(top), right, dp(bottom));
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean isArabic() { return Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT).startsWith("ar"); }
    private String safe(String value) { return value == null ? "" : value.trim(); }

    private String formatBytes(long value) {
        if (value < 1024) return value + " B";
        if (value < 1024 * 1024) return String.format(Locale.US, "%.1f KB", value / 1024d);
        return String.format(Locale.US, "%.1f MB", value / (1024d * 1024d));
    }
}
