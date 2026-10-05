package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.api.UploadApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityProfileSetupBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AvatarImageLoader;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.GenderVerificationGate;
import com.Dramizo.Series.util.ImagePlaceholder;

import java.util.Calendar;
import java.util.Locale;

import okhttp3.MultipartBody;
import retrofit2.Response;

/** Required first-login profile setup before entering the main app. */
public class ProfileSetupActivity extends ThemedActivity {
    private static final String[] GENDER_LABELS = {"اختر الجنس", "ذكر", "أنثى"};
    private static final String[] GENDER_VALUES = {"", "male", "female"};

    private ActivityProfileSetupBinding binding;
    private AppContainer container;
    private ProfileViewModel viewModel;
    private String avatarUrl;
    private String countryCode;
    private boolean initialHandled;
    private boolean saving;

    private final ActivityResultLauncher<String> pickAvatar = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::uploadAvatar);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        container = ContainerProvider.from(this);
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(ProfileViewModel.class);

        binding.spinnerGender.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, GENDER_LABELS));
        binding.spinnerCountry.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, CountryCatalog.spinnerLabels()));
        setDetectedCountry(null);
        // Default birthday: 2000-01-01 (same picker UX as edit profile).
        binding.etBirthday.setText("2000-01-01");
        binding.etBirthday.setOnClickListener(v -> showBirthdayPicker());

        binding.btnPickAvatar.setOnClickListener(v -> pickAvatar.launch("image/*"));
        binding.btnContinue.setOnClickListener(v -> saveProfile());
        if (binding.tvCountryHint != null) {
            binding.tvCountryHint.setText(R.string.month_change_country_limit);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                Toast.makeText(ProfileSetupActivity.this,
                        "أكمل الملف الشخصي أولاً", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getUser().observe(this, user -> {
            if (user == null) {
                binding.progress.setVisibility(View.GONE);
                Toast.makeText(this, "تعذر تحميل بيانات الحساب، حاول مجدداً",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (saving) return;
            if (!initialHandled) {
                initialHandled = true;
                if (isProfileComplete(user)) {
                    openMain();
                    return;
                }
                bindExisting(user);
                binding.getRoot().animate().alpha(1f).setDuration(180).start();
            }
        });
        viewModel.getSaved().observe(this, saved -> {
            if (Boolean.TRUE.equals(saved)) {
                saving = false;
                binding.progress.setVisibility(View.GONE);
                AuthDtos.UserDto user = viewModel.getUser().getValue();
                AppContainer c = ContainerProvider.from(this);
                if (GenderVerificationGate.needsVerification(user, c.getSessionManager())) {
                    startActivity(GenderVerificationGate.blockingIntent(this));
                    finish();
                    return;
                }
                openMain();
            }
        });
        viewModel.getError().observe(this, error -> {
            saving = false;
            binding.progress.setVisibility(View.GONE);
            binding.btnContinue.setEnabled(true);
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });

        binding.progress.setVisibility(View.VISIBLE);
        // Hide form until we know setup is actually required (avoids flash after splash).
        binding.getRoot().setAlpha(0f);
        viewModel.loadMe();
    }

    public static boolean isProfileComplete(AuthDtos.UserDto user) {
        if (user == null || user.isGuest) return true;
        // Profile photos are optional. Do not force a personal photo just to
        // finish onboarding or to open a voice room; the backend supplies a
        // neutral/default avatar when avatarUrl is empty.
        return nonEmpty(user.country)
                && nonEmpty(user.gender)
                && !"unspecified".equalsIgnoreCase(user.gender);
    }

    private void bindExisting(AuthDtos.UserDto user) {
        binding.progress.setVisibility(View.GONE);
        avatarUrl = user.avatarUrl;
        if (nonEmpty(user.avatarUrl)) {
            AvatarImageLoader.load(binding.imgAvatar, user.avatarUrl);
        }
        binding.etBio.setText(user.bio != null ? user.bio : "");
        String birthday = normalizeBirthday(user.birthday);
        binding.etBirthday.setText(birthday.isEmpty() ? "2000-01-01" : birthday);
        if ("male".equalsIgnoreCase(user.gender)) binding.spinnerGender.setSelection(1);
        else if ("female".equalsIgnoreCase(user.gender)) binding.spinnerGender.setSelection(2);
        setDetectedCountry(user.country);
    }

    private void showBirthdayPicker() {
        Calendar cal = Calendar.getInstance();
        String current = text(binding.etBirthday);
        if (current.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                String[] p = current.split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            } catch (Exception ignored) {
                cal.set(2000, Calendar.JANUARY, 1);
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
        Calendar max = Calendar.getInstance();
        max.add(Calendar.YEAR, -13);
        dlg.getDatePicker().setMaxDate(max.getTimeInMillis());
        Calendar min = Calendar.getInstance();
        min.add(Calendar.YEAR, -100);
        dlg.getDatePicker().setMinDate(min.getTimeInMillis());
        dlg.show();
    }

    private static String normalizeBirthday(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() >= 10) s = s.substring(0, 10);
        return s.matches("\\d{4}-\\d{2}-\\d{2}") ? s : "";
    }

    private void setDetectedCountry(String existing) {
        String code = existing;
        if (!nonEmpty(code)) {
            try {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                if (tm != null) code = tm.getNetworkCountryIso();
            } catch (Exception ignored) {
            }
        }
        if (!nonEmpty(code)) code = Locale.getDefault().getCountry();
        code = nonEmpty(code) ? code.toUpperCase(Locale.US) : "OTHER";
        CountryCatalog.Entry entry = CountryCatalog.resolve(code);
        if (entry == null) entry = CountryCatalog.resolve("OTHER");
        countryCode = entry != null ? entry.code : "OTHER";
        if (binding.spinnerCountry != null) {
            binding.spinnerCountry.setSelection(CountryCatalog.spinnerIndexFor(countryCode));
        }
    }

    private String selectedCountryCode() {
        if (binding.spinnerCountry == null) return countryCode;
        int idx = binding.spinnerCountry.getSelectedItemPosition();
        String code = CountryCatalog.codeAtSpinnerIndex(idx);
        return nonEmpty(code) ? code : (countryCode != null ? countryCode : "OTHER");
    }

    private void saveProfile() {
        String birthday = normalizeBirthday(text(binding.etBirthday));
        int genderIndex = binding.spinnerGender.getSelectedItemPosition();
        String bio = text(binding.etBio);
        if (com.Dramizo.Series.util.ChatContentFilter.containsAgencyImpersonation(bio)) {
            Toast.makeText(this,
                    com.Dramizo.Series.util.ChatContentFilter.AGENCY_WORD_REASON,
                    Toast.LENGTH_LONG).show();
            return;
        }
        // Profile photos are optional. A failed upload must never block account setup.
        if (birthday.isEmpty()) {
            Toast.makeText(this, "اختر تاريخ الميلاد", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String[] p = birthday.split("-");
            Calendar birth = Calendar.getInstance();
            birth.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            Calendar min = Calendar.getInstance();
            min.add(Calendar.YEAR, -100);
            Calendar max = Calendar.getInstance();
            max.add(Calendar.YEAR, -13);
            if (birth.before(min) || birth.after(max)) {
                Toast.makeText(this, "العمر يجب أن يكون بين 13 و100 سنة", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "تاريخ الميلاد غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }
        if (genderIndex <= 0 || genderIndex >= GENDER_VALUES.length) {
            Toast.makeText(this, "اختر الجنس", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bio.isEmpty()) {
            Toast.makeText(this, "اكتب نبذة قصيرة عنك", Toast.LENGTH_SHORT).show();
            return;
        }

        MiscDtos.UpdateProfileRequest request = new MiscDtos.UpdateProfileRequest();
        // Leave avatarUrl unset when no photo was uploaded; backend keeps the default avatar.
        if (nonEmpty(avatarUrl)) request.avatarUrl = avatarUrl;
        request.bio = bio;
        request.gender = GENDER_VALUES[genderIndex];
        request.birthday = birthday;
        request.country = selectedCountryCode();

        saving = true;
        binding.btnContinue.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        viewModel.updateRequest(request);
    }

    private void uploadAvatar(Uri uri) {
        if (uri == null) return;
        AvatarImageLoader.load(binding.imgAvatar, uri);
        binding.progress.setVisibility(View.VISIBLE);
        binding.btnPickAvatar.setEnabled(false);
        container.getIoExecutor().execute(() -> {
            try {
                MultipartBody.Part part = AvatarImageLoader.multipartFromUri(
                        getContentResolver(), uri, "avatar");
                Response<ApiResponse<UploadApi.UploadResult>> response =
                        container.getUploadApi().upload(part).execute();
                if (response.isSuccessful() && response.body() != null
                        && response.body().success && response.body().data != null) {
                    avatarUrl = AssetCatalog.absoluteUrl(response.body().data.url);
                    runOnUiThread(() -> {
                        binding.progress.setVisibility(View.GONE);
                        binding.btnPickAvatar.setEnabled(true);
                    });
                } else {
                    throw new IllegalStateException(
                            UploadApi.errorMessage(response, "فشل رفع الصورة"));
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    avatarUrl = null;
                    binding.imgAvatar.setImageResource(ImagePlaceholder.avatar());
                    binding.progress.setVisibility(View.GONE);
                    binding.btnPickAvatar.setEnabled(true);
                    Toast.makeText(this,
                            e.getMessage() != null ? e.getMessage() : "فشل رفع الصورة",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openMain() {
        String pendingRoom = getIntent() != null ? getIntent().getStringExtra("pending_room_id") : null;
        Intent intent;
        if (pendingRoom != null && !pendingRoom.isEmpty()) {
            intent = new Intent(this, com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.class);
            intent.putExtra(
                    com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_ROOM_ID,
                    pendingRoom);
            intent.putExtra(
                    com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_IS_HOST,
                    false);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        if (getIntent() != null) {
            String invite = getIntent().getStringExtra(
                    com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE);
            if (invite == null || invite.isEmpty()) {
                invite = com.Dramizo.Series.util.InviteReferralHelper.peekPendingCode(this);
            }
            if (invite != null && !invite.isEmpty()) {
                intent.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE, invite);
                intent.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
            } else if (getIntent().getBooleanExtra(
                    com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, false)) {
                intent.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
            }
        } else {
            String invite = com.Dramizo.Series.util.InviteReferralHelper.peekPendingCode(this);
            if (invite != null && !invite.isEmpty()) {
                intent.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE, invite);
                intent.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String text(android.widget.EditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private static boolean nonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
