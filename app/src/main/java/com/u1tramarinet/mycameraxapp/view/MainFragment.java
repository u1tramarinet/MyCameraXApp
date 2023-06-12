package com.u1tramarinet.mycameraxapp.view;

import androidx.camera.core.Camera;
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
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.content.ContentValues;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.provider.MediaStore;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.u1tramarinet.mycameraxapp.AspectRatio;
import com.u1tramarinet.mycameraxapp.databinding.FragmentMainBinding;
import com.u1tramarinet.mycameraxapp.viewmodel.MainViewModel;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFragment extends Fragment {
    private FragmentMainBinding viewBinding;
    private MainViewModel viewModel;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;

    private Camera camera;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewBinding = FragmentMainBinding.inflate(inflater);
        return viewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        viewModel.setMainExecutor(ContextCompat.getMainExecutor(requireActivity()));
        viewModel.takePhotoValues().observe(getViewLifecycleOwner(), this::takePhoto);
        viewModel.initializeCameraEvent().observe(getViewLifecycleOwner(), unused -> startCamera());
        viewModel.aspectRatio().observe(getViewLifecycleOwner(), (aspectRatio) -> {
            updateAspectRatio(aspectRatio);
            bindCameraUseCases();
        });
        viewModel.exposureState().observe(getViewLifecycleOwner(), this::updateExposureInfo);

        viewBinding.photoButton.setOnClickListener(v -> viewModel.takePhoto());
        viewBinding.zoomOutIcon.setOnClickListener(v -> viewModel.zoomOut());
        viewBinding.zoomInIcon.setOnClickListener(v -> viewModel.zoomIn());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cameraExecutor.shutdown();
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, ContextCompat.getMainExecutor(requireActivity()));
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

        AspectRatio aspectRatio = Optional.ofNullable(viewModel.aspectRatio().getValue()).orElse(AspectRatio.RATIO_1_1);

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
            unregisterZoomStateObserver(camera.getCameraInfo());
        }

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCases);
        viewModel.setCamera(camera);

        preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

        registerZoomStateObserver(camera.getCameraInfo());
        updateAspectRatio(aspectRatio);
        updateExposureInfo(camera.getCameraInfo().getExposureState());
    }

    private void takePhoto(ContentValues contentValues) {
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(requireActivity().getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build();
        if (imageCapture == null) {
            Toast.makeText(getContext(), "Failed to take photo", Toast.LENGTH_SHORT).show();
            return;
        }
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(requireActivity()), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(getContext(), "Photo capture succeeded: " + outputFileResults.getSavedUri(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(getContext(), "Failed to take photo", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registerZoomStateObserver(@NonNull CameraInfo cameraInfo) {
        cameraInfo.getZoomState().observe(getViewLifecycleOwner(), this::updateZoomInfo);
    }

    private void unregisterZoomStateObserver(@NonNull CameraInfo cameraInfo) {
        cameraInfo.getZoomState().removeObservers(getViewLifecycleOwner());
    }

    private void updateZoomInfo(@NonNull ZoomState zoomState) {
        float ratio = zoomState.getZoomRatio();
        float minRatio = zoomState.getMinZoomRatio();
        float maxRatio = zoomState.getMaxZoomRatio();
        viewBinding.zoomSlider.setValueFrom(minRatio);
        viewBinding.zoomSlider.setValueTo(maxRatio);
        viewBinding.zoomSlider.setValue(ratio);
    }

    private void updateExposureInfo(@NonNull ExposureState exposureState) {
        int index = exposureState.getExposureCompensationIndex();
        float stepValue = exposureState.getExposureCompensationStep().floatValue();
        viewBinding.exposureButton.setText(String.valueOf(index * stepValue));
    }

    private void updateAspectRatio(AspectRatio aspectRatio) {
        viewBinding.aspectRatioButton.setText(aspectRatio.screenName);
    }
}