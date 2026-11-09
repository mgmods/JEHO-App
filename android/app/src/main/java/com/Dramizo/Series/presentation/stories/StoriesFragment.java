package com.Dramizo.Series.presentation.stories;

import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.Dramizo.Series.data.remote.api.UploadApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.StoriesDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

/** Native Stories tab. Media bytes go through the existing durable upload service. */
public class StoriesFragment extends Fragment {
    private static final int PICK_IMAGE = 7101, PICK_VIDEO = 7102;
    private AppContainer c;
    private LinearLayout root, storyRow;
    private TextView status;
    private ProgressBar progress;
    private StoriesDtos.StoriesConfigDto config;
    private final List<StoriesDtos.StoryDto> feed = new ArrayList<>();

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        c = ContainerProvider.from(requireActivity());
        root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        root.setBackgroundColor(0xFFF7F7FA);
        TextView title = text("الستوريات", 25, 0xFF24212B, true);
        root.addView(title, match());
        TextView subtitle = text("شارك لحظاتك وشاهد قصص أصدقائك", 13, 0xFF898591, false);
        root.addView(subtitle, match());
        LinearLayout toolbar = new LinearLayout(requireContext());
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(16), 0, dp(8));
        TextView add = text("＋  أضف ستوري", 14, 0xFFFFFFFF, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(0xFFB82768, dp(18)));
        add.setPadding(dp(14), dp(12), dp(14), dp(12));
        add.setOnClickListener(v -> chooseMedia());
        toolbar.addView(add, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView refresh = text("تحديث", 13, 0xFF6D6370, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> load());
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(72), dp(46)));
        root.addView(toolbar, match());
        status = text("جارٍ تحميل الستوريات…", 13, 0xFF88838D, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, match());
        progress = new ProgressBar(requireContext());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(28), dp(28));
        pp.gravity = Gravity.CENTER;
        root.addView(progress, pp);
        android.widget.HorizontalScrollView horizontal = new android.widget.HorizontalScrollView(requireContext());
        horizontal.setHorizontalScrollBarEnabled(false);
        storyRow = new LinearLayout(requireContext());
        storyRow.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(storyRow);
        root.addView(horizontal, match());
        load();
        return root;
    }

    private void load() {
        if (c == null) return;
        c.getIoExecutor().execute(() -> {
            com.Dramizo.Series.domain.model.Result<StoriesDtos.StoriesConfigDto> cfg = ApiCall.execute(c.getStoriesApi().config());
            com.Dramizo.Series.domain.model.Result<List<StoriesDtos.StoryDto>> rows = ApiCall.execute(c.getStoriesApi().feed());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (root == null) return;
                progress.setVisibility(View.GONE);
                config = cfg.success && cfg.data != null ? cfg.data : new StoriesDtos.StoriesConfigDto();
                if (!cfg.success || cfg.data == null) config.enabled = false;
                if (!config.enabled || !config.viewingEnabled) {
                    status.setText("ميزة الستوريات متوقفة حالياً");
                    storyRow.removeAllViews();
                    return;
                }
                if (!config.publishingEnabled || config.emergencyMode) status.setText("النشر متوقف مؤقتاً — يمكنك مشاهدة الستوريات");
                else status.setText(rows.success ? "" : (rows.message == null ? "تعذر تحميل الستوريات" : rows.message));
                feed.clear();
                if (rows.success && rows.data != null) feed.addAll(rows.data);
                bindStories();
            });
        });
    }

    private void bindStories() {
        storyRow.removeAllViews();
        Map<String,List<StoriesDtos.StoryDto>> grouped = new LinkedHashMap<>();
        for (StoriesDtos.StoryDto s : feed) grouped.computeIfAbsent(s.userId == null ? s.id : s.userId, k -> new ArrayList<>()).add(s);
        for (List<StoriesDtos.StoryDto> group : grouped.values()) {
            if (group.isEmpty()) continue;
            StoriesDtos.StoryDto story = group.get(0);
            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(7), dp(4), dp(7), dp(4));
            ImageView avatar = new ImageView(requireContext());
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            android.graphics.drawable.GradientDrawable ring = round(story.viewedByMe ? 0xFFB7B3BD : 0xFFE6428A, dp(40));
            avatar.setBackground(ring);
            avatar.setClipToOutline(true);
            if (story.avatarUrl != null) Glide.with(this).load(story.avatarUrl).circleCrop().diskCacheStrategy(config != null && config.cacheEnabled ? DiskCacheStrategy.AUTOMATIC : DiskCacheStrategy.NONE).into(avatar);
            else avatar.setImageDrawable(round(0xFFE8DCE4, dp(40)));
            item.addView(avatar, new LinearLayout.LayoutParams(dp(62), dp(62)));
            TextView name = text(shortName(story), 11, 0xFF49444F, false);
            name.setGravity(Gravity.CENTER);
            name.setMaxWidth(dp(76));
            item.addView(name, new LinearLayout.LayoutParams(dp(76), dp(26)));
            item.setOnClickListener(v -> openViewer(group, 0));
            storyRow.addView(item);
        }
        if (feed.isEmpty()) status.setText("لا توجد ستوريات حالياً — كن أول من يشارك");
    }

    private void chooseMedia() {
        if (config == null || !config.enabled || !config.publishingEnabled || config.emergencyMode) {
            Toast.makeText(requireContext(), "نشر الستوريات متوقف حالياً", Toast.LENGTH_SHORT).show(); return;
        }
        String[] choices = {"إضافة صورة", "إضافة فيديو"};
        new android.app.AlertDialog.Builder(requireContext()).setTitle("إنشاء ستوري")
                .setItems(choices, (d, which) -> {
                    if (which == 0 && config.imagesEnabled) pick("image/*", PICK_IMAGE);
                    else if (which == 1 && config.videosEnabled) pick("video/*", PICK_VIDEO);
                    else Toast.makeText(requireContext(), "هذا النوع متوقف من لوحة التحكم", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void pick(String mime, int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType(mime); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, request);
    }

    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String type = request == PICK_VIDEO ? "video" : "image";
        c.getIoExecutor().execute(() -> uploadAndPublish(uri, type));
    }

    private void uploadAndPublish(Uri uri, String type) {
        try {
            long size = fileSize(uri);
            int maxMb = config == null ? 20 : config.maxMediaMb;
            if (size > (long) maxMb * 1024 * 1024) throw new IllegalStateException("حجم الملف أكبر من " + maxMb + "MB");
            int duration = type.equals("video") ? videoDuration(uri) : 0;
            if (type.equals("video") && duration > (config == null ? 30 : config.maxVideoSeconds)) throw new IllegalStateException("مدة الفيديو أطول من الحد المسموح");
            byte[] bytes;
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IllegalStateException("تعذر قراءة الملف");
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) > 0) { out.write(buffer, 0, n); if (out.size() > maxMb * 1024L * 1024L) throw new IllegalStateException("الملف كبير جداً"); }
                bytes = out.toByteArray();
            }
            String mime = requireContext().getContentResolver().getType(uri);
            if (mime == null) mime = type.equals("video") ? "video/mp4" : "image/jpeg";
            String filename = displayName(uri);
            RequestBody body = RequestBody.create(MediaType.parse(mime), bytes);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", filename, body);
            Response<ApiResponse<UploadApi.UploadResult>> upload = c.getUploadApi().upload(part).execute();
            if (!upload.isSuccessful() || upload.body() == null || upload.body().data == null || upload.body().data.url == null)
                throw new IllegalStateException(UploadApi.errorMessage(upload, "تعذر رفع الستوري"));
            String url = upload.body().data.url.trim();
            // Persist only JEHO relative upload paths; the backend rejects arbitrary external URLs.
            StoriesDtos.CreateStoryDto dto = new StoriesDtos.CreateStoryDto();
            dto.mediaType = type; dto.mediaUrl = url; dto.sizeBytes = bytes.length; dto.durationSeconds = duration;
            com.Dramizo.Series.domain.model.Result<StoriesDtos.StoryDto> saved = ApiCall.execute(c.getStoriesApi().create(dto));
            if (!saved.success) throw new IllegalStateException(saved.message == null ? "تعذر نشر الستوري" : saved.message);
            requireActivity().runOnUiThread(() -> { Toast.makeText(requireContext(), "تم نشر الستوري", Toast.LENGTH_SHORT).show(); load(); });
        } catch (Exception e) {
            if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage() == null ? "تعذر نشر الستوري" : e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private long fileSize(Uri uri) {
        try (Cursor cur = requireContext().getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getLong(0);
        } catch (Exception ignored) {}
        return 0;
    }
    private String displayName(Uri uri) {
        try (Cursor cur = requireContext().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) { String n=cur.getString(0); if(n!=null)return n.replaceAll("[^A-Za-z0-9._-]","_"); }
        } catch (Exception ignored) {}
        return "story_media";
    }
    private int videoDuration(Uri uri) {
        android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
        try { r.setDataSource(requireContext(), uri); return Integer.parseInt(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION))/1000; }
        catch (Exception e) { return 0; } finally { try { r.release(); } catch(Exception ignored){} }
    }

    private void openViewer(List<StoriesDtos.StoryDto> group, int index) {
        if (group == null || index >= group.size()) return;
        StoriesDtos.StoryDto story = group.get(index);
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setBackgroundColor(0xFF08070A);
        View media;
        if ("video".equalsIgnoreCase(story.mediaType)) {
            VideoView video = new VideoView(requireContext());
            video.setVideoURI(Uri.parse(resolveMediaUrl(story.mediaUrl)));
            video.setOnPreparedListener(mp -> { mp.setLooping(false); video.start(); });
            video.setOnCompletionListener(mp -> { if (index + 1 < group.size()) { dialog.dismiss(); openViewer(group,index+1); } });
            media = video;
        } else {
            ImageView image = new ImageView(requireContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Glide.with(this).load(resolveMediaUrl(story.mediaUrl)).diskCacheStrategy(config != null && config.cacheEnabled ? DiskCacheStrategy.AUTOMATIC : DiskCacheStrategy.NONE).into(image);
            media = image;
        }
        frame.addView(media, new FrameLayout.LayoutParams(-1,-1));
        TextView author = text(story.displayName == null ? "مستخدم JEHO" : story.displayName, 16, 0xFFFFFFFF, true);
        author.setPadding(dp(18),dp(34),dp(18),dp(18));
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-1,dp(90),Gravity.TOP);
        frame.addView(author,top);
        TextView close = text("×", 34, 0xFFFFFFFF, true); close.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(54),dp(60),Gravity.TOP|Gravity.END);
        frame.addView(close,cp); close.setOnClickListener(v -> dialog.dismiss());
        frame.setOnClickListener(v -> { if (index + 1 < group.size()) { dialog.dismiss(); openViewer(group,index+1); } });
        dialog.setContentView(frame);
        Window w = dialog.getWindow(); if(w!=null)w.setLayout(-1,-1);
        dialog.show(); if(w!=null)w.setLayout(-1,-1);
        c.getIoExecutor().execute(() -> ApiCall.execute(c.getStoriesApi().view(story.id)));
    }

    private String resolveMediaUrl(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String path = value.trim();
        return path.startsWith("/") ? com.Dramizo.Series.util.ApiOrigin.origin() + path : path;
    }

    private String shortName(StoriesDtos.StoryDto s) { String n=s.displayName!=null?s.displayName:s.username; if(n==null||n.trim().isEmpty())n="مستخدم"; return n.length()>10?n.substring(0,9)+"…":n; }
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(requireContext());v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,1);return v;}
    private android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
}
