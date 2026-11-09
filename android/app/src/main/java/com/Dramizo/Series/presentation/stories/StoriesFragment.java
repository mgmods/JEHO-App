package com.Dramizo.Series.presentation.stories;

import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.util.ApiCall;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

/** Native Stories tab. Media bytes go through the existing durable upload service. */
public class StoriesFragment extends Fragment {
    private static final int PICK_MEDIA = 7103;
    private static final String ARG_COMPACT = "compact_inbox";
    private boolean compactMode;
    private String pendingOpenUserId;

    public static StoriesFragment newCompactInstance() {
        StoriesFragment fragment = new StoriesFragment();
        Bundle args = new Bundle(); args.putBoolean(ARG_COMPACT, true);
        fragment.setArguments(args); return fragment;
    }
    private AppContainer c;
    private LinearLayout root, storyRow;
    private TextView status;
    private ProgressBar progress;
    private StoriesDtos.StoriesConfigDto config;
    private final List<StoriesDtos.StoryDto> feed = new ArrayList<>();

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        c = ContainerProvider.from(requireActivity());
        compactMode = getArguments() != null && getArguments().getBoolean(ARG_COMPACT, false);
        root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(compactMode ? dp(10) : dp(14), compactMode ? dp(3) : dp(14), compactMode ? dp(10) : dp(14), compactMode ? dp(3) : dp(20));
        root.setBackgroundColor(0x00FFFFFF);
        if (compactMode) {
            LinearLayout heading = new LinearLayout(requireContext()); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text("الستوريات",14,0xFF39323D,true); heading.addView(label,new LinearLayout.LayoutParams(0,dp(25),1));
            TextView hint = text("قصص المتابعين",11,0xFF8A8390,false); heading.addView(hint,new LinearLayout.LayoutParams(-2,dp(25)));
            root.addView(heading,match());
            status = text("",11,0xFF88838D,false); status.setVisibility(View.GONE); root.addView(status,match());
            progress = new ProgressBar(requireContext()); progress.setVisibility(View.GONE); root.addView(progress,new LinearLayout.LayoutParams(0,0));
            android.widget.HorizontalScrollView compactScroll = new android.widget.HorizontalScrollView(requireContext());
            compactScroll.setHorizontalScrollBarEnabled(false); compactScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            storyRow = new LinearLayout(requireContext()); storyRow.setOrientation(LinearLayout.HORIZONTAL);
            compactScroll.addView(storyRow); root.addView(compactScroll,new LinearLayout.LayoutParams(-1,dp(88)));
            load(); return root;
        }
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

    public void openOwnStory(String userId) {
        pendingOpenUserId = userId;
        load();
    }

    @Override public void onResume() { super.onResume(); if (compactMode && c != null) load(); }

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
                else status.setText(rows.success ? "" : (rows.error == null ? "تعذر تحميل الستوريات" : rows.error));
                feed.clear();
                if (rows.success && rows.data != null) feed.addAll(rows.data);
                bindStories();
                if (pendingOpenUserId != null) {
                    String requested = pendingOpenUserId;
                    pendingOpenUserId = null;
                    List<StoriesDtos.StoryDto> ownStories = new ArrayList<>();
                    for (StoriesDtos.StoryDto item : feed) if (requested.equals(item.userId)) ownStories.add(item);
                    if (!ownStories.isEmpty()) openViewer(ownStories, 0);
                    else {
                        Intent profile = new Intent(requireContext(), ProfileActivity.class);
                        profile.putExtra(ProfileActivity.EXTRA_USER_ID, requested);
                        startActivity(profile);
                    }
                }
            });
        });
    }

    private void bindStories() {
        storyRow.removeAllViews();
        if (compactMode) {
            LinearLayout addItem = new LinearLayout(requireContext()); addItem.setOrientation(LinearLayout.VERTICAL);
            addItem.setGravity(Gravity.CENTER); addItem.setPadding(dp(5),dp(2),dp(5),dp(2));
            FrameLayout addCircle = new FrameLayout(requireContext()); addCircle.setBackground(round(0xFFFCE8F1,dp(36)));
            TextView plus = text("＋",28,0xFFB82768,false); plus.setGravity(Gravity.CENTER); addCircle.addView(plus,new FrameLayout.LayoutParams(-1,-1));
            addItem.addView(addCircle,new LinearLayout.LayoutParams(dp(58),dp(58)));
            TextView addLabel = text("قصتي",10,0xFF49444F,true); addLabel.setGravity(Gravity.CENTER);
            addItem.addView(addLabel,new LinearLayout.LayoutParams(dp(66),dp(22)));
            addItem.setOnClickListener(v -> chooseMedia()); storyRow.addView(addItem);
        }
        Map<String,List<StoriesDtos.StoryDto>> grouped = new LinkedHashMap<>();
        for (StoriesDtos.StoryDto s : feed) grouped.computeIfAbsent(s.userId == null ? s.id : s.userId, k -> new ArrayList<>()).add(s);
        for (List<StoriesDtos.StoryDto> group : grouped.values()) {
            if (group.isEmpty()) continue;
            StoriesDtos.StoryDto story = group.get(0);
            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(7), dp(4), dp(7), dp(4));
            FrameLayout avatarWrap = new FrameLayout(requireContext());
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    story.viewedByMe ? new int[]{0xFFB7B3BD,0xFFCBC8D0,0xFFB7B3BD} : new int[]{0xFFFFB347,0xFFE6428A,0xFF8D5CF6,0xFFFFB347});
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL); avatarWrap.setBackground(ring);
            ImageView avatar = new ImageView(requireContext()); avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackground(round(0xFFFFFFFF,dp(28))); avatar.setClipToOutline(true);
            if (story.avatarUrl != null) Glide.with(this).load(story.avatarUrl).circleCrop().diskCacheStrategy(config != null && config.cacheEnabled ? DiskCacheStrategy.AUTOMATIC : DiskCacheStrategy.NONE).into(avatar);
            else avatar.setImageDrawable(round(0xFFE8DCE4,dp(28)));
            FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.CENTER);
            avatarWrap.addView(avatar,avatarParams); item.addView(avatarWrap,new LinearLayout.LayoutParams(dp(62),dp(62)));
            if (!story.viewedByMe) { android.animation.ObjectAnimator ringAnimator = android.animation.ObjectAnimator.ofFloat(avatarWrap,"rotation",0f,360f); ringAnimator.setDuration(5200); ringAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE); ringAnimator.start(); }
            TextView name = text(shortName(story), 11, 0xFF49444F, false);
            name.setGravity(Gravity.CENTER);
            name.setMaxWidth(dp(76));
            item.addView(name, new LinearLayout.LayoutParams(dp(76), dp(26)));
            item.setOnClickListener(v -> openViewer(group, 0));
            storyRow.addView(item);
        }
        if (feed.isEmpty() && !compactMode) status.setText("لا توجد ستوريات حالياً — كن أول من يشارك");
    }

    private void chooseMedia() {
        if (config == null || !config.enabled || !config.publishingEnabled || config.emergencyMode) {
            Toast.makeText(requireContext(), "نشر الستوريات متوقف حالياً", Toast.LENGTH_SHORT).show(); return;
        }
        pickMedia();
    }

    private void pickMedia() {
        // Open the media gallery directly, rather than the Documents/file browser.
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setDataAndType(MediaStore.Files.getContentUri("external"), "*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        try {
            startActivityForResult(i, PICK_MEDIA);
        } catch (android.content.ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_PICK);
            fallback.setType("*/*");
            fallback.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            startActivityForResult(fallback, PICK_MEDIA);
        }
    }

    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String mime = requireContext().getContentResolver().getType(uri);
        String name = displayName(uri).toLowerCase(Locale.ROOT);
        String type = (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/"))
                || name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".3gp")
                ? "video" : "image";
        if ((type.equals("video") && (config == null || !config.videosEnabled))
                || (type.equals("image") && (config == null || !config.imagesEnabled))) {
            Toast.makeText(requireContext(), "هذا النوع من الستوريات متوقف من لوحة التحكم", Toast.LENGTH_SHORT).show();
            return;
        }
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
            if (!saved.success) throw new IllegalStateException(saved.error == null ? "تعذر نشر الستوري" : saved.error);
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
        VideoView storyVideo = null;
        if ("video".equalsIgnoreCase(story.mediaType)) {
            VideoView video = new VideoView(requireContext());
            video.setKeepScreenOn(true);
            video.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                mp.setVolume(1f, 1f);
                video.start();
            });
            video.setOnCompletionListener(mp -> { if (index + 1 < group.size()) { dialog.dismiss(); openViewer(group,index+1); } });
            video.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(requireContext(), "تعذر تشغيل هذا الفيديو. تحقق من صيغة الملف أو أعد رفعه", Toast.LENGTH_LONG).show();
                return true;
            });
            storyVideo = video;
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
        if (story.isMine) {
            LinearLayout ownerTools = new LinearLayout(requireContext());
            ownerTools.setGravity(Gravity.CENTER);
            ownerTools.setPadding(dp(12), dp(8), dp(12), dp(8));
            ownerTools.setBackground(round(0xCC17131D, dp(18)));
            TextView viewers = text("👁  المشاهدون · " + story.viewCount, 14, 0xFFFFFFFF, true);
            viewers.setGravity(Gravity.CENTER);
            viewers.setPadding(dp(12), dp(10), dp(12), dp(10));
            viewers.setOnClickListener(v -> showStoryViewers(story));
            ownerTools.addView(viewers, new LinearLayout.LayoutParams(0, dp(46), 1));
            TextView delete = text("حذف الستوري", 14, 0xFFFFB6C7, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(dp(12), dp(10), dp(12), dp(10));
            delete.setOnClickListener(v -> confirmDeleteStory(story, dialog));
            ownerTools.addView(delete, new LinearLayout.LayoutParams(-2, dp(46)));
            FrameLayout.LayoutParams ownerParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            ownerParams.setMargins(dp(10), 0, dp(10), dp(18));
            frame.addView(ownerTools, ownerParams);
        } else {
        LinearLayout actions = new LinearLayout(requireContext()); actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(14),dp(10),dp(14),dp(18)); actions.setBackground(round(0xCC17131D,dp(18)));
        LinearLayout reactions = new LinearLayout(requireContext()); reactions.setGravity(Gravity.CENTER);
        String[] emojis = {"❤️","😍","😂","😮","👏","🔥"};
        for (String emoji : emojis) {
            TextView reaction = text(emoji, 23, 0xFFFFFFFF, false);
            reaction.setGravity(Gravity.CENTER);
            reaction.setClickable(true);
            reaction.setFocusable(true);
            reaction.setBackground(round(0x22FFFFFF, dp(18)));
            reaction.setOnClickListener(v -> sendStoryReaction(story, emoji));
            reactions.addView(reaction, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        actions.addView(reactions,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout replyRow = new LinearLayout(requireContext()); replyRow.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.EditText reply = new android.widget.EditText(requireContext()); reply.setSingleLine(true); reply.setTextSize(13);
        reply.setTextColor(0xFFFFFFFF); reply.setHintTextColor(0xFFBEB8C5); reply.setHint("الرد على الستوري…");
        reply.setPadding(dp(12),0,dp(12),0); reply.setBackground(round(0xFF39323F,dp(22)));
        replyRow.addView(reply,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView send = text("إرسال",13,0xFFFFB8D5,true); send.setGravity(Gravity.CENTER);
        replyRow.addView(send,new LinearLayout.LayoutParams(dp(58),dp(44)));
        send.setOnClickListener(v -> { String message=reply.getText().toString().trim(); if(!message.isEmpty()) sendStoryReply(story,message,dialog); });
        actions.addView(replyRow,new LinearLayout.LayoutParams(-1,dp(48)));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        actionParams.setMargins(dp(10),0,dp(10),dp(18)); frame.addView(actions,actionParams);
        }
        frame.setOnClickListener(v -> { if (index + 1 < group.size()) { dialog.dismiss(); openViewer(group,index+1); } });
        dialog.setContentView(frame);
        Window w = dialog.getWindow(); if(w!=null)w.setLayout(-1,-1);
        dialog.show(); if(w!=null)w.setLayout(-1,-1);
        if (storyVideo != null) {
            final VideoView target = storyVideo;
            android.widget.MediaController controls = new android.widget.MediaController(requireContext());
            controls.setAnchorView(target);
            target.setMediaController(controls);
            final String mediaUrl = resolveMediaUrl(story.mediaUrl);
            if (config != null && config.cacheEnabled) {
                c.getIoExecutor().execute(() -> {
                    try {
                        File cached = cachedStoryFile(story.id, story.mediaUrl);
                        if (!cached.exists() || cached.length() == 0) downloadStoryToCache(mediaUrl, cached);
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (dialog.isShowing()) target.setVideoPath(cached.getAbsolutePath());
                        });
                    } catch (Exception cacheError) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (dialog.isShowing()) target.setVideoURI(Uri.parse(mediaUrl));
                        });
                    }
                });
            } else {
                target.setVideoURI(Uri.parse(mediaUrl));
            }
        }
        c.getIoExecutor().execute(() -> ApiCall.execute(c.getStoriesApi().view(story.id)));
    }

    private void showStoryViewers(StoriesDtos.StoryDto story) {
        if (story == null || story.id == null || !story.isMine) return;
        c.getIoExecutor().execute(() -> {
            com.Dramizo.Series.domain.model.Result<StoriesDtos.ViewersResult> result =
                    ApiCall.execute(c.getStoriesApi().viewers(story.id));
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (!result.success || result.data == null) {
                    Toast.makeText(requireContext(), "تعذر تحميل قائمة المشاهدين", Toast.LENGTH_SHORT).show();
                    return;
                }
                showViewersSheet(result.data);
            });
        });
    }

    private void showViewersSheet(StoriesDtos.ViewersResult data) {
        Dialog sheet = new Dialog(requireContext());
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(10), dp(18), dp(18));
        panel.setBackground(round(0xFFFDFBFD, dp(26)));

        TextView handle = text("—", 24, 0xFFD2CCD5, true);
        handle.setGravity(Gravity.CENTER);
        panel.addView(handle, new LinearLayout.LayoutParams(-1, dp(26)));

        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("المشاهدون", 19, 0xFF211D25, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView count = text(String.valueOf(data.viewCount), 16, 0xFFB82768, true);
        count.setGravity(Gravity.CENTER);
        count.setBackground(round(0xFFFCE8F1, dp(18)));
        titleRow.addView(count, new LinearLayout.LayoutParams(dp(48), dp(34)));
        panel.addView(titleRow);

        TextView subtitle = text(data.viewCount == 0 ? "لا توجد مشاهدات بعد" : "الحسابات التي شاهدت قصتك", 12, 0xFF8C8590, false);
        panel.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(28)));

        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        if (data.viewers == null || data.viewers.isEmpty()) {
            TextView empty = text("لم يشاهدها أحد بعد", 14, 0xFF77717A, false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(150)));
        } else {
            for (StoriesDtos.StoryViewerDto viewer : data.viewers) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(7), 0, dp(7));
                ImageView avatar = new ImageView(requireContext());
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                avatar.setBackground(round(0xFFECE7ED, dp(24)));
                if (viewer.avatarUrl != null && !viewer.avatarUrl.trim().isEmpty())
                    Glide.with(this).load(viewer.avatarUrl).circleCrop().into(avatar);
                row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout info = new LinearLayout(requireContext());
                info.setOrientation(LinearLayout.VERTICAL);
                info.setPadding(dp(12), 0, dp(8), 0);
                String name = viewer.displayName != null && !viewer.displayName.trim().isEmpty()
                        ? viewer.displayName : (viewer.username == null ? "مستخدم JEHO" : viewer.username);
                TextView n = text(name, 14, 0xFF28242C, true);
                info.addView(n, new LinearLayout.LayoutParams(-1, dp(25)));
                String id = viewer.publicId == null ? "" : viewer.publicId.trim();
                TextView pid = text(id.isEmpty() ? "شاهد قصتك" : "ID " + id, 11, 0xFF8A8390, false);
                info.addView(pid, new LinearLayout.LayoutParams(-1, dp(22)));
                row.addView(info, new LinearLayout.LayoutParams(0, dp(62), 1));
                row.setOnClickListener(v -> {
                    if (viewer.id == null || viewer.id.trim().isEmpty()) return;
                    Intent profile = new Intent(requireContext(), ProfileActivity.class);
                    profile.putExtra(ProfileActivity.EXTRA_USER_ID, viewer.id);
                    startActivity(profile);
                });
                list.addView(row);
            }
        }
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(0), 1));

        TextView close = text("إغلاق", 14, 0xFFB82768, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(0xFFFCE8F1, dp(22)));
        panel.addView(close, new LinearLayout.LayoutParams(-1, dp(46)));
        close.setOnClickListener(v -> sheet.dismiss());

        sheet.setContentView(panel);
        Window w = sheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.55f);
        }
        sheet.setOnShowListener(d -> {
            Window ww = sheet.getWindow();
            if (ww != null) {
                ww.setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * 0.72f));
                ww.setGravity(Gravity.BOTTOM);
            }
        });
        sheet.show();
    }

    private void confirmDeleteStory(StoriesDtos.StoryDto story, Dialog viewer) {
        if (story == null || story.id == null || !story.isMine) return;
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("حذف الستوري؟")
                .setMessage("سيتم حذف الستوري نهائياً وإزالتها من قائمة جميع المستخدمين.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, which) -> c.getIoExecutor().execute(() -> {
                    com.Dramizo.Series.domain.model.Result<Void> result =
                            ApiCall.execute(c.getStoriesApi().delete(story.id));
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (result.success) {
                            feed.removeIf(item -> story.id.equals(item.id));
                            bindStories();
                            viewer.dismiss();
                            Toast.makeText(requireContext(), "تم حذف الستوري", Toast.LENGTH_SHORT).show();
                            load();
                        } else Toast.makeText(requireContext(), "تعذر حذف الستوري", Toast.LENGTH_SHORT).show();
                    });
                })).show();
    }

    private void sendStoryReaction(StoriesDtos.StoryDto story, String emoji) {
        if (story == null || story.id == null) return;
        c.getIoExecutor().execute(() -> { com.Dramizo.Series.domain.model.Result<Object> result = ApiCall.execute(c.getStoriesApi().react(story.id,Collections.singletonMap("emoji",emoji)));
            if (!isAdded()) return; requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),result.success?"تم إرسال التفاعل":"تعذر إرسال التفاعل",Toast.LENGTH_SHORT).show()); });
    }

    private void sendStoryReply(StoriesDtos.StoryDto story, String message, Dialog dialog) {
        if (story == null || story.userId == null || story.userId.trim().isEmpty()) return;
        c.getIoExecutor().execute(() -> {
            com.Dramizo.Series.domain.model.Result<ChatDtos.ConversationDto> conversation = ApiCall.execute(c.getChatApi().create(Collections.singletonMap("peerId",story.userId)));
            boolean sent=false;
            if (conversation.success && conversation.data != null && conversation.data.id != null) {
                ChatDtos.SendMessageRequest request = new ChatDtos.SendMessageRequest("رد على الستوري: " + message,"text",null);
                sent=ApiCall.execute(c.getChatApi().send(conversation.data.id,request)).success;
            }
            final boolean ok=sent; if(!isAdded())return;
            requireActivity().runOnUiThread(() -> { Toast.makeText(requireContext(),ok?"تم إرسال الرد برسالة خاصة":"تعذر إرسال الرد",Toast.LENGTH_SHORT).show(); if(ok)dialog.dismiss(); });
        });
    }

    private File cachedStoryFile(String storyId, String mediaUrl) throws Exception {
        File directory = new File(requireContext().getCacheDir(), "stories");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("تعذر إنشاء ذاكرة الستوري المؤقتة");
        String safeId = storyId == null ? "story" : storyId.replaceAll("[^A-Za-z0-9_-]", "");
        String extension = ".mp4";
        if (mediaUrl != null) {
            String path = mediaUrl.split("\\?")[0].toLowerCase(Locale.ROOT);
            int dot = path.lastIndexOf('.');
            if (dot >= 0) {
                String candidate = path.substring(dot);
                if (candidate.equals(".mp4") || candidate.equals(".mov") || candidate.equals(".webm") || candidate.equals(".3gp")) extension = candidate;
            }
        }
        return new File(directory, safeId + extension);
    }

    private void downloadStoryToCache(String url, File destination) throws Exception {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".part");
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .callTimeout(90, TimeUnit.SECONDS).build();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("تعذر تحميل فيديو الستوري");
            try (InputStream input = response.body().byteStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
            }
        } catch (Exception error) {
            if (temporary.exists()) temporary.delete();
            throw error;
        }
        if (destination.exists()) destination.delete();
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("تعذر حفظ فيديو الستوري مؤقتاً");
        }
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
