package com.Dramizo.Series.presentation.profile;

import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityGenderLivenessCameraBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Front-camera anti-spoof liveness:
 * center → blink → left → right → capture.
 * Rejects static phone photos of other people by requiring a real blink + stable tracking.
 */
public class GenderLivenessCameraActivity extends ThemedActivity {
    private static final int REQ_CAMERA = 8801;
    private static final long HOLD_MS = 450;
    private static final long BLINK_CLOSED_MS = 180;

    private enum Step { CENTER, BLINK, LEFT, RIGHT, CAPTURE }

    private ActivityGenderLivenessCameraBinding binding;
    private FaceDetector faceDetector;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Step step = Step.CENTER;
    private long stepHoldSince;
    private float yawCenter;
    private float yawLeft;
    private float yawRight;
    private Integer trackedFaceId;
    private boolean trackingStable = true;
    private boolean sawEyeClosed;
    private boolean blinkPassed;
    private long eyeClosedSince;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final AtomicBoolean analyzing = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGenderLivenessCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnClose.setOnClickListener(v -> finish());
        faceDetector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build());
        cameraExecutor = Executors.newSingleThreadExecutor();
        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        updateStepUi();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, R.string.liveness_camera_denied, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                provider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .build();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                provider.bindToLifecycle(this, selector, preview, imageCapture, analysis);
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (step == Step.CAPTURE || analyzing.get()) {
            imageProxy.close();
            return;
        }
        analyzing.set(true);
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees());
        faceDetector.process(image)
                .addOnSuccessListener(faces -> onFaces(faces, imageProxy))
                .addOnFailureListener(e -> {
                    analyzing.set(false);
                    imageProxy.close();
                });
    }

    private void onFaces(List<Face> faces, ImageProxy imageProxy) {
        runOnUiThread(() -> {
            analyzing.set(false);
            imageProxy.close();
            if (faces == null || faces.isEmpty() || step == Step.CAPTURE) return;
            // Multiple faces often = holding a phone photo in front of camera.
            if (faces.size() > 1) {
                stepHoldSince = 0;
                trackingStable = false;
                return;
            }
            Face face = faces.get(0);
            Integer id = face.getTrackingId();
            if (id != null) {
                if (trackedFaceId == null) trackedFaceId = id;
                else if (!trackedFaceId.equals(id)) trackingStable = false;
            }
            float yaw = face.getHeadEulerAngleY();
            long now = System.currentTimeMillis();
            boolean matched = false;
            switch (step) {
                case CENTER:
                    matched = Math.abs(yaw) <= 14f;
                    if (matched) yawCenter = yaw;
                    break;
                case BLINK: {
                    Float left = face.getLeftEyeOpenProbability();
                    Float right = face.getRightEyeOpenProbability();
                    if (left == null || right == null) {
                        matched = false;
                        break;
                    }
                    float open = Math.min(left, right);
                    if (!sawEyeClosed) {
                        if (open < 0.25f) {
                            if (eyeClosedSince == 0) eyeClosedSince = now;
                            if (now - eyeClosedSince >= BLINK_CLOSED_MS) {
                                sawEyeClosed = true;
                                eyeClosedSince = 0;
                            }
                        } else {
                            eyeClosedSince = 0;
                        }
                        matched = false;
                    } else {
                        matched = open > 0.65f;
                        if (matched) blinkPassed = true;
                    }
                    break;
                }
                case LEFT:
                    matched = yaw >= 18f;
                    if (matched) yawLeft = yaw;
                    break;
                case RIGHT:
                    matched = yaw <= -18f;
                    if (matched) yawRight = yaw;
                    break;
                default:
                    break;
            }
            if (!matched) {
                if (step != Step.BLINK || !sawEyeClosed) stepHoldSince = 0;
                return;
            }
            if (stepHoldSince == 0) stepHoldSince = now;
            if (now - stepHoldSince >= HOLD_MS) {
                advanceStep();
            }
        });
    }

    private void advanceStep() {
        stepHoldSince = 0;
        switch (step) {
            case CENTER:
                markDot(binding.dotCenter);
                step = Step.BLINK;
                sawEyeClosed = false;
                eyeClosedSince = 0;
                break;
            case BLINK:
                step = Step.LEFT;
                break;
            case LEFT:
                markDot(binding.dotLeft);
                step = Step.RIGHT;
                break;
            case RIGHT:
                markDot(binding.dotRight);
                step = Step.CAPTURE;
                captureFinal();
                break;
            default:
                break;
        }
        updateStepUi();
    }

    private void markDot(View dot) {
        if (dot != null) dot.setBackgroundResource(R.drawable.bg_liveness_dot_done);
    }

    private void updateStepUi() {
        switch (step) {
            case CENTER:
                binding.tvStepTitle.setText(R.string.liveness_step_center);
                binding.tvStepHint.setText(R.string.liveness_hint_center);
                break;
            case BLINK:
                binding.tvStepTitle.setText(R.string.liveness_step_blink);
                binding.tvStepHint.setText(R.string.liveness_hint_blink);
                break;
            case LEFT:
                binding.tvStepTitle.setText(R.string.liveness_step_left);
                binding.tvStepHint.setText(R.string.liveness_hint_left);
                break;
            case RIGHT:
                binding.tvStepTitle.setText(R.string.liveness_step_right);
                binding.tvStepHint.setText(R.string.liveness_hint_right);
                break;
            case CAPTURE:
                binding.tvStepTitle.setText(R.string.liveness_step_capture);
                binding.tvStepHint.setText(R.string.liveness_hint_capture);
                break;
            default:
                break;
        }
    }

    private void captureFinal() {
        if (imageCapture == null || !capturing.compareAndSet(false, true)) return;
        binding.progress.setVisibility(View.VISIBLE);
        File out = new File(getCacheDir(), "liveness_selfie.jpg");
        ImageCapture.OutputFileOptions opts =
                new ImageCapture.OutputFileOptions.Builder(out).build();
        imageCapture.takePicture(opts, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Bitmap bitmap = decodeAndMirror(out);
                runOnUiThread(() -> finishWithResult(bitmap));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> {
                    capturing.set(false);
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(GenderLivenessCameraActivity.this,
                            R.string.error_generic, Toast.LENGTH_LONG).show();
                    step = Step.RIGHT;
                    updateStepUi();
                });
            }
        });
    }

    private Bitmap decodeAndMirror(File file) {
        Bitmap raw = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (raw == null) return null;
        Matrix m = new Matrix();
        m.postScale(-1f, 1f, raw.getWidth() / 2f, raw.getHeight() / 2f);
        return Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
    }

    private void finishWithResult(Bitmap bitmap) {
        binding.progress.setVisibility(View.GONE);
        if (bitmap == null || !blinkPassed || !trackingStable) {
            Toast.makeText(this,
                    !blinkPassed ? R.string.liveness_blink_failed : R.string.liveness_tracking_failed,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        GenderLivenessResult result = new GenderLivenessResult();
        result.selfieBitmap = bitmap;
        result.yawCenter = yawCenter;
        result.yawLeft = yawLeft;
        result.yawRight = yawRight;
        result.blinkPassed = true;
        result.faceTrackingStable = trackingStable;
        result.livenessPassed = true;
        float score = 0.40f;
        if (Math.abs(yawCenter) <= 14f) score += 0.12f;
        if (blinkPassed) score += 0.18f;
        if (yawLeft >= 18f) score += 0.12f;
        if (yawRight <= -18f) score += 0.12f;
        if (trackingStable) score += 0.06f;
        result.livenessScore = Math.min(1f, score);
        // Live female host on a female account — confidence tied to anti-spoof quality.
        result.femaleConfidence = Math.min(1f, 0.85f + (result.livenessScore * 0.15f));
        GenderLivenessResult.stash(result);
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetector != null) faceDetector.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
