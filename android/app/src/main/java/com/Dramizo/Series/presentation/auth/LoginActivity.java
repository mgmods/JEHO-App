package com.Dramizo.Series.presentation.auth;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Toast;

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

        if (ContainerProvider.from(this).getSessionManager().isLoggedIn()) {
            AuthDtos.UserDto cached = ContainerProvider.from(this).getSessionManager().getUser();
            Intent next = ProfileSetupActivity.isProfileComplete(cached)
                    ? new Intent(this, MainActivity.class)
                    : new Intent(this, ProfileSetupActivity.class);
            copyPendingDeepLink(getIntent(), next);
            startActivity(next);
            finish();
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
        // Email / create-account / phone / guest stay hidden — Google only.

        viewModel.getLoading().observe(this, loading ->
                binding.progress.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(this, err -> {
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        });
        viewModel.getAuthSuccess().observe(this, result -> {
            if (result == null) return;
            AuthDtos.UserDto user = result.user != null
                    ? result.user
                    : ContainerProvider.from(this).getSessionManager().getUser();
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
            // Also carry Play Install Referrer code saved before login.
            String stored = com.Dramizo.Series.util.InviteReferralHelper.peekPendingCode(this);
            if (stored != null && !stored.isEmpty()) {
                next.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_PENDING_INVITE, stored);
                next.putExtra(com.Dramizo.Series.util.InviteReferralHelper.EXTRA_OPEN_INVITE, true);
            }
            startActivity(next);
            finish();
        });
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
