package com.Dramizo.Series.presentation.home;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.Dramizo.Series.data.remote.api.UploadApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.bumptech.glide.Glide;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

/** Persistent moments feed: server-owned posts, visibility, likes and comments. */
public class HomeMomentsFragment extends Fragment {
    private static final int PICK_IMAGE = 8417;
    public static final String ARG_PROFILE_USER_ID = "moments_profile_user_id";
    private String profileUserId;
    private AppContainer container;
    private LinearLayout root, feedContainer;
    private EditText composer;
    private TextView privacySelector, imageStatus, loading;
    private ProgressBar publishProgress;
    private Button publishButton, imagePickerButton;
    private ImageView selectedPreview;
    private View selectedImagePanel;
    private int selectedVisibility = 0;
    private Uri selectedImage;
    private String uploadedImageUrl;
    private final List<Map<String, Object>> posts = new ArrayList<>();
    private boolean busy;

    public static HomeMomentsFragment newInstance() { return new HomeMomentsFragment(); }
    public static HomeMomentsFragment forProfile(String userId) {
        HomeMomentsFragment fragment = new HomeMomentsFragment();
        Bundle args = new Bundle(); args.putString(ARG_PROFILE_USER_ID, userId);
        fragment.setArguments(args); return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        container = ContainerProvider.from(requireActivity());
        profileUserId = getArguments() != null ? getArguments().getString(ARG_PROFILE_USER_ID) : null;
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(24));
        root.setBackgroundColor(0xFFF7F7FA);
        scroll.addView(root);
        LinearLayout hero = new LinearLayout(requireContext());
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable heroGradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF94144E, 0xFFD92768, 0xFFF45A85});
        heroGradient.setCornerRadius(dp(22));
        hero.setBackground(heroGradient);
        if (android.os.Build.VERSION.SDK_INT >= 21) hero.setElevation(dp(3));
        TextView heading = text("اللحظات", 25, 0xFFFFFFFF, true);
        heading.setPadding(0, 0, 0, dp(4));
        hero.addView(heading, matchWrap());
        TextView sub = text("مساحتك لمشاركة ما يستحق أن يُرى", 14, 0xFFFFE8F0, false);
        sub.setPadding(0, 0, 0, 0);
        hero.addView(sub, matchWrap());
        root.addView(hero, matchWrap());

        LinearLayout composerCard = card();
        composerCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        composerCard.setBackground(rounded(0xFFFFFFFF, 0xFFF0E8EE, dp(20), dp(1)));
        if (android.os.Build.VERSION.SDK_INT >= 21) composerCard.setElevation(dp(3));
        TextView composerTitle = text("أنشئ لحظة جديدة", 17, 0xFF25232A, true);
        composerCard.addView(composerTitle, matchWrap());
        TextView composerHint = text("اكتب شيئاً جميلاً أو أرفق صورة من يومك", 12, 0xFF898590, false);
        composerHint.setPadding(0, 0, 0, dp(10));
        composerCard.addView(composerHint, matchWrap());
        composer = new EditText(requireContext());
        composer.setHint("بماذا تود أن تشارك أصدقاءك؟");
        composer.setMinLines(3);
        composer.setMaxLines(7);
        composer.setTextSize(15);
        composer.setPadding(dp(13), dp(12), dp(13), dp(12));
        composer.setGravity(Gravity.TOP | Gravity.RIGHT);
        composer.setBackground(rounded(0xFFF8F7FA, 0xFFEAE7EF, dp(14), dp(1)));
        composer.setTextColor(0xFF292630);
        composer.setHintTextColor(0xFFA29DA8);
        composerCard.addView(composer, matchWrap());

        selectedImagePanel = new LinearLayout(requireContext());
        ((LinearLayout) selectedImagePanel).setOrientation(LinearLayout.VERTICAL);
        selectedImagePanel.setVisibility(View.GONE);
        selectedPreview = new ImageView(requireContext());
        selectedPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ((LinearLayout) selectedImagePanel).addView(selectedPreview,
                new LinearLayout.LayoutParams(-1, dp(180)));
        Button removePhoto = button("إزالة الصورة  ×");
        styleButton(removePhoto, 0xFFFFF0F4, 0xFFB70F52, dp(12));
        removePhoto.setOnClickListener(v -> clearSelectedImage());
        ((LinearLayout) selectedImagePanel).addView(removePhoto, matchWrap());
        LinearLayout.LayoutParams previewLp = matchWrap();
        previewLp.topMargin = dp(10);
        composerCard.addView(selectedImagePanel, previewLp);

        LinearLayout toolsRow = new LinearLayout(requireContext());
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER_VERTICAL);
        toolsRow.setPadding(0, dp(12), 0, dp(8));
        imagePickerButton = button("＋  إضافة صورة");
        styleButton(imagePickerButton, 0xFFFFF0F5, 0xFFB70F52, dp(14));
        imagePickerButton.setCompoundDrawablePadding(dp(5));
        imagePickerButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_IMAGE);
        });
        privacySelector = text("🌐  عام  ▾", 13, 0xFF5D5664, true);
        privacySelector.setGravity(Gravity.CENTER);
        privacySelector.setPadding(dp(12), dp(10), dp(12), dp(10));
        privacySelector.setBackground(rounded(0xFFF5F3F7, 0xFFE8E4EB, dp(14), dp(1)));
        privacySelector.setOnClickListener(v -> chooseVisibility());
        toolsRow.addView(imagePickerButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        privacyLp.leftMargin = dp(8);
        toolsRow.addView(privacySelector, privacyLp);
        composerCard.addView(toolsRow, matchWrap());

        imageStatus = text("", 12, 0xFF777782, false);
        imageStatus.setGravity(Gravity.CENTER);
        composerCard.addView(imageStatus, matchWrap());
        publishProgress = new ProgressBar(requireContext());
        publishProgress.setIndeterminate(true);
        publishProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        progressLp.gravity = Gravity.CENTER;
        composerCard.addView(publishProgress, progressLp);
        publishButton = button("نشر اللحظة  ↑");
        publishButton.setTextSize(15);
        publishButton.setTypeface(null, android.graphics.Typeface.BOLD);
        styleButton(publishButton, 0xFFB70F52, 0xFFFFFFFF, dp(14));
        publishButton.setElevation(dp(2));
        publishButton.setOnClickListener(v -> publish());
        LinearLayout.LayoutParams publishLp = new LinearLayout.LayoutParams(-1, dp(50));
        publishLp.topMargin = dp(6);
        composerCard.addView(publishButton, publishLp);
        root.addView(composerCard, matchWrap());
        if (profileUserId != null && !profileUserId.trim().isEmpty()) {
            heading.setText("لحظاتي");
            sub.setText("المنشورات التي يسمح صاحب الحساب بعرضها");
            composerCard.setVisibility(View.GONE);
            hero.setVisibility(View.GONE);
        }
        loading = text("جارٍ تحميل اللحظات…", 14, 0xFF777782, false);
        loading.setGravity(Gravity.CENTER);
        root.addView(loading, new LinearLayout.LayoutParams(-1, dp(54)));
        feedContainer = new LinearLayout(requireContext());
        feedContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(feedContainer, matchWrap());
        return scroll;
    }

    @Override public void onResume() { super.onResume(); loadFeed(); }

    public void reloadFromHost() { loadFeed(); }

    private void loadFeed() {
        if (container == null || busy) return;
        busy = true;
        runIo(() -> {
            try {
                Response<ApiResponse<List<Map<String, Object>>>> response =
                    (profileUserId != null && !profileUserId.trim().isEmpty()
                        ? container.getUserApi().userMoments(profileUserId)
                        : container.getUserApi().momentsFeed(1, 30)).execute();
                List<Map<String, Object>> data = response.isSuccessful() && response.body() != null
                    ? response.body().data : null;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    posts.clear();
                    if (data != null) posts.addAll(data);
                    renderFeed();
                    busy = false;
                });
            } catch (Exception e) { showError("تعذر تحميل اللحظات"); }
        });
    }

    private void publish() {
        String body = composer.getText().toString().trim();
        if (body.isEmpty() && selectedImage == null && uploadedImageUrl == null) {
            Toast.makeText(requireContext(), "اكتب نصاً أو أضف صورة", Toast.LENGTH_SHORT).show();
            return;
        }
        setComposerEnabled(false);
        setPublishStatus(selectedImage != null && uploadedImageUrl == null
                ? "جارٍ تجهيز الصورة ورفعها…" : "جارٍ نشر اللحظة…", true);
        runIo(() -> {
            try {
                String imageUrl = uploadedImageUrl;
                if (selectedImage != null && imageUrl == null) {
                    setPublishStatus("جارٍ رفع الصورة إلى التخزين…", true);
                    imageUrl = uploadImage(selectedImage);
                    uploadedImageUrl = imageUrl;
                }
                setPublishStatus("تم رفع الصورة، جارٍ حفظ النص والمنشور…", true);
                String[] values = {"public", "followers", "friends", "private"};
                Map<String, Object> payload = new HashMap<>();
                payload.put("body", body);
                payload.put("imageUrl", imageUrl);
                payload.put("visibility", values[Math.max(0, Math.min(selectedVisibility, values.length - 1))]);
                Response<ApiResponse<Map<String, Object>>> response = container.getUserApi().createMoment(payload).execute();
                if (!response.isSuccessful() || response.body() == null || !response.body().success) {
                    String error = response != null ? UploadApi.errorMessage(response, "تعذر حفظ المنشور") : "تعذر حفظ المنشور";
                    throw new IllegalStateException(error);
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    composer.setText("");
                    clearSelectedImage();
                    selectedVisibility = 0;
                    updatePrivacyLabel();
                    setPublishStatus("", false);
                    setComposerEnabled(true);
                    Toast.makeText(requireContext(), "تم نشر اللحظة مع الصورة بنجاح", Toast.LENGTH_SHORT).show();
                    busy = false;
                    loadFeed();
                });
            } catch (Exception e) {
                setPublishStatus("لم يكتمل النشر. احتفظنا بالنص والصورة لإعادة المحاولة.", false);
                showError("تعذر نشر اللحظة؛ " + (e.getMessage() == null ? "تحقق من الاتصال والصورة" : e.getMessage()));
                requireActivity().runOnUiThread(() -> setComposerEnabled(true));
            }
        });
    }

    private String uploadImage(Uri uri) throws Exception {
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("image unavailable");
            byte[] buffer = new byte[8192]; int n; long total = 0;
            while ((n = input.read(buffer)) > 0) {
                total += n; if (total > 40L * 1024 * 1024) throw new IllegalStateException("image too large");
                output.write(buffer, 0, n);
            }
            String name = "moment.jpg";
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String candidate = cursor.getString(0);
                    if (candidate != null && !candidate.trim().isEmpty()) name = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
                }
            }
            String mimeType = requireContext().getContentResolver().getType(uri);
            if (mimeType == null || !mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                mimeType = "image/jpeg";
            }
            RequestBody body = RequestBody.create(MediaType.parse(mimeType), output.toByteArray());
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", name, body);
            Response<ApiResponse<UploadApi.UploadResult>> response = container.getUploadApi().upload(part).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().data == null
                    || response.body().data.url == null) {
                throw new IllegalStateException(UploadApi.errorMessage(response, "تعذر رفع الصورة"));
            }
            String returnedUrl = response.body().data.url.trim();
            // UploadsService returns a root-relative /uploads/... URL. Resolve it to
            // the canonical backend origin before persisting the moment.
            if (returnedUrl.startsWith("/")) returnedUrl = com.Dramizo.Series.util.ApiOrigin.origin() + returnedUrl;
            if (!returnedUrl.matches("(?i)^https?://.+")) {
                throw new IllegalStateException("رابط الصورة الذي أعاده الخادم غير صالح");
            }
            return returnedUrl;
        }
    }

    private void renderFeed() {
        if (profileUserId != null && !profileUserId.trim().isEmpty()) {
            renderProfileGrid();
            return;
        }
        if (feedContainer == null) return;
        feedContainer.removeAllViews();
        loading.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        loading.setText(posts.isEmpty() ? "لا توجد لحظات بعد — كن أول من ينشر" : "");
        for (Map<String, Object> post : posts) {
            LinearLayout item = card();
            String name = string(post, "displayName");
            if (name.isEmpty()) name = string(post, "username");
            if (name.isEmpty()) name = "مستخدم";
            final String authorId = string(post, "userId");
            LinearLayout authorRow = new LinearLayout(requireContext());
            authorRow.setOrientation(LinearLayout.HORIZONTAL);
            authorRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView avatar = new ImageView(requireContext());
            avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackground(rounded(0xFFF3EAF0, 0xFFF3EAF0, dp(22)));
            avatar.setClipToOutline(true);
            String avatarUrl = string(post, "avatarUrl");
            if (!avatarUrl.isEmpty()) Glide.with(this).load(avatarUrl).circleCrop()
                .placeholder(rounded(0xFFF3EAF0, 0xFFF3EAF0, dp(22))).into(avatar);
            else avatar.setImageDrawable(rounded(0xFFF3EAF0, 0xFFF3EAF0, dp(22)));
            LinearLayout authorInfo = new LinearLayout(requireContext());
            authorInfo.setOrientation(LinearLayout.VERTICAL);
            authorInfo.setPadding(dp(10), 0, 0, 0);
            TextView author = text(name, 15, 0xFF24212A, true);
            author.setPadding(0, 0, 0, 0);
            TextView authorMeta = text("@" + string(post, "username"), 11, 0xFF8B8491, false);
            authorMeta.setPadding(0, dp(2), 0, 0);
            authorInfo.addView(author, matchWrap());
            authorInfo.addView(authorMeta, matchWrap());
            authorRow.addView(avatar);
            authorRow.addView(authorInfo, new LinearLayout.LayoutParams(0, -2, 1));
            String createdAt = string(post, "createdAt");
            if (!createdAt.isEmpty()) {
                String dateLabel = createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt;
                authorRow.addView(text(dateLabel, 10, 0xFF9A94A0, false));
            }
            View.OnClickListener openAuthor = v -> {
                if (authorId.isEmpty()) return;
                Intent intent = new Intent(requireContext(), com.Dramizo.Series.presentation.profile.ProfileActivity.class);
                intent.putExtra(com.Dramizo.Series.presentation.profile.ProfileActivity.EXTRA_USER_ID, authorId);
                startActivity(intent);
            };
            authorRow.setOnClickListener(openAuthor);
            avatar.setOnClickListener(openAuthor);
            item.addView(authorRow, matchWrap());
            String body = string(post, "body");
            if (!body.isEmpty()) {
                TextView bodyText = text(body, 15, 0xFF33313A, false);
                bodyText.setPadding(0, dp(12), 0, dp(10));
                item.addView(bodyText, matchWrap());
            }
            String image = string(post, "imageUrl");
            if (!image.isEmpty()) {
                ImageView photo = new ImageView(requireContext());
                photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(250)));
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photo.setBackground(rounded(0xFFF3F0F4, 0xFFF3F0F4, dp(14)));
                photo.setClipToOutline(true);
                photo.setAdjustViewBounds(true);
                Glide.with(this).load(image)
                    .placeholder(rounded(0xFFF3F0F4, 0xFFF3F0F4, dp(14)))
                    .error(rounded(0xFFFFE7EF, 0xFFFFE7EF, dp(14)))
                    .centerCrop().into(photo);
                LinearLayout.LayoutParams photoLp = matchWrap();
                photoLp.topMargin = dp(4);
                photoLp.bottomMargin = dp(10);
                item.addView(photo, photoLp);
            }
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button like = button((Boolean.TRUE.equals(post.get("likedByMe")) ? "♥  " : "♡  ")
                    + number(post.get("likeCount")) + " إعجاب");
            if (Boolean.TRUE.equals(post.get("likedByMe"))) styleButton(like, 0xFFFFE6EE, 0xFFB70F52, dp(12));
            like.setOnClickListener(v -> like(post));
            Button comments = button("تعليقات " + number(post.get("commentCount")));
            comments.setOnClickListener(v -> comments(post));
            Button follow = button("متابعة");
            String myId = container.getSessionManager().getUserId();
            follow.setEnabled(!authorId.isEmpty() && !authorId.equals(myId));
            follow.setOnClickListener(v -> followAuthor(authorId));
            Button share = button("مشاركة");
            share.setOnClickListener(v -> sharePost(post));
            row.addView(like, new LinearLayout.LayoutParams(0, dp(42), 1));
            row.addView(comments, new LinearLayout.LayoutParams(0, dp(42), 1));
            row.addView(follow, new LinearLayout.LayoutParams(0, dp(42), 1));
            row.addView(share, new LinearLayout.LayoutParams(0, dp(42), 1));
            View actionDivider = new View(requireContext());
            actionDivider.setBackgroundColor(0xFFF1EDF2);
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
            dividerLp.topMargin = dp(8);
            dividerLp.bottomMargin = dp(8);
            item.addView(actionDivider, dividerLp);
            item.addView(row, matchWrap());
            Button report = button("⚑  إبلاغ");
            report.setTextColor(0xFFB3261E);
            report.setBackground(rounded(0xFFFFF4F4, 0xFFFFE2E2, dp(12), dp(1)));
            report.setOnClickListener(v -> askMomentReport(post));
            LinearLayout reportRow = new LinearLayout(requireContext());
            reportRow.setGravity(Gravity.END);
            reportRow.addView(report, new LinearLayout.LayoutParams(-2, dp(40)));
            item.addView(reportRow, matchWrap());
            feedContainer.addView(item, matchWrap());
        }
    }

    private void renderProfileGrid() {
        feedContainer.removeAllViews();
        loading.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        loading.setText(posts.isEmpty() ? "لا توجد لحظات ظاهرة لهذا الحساب" : "");
        LinearLayout currentRow = null;
        for (int i = 0; i < posts.size(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(requireContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                feedContainer.addView(currentRow, matchWrap());
            }
            Map<String, Object> post = posts.get(i);
            LinearLayout tile = new LinearLayout(requireContext());
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setBackgroundColor(0xFFFFFFFF);
            tile.setPadding(dp(5), dp(5), dp(5), dp(5));
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0, dp(168), 1);
            tileLp.setMargins(dp(3), dp(3), dp(3), dp(3));
            String image = string(post, "imageUrl");
            if (!image.isEmpty()) {
                ImageView photo = new ImageView(requireContext());
                photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(138)));
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Glide.with(this).load(image).into(photo);
                tile.addView(photo);
            } else {
                TextView preview = text(string(post, "body"), 13, 0xFF33333A, false);
                preview.setMaxLines(5);
                tile.addView(preview, new LinearLayout.LayoutParams(-1, dp(138)));
            }
            tile.addView(text("♡ " + number(post.get("likeCount")), 12, 0xFFB9164B, true));
            tile.setOnClickListener(v -> showMomentDetails(post));
            currentRow.addView(tile, tileLp);
        }
        if (currentRow != null && posts.size() % 2 != 0) {
            currentRow.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, dp(168), 1));
        }
    }

    private void showMomentDetails(Map<String, Object> post) {
        LinearLayout detail = new LinearLayout(requireContext());
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(16), dp(12), dp(16), dp(8));
        String image = string(post, "imageUrl");
        if (!image.isEmpty()) {
            ImageView photo = new ImageView(requireContext());
            photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(220)));
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(image).into(photo);
            detail.addView(photo);
        }
        detail.addView(text(string(post, "body"), 15, 0xFF222222, false));
        Button report = button("⚑  إبلاغ عن المنشور");
        report.setTextColor(0xFFB3261E);
        report.setBackground(rounded(0xFFFFF4F4, 0xFFFFE2E2, dp(12), dp(1)));
        report.setOnClickListener(v -> {
            askMomentReport(post);
        });
        detail.addView(report, matchWrap());
        new AlertDialog.Builder(requireContext()).setTitle("اللحظة").setView(detail)
            .setPositiveButton("إعجاب", (d, w) -> like(post))
            .setNeutralButton("تعليق", (d, w) -> comments(post))
            .setNegativeButton("إغلاق", null).show();
    }

    private void askMomentReport(Map<String, Object> post) {
        String[] labels = {"محتوى مزعج أو سبام", "إساءة أو تحرّش", "محتوى غير لائق", "عنف أو تهديد", "احتيال أو تضليل", "سبب آخر"};
        String[] reasons = {"spam", "harassment", "inappropriate", "violence", "scam", "other"};
        new AlertDialog.Builder(requireContext())
            .setTitle("سبب الإبلاغ")
            .setItems(labels, (dialog, which) -> {
                EditText details = new EditText(requireContext());
                details.setHint("تفاصيل إضافية (اختياري)");
                details.setMinLines(2);
                new AlertDialog.Builder(requireContext())
                    .setTitle("إرسال البلاغ")
                    .setMessage("سيصل البلاغ إلى لوحة الإدارة لمراجعته.")
                    .setView(details)
                    .setPositiveButton("إرسال", (d, w) -> submitMomentReport(post, reasons[which], details.getText().toString()))
                    .setNegativeButton("إلغاء", null)
                    .show();
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void submitMomentReport(Map<String, Object> post, String reason, String description) {
        String id = string(post, "id");
        if (id.isEmpty()) {
            Toast.makeText(requireContext(), "تعذر تحديد المنشور", Toast.LENGTH_SHORT).show();
            return;
        }
        runIo(() -> {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("reason", reason);
                payload.put("description", description == null ? "" : description.trim());
                Response<ApiResponse<Map<String, Object>>> response = container.getUserApi().reportMoment(id, payload).execute();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    String message = response.isSuccessful() && response.body() != null && response.body().success
                        ? "تم إرسال البلاغ إلى الإدارة"
                        : "تعذر إرسال البلاغ؛ قد تكون أبلغت عن هذا المنشور سابقاً";
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) { showError("تعذر إرسال البلاغ، تحقق من الاتصال"); }
        });
    }

    private void followAuthor(String authorId) {
        if (authorId == null || authorId.trim().isEmpty()) return;
        runIo(() -> {
            try {
                Response<ApiResponse<Object>> response = container.getUserApi().follow(authorId).execute();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                    response.isSuccessful() ? "تمت المتابعة" : "تعذر إتمام المتابعة", Toast.LENGTH_SHORT).show());
            } catch (Exception e) { showError("تعذر إتمام المتابعة"); }
        });
    }

    private void sharePost(Map<String, Object> post) {
        String body = string(post, "body");
        String image = string(post, "imageUrl");
        String content = body + (image.isEmpty() ? "" : "\n" + image);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, content.trim());
        startActivity(Intent.createChooser(intent, "مشاركة اللحظة"));
    }

    private void like(Map<String, Object> post) {
        String id = string(post, "id");
        runIo(() -> {
            try {
                container.getUserApi().toggleMomentLike(id).execute();
                if (isAdded()) requireActivity().runOnUiThread(this::loadFeed);
            } catch (Exception e) { showError("تعذر تسجيل الإعجاب"); }
        });
    }

    private void comments(Map<String, Object> post) {
        String id = string(post, "id");
        EditText input = new EditText(requireContext());
        input.setHint("اكتب تعليقاً");
        new AlertDialog.Builder(requireContext()).setTitle("التعليقات")
            .setView(input).setPositiveButton("إرسال", (d, w) -> {
                String body = input.getText().toString().trim();
                if (body.isEmpty()) return;
                runIo(() -> {
                    try {
                        Map<String, String> payload = new HashMap<>(); payload.put("body", body);
                        container.getUserApi().commentOnMoment(id, payload).execute();
                        if (isAdded()) requireActivity().runOnUiThread(this::loadFeed);
                    } catch (Exception e) { showError("تعذر إرسال التعليق"); }
                });
            }).setNegativeButton("إغلاق", null).show();
    }

    private void setComposerEnabled(boolean enabled) {
        if (composer != null) composer.setEnabled(enabled);
        if (privacySelector != null) privacySelector.setEnabled(enabled);
        if (imagePickerButton != null) imagePickerButton.setEnabled(enabled);
        if (publishButton != null) publishButton.setEnabled(enabled);
    }

    private void setPublishStatus(String message, boolean inProgress) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            if (imageStatus != null) imageStatus.setText(message == null ? "" : message);
            if (publishProgress != null) publishProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
            if (publishButton != null) publishButton.setText(inProgress ? "جارٍ النشر…" : "نشر اللحظة  ↑");
        });
    }

    @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            selectedImage = data.getData();
            uploadedImageUrl = null;
            if (selectedImagePanel != null) selectedImagePanel.setVisibility(View.VISIBLE);
            if (selectedPreview != null) {
                Glide.with(this).load(selectedImage).centerCrop()
                    .placeholder(rounded(0xFFF2EFF4, 0xFFF2EFF4, dp(12)))
                    .error(rounded(0xFFFFE7EF, 0xFFFFE7EF, dp(12)))
                    .into(selectedPreview);
            }
            if (imageStatus != null) imageStatus.setText("تم اختيار الصورة — ستُرفع عند النشر");
            try { requireContext().getContentResolver().takePersistableUriPermission(selectedImage,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); }
            catch (Exception ignored) {}
        }
    }

    private void chooseVisibility() {
        String[] labels = {"🌐  عام — يمكن للجميع رؤيتها", "👥  المتابعون", "🤝  الأصدقاء", "🔒  أنا فقط"};
        new AlertDialog.Builder(requireContext())
            .setTitle("من يمكنه رؤية اللحظة؟")
            .setSingleChoiceItems(labels, selectedVisibility, (dialog, which) -> {
                selectedVisibility = which;
                updatePrivacyLabel();
                dialog.dismiss();
            })
            .setNegativeButton("إلغاء", null)
            .show();
    }

    private void updatePrivacyLabel() {
        if (privacySelector == null) return;
        String[] labels = {"🌐  عام  ▾", "👥  المتابعون  ▾", "🤝  الأصدقاء  ▾", "🔒  أنا فقط  ▾"};
        privacySelector.setText(labels[Math.max(0, Math.min(selectedVisibility, labels.length - 1))]);
    }

    private void clearSelectedImage() {
        selectedImage = null;
        uploadedImageUrl = null;
        if (selectedImagePanel != null) selectedImagePanel.setVisibility(View.GONE);
        if (selectedPreview != null) selectedPreview.setImageDrawable(null);
        if (imageStatus != null) imageStatus.setText("");
    }

    private GradientDrawable rounded(int color, int strokeColor, int radiusDp) {
        return rounded(color, strokeColor, radiusDp, 0);
    }

    private GradientDrawable rounded(int color, int strokeColor, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private void styleButton(Button button, int backgroundColor, int textColor, int radiusDp) {
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(13);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        button.setMinHeight(dp(42));
        button.setBackground(rounded(backgroundColor, backgroundColor, radiusDp));
        if (android.os.Build.VERSION.SDK_INT >= 21) button.setStateListAnimator(null);
    }

    private void runIo(Runnable runnable) { ExecutorService executor = container.getIoExecutor(); executor.execute(runnable); }
    private void showError(String message) {
        if (isAdded()) requireActivity().runOnUiThread(() -> {
            if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            busy = false;
        });
    }
    private LinearLayout card() {
        LinearLayout box = new LinearLayout(requireContext()); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(12));
        box.setBackground(rounded(0xFFFFFFFF, 0xFFF0EDF2, dp(18), dp(1)));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = matchWrap(); lp.bottomMargin = dp(14);
        box.setLayoutParams(lp); return box;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(requireContext()); v.setText(value); v.setTextSize(size);
        v.setTextColor(color); if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, dp(4), 0, dp(5)); return v;
    }
    private Button button(String value) {
        Button b = new Button(requireContext());
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(0xFF5E5864);
        b.setPadding(dp(7), dp(2), dp(7), dp(2));
        b.setMinHeight(dp(40));
        b.setBackground(rounded(0xFFF6F4F8, 0xFFEAE6EE, dp(12), dp(1)));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String string(Map<String, Object> map, String key) {
        Object v = map.get(key); return v == null ? "" : String.valueOf(v);
    }
    private String number(Object value) {
        try { return String.format(Locale.getDefault(), "%d", (long) Double.parseDouble(String.valueOf(value))); }
        catch (Exception e) { return "0"; }
    }
}
