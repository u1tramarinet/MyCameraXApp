package com.u1tramarinet.mycameraxapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Range;
import android.util.Rational;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.u1tramarinet.mycameraxapp.databinding.ActivityMainBinding;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CODE = 777;
    private static final String FILE_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private ActivityMainBinding viewBinding;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;
    private AspectRatio aspectRatio = AspectRatio.RATIO_1_1;

    private Camera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        if (isAllPermissionGranted()) {
            startCamera();
        } else {
            requestPermissions();
        }
        viewBinding.takePhotoButton.setOnClickListener(v -> takePhoto());
        viewBinding.switchAspectRatioButton.setOnClickListener(v -> switchAspectRatio());
        viewBinding.switchAspectRatioButton.setText(aspectRatio.screenName);
        viewBinding.zoomMinusButton.setOnClickListener(v -> zoomOut());
        viewBinding.zoomPlusButton.setOnClickListener(v -> zoomIn());
        viewBinding.exposureMinusButton.setOnClickListener(v -> countDownExposureIndex());
        viewBinding.exposurePlusButton.setOnClickListener(v -> countUpExposureIndex());
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (isAllPermissionGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            return;
        }
        String name = new SimpleDateFormat(FILE_FORMAT, Locale.JAPAN).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();

        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(getBaseContext(), "Photo capture succeeded: " + outputFileResults.getSavedUri(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(getBaseContext(), "Failed to take photo", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void switchAspectRatio() {
        aspectRatio = AspectRatio.next(aspectRatio);
        viewBinding.switchAspectRatioButton.setText(aspectRatio.screenName);
        bindCameraUseCases();
    }

    private void zoomIn() {
        if (cameraInfo == null || cameraControl == null) {
            return;
        }
        ZoomState zoomState = cameraInfo.getZoomState().getValue();
        float currentRatio = (zoomState != null) ? zoomState.getZoomRatio() : 1;
        float maxRatio = (zoomState != null) ? zoomState.getMaxZoomRatio() : 1;
        float leftRatio = maxRatio - currentRatio;
        if (leftRatio >= 1) {
            cameraControl.setZoomRatio((float) Math.floor(++currentRatio));
        } else if (leftRatio >= 0.1f) {
            cameraControl.setZoomRatio(currentRatio + 0.1f);
        } else if (leftRatio > 0) {
            cameraControl.setZoomRatio(maxRatio);
        }
    }

    private void zoomOut() {
        if (cameraInfo == null || cameraControl == null) {
            return;
        }
        ZoomState zoomState = cameraInfo.getZoomState().getValue();
        float currentRatio = (zoomState != null) ? zoomState.getZoomRatio() : 1;
        float minRatio = (zoomState != null) ? zoomState.getMinZoomRatio() : 1;
        float leftRatio = currentRatio - minRatio;
        if (leftRatio >= 1) {
            cameraControl.setZoomRatio((float) Math.floor(--currentRatio));
        } else if (leftRatio >= 0.1f) {
            cameraControl.setZoomRatio(currentRatio - 0.1f);
        } else if (leftRatio > 0) {
            cameraControl.setZoomRatio(minRatio);
        }
    }

    private void countUpExposureIndex() {
        if (cameraInfo == null || cameraControl == null) {
            return;
        }
        int currentIndex = cameraInfo.getExposureState().getExposureCompensationIndex();
        Range<Integer> range = cameraInfo.getExposureState().getExposureCompensationRange();
        if (range.getUpper() > currentIndex) {
            cameraControl.setExposureCompensationIndex(++currentIndex).addListener(() -> outputExposureInfo(viewBinding.exposureInfo), ContextCompat.getMainExecutor(this));
        }
    }

    private void countDownExposureIndex() {
        if (cameraInfo == null || cameraControl == null) {
            return;
        }
        int currentIndex = cameraInfo.getExposureState().getExposureCompensationIndex();
        Range<Integer> range = cameraInfo.getExposureState().getExposureCompensationRange();
        if (range.getLower() < currentIndex) {
            cameraControl.setExposureCompensationIndex(--currentIndex).addListener(() -> outputExposureInfo(viewBinding.exposureInfo), ContextCompat.getMainExecutor(this));
        }
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }
        PreviewView viewFinder = viewBinding.viewFinder;
        viewFinder.setScaleType(PreviewView.ScaleType.FIT_START);
        int rotation = viewFinder.getDisplay().getRotation();
        int imageWidth = viewFinder.getWidth();
        Preview preview = new Preview.Builder()
                .setTargetRotation(rotation)
                .build();

        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(rotation)
                .build();

        ViewPort viewPort = new ViewPort.Builder(
                new Rational(imageWidth,
                        (int) (imageWidth * aspectRatio.value)), rotation)
                .build();

        UseCaseGroup useCases = new UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .setViewPort(viewPort)
                .build();

        cameraProvider.unbindAll();
        if (camera != null) {
            removeZoomStateObservers(camera.getCameraInfo());
        }

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCases);
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
        cameraControl = camera.getCameraControl();
        cameraInfo = camera.getCameraInfo();

        outputExposureInfo(viewBinding.exposureInfo);

        observeZoomState(viewBinding.zoomInfo);
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSION_CODE);
    }

    private boolean isAllPermissionGranted() {
        return Arrays.stream(REQUIRED_PERMISSIONS).allMatch(permission -> isPermissionGranted(getBaseContext(), permission));
    }

    private boolean isPermissionGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void outputExposureInfo(TextView textView) {
        if (cameraInfo == null) {
            return;
        }
        ExposureState exposureState = cameraInfo.getExposureState();
        int exposureIndex = exposureState.getExposureCompensationIndex();
        Range<Integer> exposureRange = exposureState.getExposureCompensationRange();
        Rational exposureStep = exposureState.getExposureCompensationStep();
        float exposureStepValue = exposureStep.floatValue();
        String info = "exposure= " + (exposureIndex * exposureStepValue) + " (" + (exposureRange.getLower() * exposureStepValue) + "～" + (exposureRange.getUpper() * exposureStepValue) + ", " + exposureStep + ")";
        textView.setText(info);
    }

    private void observeZoomState(TextView textView) {
        if (cameraInfo == null) {
            return;
        }
        cameraInfo.getZoomState().observe(this, zoomState -> {
            float linear = zoomState.getLinearZoom();
            float ratio = zoomState.getZoomRatio();
            float minRatio = zoomState.getMinZoomRatio();
            float maxRatio = zoomState.getMaxZoomRatio();
            String info = "zoom(linear)= " + linear + "\n";
            info += "zoom(ratio)= " + ratio + " (" + minRatio + "～" + maxRatio + ")";
            textView.setText(info);
        });
    }

    private void removeZoomStateObservers(CameraInfo cameraInfo) {
        cameraInfo.getZoomState().removeObservers(this);
    }
}