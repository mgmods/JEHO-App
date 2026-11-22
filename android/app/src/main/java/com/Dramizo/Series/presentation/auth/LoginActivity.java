package com.Dramizo.Series.presentation.auth;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.text.InputType;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import java.util.ArrayList;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityLoginBinding;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.presentation.profile.ProfileSetupActivity;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class LoginActivity extends ThemedActivity {
    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;
    private GoogleSignInClient googleClient;
    private boolean registerMode = false;
    private String pendingPhoneSignup;
    private String pendingPhonePassword;
    private AlertDialog phoneVerificationDialog;
    private EditText[] otpFields;
    private TextView otpCountdown;
    private TextView otpResend;
    private final Handler otpHandler = new Handler(Looper.getMainLooper());
    private int otpSecondsRemaining = 30;
    private final Runnable otpTicker = new Runnable() {
        @Override public void run() {
            if (phoneVerificationDialog == null || !phoneVerificationDialog.isShowing()) return;
            if (otpSecondsRemaining > 0) {
                otpCountdown.setText("يمكنك طلب رمز جديد بعد " + otpSecondsRemaining + " ثانية");
                otpSecondsRemaining--;
                otpHandler.postDelayed(this, 1000L);
            } else {
                otpCountdown.setText("لم يصلك الرمز؟");
                otpResend.setEnabled(true);
                otpResend.setAlpha(1f);
            }
        }
    };

    @Override
    protected boolean wantsContentSystemPadding() {
        // Full-bleed Mikoo login art — do not pad the hero ConstraintLayout.
        return false;
    }

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account == null || account.getIdToken() == null) {
                        Toast.makeText(this, "تعذر الحصول على رمز Google", Toast.LENGTH_LONG).show();
                        binding.progress.setVisibility(View.GONE);
                        return;
                    }
                    viewModel.socialLogin(
                            "google",
                            account.getIdToken(),
                            account.getEmail(),
                            account.getDisplayName(),
                            account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null,
                            account.getId()
                    );
                } catch (ApiException e) {
                    binding.progress.setVisibility(View.GONE);
                    String detail = e.getStatusCode() == 10
                            ? "إعداد Google OAuth غير مطابق: راجع SHA-1 للـAPK واسم الحزمة في Google Cloud (رمز 10)"
                            : ("رمز " + e.getStatusCode());
                    Toast.makeText(this, "فشل تسجيل Google: " + detail, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padStatusOnly(binding.contentRoot);
        EdgeToEdgeHelper.padBottom(binding.contentRoot);
        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(AuthViewModel.class);

        setupCountryCodes();
        binding.identifierLayout.setHint("رقم الهاتف");
        viewModel.getOtpSent().observe(this, sent -> {
            if (sent == null || !sent.sent || pendingPhoneSignup == null) return;
            if (phoneVerificationDialog == null || !phoneVerificationDialog.isShowing()) {
                showPhoneVerificationDialog();
            } else {
                startOtpCountdown();
                Toast.makeText(this, "تم إرسال رمز تحقق جديد", Toast.LENGTH_SHORT).show();
            }
        });

        if (ContainerProvider.from(this).getSessionManager().isLoggedIn()) {
            AppContainer container = ContainerProvider.from(this);
            container.getIoExecutor().execute(() -> {
                AuthDtos.UserDto resolved = null;
                try {
                    Result<AuthDtos.UserDto> fresh = container.getProfileUseCase.me();
                    if (fresh != null && fresh.success && fresh.data != null) {
                        resolved = fresh.data;
                        container.getSessionManager().updateCachedUser(resolved);
                    }
                } catch (Exception ignored) {
                    // Use the last saved profile if the server cannot be reached.
                }
                if (resolved == null) resolved = container.getSessionManager().getUser();
                final AuthDtos.UserDto user = resolved;
                runOnUiThread(() -> {
                    Intent next = ProfileSetupActivity.isProfileComplete(user)
                            ? new Intent(LoginActivity.this, MainActivity.class)
                            : new Intent(LoginActivity.this, ProfileSetupActivity.class);
                    copyPendingDeepLink(getIntent(), next);
                    startActivity(next);
                    finish();
                });
            });
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        // Soft entrance matching splash brand motion.
        if (binding.imgLogo != null) {
            ScaleAnimation scale = new ScaleAnimation(
                    0.94f, 1f, 0.94f, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(420L);
            AlphaAnimation fade = new AlphaAnimation(0f, 1f);
            fade.setDuration(420L);
            android.view.animation.AnimationSet enter = new android.view.animation.AnimationSet(true);
            enter.addAnimation(scale);
            enter.addAnimation(fade);
            enter.setFillAfter(true);
            binding.imgLogo.startAnimation(enter);
        }

        binding.btnGoogle.setOnClickListener(v -> {
            binding.progress.setVisibility(View.VISIBLE);
            googleClient.signOut().addOnCompleteListener(t ->
                    googleLauncher.launch(googleClient.getSignInIntent()));
        });

        // Working backend-backed email/username + password authentication.
        binding.btnAuthSubmit.setOnClickListener(v -> submitPasswordAuth());
        binding.btnAuthMode.setOnClickListener(v -> setRegisterMode(!registerMode));
        setRegisterMode(false);

        viewModel.getLoading().observe(this, loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(this, err -> {
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        });
        viewModel.getAuthSuccess().observe(this, result -> {
            if (result == null) return;
            AppContainer container = ContainerProvider.from(this);
            // Login DTOs can omit country/gender for an existing account. Resolve /me first
            // so returning users are not sent through profile creation again.
            container.getIoExecutor().execute(() -> {
                AuthDtos.UserDto resolvedUser = null;
                try {
                    Result<AuthDtos.UserDto> fresh = container.getProfileUseCase.me();
                    if (fresh != null && fresh.success && fresh.data != null) {
                        resolvedUser = fresh.data;
                        String identifier = result.user != null && result.user.username != null
                                ? result.user.username
                                : (result.user != null && result.user.email != null
                                        ? result.user.email : resolvedUser.username);
                        resolvedUser = container.getSessionManager()
                                .restoreSavedAccountProfile(resolvedUser, identifier);
                        container.getSessionManager().updateCachedUser(resolvedUser);
                    }
                } catch (Exception ignored) {
                    // Keep a successful login usable if /me is temporarily unavailable.
                }
                if (resolvedUser == null) {
                    // A previous logout stores a per-account profile snapshot. Prefer
                    // that richer snapshot over a partial login DTO when /me is unavailable.
                    AuthDtos.UserDto loginUser = result.user;
                    AuthDtos.UserDto savedUser = loginUser != null
                            ? container.getSessionManager().getSavedAccountUser(loginUser.username)
                            : null;
                    if (savedUser != null && loginUser != null
                            && loginUser.id != null && loginUser.id.equals(savedUser.id)
                            && ProfileSetupActivity.isProfileComplete(savedUser)) {
                        resolvedUser = savedUser;
                        container.getSessionManager().updateCachedUser(savedUser);
                    } else {
                        resolvedUser = loginUser != null
                                ? container.getSessionManager().restoreSavedAccountProfile(
                                        loginUser, loginUser.username != null
                                                ? loginUser.username : loginUser.email)
                                : container.getSessionManager().getUser();
                        if (resolvedUser != null) {
                            container.getSessionManager().updateCachedUser(resolvedUser);
                        }
                    }
                }
                final AuthDtos.UserDto user = resolvedUser;
                runOnUiThread(() -> continueAfterAuth(result, user));
            });
        });
    }

    private void continueAfterAuth(AuthDtos.AuthResult result, AuthDtos.UserDto user) {
        Intent next;
        if (ProfileSetupActivity.isProfileComplete(user)) {
            String pendingRoom = getIntent() != null
                    ? getIntent().getStringExtra("pending_room_id") : null;
            if (pendingRoom == null || pendingRoom.isEmpty()) {
                pendingRoom = com.Dramizo.Series.util.InviteReferralHelper.peekPendingRoom(this);
            }
            if (pendingRoom != null && !pendingRoom.isEmpty()) {
                next = new Intent(this, com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.class);
                next.putExtra(com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_ROOM_ID, pendingRoom);
                next.putExtra(com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity.EXTRA_IS_HOST, false);
                com.Dramizo.Series.util.InviteReferralHelper.takePendingRoom(this);
            } else {
                next = new Intent(this, MainActivity.class);
            }
        } else {
            next = new Intent(this, ProfileSetupActivity.class);
        }
        copyPendingDeepLink(getIntent(), next);
        String stored = com.Dramizo.Series.util.InviteReferralHelper.peekPendingCode(this);
        if (stored != null && !stored.isEmpty()) {
            next.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE, stored);
            next.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
        }
        startActivity(next);
        finish();
    }

    private void setupCountryCodes() {
        String[] codes = {
                "🇧🇬 +359", "🇸🇾 +963", "🇸🇦 +966", "🇦🇪 +971",
                "🇪🇬 +20", "🇯🇴 +962", "🇱🇧 +961", "🇮🇶 +964",
                "🇹🇷 +90", "🇧🇭 +973", "🇰🇼 +965", "🇶🇦 +974",
                "🇴🇲 +968", "🇺🇸 +1", "🇬🇧 +44", "🇩🇪 +49",
                "🇫🇷 +33", "🇳🇱 +31", "🇸🇪 +46", "🇨🇦 +1"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, codes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCountryCode.setAdapter(adapter);
        String region = Locale.getDefault().getCountry();
        String defaultCode = "BG".equalsIgnoreCase(region) ? "+359"
                : "SY".equalsIgnoreCase(region) ? "+963"
                : "SA".equalsIgnoreCase(region) ? "+966"
                : "AE".equalsIgnoreCase(region) ? "+971"
                : "US".equalsIgnoreCase(region) ? "+1"
                : "GB".equalsIgnoreCase(region) ? "+44"
                : "DE".equalsIgnoreCase(region) ? "+49" : "+359";
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].endsWith(defaultCode)) {
                binding.spinnerCountryCode.setSelection(i);
                break;
            }
        }
    }

    private String fullPhone(String localNumber) {
        String raw = localNumber == null ? "" : localNumber.replaceAll("[^0-9]", "");
        String selected = String.valueOf(binding.spinnerCountryCode.getSelectedItem());
        String code = selected.substring(selected.lastIndexOf(' ') + 1);
        if (raw.startsWith("0")) raw = raw.substring(1);
        return code + raw;
    }

    private void setRegisterMode(boolean register) {
        registerMode = register;
        binding.spinnerCountryCode.setVisibility(View.VISIBLE);
        binding.emailLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.confirmPasswordLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        binding.googleLoginGroup.setVisibility(register ? View.GONE : View.VISIBLE);
        binding.tvAuthDivider.setVisibility(register ? View.GONE : View.VISIBLE);
        binding.tvLoginHint.setVisibility(register ? View.GONE : View.VISIBLE);
        binding.usernameLayout.setVisibility(View.GONE);
        binding.identifierLayout.setVisibility(register ? View.GONE : View.VISIBLE);
        binding.btnAuthSubmit.setText(register ? "متابعة" : "تسجيل الدخول");
        binding.btnAuthMode.setText(register
                ? "لديك حساب؟ تسجيل الدخول"
                : "ليس لديك حساب؟ إنشاء حساب جديد");
        binding.tvLoginHint.setText("المتابعة باستخدام Google أو رقم الهاتف");
    }

    private void submitPasswordAuth() {
        String password = binding.etAuthPassword.getText() == null
                ? "" : binding.etAuthPassword.getText().toString();
        if (password.length() < 6) {
            binding.etAuthPassword.setError(getString(R.string.password_min_length));
            binding.etAuthPassword.requestFocus();
            return;
        }
        if (!registerMode) {
            String local = binding.etAuthIdentifier.getText() == null
                    ? "" : binding.etAuthIdentifier.getText().toString();
            String phone = fullPhone(local);
            if (local.trim().length() < 5) {
                binding.etAuthIdentifier.setError("أدخل رقم هاتف صحيح");
                binding.etAuthIdentifier.requestFocus();
                return;
            }
            viewModel.login(phone, password);
            return;
        }

        String local = binding.etAuthEmail.getText() == null
                ? "" : binding.etAuthEmail.getText().toString();
        String phone = fullPhone(local);
        String confirm = binding.etAuthConfirmPassword.getText() == null
                ? "" : binding.etAuthConfirmPassword.getText().toString();
        if (local.replaceAll("[^0-9]", "").length() < 5) {
            binding.etAuthEmail.setError("أدخل رقم هاتف صحيح");
            binding.etAuthEmail.requestFocus();
            return;
        }
        if (!password.equals(confirm)) {
            binding.etAuthConfirmPassword.setError("كلمتا المرور غير متطابقتين");
            binding.etAuthConfirmPassword.requestFocus();
            return;
        }
        pendingPhoneSignup = phone;
        pendingPhonePassword = password;
        binding.progress.setVisibility(View.VISIBLE);
        viewModel.sendOtp(phone);
    }

    private void startOtpCountdown() {
        otpHandler.removeCallbacks(otpTicker);
        otpSecondsRemaining = 30;
        otpResend.setEnabled(false);
        otpResend.setAlpha(0.45f);
        otpTicker.run();
    }

    private void showPhoneVerificationDialog() {
        binding.progress.setVisibility(View.GONE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(10), dp(18), dp(4));

        TextView description = new TextView(this);
        description.setText("أدخل رمز التحقق المكوّن من ستة أرقام الذي وصلك على هاتفك");
        description.setGravity(Gravity.CENTER);
        description.setTextSize(14);
        content.addView(description);

        LinearLayout digits = new LinearLayout(this);
        digits.setGravity(Gravity.CENTER);
        digits.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams digitsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        digitsParams.topMargin = dp(18);
        content.addView(digits, digitsParams);

        otpFields = new EditText[6];
        for (int i = 0; i < 6; i++) {
            final int index = i;
            EditText field = new EditText(this);
            otpFields[i] = field;
            field.setGravity(Gravity.CENTER);
            field.setTextSize(21);
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
            field.setSingleLine(true);
            field.setSelectAllOnFocus(true);
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1)});
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            digits.addView(field, lp);
            field.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                    if (s.length() == 1 && index < 5) otpFields[index + 1].requestFocus();
                    if (s.length() == 0 && index > 0) otpFields[index - 1].requestFocus();
                }
                @Override public void afterTextChanged(Editable e) {}
            });
        }

        otpCountdown = new TextView(this);
        otpCountdown.setGravity(Gravity.CENTER);
        otpCountdown.setTextSize(13);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
        countLp.topMargin = dp(10);
        content.addView(otpCountdown, countLp);

        otpResend = new TextView(this);
        otpResend.setText("إعادة إرسال الرمز");
        otpResend.setGravity(Gravity.CENTER);
        otpResend.setTextSize(14);
        otpResend.setTextColor(getColor(R.color.gift_accent));
        otpResend.setEnabled(false);
        otpResend.setAlpha(0.45f);
        content.addView(otpResend, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        otpResend.setOnClickListener(v -> {
            if (otpSecondsRemaining > 0 || pendingPhoneSignup == null) return;
            otpResend.setEnabled(false);
            otpResend.setAlpha(0.45f);
            binding.progress.setVisibility(View.VISIBLE);
            viewModel.sendOtp(pendingPhoneSignup);
        });

        phoneVerificationDialog = new AlertDialog.Builder(this)
                .setTitle("التحقق من حسابك")
                .setView(content)
                .setPositiveButton("تأكيد", null)
                .setNegativeButton("إلغاء", (dialog, which) -> {
                    pendingPhoneSignup = null;
                    pendingPhonePassword = null;
                })
                .create();
        phoneVerificationDialog.setOnDismissListener(dialog -> {
            otpHandler.removeCallbacks(otpTicker);
            phoneVerificationDialog = null;
        });
        phoneVerificationDialog.setOnShowListener(dialog -> {
            phoneVerificationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                StringBuilder code = new StringBuilder(6);
                for (EditText field : otpFields) {
                    String value = field.getText() == null ? "" : field.getText().toString();
                    if (value.isEmpty()) {
                        field.requestFocus();
                        Toast.makeText(this, "أدخل الأرقام الستة كاملة", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    code.append(value);
                }
                binding.progress.setVisibility(View.VISIBLE);
                viewModel.verifyPhoneSignup(pendingPhoneSignup, code.toString(), pendingPhonePassword);
            });
            otpFields[0].requestFocus();
            startOtpCountdown();
        });
        phoneVerificationDialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static void copyPendingDeepLink(Intent from, Intent to) {
        if (from == null || to == null) return;
        String room = from.getStringExtra("pending_room_id");
        if (room != null && !room.isEmpty()) to.putExtra("pending_room_id", room);
        String invite = from.getStringExtra(
                com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE);
        if (invite != null && !invite.isEmpty()) {
            to.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE, invite);
            to.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
        } else if (from.getBooleanExtra(
                com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, false)) {
            to.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
        }
    }
}
