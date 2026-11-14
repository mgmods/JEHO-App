package com.Dramizo.Series.presentation.profile;



import com.Dramizo.Series.presentation.common.ThemedActivity;



import android.content.Intent;

import android.graphics.Bitmap;

import android.os.Bundle;

import android.view.View;

import android.widget.Toast;



import androidx.activity.OnBackPressedCallback;

import androidx.activity.result.ActivityResultLauncher;

import androidx.activity.result.contract.ActivityResultContracts;



import com.Dramizo.Series.R;

import com.Dramizo.Series.data.remote.api.UploadApi;

import com.Dramizo.Series.data.remote.dto.ApiResponse;

import com.Dramizo.Series.data.remote.dto.AuthDtos;

import com.Dramizo.Series.data.remote.dto.MiscDtos;

import com.Dramizo.Series.databinding.ActivityFemaleIdentityVerificationBinding;

import com.Dramizo.Series.di.AppContainer;

import com.Dramizo.Series.presentation.common.ContainerProvider;

import com.Dramizo.Series.presentation.main.MainActivity;

import com.Dramizo.Series.util.ApiCall;

import com.Dramizo.Series.util.AppFeatures;
import com.Dramizo.Series.util.AssetCatalog;

import com.Dramizo.Series.util.GenderVerifiedBadge;

import com.Dramizo.Series.util.ImagePlaceholder;

import com.bumptech.glide.Glide;



import okhttp3.MediaType;

import okhttp3.MultipartBody;

import okhttp3.RequestBody;

import retrofit2.Response;



/** Mandatory liveness verification for female hosts. */

public class FemaleIdentityVerificationActivity extends ThemedActivity {

    public static final String EXTRA_AFTER_SETUP = "after_setup";

    public static final String EXTRA_BLOCKING = "blocking";



    private ActivityFemaleIdentityVerificationBinding binding;

    private AppContainer container;

    private Bitmap selfieBitmap;

    private String uploadedSelfieUrl;

    private String status = "none";

    private boolean afterSetup;

    private boolean blocking;

    private float livenessScore;

    private boolean livenessPassed;

    private float yawCenter;

    private float yawLeft;

    private float yawRight;

    private boolean blinkPassed;

    private boolean faceTrackingStable = true;

    private float femaleConfidence;



    private final ActivityResultLauncher<Intent> livenessScan = registerForActivityResult(

            new ActivityResultContracts.StartActivityForResult(),

            result -> {

                if (result.getResultCode() != RESULT_OK) return;

                GenderLivenessResult lr = GenderLivenessResult.take();

                if (lr == null || lr.selfieBitmap == null) return;

                selfieBitmap = lr.selfieBitmap;

                uploadedSelfieUrl = null;

                livenessPassed = lr.livenessPassed;

                livenessScore = lr.livenessScore;

                yawCenter = lr.yawCenter;

                yawLeft = lr.yawLeft;

                yawRight = lr.yawRight;

                blinkPassed = lr.blinkPassed;

                faceTrackingStable = lr.faceTrackingStable;

                femaleConfidence = lr.femaleConfidence;

                binding.imgSelfiePreview.setImageBitmap(selfieBitmap);

                binding.btnSubmitVerification.setEnabled(true);

            });



    @Override

    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        binding = ActivityFemaleIdentityVerificationBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        container = ContainerProvider.from(this);
        AppFeatures.refresh(container);
        if (!container.getSessionManager().isFemaleOnlyVoiceHostsFromServer()) {
            finishFlow(true);
            return;
        }
        AuthDtos.UserDto me = container.getSessionManager().getUser();
        if (me == null || !"female".equalsIgnoreCase(me.gender)) {
            Toast.makeText(this, "التحقق من الهوية متاح للمضيفات (أنثى) فقط", Toast.LENGTH_LONG).show();
            finishFlow(true);
            return;
        }

        afterSetup = getIntent().getBooleanExtra(EXTRA_AFTER_SETUP, false);

        blocking = getIntent().getBooleanExtra(EXTRA_BLOCKING, false) || afterSetup;



        binding.btnBack.setVisibility(blocking ? View.GONE : View.VISIBLE);

        binding.btnBack.setOnClickListener(v -> finishFlow(false));

        binding.btnCaptureSelfie.setOnClickListener(v ->

                livenessScan.launch(new Intent(this, GenderLivenessCameraActivity.class)));

        binding.btnSubmitVerification.setOnClickListener(v -> submitVerification());

        binding.btnSkipForNow.setOnClickListener(v -> finishFlow(true));



        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(blocking) {

            @Override

            public void handleOnBackPressed() {

                if (!blocking) finishFlow(false);

            }

        });



        loadStatus();

    }



    private void loadStatus() {

        setBusy(true);

        container.getIoExecutor().execute(() -> {

            MiscDtos.GenderVerificationDto dto =

                    ApiCall.execute(container.getUserApi().getGenderVerification()).data;

            AuthDtos.UserDto me = ApiCall.execute(container.getUserApi().me()).data;

            if (me != null) container.getSessionManager().updateCachedUser(me);

            runOnUiThread(() -> {

                setBusy(false);

                if (dto == null) {

                    applyStatusUi("none", null, me != null && me.genderVerified);

                    return;

                }

                status = dto.genderVerificationStatus != null ? dto.genderVerificationStatus : "none";

                uploadedSelfieUrl = dto.selfieUrl;

                if (uploadedSelfieUrl != null && !uploadedSelfieUrl.isEmpty()) {

                    Glide.with(this).load(AssetCatalog.absoluteUrl(uploadedSelfieUrl))

                            .placeholder(ImagePlaceholder.avatar())

                            .centerCrop()

                            .into(binding.imgSelfiePreview);

                }

                boolean verified = me != null && me.genderVerified;

                applyStatusUi(status, dto.reviewNote, verified);

            });

        });

    }



    private void applyStatusUi(String current, String reviewNote, Boolean verified) {

        status = current != null ? current : "none";

        binding.tvVerificationStatus.setVisibility(View.VISIBLE);

        binding.btnSkipForNow.setVisibility(View.GONE);



        if (Boolean.TRUE.equals(verified) || "approved".equalsIgnoreCase(status)) {

            binding.tvVerificationStatus.setText(R.string.gender_verification_approved);

            binding.btnSubmitVerification.setEnabled(false);

            binding.btnCaptureSelfie.setEnabled(false);

            binding.btnSkipForNow.setVisibility(View.VISIBLE);

            binding.btnSkipForNow.setText(R.string.continue_text);

            return;

        }

        if ("pending".equalsIgnoreCase(status)) {

            binding.tvVerificationStatus.setText(R.string.gender_verification_pending);

            binding.btnSubmitVerification.setEnabled(false);

            binding.btnCaptureSelfie.setEnabled(false);

            // Always allow continuing into the app while under review.

            binding.btnSkipForNow.setVisibility(View.VISIBLE);

            binding.btnSkipForNow.setText(R.string.continue_text);

            return;

        }

        if ("rejected".equalsIgnoreCase(status)) {

            String note = reviewNote != null && !reviewNote.isEmpty()

                    ? ("\n" + reviewNote) : "";

            binding.tvVerificationStatus.setText(getString(R.string.gender_verification_rejected) + note);

            binding.btnCaptureSelfie.setEnabled(true);

            binding.btnSubmitVerification.setEnabled(selfieBitmap != null);

            return;

        }

        binding.tvVerificationStatus.setText(R.string.gender_verification_liveness_hint);

        binding.btnSubmitVerification.setEnabled(selfieBitmap != null);

    }



    private void submitVerification() {

        if (selfieBitmap == null && (uploadedSelfieUrl == null || uploadedSelfieUrl.isEmpty())) {

            Toast.makeText(this, R.string.gender_verification_need_liveness, Toast.LENGTH_SHORT).show();

            return;

        }

        if (!livenessPassed && selfieBitmap != null) {

            Toast.makeText(this, R.string.gender_verification_need_liveness, Toast.LENGTH_SHORT).show();

            return;

        }

        setBusy(true);

        container.getIoExecutor().execute(() -> {

            try {

                String url = uploadedSelfieUrl;

                if (selfieBitmap != null) {

                    byte[] bytes = GenderVerifiedBadge.jpegFromBitmap(selfieBitmap, 88);

                    if (bytes == null) throw new IllegalStateException("تعذر معالجة الصورة");

                    RequestBody body = RequestBody.create(bytes, MediaType.parse("image/jpeg"));

                    MultipartBody.Part part = MultipartBody.Part.createFormData(

                            "file", "selfie.jpg", body);

                    Response<ApiResponse<UploadApi.UploadResult>> upload =

                            container.getUploadApi().upload(part).execute();

                    if (!upload.isSuccessful() || upload.body() == null || !upload.body().success

                            || upload.body().data == null) {

                        throw new IllegalStateException("فشل رفع الصورة");

                    }

                    url = AssetCatalog.absoluteUrl(upload.body().data.url);

                }

                MiscDtos.SubmitGenderVerificationRequest req = new MiscDtos.SubmitGenderVerificationRequest();

                req.selfieUrl = url;

                req.livenessPassed = livenessPassed;

                req.livenessScore = livenessScore;

                req.livenessYawCenter = yawCenter;

                req.livenessYawLeft = yawLeft;

                req.livenessYawRight = yawRight;

                req.blinkPassed = blinkPassed;

                req.faceTrackingStable = faceTrackingStable;

                req.femaleConfidence = femaleConfidence;

                MiscDtos.GenderVerificationDto result =

                        ApiCall.execute(container.getUserApi().submitGenderVerification(req)).data;

                AuthDtos.UserDto me = ApiCall.execute(container.getUserApi().me()).data;

                if (me != null) container.getSessionManager().updateCachedUser(me);

                runOnUiThread(() -> {

                    setBusy(false);

                    if (result == null) {

                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();

                        return;

                    }

                    uploadedSelfieUrl = result.selfieUrl;

                    boolean approved = result.genderVerified

                            || "approved".equalsIgnoreCase(result.genderVerificationStatus);

                    if (approved) {

                        Toast.makeText(this, R.string.gender_verification_auto_ok, Toast.LENGTH_LONG).show();

                    } else {

                        Toast.makeText(this, R.string.gender_verification_sent_continue, Toast.LENGTH_LONG).show();

                    }

                    applyStatusUi(result.genderVerificationStatus, result.reviewNote, result.genderVerified);

                    // After submit: enter the app. Female host features stay locked until approved.

                    finishFlow(true);

                });

            } catch (Exception e) {

                runOnUiThread(() -> {

                    setBusy(false);

                    Toast.makeText(this,

                            e.getMessage() != null ? e.getMessage() : getString(R.string.error_generic),

                            Toast.LENGTH_LONG).show();

                });

            }

        });

    }



    private void finishFlow(boolean success) {

        // After submitting, always let the user into the app (features stay gated).

        if ((blocking || afterSetup) && success) {

            Intent i = new Intent(this, MainActivity.class);

            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(i);

        }

        finish();

    }



    private void setBusy(boolean busy) {

        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);

        binding.btnSubmitVerification.setEnabled(!busy && selfieBitmap != null);

        binding.btnCaptureSelfie.setEnabled(!busy);

    }

}


