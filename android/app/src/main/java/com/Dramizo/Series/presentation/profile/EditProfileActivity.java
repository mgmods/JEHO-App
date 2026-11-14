package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import com.yalantis.ucrop.UCrop;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.api.UploadApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityEditProfileBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarImageLoader;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;

import java.util.Calendar;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Response;

public class EditProfileActivity extends ThemedActivity {
    private ActivityEditProfileBinding binding;
    private ProfileViewModel vm;
    private AppContainer c;
    private boolean pickingCover;
    // -1 adds a cover; a non-negative value replaces that gallery position.
    private int replacingCoverIndex = -1;
    private final ArrayList<String> profileCoverUrls = new ArrayList<>();
    private String lastGender = "";
    private String originalCountryCode = "";
    private boolean countryLocked;
    private long countryUnlockAtMs;
    private boolean suppressCountrySelect;
    private boolean countryChangeConfirmed;

    private static final String[] GENDER_LABELS = {"اختر الجنس", "ذكر", "أنثى"};
    private static final String[] GENDER_VALUES = {"", "male", "female"};

    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onImagePicked);
    private final ActivityResultLauncher<String> pickCoverGallery = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), this::onCoverImagesPicked);

    private final ActivityResultLauncher<Intent> cropImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    replacingCoverIndex = -1;
                    if (result.getResultCode() == UCrop.RESULT_ERROR && result.getData() != null) {
                        Throwable error = UCrop.getError(result.getData());
                        Toast.makeText(this, error != null && error.getMessage() != null
                                ? error.getMessage() : "تعذر قص الصورة، حاول بصورة أخرى",
                                Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                Uri cropped = UCrop.getOutput(result.getData());
                if (cropped != null) uploadPickedImage(cropped);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Explicit system padding on contentRoot (layout id) — avoids toolbar-only pad
        // which caused overlapping status/fields on edge-to-edge Android 14+.
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBarsWithIme(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        c = ContainerProvider.from(this);
        vm = new ViewModelProvider(this, new ViewModelFactory(c)).get(ProfileViewModel.class);

        binding.spinnerGender.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, GENDER_LABELS));
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, CountryCatalog.spinnerLabels()) {
            private android.view.View bindCountryFlag(android.view.View row, int position) {
                if (row instanceof android.widget.TextView && position == 1) {
                    android.widget.TextView text = (android.widget.TextView) row;
                    text.setText("سوريا");
                    text.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.ic_flag_syria, 0, 0, 0);
                    text.setCompoundDrawablePadding(Math.round(6 * getResources().getDisplayMetrics().density));
                } else if (row instanceof android.widget.TextView) {
                    android.widget.TextView text = (android.widget.TextView) row;
                    text.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                }
                return row;
            }
            @Override public android.view.View getView(int position, android.view.View convertView,
                    android.view.ViewGroup parent) {
                return bindCountryFlag(super.getView(position, convertView, parent), position);
            }
            @Override public android.view.View getDropDownView(int position, android.view.View convertView,
                    android.view.ViewGroup parent) {
                return bindCountryFlag(super.getDropDownView(position, convertView, parent), position);
            }
        };
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCountry.setAdapter(countryAdapter);

        binding.etBirthday.setOnClickListener(v -> showBirthdayPicker());
        wireCountrySpinner();

        vm.getUser().observe(this, user -> {
            if (user == null) return;
            binding.etDisplayName.setText(user.displayName);
            binding.etBio.setText(user.bio);
            binding.etAvatar.setText(user.avatarUrl != null ? user.avatarUrl : "");
            binding.etCover.setText(user.coverUrl != null ? user.coverUrl : "");
            profileCoverUrls.clear();
            if (user.profileCoverUrls != null) profileCoverUrls.addAll(user.profileCoverUrls);
            else if (user.coverUrl != null && !user.coverUrl.trim().isEmpty()) profileCoverUrls.add(user.coverUrl);
            updateCoverGalleryCount();
            refreshCoverGalleryThumbnails();
            binding.etBirthday.setText(normalizeBirthday(user.birthday));
            selectGender(user.gender);
            lastGender = user.gender != null ? user.gender : "";
            originalCountryCode = user.country != null ? user.country.trim() : "";
            countryChangeConfirmed = false;
            suppressCountrySelect = true;
            selectCountry(user.country);
            suppressCountrySelect = false;
            boolean hasGender = user.gender != null && !user.gender.trim().isEmpty()
                    && !"unspecified".equalsIgnoreCase(user.gender);
            binding.spinnerGender.setEnabled(!hasGender);
            binding.spinnerGender.setAlpha(hasGender ? 0.55f : 1f);
            applyCountryLockState(user);
            binding.tvUserIdReadonly.setText("المعرّف: " + (
                    user.displayPublicId().isEmpty()
                            ? (user.username != null ? user.username : user.id)
                            : user.displayPublicId()));
            AvatarImageLoader.load(binding.imgAvatarPreview, user.avatarUrl);
            AvatarImageLoader.loadMedia(binding.imgCoverPreview, user.coverUrl,
                    ImagePlaceholder.cover());
        });

        binding.btnPickAvatar.setOnClickListener(v -> {
            pickingCover = false;
            pickImage.launch("image/*");
        });
        binding.imgAvatarPreview.setOnClickListener(v -> binding.btnPickAvatar.performClick());
        if (binding.rowAvatar != null) {
            binding.rowAvatar.setOnClickListener(v -> binding.btnPickAvatar.performClick());
        }
        binding.btnPickCoverGallery.setOnClickListener(v -> {
            int remaining = 5 - profileCoverUrls.size();
            if (remaining <= 0) {
                Toast.makeText(this, "وصلت للحد الأقصى: 5 صور. احذف الصور القديمة أولاً.", Toast.LENGTH_LONG).show();
                return;
            }
            pickCoverGallery.launch("image/*");
        });
        binding.btnPickCover.setOnClickListener(v -> {
            pickingCover = true;
            pickImage.launch("image/*");
        });
        binding.imgCoverPreview.setOnClickListener(v -> binding.btnPickCover.performClick());
        if (binding.rowCover != null) {
            binding.rowCover.setOnClickListener(v -> binding.btnPickCover.performClick());
        }
        binding.btnSave.setOnClickListener(v -> save());

        vm.getSaved().observe(this, saved -> {
            binding.progress.setVisibility(View.GONE);
            if (Boolean.TRUE.equals(saved)) {
                AuthDtos.UserDto user = vm.getUser().getValue();
                String newGender = selectedGenderValue();
                boolean becameFemale = "female".equalsIgnoreCase(newGender)
                        && !"female".equalsIgnoreCase(lastGender);
                if (c.getSessionManager().isFemaleOnlyVoiceHostsFromServer()
                        && (becameFemale || ("female".equalsIgnoreCase(newGender) && user != null && !user.genderVerified))) {
                    Toast.makeText(this, "أكمل التحقق من الهوية كمضيفة", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, FemaleIdentityVerificationActivity.class));
                    finish();
                    return;
                }
                Toast.makeText(this, "تم الحفظ بنجاح", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        vm.getError().observe(this, e -> {
            binding.progress.setVisibility(View.GONE);
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_LONG).show();
        });
        vm.loadMe();
    }

    @Override
    protected boolean wantsContentSystemPadding() {
        // Handled manually in onCreate with padSystemBarsWithIme on contentRoot.
        return false;
    }

    private void wireCountrySpinner() {
        binding.spinnerCountry.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;
            if (countryLocked) {
                showCountryLockedDialog();
                return true;
            }
            return false;
        });
        binding.spinnerCountry.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressCountrySelect || countryLocked) return;
                String nextCode = CountryCatalog.codeAtSpinnerIndex(position);
                String prev = originalCountryCode != null ? originalCountryCode : "";
                if (nextCode == null) nextCode = "";
                final String next = nextCode;
                if (next.equalsIgnoreCase(prev) || prev.isEmpty()) {
                    countryChangeConfirmed = prev.isEmpty() && !next.isEmpty();
                    return;
                }
                if (countryChangeConfirmed) return;
                // Revert until user confirms Mikoo warning about clearing rank data.
                suppressCountrySelect = true;
                selectCountry(originalCountryCode);
                suppressCountrySelect = false;
                AuraDialogHelper.confirm(
                        EditProfileActivity.this,
                        getString(R.string.country_change_title),
                        getString(R.string.change_country_data_will_clear)
                                + "\n\n"
                                + getString(R.string.month_change_country_limit),
                        getString(android.R.string.ok),
                        () -> {
                            countryChangeConfirmed = true;
                            suppressCountrySelect = true;
                            selectCountry(next);
                            suppressCountrySelect = false;
                            if (binding.tvCountryHint != null) {
                                binding.tvCountryHint.setText(
                                        "سيتم مسح بيانات الترتيب عند الحفظ · مرة كل 30 يوماً");
                            }
                        },
                        getString(android.R.string.cancel),
                        null);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyCountryLockState(AuthDtos.UserDto user) {
        countryLocked = false;
        countryUnlockAtMs = 0L;
        String countryHint = getString(R.string.month_change_country_limit);
        if (user.countryChangeAvailableAt != null && !user.countryChangeAvailableAt.isEmpty()) {
            try {
                long next = java.time.Instant.parse(user.countryChangeAvailableAt).toEpochMilli();
                countryUnlockAtMs = next;
                if (next > System.currentTimeMillis()) {
                    countryLocked = true;
                    long days = Math.max(1, (next - System.currentTimeMillis() + 86399999L)
                            / (24L * 60L * 60L * 1000L));
                    countryHint = getString(R.string.month_change_country_limit)
                            + "\nمتبقي " + days + " يوم";
                } else {
                    countryHint = "يمكنك تغيير الدولة الآن · "
                            + getString(R.string.month_change_country_limit);
                }
            } catch (Exception ignored) {}
        } else if (user.country == null || user.country.trim().isEmpty()) {
            countryHint = "اختر دولتك (تُحدَّد حسب موقعك عند التسجيل)";
        } else {
            // Has country but no cooldown stamp yet — still show Mikoo rule.
            countryHint = getString(R.string.month_change_country_limit);
        }
        // Keep enabled so tap shows Mikoo dialog when locked; dim when locked.
        binding.spinnerCountry.setEnabled(true);
        binding.spinnerCountry.setClickable(true);
        binding.spinnerCountry.setAlpha(countryLocked ? 0.55f : 1f);
        if (binding.tvCountryHint != null) {
            binding.tvCountryHint.setText(countryHint);
            binding.tvCountryHint.setVisibility(View.VISIBLE);
            binding.tvCountryHint.setOnClickListener(v -> {
                if (countryLocked) showCountryLockedDialog();
            });
        }
    }

    private void showCountryLockedDialog() {
        long days = 1;
        if (countryUnlockAtMs > System.currentTimeMillis()) {
            days = Math.max(1, (countryUnlockAtMs - System.currentTimeMillis() + 86399999L)
                    / (24L * 60L * 60L * 1000L));
        }
        AuraDialogHelper.message(
                this,
                getString(R.string.country_change_title),
                getString(R.string.month_change_country_limit)
                        + "\n\nمتبقي " + days + " يوم");
    }

    private void save() {
        String name = text(binding.etDisplayName);
        if (name.isEmpty()) {
            Toast.makeText(this, "أدخل الاسم الظاهر", Toast.LENGTH_SHORT).show();
            return;
        }
        if (com.Dramizo.Series.util.ChatContentFilter.containsAgencyImpersonation(name)
                || com.Dramizo.Series.util.ChatContentFilter.containsAgencyImpersonation(
                        text(binding.etBio))) {
            Toast.makeText(this,
                    com.Dramizo.Series.util.ChatContentFilter.AGENCY_WORD_REASON,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (countryLocked) {
            String next = selectedCountryValue();
            String prev = originalCountryCode != null ? originalCountryCode : "";
            if (next != null && !next.equalsIgnoreCase(prev)) {
                showCountryLockedDialog();
                return;
            }
        }
        binding.progress.setVisibility(View.VISIBLE);

        MiscDtos.UpdateProfileRequest req = new MiscDtos.UpdateProfileRequest();
        req.displayName = name;
        req.bio = emptyToNull(text(binding.etBio));
        req.avatarUrl = emptyToNull(text(binding.etAvatar));
        req.coverUrl = emptyToNull(text(binding.etCover));
        req.profileCoverUrls = new ArrayList<>(profileCoverUrls);
        if (binding.spinnerGender.isEnabled()) {
            req.gender = emptyToNull(selectedGenderValue());
        }
        req.birthday = emptyToNull(normalizeBirthday(text(binding.etBirthday)));
        if (!countryLocked) {
            String next = emptyToNull(selectedCountryValue());
            String prev = originalCountryCode != null ? originalCountryCode.trim() : "";
            if (next != null && !next.equalsIgnoreCase(prev)) {
                if (!prev.isEmpty() && !countryChangeConfirmed) {
                    binding.progress.setVisibility(View.GONE);
                    AuraDialogHelper.confirm(
                            this,
                            getString(R.string.country_change_title),
                            getString(R.string.change_country_data_will_clear)
                                    + "\n\n"
                                    + getString(R.string.month_change_country_limit),
                            getString(android.R.string.ok),
                            () -> {
                                countryChangeConfirmed = true;
                                save();
                            },
                            getString(android.R.string.cancel),
                            null);
                    return;
                }
                req.country = next;
            } else if (prev.isEmpty() && next != null) {
                req.country = next;
            }
        }
        vm.updateRequest(req);
    }

    private void showBirthdayPicker() {
        Calendar cal = Calendar.getInstance();
        String current = text(binding.etBirthday);
        if (current.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                String[] p = current.split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) {
            }
        } else {
            cal.set(2000, Calendar.JANUARY, 1);
        }
        DatePickerDialog dlg = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> binding.etBirthday.setText(
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)),
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
        dlg.show();
    }

    private void selectGender(String gender) {
        String g = gender != null ? gender.trim().toLowerCase(Locale.US) : "";
        if ("ذكر".equals(gender) || "m".equals(g)) g = "male";
        if ("أنثى".equals(gender) || "انثى".equals(gender) || "f".equals(g)) g = "female";
        int idx = 0;
        for (int i = 0; i < GENDER_VALUES.length; i++) {
            if (GENDER_VALUES[i].equals(g)) {
                idx = i;
                break;
            }
        }
        binding.spinnerGender.setSelection(idx);
    }

    private String selectedGenderValue() {
        int i = binding.spinnerGender.getSelectedItemPosition();
        if (i < 0 || i >= GENDER_VALUES.length) return "";
        return GENDER_VALUES[i];
    }

    private void selectCountry(String country) {
        binding.spinnerCountry.setSelection(CountryCatalog.spinnerIndexFor(country));
    }

    private String selectedCountryValue() {
        return CountryCatalog.codeAtSpinnerIndex(binding.spinnerCountry.getSelectedItemPosition());
    }

    private void updateCoverGalleryCount() {
        if (binding != null && binding.tvCoverGalleryCount != null) {
            binding.tvCoverGalleryCount.setText(profileCoverUrls.size() + " من 5 صور");
        }
    }

    private void refreshCoverGalleryThumbnails() {
        if (binding == null || binding.coverGalleryItems == null) return;
        binding.coverGalleryItems.removeAllViews();
        final float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 5; i++) {
            final int index = i;
            final boolean hasImage = i < profileCoverUrls.size();
            final String url = hasImage ? profileCoverUrls.get(i) : null;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            tile.setPadding((int)(6*density), (int)(6*density), (int)(6*density), (int)(6*density));
            android.graphics.drawable.GradientDrawable tileBg = new android.graphics.drawable.GradientDrawable();
            tileBg.setColor(0xFFF8F8FC);
            tileBg.setCornerRadius(12*density);
            tileBg.setStroke((int)Math.max(1, density), 0xFFE4E5ED);
            tile.setBackground(tileBg);
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams((int)(132*density), ViewGroup.LayoutParams.WRAP_CONTENT);
            tileLp.setMargins(0, 0, (int)(8*density), 0);
            tile.setLayoutParams(tileLp);
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackgroundColor(0xFFE9EAF0);
            preview.setContentDescription(hasImage ? "معاينة صورة الغلاف " + (i+1) : "إضافة صورة غلاف");
            tile.addView(preview, new LinearLayout.LayoutParams((int)(118*density), (int)(76*density)));
            if (hasImage) {
                Glide.with(this).load(url).centerCrop().placeholder(ImagePlaceholder.cover())
                        .error(ImagePlaceholder.cover()).into(preview);
                preview.setOnClickListener(v -> showCoverPreview(url));
                TextView label = new TextView(this);
                label.setText("صورة " + (i+1));
                label.setTextColor(0xFF555B70);
                label.setTextSize(11f);
                label.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
                labelLp.topMargin = (int)(4*density);
                tile.addView(label, labelLp);
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
                actionsLp.topMargin = (int)(3*density);
                tile.addView(actions, actionsLp);
                com.google.android.material.button.MaterialButton replace = new com.google.android.material.button.MaterialButton(this);
                replace.setText("تبديل"); replace.setTextSize(10f); replace.setMinWidth(0); replace.setMinimumWidth(0);
                replace.setInsetTop(0); replace.setInsetBottom(0);
                replace.setPadding((int)(6*density), 0, (int)(6*density), 0);
                LinearLayout.LayoutParams replaceLp = new LinearLayout.LayoutParams(0, (int)(34*density), 1f);
                replaceLp.setMarginEnd((int)(3*density));
                actions.addView(replace, replaceLp);
                replace.setOnClickListener(v -> startCoverPicker(index));
                com.google.android.material.button.MaterialButton remove = new com.google.android.material.button.MaterialButton(this);
                remove.setText("حذف"); remove.setTextSize(10f); remove.setMinWidth(0); remove.setMinimumWidth(0);
                remove.setInsetTop(0); remove.setInsetBottom(0);
                remove.setPadding((int)(6*density), 0, (int)(6*density), 0);
                remove.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFE8E8));
                remove.setTextColor(0xFFC62828);
                actions.addView(remove, new LinearLayout.LayoutParams(0, (int)(34*density), 1f));
                remove.setOnClickListener(v -> confirmRemoveCover(index));
            } else {
                preview.setImageResource(ImagePlaceholder.cover());
                preview.setOnClickListener(v -> startCoverPicker(index));
                TextView add = new TextView(this);
                add.setText("+ إضافة صورة"); add.setTextColor(0xFF6850A8); add.setTextSize(12f);
                add.setTypeface(add.getTypeface(), android.graphics.Typeface.BOLD);
                add.setGravity(android.view.Gravity.CENTER);
                tile.addView(add, new LinearLayout.LayoutParams(-1, (int)(38*density)));
                add.setOnClickListener(v -> startCoverPicker(index));
            }
            binding.coverGalleryItems.addView(tile);
        }
    }

    private void startCoverPicker(int replaceIndex) {
        if (profileCoverUrls.size() >= 5 && replaceIndex < 0) {
            Toast.makeText(this, "وصلت للحد الأقصى: 5 صور", Toast.LENGTH_LONG).show();
            return;
        }
        pickingCover = true;
        replacingCoverIndex = replaceIndex;
        pickImage.launch("image/*");
    }

    private void showCoverPreview(String url) {
        if (url == null || url.trim().isEmpty()) return;
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = (int)(12 * getResources().getDisplayMetrics().density);
        image.setPadding(pad, pad, pad, pad);
        Glide.with(this).load(url).fitCenter().into(image);
        new AlertDialog.Builder(this).setView(image).setPositiveButton("إغلاق", null).show();
    }

        private void confirmRemoveCover(int index) {
        if (index < 0 || index >= profileCoverUrls.size()) return;
        new AlertDialog.Builder(this).setTitle("حذف صورة الغلاف")
                .setMessage("هل تريد حذف هذه الصورة من صور الغلاف؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (dialog, which) -> removeCoverAt(index)).show();
    }

    private void removeCoverAt(int index) {
        if (index < 0 || index >= profileCoverUrls.size() || binding == null) return;
        ArrayList<String> updated = new ArrayList<>(profileCoverUrls); updated.remove(index);
        MiscDtos.UpdateProfileRequest patch = new MiscDtos.UpdateProfileRequest();
        patch.profileCoverUrls = updated; patch.coverUrl = updated.isEmpty() ? "" : updated.get(0);
        binding.progress.setVisibility(View.VISIBLE);
        c.getIoExecutor().execute(() -> {
            try {
                Response<ApiResponse<AuthDtos.UserDto>> response = c.getUserApi().updateProfile(patch).execute();
                if (!response.isSuccessful() || response.body() == null || !response.body().success)
                    throw new IllegalStateException(UploadApi.errorMessage(response, "تعذر حذف صورة الغلاف"));
                if (response.body().data != null) c.getSessionManager().updateCachedUser(response.body().data);
                runOnUiThread(() -> {
                    if (binding == null || isFinishing()) return;
                    profileCoverUrls.clear(); profileCoverUrls.addAll(updated);
                    binding.etCover.setText(updated.isEmpty() ? "" : updated.get(0));
                    updateCoverGalleryCount(); refreshCoverGalleryThumbnails();
                    AvatarImageLoader.loadMedia(binding.imgCoverPreview, updated.isEmpty() ? null : updated.get(0), ImagePlaceholder.cover());
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, "تم حذف صورة الغلاف", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (binding != null) { binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, error.getMessage() == null ? "تعذر حذف الصورة" : error.getMessage(), Toast.LENGTH_LONG).show(); } });
            }
        });
    }

    private void onCoverImagesPicked(List<Uri> selected) {
        if (selected == null || selected.isEmpty()) return;
        int remaining = 5 - profileCoverUrls.size();
        if (selected.size() > remaining) {
            Toast.makeText(this, "يمكنك إضافة " + remaining + " صورة فقط", Toast.LENGTH_LONG).show();
            return;
        }
        binding.progress.setVisibility(View.VISIBLE);
        c.getIoExecutor().execute(() -> {
            ArrayList<String> uploaded = new ArrayList<>(profileCoverUrls);
            try {
                for (Uri uri : selected) {
                    // Preserve original animated GIF/WebP bytes in the gallery upload.
                    MultipartBody.Part part = AvatarImageLoader.multipartFromUri(getContentResolver(), uri, "cover");
                    Response<ApiResponse<UploadApi.UploadResult>> response = c.getUploadApi().upload(part).execute();
                    if (!response.isSuccessful() || response.body() == null || !response.body().success
                            || response.body().data == null || response.body().data.url == null) {
                        throw new IllegalStateException(UploadApi.errorMessage(response, "فشل رفع إحدى صور الغلاف"));
                    }
                    uploaded.add(AssetCatalog.absoluteUrl(response.body().data.url));
                }
                MiscDtos.UpdateProfileRequest patch = new MiscDtos.UpdateProfileRequest();
                patch.profileCoverUrls = uploaded;
                if (!uploaded.isEmpty()) patch.coverUrl = uploaded.get(0);
                Response<ApiResponse<AuthDtos.UserDto>> saved = c.getUserApi().updateProfile(patch).execute();
                if (!saved.isSuccessful() || saved.body() == null || !saved.body().success) {
                    throw new IllegalStateException(UploadApi.errorMessage(saved, "تم الرفع لكن تعذر حفظ صور الغلاف"));
                }
                if (saved.body().data != null) c.getSessionManager().updateCachedUser(saved.body().data);
                runOnUiThread(() -> {
                    if (isFinishing() || binding == null) return;
                    profileCoverUrls.clear();
                    profileCoverUrls.addAll(uploaded);
                    updateCoverGalleryCount();
                    refreshCoverGalleryThumbnails();
                    if (!uploaded.isEmpty()) {
                        binding.etCover.setText(uploaded.get(0));
                        AvatarImageLoader.loadMedia(binding.imgCoverPreview, uploaded.get(0), ImagePlaceholder.cover());
                    }
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, "تم رفع وحفظ صور الغلاف بنجاح", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, error.getMessage() == null ? "تعذر رفع صور الغلاف" : error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onImagePicked(Uri uri) {
        if (uri == null || binding == null) { replacingCoverIndex = -1; return; }
        final boolean cover = pickingCover;
        binding.progress.setVisibility(View.VISIBLE);
        c.getIoExecutor().execute(() -> {
            boolean animated = isAnimatedImage(uri);
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                binding.progress.setVisibility(View.GONE);
                if (animated) {
                    // Do not pass animated GIF/WebP through a bitmap cropper: that would flatten it.
                    Toast.makeText(this, "تم الحفاظ على حركة الصورة. القص متاح للصور الثابتة",
                            Toast.LENGTH_LONG).show();
                    uploadPickedImage(uri);
                } else {
                    try {
                        File destination = new File(getCacheDir(),
                                (cover ? "profile_cover_" : "profile_avatar_")
                                + System.currentTimeMillis() + ".jpg");
                        UCrop.Options options = new UCrop.Options();
                        options.setToolbarTitle(cover ? "قص غلاف الملف الشخصي" : "قص الصورة الشخصية");
                        options.setToolbarColor(Color.rgb(38, 25, 66));
                        options.setStatusBarColor(Color.rgb(28, 18, 49));
                        options.setActiveControlsWidgetColor(Color.rgb(151, 94, 235));
                        options.setToolbarWidgetColor(Color.WHITE);
                        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
                        options.setCompressionQuality(92);
                        options.setFreeStyleCropEnabled(false);
                        options.setHideBottomControls(false);
                        UCrop crop = UCrop.of(uri, Uri.fromFile(destination))
                                .withAspectRatio(cover ? 16f : 1f, cover ? 9f : 1f)
                                .withMaxResultSize(2048, cover ? 1152 : 2048)
                                .withOptions(options);
                        cropImage.launch(crop.getIntent(this));
                    } catch (Exception ex) {
                        Toast.makeText(this, "تعذر فتح أداة القص: "
                                + (ex.getMessage() == null ? "خطأ غير معروف" : ex.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
    }

    /**
     * Detect GIF by MIME/signature and animated WebP by its RIFF animation chunks.
     * This avoids flattening animation by routing animated files through UCrop.
     */
    private boolean isAnimatedImage(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if (mime != null && mime.toLowerCase(Locale.ROOT).contains("gif")) return true;
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return false;
            byte[] buffer = new byte[4096];
            int count, total = 0;
            while ((count = in.read(buffer)) != -1 && total < 8 * 1024 * 1024) {
                out.write(buffer, 0, count);
                total += count;
            }
            byte[] data = out.toByteArray();
            if (data.length >= 6 && data[0] == 'G' && data[1] == 'I'
                    && data[2] == 'F' && data[3] == '8') return true;
            if (data.length >= 12 && data[0] == 'R' && data[1] == 'I'
                    && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E'
                    && data[10] == 'B' && data[11] == 'P') {
                for (int i = 12; i + 4 <= data.length; i++) {
                    if ((data[i] == 'A' && data[i+1] == 'N'
                            && data[i+2] == 'I' && data[i+3] == 'M')
                            || (data[i] == 'A' && data[i+1] == 'N'
                            && data[i+2] == 'M' && data[i+3] == 'F')) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void uploadPickedImage(Uri uri) {
        if (uri == null || binding == null) return;
        binding.progress.setVisibility(View.VISIBLE);
        final boolean cover = pickingCover;
        final int requestedReplaceIndex = replacingCoverIndex;
        final ArrayList<String> previousCoverUrls = new ArrayList<>(profileCoverUrls);
        if (cover) {
            AvatarImageLoader.loadMedia(binding.imgCoverPreview, uri, ImagePlaceholder.cover());
        } else {
            AvatarImageLoader.load(binding.imgAvatarPreview, uri);
        }
        c.getIoExecutor().execute(() -> {
            try {
                MultipartBody.Part part = AvatarImageLoader.multipartFromUri(
                        getContentResolver(), uri, cover ? "cover" : "avatar");
                Response<ApiResponse<UploadApi.UploadResult>> resp = c.getUploadApi().upload(part).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success
                        && resp.body().data != null && resp.body().data.url != null
                        && !resp.body().data.url.trim().isEmpty()) {
                    String url = AssetCatalog.absoluteUrl(resp.body().data.url);
                    // Uploading the file alone is not enough: persist the selected image URL
                    // before telling the user it is applied. Send only the changed image field
                    // so unsaved name/bio edits in this form are not overwritten.
                    MiscDtos.UpdateProfileRequest imagePatch = new MiscDtos.UpdateProfileRequest();
                    if (cover) {
                        ArrayList<String> updatedCovers = new ArrayList<>(profileCoverUrls);
                        if (requestedReplaceIndex >= 0 && requestedReplaceIndex < updatedCovers.size()) {
                            updatedCovers.set(requestedReplaceIndex, url);
                        } else {
                            updatedCovers.remove(url);
                            updatedCovers.add(0, url);
                        }
                        while (updatedCovers.size() > 5) updatedCovers.remove(updatedCovers.size() - 1);
                        imagePatch.coverUrl = updatedCovers.isEmpty() ? url : updatedCovers.get(0);
                        imagePatch.profileCoverUrls = updatedCovers;
                    } else imagePatch.avatarUrl = url;
                    Response<ApiResponse<AuthDtos.UserDto>> saved =
                            c.getUserApi().updateProfile(imagePatch).execute();
                    if (!saved.isSuccessful() || saved.body() == null || !saved.body().success) {
                        throw new IllegalStateException(
                                UploadApi.errorMessage(saved, "تم رفع الصورة لكن تعذر تطبيقها على الملف الشخصي"));
                    }
                    // Keep the local account snapshot in sync immediately. Otherwise
                    // returning to Profile can briefly restore the old cover/avatar.
                    if (saved.body().data != null) {
                        c.getSessionManager().updateCachedUser(saved.body().data);
                    }
                    runOnUiThread(() -> {
                        if (isFinishing() || binding == null) return;
                        binding.progress.setVisibility(View.GONE);
                        if (cover) {
                            binding.etCover.setText(url);
                            profileCoverUrls.clear();
                            if (saved.body().data != null && saved.body().data.profileCoverUrls != null
                                    && !saved.body().data.profileCoverUrls.isEmpty()) {
                                profileCoverUrls.addAll(saved.body().data.profileCoverUrls);
                            } else {
                                profileCoverUrls.addAll(previousCoverUrls);
                                if (requestedReplaceIndex >= 0 && requestedReplaceIndex < profileCoverUrls.size())
                                    profileCoverUrls.set(requestedReplaceIndex, url);
                                else { profileCoverUrls.remove(url); profileCoverUrls.add(0, url); }
                            }
                            if (profileCoverUrls.isEmpty()) profileCoverUrls.add(url);
                            replacingCoverIndex = -1;
                            updateCoverGalleryCount(); refreshCoverGalleryThumbnails();
                            String activeCover = profileCoverUrls.get(0);
                            binding.etCover.setText(activeCover);
                            AvatarImageLoader.loadMedia(binding.imgCoverPreview, activeCover, ImagePlaceholder.cover());
                        } else {
                            binding.etAvatar.setText(url);
                            AvatarImageLoader.load(binding.imgAvatarPreview, url);
                        }
                        Toast.makeText(EditProfileActivity.this, "تم رفع الصورة وتطبيقها بنجاح",
                                Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progress.setVisibility(View.GONE);
                        Toast.makeText(this, UploadApi.errorMessage(resp, "فشل رفع الصورة"), Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage() != null ? e.getMessage() : "خطأ الرفع", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static String normalizeBirthday(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String s = raw.trim();
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            return s.substring(0, 10);
        }
        return s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static String text(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
