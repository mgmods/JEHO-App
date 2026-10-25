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
    public static final String EXTRA_TITLE = "update_title";
    public static final String EXTRA_MESSAGE = "update_message";

    private static final int REQUEST_UNKNOWN_SOURCES = 7304;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean forceUpdate;
    private int targetVersionCode;
    private String targetVersionName = "";
    private String downloadUrl = "";
    private long downloadId = -1L;
    private boolean downloadRunning;
    private boolean waitingForInstallPermission;
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
        int bg = Color.rgb(5, 9, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(16, 18, 55), bg, Color.rgb(8, 20, 43)}));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        setContentView(scroll);

        TextView brand = text("JEHO", 30, Color.WHITE, true);
        brand.setLetterSpacing(0.12f);
        brand.setGravity(Gravity.CENTER);
        content.addView(brand, params(-1, -2, 0, 8, 0, 0));

        TextView icon = text("🚀", 54, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = rounded(Color.rgb(37, 28, 103), Color.rgb(115, 70, 255), dp(28));
        iconBg.setStroke(dp(1), Color.rgb(115, 91, 255));
        icon.setBackground(iconBg);
        content.addView(icon, params(dp(116), dp(116), 0, 30, 0, 22));

        TextView heading = text(title, 24, Color.WHITE, true);
        heading.setGravity(Gravity.CENTER);
        heading.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        content.addView(heading, params(-1, -2, 0, 0, 0, 10));

        TextView copy = text(message, 15, Color.rgb(204, 212, 237), false);
        copy.setGravity(Gravity.CENTER);
        copy.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        copy.setLineSpacing(dp(4), 1.0f);
        content.addView(copy, params(-1, -2, 0, 0, 0, 22));

        LinearLayout versionCard = new LinearLayout(this);
        versionCard.setOrientation(LinearLayout.VERTICAL);
        versionCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        versionCard.setBackground(rounded(Color.rgb(14, 28, 62), Color.rgb(48, 68, 132), dp(20)));
        TextView eyebrow = text(isArabic() ? "الإصدار الجديد" : "NEW VERSION", 12, Color.rgb(155, 166, 215), true);
        versionCard.addView(eyebrow, params(-1, -2, 0, 0, 0, 6));
        TextView version = text(targetVersionName.isEmpty() ? "JEHO" : targetVersionName, 25, Color.WHITE, true);
        version.setGravity(Gravity.CENTER_VERTICAL);
        versionCard.addView(version, params(-1, -2, 0, 0, 0, 4));
        TextView notes = text(isArabic()
                ? "✦ تحسينات في الأداء\n✦ إصلاحات واستقرار أفضل\n✦ تجربة أحدث وأكثر سلاسة"
                : "✦ Performance improvements\n✦ Fixes and stability updates\n✦ A smoother, newer experience",
                13, Color.rgb(213, 220, 246), false);
        notes.setLineSpacing(dp(4), 1f);
        versionCard.addView(notes, params(-1, -2, 0, 0, 0, 0));
        content.addView(versionCard, params(-1, -2, 0, 0, 0, 24));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        content.addView(progressBar, params(-1, dp(6), 0, 0, 0, 8));
        progressLabel = text("", 12, Color.rgb(195, 205, 236), false);
        progressLabel.setGravity(Gravity.CENTER);
        progressLabel.setVisibility(View.GONE);
        content.addView(progressLabel, params(-1, -2, 0, 0, 0, 8));
        statusText = text(isArabic() ? "حمّل التحديث لتتابع استخدام JEHO" : "Install the update to continue using JEHO",
                12, Color.rgb(157, 170, 214), false);
        statusText.setGravity(Gravity.CENTER);
        content.addView(statusText, params(-1, -2, 0, 0, 0, 16));

        updateButton = button(isArabic() ? "تحديث الآن" : "Update now", true);
        updateButton.setOnClickListener(v -> beginUpdate());
        content.addView(updateButton, params(-1, dp(54), 0, 0, 0, 12));

        laterButton = button(isArabic() ? "لاحقاً" : "Later", false);
        laterButton.setVisibility(forceUpdate ? View.GONE : View.VISIBLE);
        laterButton.setOnClickListener(v -> continueWithoutUpdate());
        content.addView(laterButton, params(-1, dp(48), 0, 0, 0, 0));
    }

    private void beginUpdate() {
        if (downloadRunning) return;
        if (downloadUrl.isEmpty() || !downloadUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
            showFailure(isArabic() ? "رابط التحديث غير صالح. حاول لاحقاً أو تواصل مع الدعم." : "The update link is invalid. Please try again later.");
            return;
        }
        Uri uri;
        try { uri = Uri.parse(downloadUrl); } catch (Exception error) {
            showFailure(isArabic() ? "تعذّر فتح رابط التحديث." : "Could not open the update link.");
            return;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("play.google.com") || "market".equalsIgnoreCase(uri.getScheme())) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (Exception error) { showFailure(isArabic() ? "تعذّر فتح صفحة التحديث." : "Could not open the update page."); }
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
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
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
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(primary ? Color.rgb(119, 50, 255) : Color.rgb(10, 20, 44),
                primary ? Color.rgb(167, 76, 255) : Color.rgb(63, 87, 153), dp(28)));
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
