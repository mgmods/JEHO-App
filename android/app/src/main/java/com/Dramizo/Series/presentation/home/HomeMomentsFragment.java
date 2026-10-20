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
import androidx.core.widget.NestedScrollView;
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
import com.Dramizo.Series.util.AvatarCosmetics;
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
    private AlertDialog composerDialog;
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
        NestedScrollView scroll = new NestedScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setNestedScrollingEnabled(true);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
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
        composerCard.setVisibility(View.GONE);
        Button openComposerButton = button("＋  نشر لحظة");
        styleButton(openComposerButton, 0xFFB70F52, 0xFFFFFFFF, dp(20));
        openComposerButton.setTextSize(13);
        LinearLayout.LayoutParams openComposerLp = new LinearLayout.LayoutParams(-2, dp(42));
        openComposerLp.gravity = Gravity.END;
        openComposerLp.topMargin = dp(8);
        openComposerLp.bottomMargin = dp(4);
        root.addView(openComposerButton, openComposerLp);
        openComposerButton.setOnClickListener(v -> {
            if (composerDialog != null && composerDialog.isShowing()) return;
            if (composerCard.getParent() instanceof ViewGroup) {
                ((ViewGroup) composerCard.getParent()).removeView(composerCard);
            }
            composerCard.setVisibility(View.VISIBLE);
            composerDialog = new AlertDialog.Builder(requireContext())
                    .setTitle("إنشاء لحظة")
                    .setView(composerCard)
                    .setNegativeButton("إغلاق", null)
                    .create();
            composerDialog.setOnDismissListener(d -> {
                composerCard.setVisibility(View.GONE);
                if (isAdded() && composerCard.getParent() == null) {
                    root.addView(composerCard, Math.min(1, root.getChildCount()), matchWrap());
                }
                openComposerButton.setVisibility(profileUserId == null || profileUserId.trim().isEmpty()
                        ? View.VISIBLE : View.GONE);
            });
            openComposerButton.setVisibility(View.GONE);
            composerDialog.show();
        });
        if (profileUserId != null && !profileUserId.trim().isEmpty()) {
            openComposerButton.setVisibility(View.GONE);
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
                Response<ApiResponse<Map<String, Object>>> response = container.getUserApi().createMoment(com.Dramizo.Series.util.ApiOrigin.apiV1() + "moments", payload).execute();
                if (!response.isSuccessful() || response.body() == null || !response.body().success) {
                    String error = response != null ? UploadApi.errorMessage(response, "تعذر حفظ المنشور") : "تعذر حفظ المنشور";
                    throw new IllegalStateException(error);
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    composer.setText("");
                    clearSelectedImage();
                    if (imagePickerButton != null) {
                        imagePickerButton.setEnabled(true);
                        imagePickerButton.setText("＋  إضافة صورة");
                    }
                    selectedVisibility = 0;
                    updatePrivacyLabel();
                    setPublishStatus("", false);
                    setComposerEnabled(true);
                    Toast.makeText(requireContext(), "تم نشر اللحظة مع الصورة بنجاح", Toast.LENGTH_SHORT).show();
                    busy = false;
                    if (composerDialog != null && composerDialog.isShowing()) composerDialog.dismiss();
                    loadFeed();
                });
            } catch (Exception e) {
                setPublishStatus("لم يكتمل النشر. احتفظنا بالنص والصورة لإعادة المحاولة.", false);
                showError("تعذر نشر اللحظة؛ " + (e.getMessage() == null ? "تحقق من الاتصال والصورة" : e.getMessage()));
                requireActivity().runOnUiThread(() -> {
                    setComposerEnabled(true);
                    if (imagePickerButton != null) imagePickerButton.setEnabled(true);
                });
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
            FrameLayout avatarStack = new FrameLayout(requireContext());
            avatarStack.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
            ImageView avatar = new ImageView(requireContext());
            FrameLayout.LayoutParams avatarLp = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER);
            avatar.setLayoutParams(avatarLp);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackground(rounded(0xFFF3EAF0, 0xFFF3EAF0, dp(22)));
            avatar.setClipToOutline(true);
            String avatarUrl = string(post, "avatarUrl");
            if (!avatarUrl.isEmpty()) Glide.with(this).load(avatarUrl).circleCrop()
                .placeholder(rounded(0xFFF3EAF0, 0xFFF3EAF0, dp(22))).into(avatar);
            else avatar.setImageDrawable(rounded(0xFFF3EAF0, 0xFFF3EAF0, dp(22)));
            ImageView avatarFrame = new ImageView(requireContext());
            avatarFrame.setLayoutParams(new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
            avatarFrame.setScaleType(ImageView.ScaleType.FIT_CENTER);
            String frameUrl = string(post, "frameUrl");
            if (frameUrl.isEmpty()) frameUrl = string(post, "avatarFrameUrl");
            if (frameUrl.isEmpty()) frameUrl = string(post, "equippedFrameUrl");
            AvatarCosmetics.bindStacked(avatar, avatarFrame, avatarUrl, frameUrl);
            avatarStack.addView(avatar);
            avatarStack.addView(avatarFrame);
            LinearLayout authorInfo = new LinearLayout(requireContext());
            authorInfo.setOrientation(LinearLayout.VERTICAL);
            authorInfo.setPadding(dp(10), 0, 0, 0);
            TextView author = text(name, 15, 0xFF24212A, true);
            author.setPadding(0, 0, 0, 0);
            String publicId = string(post, "publicId");
            String createdAt = string(post, "createdAt");
            String dateLabel = createdAt.isEmpty() ? "" : (createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt);
            TextView authorMeta = text("ID: " + (publicId.isEmpty() ? "—" : publicId)
                    + (dateLabel.isEmpty() ? "" : "   •   " + dateLabel), 11, 0xFF8B8491, false);
            authorMeta.setPadding(0, dp(3), 0, 0);
            authorInfo.addView(author, matchWrap());
            authorInfo.addView(authorMeta, matchWrap());
            authorRow.addView(avatarStack);
            authorRow.addView(authorInfo, new LinearLayout.LayoutParams(0, -2, 1));
            Button followTop = button("＋ متابعة");
            styleButton(followTop, 0xFFE51E55, 0xFFFFFFFF, dp(18));
            String myId = container.getSessionManager().getUserId();
            followTop.setEnabled(!authorId.isEmpty() && !authorId.equals(myId));
            followTop.setOnClickListener(v -> followAuthor(authorId));
            authorRow.addView(followTop, new LinearLayout.LayoutParams(-2, dp(40)));
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
                photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(176)));
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photo.setBackground(rounded(0xFFF3F0F4, 0xFFF3F0F4, dp(14)));
                photo.setClipToOutline(true);
                photo.setAdjustViewBounds(false);
                photo.setContentDescription("اضغط لعرض الصورة كاملة");
                photo.setOnClickListener(v -> showFullImage(image, post));
                Glide.with(this).load(image)
                    .placeholder(rounded(0xFFF3F0F4, 0xFFF3F0F4, dp(14)))
                    .error(rounded(0xFFFFE7EF, 0xFFFFE7EF, dp(14)))
                    .centerCrop().into(photo);
                LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(-1, dp(176));
                photoLp.topMargin = dp(4);
                photoLp.bottomMargin = dp(10);
                item.addView(photo, photoLp);
            }
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            boolean liked = Boolean.TRUE.equals(post.get("likedByMe"));
            Button like = button((liked ? "♥  " : "♡  ") + number(post.get("likeCount")));
            styleButton(like, liked ? 0xFFFFE1EA : 0xFFFFF5F8,
                    liked ? 0xFFE51E55 : 0xFFB70F52, dp(12));
            like.setOnClickListener(v -> like(post));
            Button comments = button("💬  " + number(post.get("commentCount")));
            comments.setOnClickListener(v -> comments(post));
            row.addView(like, new LinearLayout.LayoutParams(0, dp(42), 1));
            row.addView(comments, new LinearLayout.LayoutParams(0, dp(42), 1));
            View actionDivider = new View(requireContext());
            actionDivider.setBackgroundColor(0xFFF1EDF2);
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
            dividerLp.topMargin = dp(8);
            dividerLp.bottomMargin = dp(8);
            item.addView(actionDivider, dividerLp);
            item.addView(row, matchWrap());
            Button report = button("⚑");
            report.setContentDescription("الإبلاغ عن اللحظة");
            report.setTextSize(10);
            report.setPadding(dp(5), 0, dp(5), 0);
            report.setMinHeight(dp(30));
            report.setTextColor(0xFFB3261E);
            report.setBackground(rounded(0xFFFFF4F4, 0xFFFFE2E2, dp(10), dp(1)));
            report.setOnClickListener(v -> askMomentReport(post));
            LinearLayout reportRow = new LinearLayout(requireContext());
            reportRow.setGravity(Gravity.END);
            reportRow.addView(report, new LinearLayout.LayoutParams(dp(34), dp(30)));
            item.addView(reportRow, matchWrap());
            item.setOnClickListener(v -> showMomentDetails(post));
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
        String image = string(post, "imageUrl");
        if (!image.isEmpty()) {
            showFullImage(image, post);
            return;
        }
        LinearLayout detail = new LinearLayout(requireContext());
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(16), dp(12), dp(16), dp(8));
        detail.addView(text(string(post, "body"), 15, 0xFF222222, false));
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button like = button((Boolean.TRUE.equals(post.get("likedByMe")) ? "♥  " : "♡  ")
                + number(post.get("likeCount")));
        like.setTextColor(0xFFE51E55);
        like.setOnClickListener(v -> { like(post); });
        Button comment = button("💬  " + number(post.get("commentCount")));
        comment.setOnClickListener(v -> comments(post));
        actions.addView(like, new LinearLayout.LayoutParams(0, dp(42), 1));
        actions.addView(comment, new LinearLayout.LayoutParams(0, dp(42), 1));
        detail.addView(actions, matchWrap());
        Button report = button("⚑");
        report.setContentDescription("الإبلاغ عن اللحظة");
        report.setTextSize(10);
        report.setMinHeight(dp(30));
        report.setOnClickListener(v -> askMomentReport(post));
        detail.addView(report, new LinearLayout.LayoutParams(dp(34), dp(30)));
        new AlertDialog.Builder(requireContext()).setTitle("اللحظة").setView(detail)
            .setNegativeButton("إغلاق", null).show();
    }

    private void showFullImage(String imageUrl, Map<String, Object> post) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) return;
        android.app.Dialog dialog = new android.app.Dialog(requireContext(),
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout page = new LinearLayout(requireContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF08080B);
        page.setPadding(dp(12), dp(14), dp(12), dp(12));
        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button close = button("✕");
        close.setTextColor(0xFFFFFFFF);
        close.setBackground(rounded(0xFF29262D, 0xFF29262D, dp(18)));
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        page.addView(top, matchWrap());
        ImageView full = new ImageView(requireContext());
        full.setScaleType(ImageView.ScaleType.FIT_CENTER);
        full.setAdjustViewBounds(false);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, 0, 1);
        imageLp.topMargin = dp(8);
        imageLp.bottomMargin = dp(8);
        page.addView(full, imageLp);
        Glide.with(this).load(imageUrl).into(full);
        String bodyText = string(post, "body");
        if (!bodyText.isEmpty()) {
            TextView caption = text(bodyText, 14, 0xFFF4F0F4, false);
            caption.setMaxLines(3);
            page.addView(caption, matchWrap());
        }
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button like = button((Boolean.TRUE.equals(post.get("likedByMe")) ? "♥  " : "♡  ")
                + number(post.get("likeCount")));
        styleButton(like, 0xFFFFE7EF, 0xFFE51E55, dp(18));
        like.setOnClickListener(v -> {
            like(post);
            dialog.dismiss();
        });
        Button comment = button("💬  " + number(post.get("commentCount")));
        styleButton(comment, 0xFF29262D, 0xFFFFFFFF, dp(18));
        comment.setOnClickListener(v -> {
            dialog.dismiss();
            comments(post);
        });
        Button report = button("⚑");
        report.setContentDescription("الإبلاغ عن اللحظة");
        report.setTextSize(10);
        report.setTextColor(0xFFFF9BAF);
        report.setBackground(rounded(0xFF29262D, 0xFF49434D, dp(12), dp(1)));
        report.setOnClickListener(v -> {
            dialog.dismiss();
            askMomentReport(post);
        });
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        actionLp.rightMargin = dp(6);
        actions.addView(like, actionLp);
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        commentLp.rightMargin = dp(6);
        actions.addView(comment, commentLp);
        actions.addView(report, new LinearLayout.LayoutParams(dp(42), dp(40)));
        page.addView(actions, matchWrap());
        dialog.setContentView(page);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
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
        final String momentId = string(post, "id");
        final android.app.Dialog dialog = new android.app.Dialog(requireContext());
        LinearLayout page = new LinearLayout(requireContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF7F7FA);
        page.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("التعليقات والردود", 20, 0xFF25232A, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("إغلاق  ×");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(-2, dp(40)));
        page.addView(header, matchWrap());

        LinearLayout postPreview = new LinearLayout(requireContext());
        postPreview.setOrientation(LinearLayout.VERTICAL);
        postPreview.setPadding(dp(12), dp(10), dp(12), dp(10));
        postPreview.setBackground(rounded(0xFFFFFFFF, 0xFFECE7ED, dp(14), dp(1)));
        String authorName = string(post, "displayName");
        if (authorName.isEmpty()) authorName = string(post, "username");
        postPreview.addView(text(authorName, 13, 0xFFB70F52, true), matchWrap());
        String previewBody = string(post, "body");
        if (!previewBody.isEmpty()) {
            TextView preview = text(previewBody, 14, 0xFF38343D, false);
            preview.setMaxLines(3);
            postPreview.addView(preview, matchWrap());
        }
        LinearLayout.LayoutParams postPreviewLp = matchWrap();
        postPreviewLp.topMargin = dp(8);
        postPreviewLp.bottomMargin = dp(8);
        page.addView(postPreview, postPreviewLp);

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(false);
        LinearLayout commentsList = new LinearLayout(requireContext());
        commentsList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(commentsList);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        final String[] replyTarget = {""};
        final String[] replyName = {""};
        TextView replyStatus = text("", 12, 0xFFB70F52, true);
        replyStatus.setVisibility(View.GONE);
        page.addView(replyStatus, matchWrap());
        LinearLayout composerRow = new LinearLayout(requireContext());
        composerRow.setOrientation(LinearLayout.HORIZONTAL);
        composerRow.setGravity(Gravity.CENTER_VERTICAL);
        composerRow.setPadding(0, dp(8), 0, 0);
        EditText input = new EditText(requireContext());
        input.setHint("اكتب تعليقك بأدب واحترام…");
        input.setTextSize(14);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setPadding(dp(13), dp(10), dp(13), dp(10));
        input.setBackground(rounded(0xFFFFFFFF, 0xFFE4DCE5, dp(22), dp(1)));
        composerRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = button("إرسال  ➤");
        styleButton(send, 0xFFB70F52, 0xFFFFFFFF, dp(20));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(-2, dp(46));
        sendLp.leftMargin = dp(8);
        composerRow.addView(send, sendLp);
        page.addView(composerRow, matchWrap());

        Runnable[] refreshComments = new Runnable[1];
        refreshComments[0] = () -> runIo(() -> {
            try {
                Response<ApiResponse<List<Map<String, Object>>>> response =
                    container.getUserApi().momentComments(momentId).execute();
                List<Map<String, Object>> rows = response.isSuccessful() && response.body() != null
                    ? response.body().data : null;
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    commentsList.removeAllViews();
                    if (rows == null || rows.isEmpty()) {
                        TextView empty = text("كن أول من يكتب تعليقاً على هذه اللحظة", 14, 0xFF8A8490, false);
                        empty.setGravity(Gravity.CENTER);
                        commentsList.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
                        return;
                    }
                    Map<String, List<Map<String, Object>>> replies = new HashMap<>();
                    List<Map<String, Object>> topLevel = new ArrayList<>();
                    for (Map<String, Object> comment : rows) {
                        String parent = string(comment, "parentCommentId");
                        if (parent.isEmpty()) topLevel.add(comment);
                        else {
                            if (!replies.containsKey(parent)) replies.put(parent, new ArrayList<>());
                            replies.get(parent).add(comment);
                        }
                    }
                    for (Map<String, Object> comment : topLevel) {
                        addCommentCard(commentsList, comment, replyTarget, replyName, replyStatus, input);
                        List<Map<String, Object>> children = replies.get(string(comment, "id"));
                        if (children != null) {
                            for (Map<String, Object> child : children) {
                                addCommentCard(commentsList, child, replyTarget, replyName, replyStatus, input);
                            }
                        }
                    }
                    for (Map<String, Object> comment : rows) {
                        String parent = string(comment, "parentCommentId");
                        if (!parent.isEmpty() && !containsCommentId(topLevel, parent)) {
                            addCommentCard(commentsList, comment, replyTarget, replyName, replyStatus, input);
                        }
                    }
                });
            } catch (Exception e) {
                if (isAdded()) requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "تعذر تحميل التعليقات، تحقق من الاتصال", Toast.LENGTH_LONG).show());
            }
        });
        send.setOnClickListener(v -> {
            String body = input.getText().toString().trim();
            if (body.isEmpty()) {
                input.setError("اكتب تعليقاً أولاً");
                return;
            }
            send.setEnabled(false);
            runIo(() -> {
                try {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("body", body);
                    if (!replyTarget[0].isEmpty()) payload.put("parentCommentId", replyTarget[0]);
                    Response<ApiResponse<Map<String, Object>>> response =
                        container.getUserApi().commentOnMoment(momentId, payload).execute();
                    if (!response.isSuccessful() || response.body() == null || !response.body().success) {
                        throw new IllegalStateException("comment rejected");
                    }
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        input.setText("");
                        replyTarget[0] = "";
                        replyName[0] = "";
                        replyStatus.setVisibility(View.GONE);
                        send.setEnabled(true);
                        refreshComments[0].run();
                        loadFeed();
                    });
                } catch (Exception e) {
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        send.setEnabled(true);
                        Toast.makeText(requireContext(), "تعذر إرسال التعليق؛ حاول مرة أخرى", Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        dialog.setContentView(page);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
            dialog.getWindow().setLayout(-1, -1);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        refreshComments[0].run();
    }

    private void addCommentCard(LinearLayout parent, Map<String, Object> comment,
            String[] replyTarget, String[] replyName, TextView replyStatus, EditText input) {
        String name = string(comment, "displayName");
        if (name.isEmpty()) name = string(comment, "username");
        if (name.isEmpty()) name = "مستخدم";
        final String commentId = string(comment, "id");
        final String displayName = name;
        String parentId = string(comment, "parentCommentId");
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(parentId.isEmpty() ? 12 : 10), dp(9), dp(12), dp(8));
        card.setBackground(rounded(parentId.isEmpty() ? 0xFFFFFFFF : 0xFFFFF0F5,
                parentId.isEmpty() ? 0xFFECE7ED : 0xFFFFD8E5, dp(14), dp(1)));
        LinearLayout.LayoutParams cardLp = matchWrap();
        cardLp.bottomMargin = dp(8);
        if (!parentId.isEmpty()) cardLp.leftMargin = dp(24);
        LinearLayout authorLine = new LinearLayout(requireContext());
        authorLine.setOrientation(LinearLayout.HORIZONTAL);
        authorLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView author = text(name, 13, 0xFF302B35, true);
        authorLine.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
        TextView id = text("ID " + (string(comment, "publicId").isEmpty() ? "—" : string(comment, "publicId")),
                10, 0xFF918996, false);
        authorLine.addView(id, new LinearLayout.LayoutParams(-2, -2));
        card.addView(authorLine, matchWrap());
        TextView body = text(string(comment, "body"), 14, 0xFF413B46, false);
        body.setPadding(0, dp(3), 0, dp(5));
        card.addView(body, matchWrap());
        Button reply = button("رد على التعليق");
        reply.setTextColor(0xFFB70F52);
        reply.setBackground(rounded(0xFFFFF0F5, 0xFFFFD8E5, dp(12), dp(1)));
        reply.setOnClickListener(v -> {
            replyTarget[0] = commentId;
            replyName[0] = displayName;
            replyStatus.setText("الرد على " + displayName + "  ×");
            replyStatus.setVisibility(View.VISIBLE);
            replyStatus.setOnClickListener(x -> {
                replyTarget[0] = "";
                replyName[0] = "";
                replyStatus.setVisibility(View.GONE);
            });
            input.setHint("اكتب ردك على " + displayName + "…");
            input.requestFocus();
        });
        LinearLayout replyRow = new LinearLayout(requireContext());
        replyRow.setGravity(Gravity.END);
        replyRow.addView(reply, new LinearLayout.LayoutParams(-2, dp(34)));
        card.addView(replyRow, matchWrap());
        parent.addView(card, cardLp);
    }

    private boolean containsCommentId(List<Map<String, Object>> comments, String id) {
        for (Map<String, Object> comment : comments) if (id.equals(string(comment, "id"))) return true;
        return false;
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
