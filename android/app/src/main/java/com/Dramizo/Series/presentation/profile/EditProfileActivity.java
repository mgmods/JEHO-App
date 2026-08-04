package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
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

import okhttp3.MultipartBody;
import retrofit2.Response;

public class EditProfileActivity extends ThemedActivity {
    private ActivityEditProfileBinding binding;
    private ProfileViewModel vm;
    private AppContainer c;
    private boolean pickingCover;
    private String lastGender = "";
    private String originalCountryCode = "";
    private boolean countryLocked;
    private long countryUnlockAtMs;
    private boolean suppressCountrySelect;
    private boolean countryChangeConfirmed;

    private static final String[] GENDER_LABELS = {"اختر الجنس", "ذكر", "أنثى"};
    private static final String[] GENDER_VALUES = {"", "male", "female"};

    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onImagePicked
    );

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
        binding.spinnerCountry.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, CountryCatalog.spinnerLabels()));

        binding.etBirthday.setOnClickListener(v -> showBirthdayPicker());
        wireCountrySpinner();

        vm.getUser().observe(this, user -> {
            if (user == null) return;
            binding.etDisplayName.setText(user.displayName);
            binding.etBio.setText(user.bio);
            binding.etAvatar.setText(user.avatarUrl != null ? user.avatarUrl : "");
            binding.etCover.setText(user.coverUrl != null ? user.coverUrl : "");
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
            Glide.with(this).load(AssetCatalog.absoluteUrl(user.coverUrl))
                    .placeholder(ImagePlaceholder.cover()).into(binding.imgCoverPreview);
        });

        binding.btnPickAvatar.setOnClickListener(v -> {
            pickingCover = false;
            pickImage.launch("image/*");
        });
        binding.imgAvatarPreview.setOnClickListener(v -> binding.btnPickAvatar.performClick());
        if (binding.rowAvatar != null) {
            binding.rowAvatar.setOnClickListener(v -> binding.btnPickAvatar.performClick());
        }
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

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        binding.progress.setVisibility(View.VISIBLE);
        boolean cover = pickingCover;
        if (cover) {
            Glide.with(this).load(uri).centerCrop().into(binding.imgCoverPreview);
        } else {
            AvatarImageLoader.load(binding.imgAvatarPreview, uri);
        }
        c.getIoExecutor().execute(() -> {
            try {
                MultipartBody.Part part = AvatarImageLoader.multipartFromUri(
                        getContentResolver(), uri, cover ? "cover" : "avatar");
                Response<ApiResponse<UploadApi.UploadResult>> resp = c.getUploadApi().upload(part).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success && resp.body().data != null) {
                    String url = AssetCatalog.absoluteUrl(resp.body().data.url);
                    runOnUiThread(() -> {
                        binding.progress.setVisibility(View.GONE);
                        if (cover) binding.etCover.setText(url);
                        else binding.etAvatar.setText(url);
                        Toast.makeText(this, "تم رفع الصورة — اضغط حفظ", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.progress.setVisibility(View.GONE);
                        Toast.makeText(this, "فشل رفع الصورة", Toast.LENGTH_LONG).show();
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
