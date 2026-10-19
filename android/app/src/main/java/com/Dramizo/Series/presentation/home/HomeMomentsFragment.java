package com.Dramizo.Series.presentation.home;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private Spinner visibility;
    private TextView imageStatus, loading;
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
        TextView heading = text("اللحظات", 22, 0xFF222222, true);
        root.addView(heading, matchWrap());
        TextView sub = text("شارك لحظتك مع الناس حسب الخصوصية التي تختارها", 13, 0xFF777782, false);
        root.addView(sub, matchWrap());
        LinearLayout composerCard = card();
        composer = new EditText(requireContext());
        composer.setHint("بماذا تفكر؟");
        composer.setMinLines(2);
        composer.setGravity(Gravity.TOP | Gravity.RIGHT);
        composer.setTextSize(15);
        composerCard.addView(composer, matchWrap());
        visibility = new Spinner(requireContext());
        String[] privacy = {"عام", "المتابعون", "الأصدقاء", "أنا فقط"};
        visibility.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, privacy));
        composerCard.addView(visibility, matchWrap());
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button imageButton = button("إضافة صورة");
        imageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_IMAGE);
        });
        Button publish = button("نشر اللحظة");
        publish.setOnClickListener(v -> publish());
        actions.addView(imageButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(publish, new LinearLayout.LayoutParams(0, dp(44), 1));
        composerCard.addView(actions, matchWrap());
        imageStatus = text("", 12, 0xFF777782, false);
        composerCard.addView(imageStatus, matchWrap());
        root.addView(composerCard, matchWrap());
        if (profileUserId != null && !profileUserId.trim().isEmpty()) {
            heading.setText("لحظاتي");
            sub.setText("المنشورات التي يسمح صاحب الحساب بعرضها");
            composerCard.setVisibility(View.GONE);
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
        runIo(() -> {
            try {
                String imageUrl = uploadedImageUrl;
                if (selectedImage != null && imageUrl == null) imageUrl = uploadImage(selectedImage);
                String[] values = {"public", "followers", "friends", "private"};
                Map<String, Object> payload = new HashMap<>();
                payload.put("body", body);
                payload.put("imageUrl", imageUrl);
                payload.put("visibility", values[Math.max(0, visibility.getSelectedItemPosition())]);
                Response<ApiResponse<Map<String, Object>>> response = container.getUserApi().createMoment(payload).execute();
                if (!response.isSuccessful() || response.body() == null || !response.body().success)
                    throw new IllegalStateException("publish failed");
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    composer.setText("");
                    selectedImage = null;
                    uploadedImageUrl = null;
                    imageStatus.setText("");
                    visibility.setSelection(0);
                    setComposerEnabled(true);
                    Toast.makeText(requireContext(), "تم نشر اللحظة", Toast.LENGTH_SHORT).show();
                    loadFeed();
                });
            } catch (Exception e) {
                showError("تعذر نشر اللحظة؛ تحقق من الاتصال والصورة");
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
            RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), output.toByteArray());
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", name, body);
            Response<ApiResponse<UploadApi.UploadResult>> response = container.getUploadApi().upload(part).execute();
            if (!response.isSuccessful() || response.body() == null || response.body().data == null
                    || response.body().data.url == null) throw new IllegalStateException("upload failed");
            return response.body().data.url;
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
            TextView author = text(name.isEmpty() ? "مستخدم" : name, 16, 0xFF222222, true);
            final String authorId = string(post, "userId");
            author.setOnClickListener(v -> {
                if (authorId.isEmpty()) return;
                Intent intent = new Intent(requireContext(), com.Dramizo.Series.presentation.profile.ProfileActivity.class);
                intent.putExtra(com.Dramizo.Series.presentation.profile.ProfileActivity.EXTRA_USER_ID, authorId);
                startActivity(intent);
            });
            item.addView(author, matchWrap());
            String body = string(post, "body");
            if (!body.isEmpty()) item.addView(text(body, 15, 0xFF33333A, false), matchWrap());
            String image = string(post, "imageUrl");
            if (!image.isEmpty()) {
                ImageView photo = new ImageView(requireContext());
                photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(230)));
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Glide.with(this).load(image).into(photo);
                item.addView(photo);
            }
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button like = button((Boolean.TRUE.equals(post.get("likedByMe")) ? "♥ " : "♡ ")
                    + number(post.get("likeCount")) + " إعجاب");
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
            item.addView(row, matchWrap());
            Button report = button("⚑ إبلاغ عن المنشور");
            report.setTextColor(0xFFB3261E);
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
        Button report = button("⚑ إبلاغ عن المنشور");
        report.setTextColor(0xFFB3261E);
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
        String content = body + (image.isEmpty() ? "" : "\\n" + image);
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
        if (visibility != null) visibility.setEnabled(enabled);
    }

    @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            selectedImage = data.getData(); uploadedImageUrl = null;
            imageStatus.setText("تم اختيار صورة — سيتم رفعها عند النشر");
            try { requireContext().getContentResolver().takePersistableUriPermission(selectedImage,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); }
            catch (Exception ignored) {}
        }
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
        box.setPadding(dp(12), dp(12), dp(12), dp(10)); box.setBackgroundColor(0xFFFFFFFF);
        box.setElevation(dp(1)); LinearLayout.LayoutParams lp = matchWrap(); lp.bottomMargin = dp(12);
        box.setLayoutParams(lp); return box;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(requireContext()); v.setText(value); v.setTextSize(size);
        v.setTextColor(color); if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, dp(4), 0, dp(5)); return v;
    }
    private Button button(String value) {
        Button b = new Button(requireContext()); b.setText(value); b.setAllCaps(false);
        b.setTextSize(13); return b;
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
