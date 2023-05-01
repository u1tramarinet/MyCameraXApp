package com.u1tramarinet.mycameraxapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Rational;
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
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, useCases);
        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
        cameraControl = camera.getCameraControl();
        cameraInfo = camera.getCameraInfo();
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

    enum AspectRatio {
        RATIO_4_3(4.0 / 3.0, "4:3"),
        RATIO_16_9(16.0 / 9.0, "16:9"),
        RATIO_1_1(1.0, "1:1"),
        ;
        final double value;
        final String screenName;

        AspectRatio(double value, String screenName) {
            this.value = value;
            this.screenName = screenName;
        }

        static AspectRatio next(AspectRatio origin) {
            switch (origin) {
                case RATIO_1_1:
                    return RATIO_4_3;
                case RATIO_16_9:
                    return RATIO_1_1;
                case RATIO_4_3:
                default:
                    return RATIO_16_9;
            }
        }
    }
}