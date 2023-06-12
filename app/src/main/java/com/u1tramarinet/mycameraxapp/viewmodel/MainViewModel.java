package com.u1tramarinet.mycameraxapp.viewmodel;

import android.content.ContentValues;
import android.provider.MediaStore;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.ExposureState;
import androidx.camera.core.ZoomState;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.u1tramarinet.mycameraxapp.AspectRatio;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.Executor;

public class MainViewModel extends ViewModel {
    private static final String FILE_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private final MutableLiveData<ContentValues> _takePhotoValues = new MutableLiveData<>();
    private final MutableLiveData<Void> _initializeCameraEvent = new MutableLiveData<>();
    private final MutableLiveData<ExposureState> _exposureState = new MutableLiveData<>();
    private final MutableLiveData<AspectRatio> _aspectRatio = new MutableLiveData<>();

    private Camera camera;
    private Executor mainExecutor;

    public final LiveData<ContentValues> takePhotoValues() {
        return _takePhotoValues;
    }

    public final LiveData<Void> initializeCameraEvent() {
        return _initializeCameraEvent;
    }

    public final LiveData<ExposureState> exposureState() {
        return _exposureState;
    }

    public final LiveData<AspectRatio> aspectRatio() {
        return _aspectRatio;
    }

    public void initializeCamera() {
        _initializeCameraEvent.setValue(null);
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public void setMainExecutor(@NonNull Executor mainExecutor) {
        this.mainExecutor = mainExecutor;
    }

    public void takePhoto() {
        String name = new SimpleDateFormat(FILE_FORMAT, Locale.JAPAN).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image");
        _takePhotoValues.setValue(contentValues);
    }

    public void setAspectRatio(AspectRatio aspectRatio) {
        _aspectRatio.setValue(aspectRatio);
    }

    public void zoomOut() {
        if (camera == null) {
            return;
        }
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        float currentRatio = (zoomState != null) ? zoomState.getZoomRatio() : 1;
        float minRatio = (zoomState != null) ? zoomState.getMinZoomRatio() : 1;
        float leftRatio = currentRatio - minRatio;
        float ratio;
        if (leftRatio >= 1) {
            ratio = --currentRatio;
        } else if (leftRatio >= 0.1f) {
            ratio = currentRatio - 0.1f;
        } else if (leftRatio > 0) {
            ratio = minRatio;
        } else {
            return;
        }
        setZoomRatio(ratio);
    }

    public void zoomIn() {
        if (camera == null) {
            return;
        }
        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
        float currentRatio = (zoomState != null) ? zoomState.getZoomRatio() : 1;
        float maxRatio = (zoomState != null) ? zoomState.getMaxZoomRatio() : 1;
        float leftRatio = maxRatio - currentRatio;
        float ratio;
        if (leftRatio >= 1) {
            ratio = (float) Math.floor(++currentRatio);
        } else if (leftRatio >= 0.1f) {
            ratio = currentRatio + 0.1f;
        } else if (leftRatio > 0) {
            ratio = maxRatio;
        } else {
            return;
        }
        setZoomRatio(ratio);
    }

    public void setZoomRatio(float zoom) {
        if (camera == null) {
            return;
        }
        camera.getCameraControl().setZoomRatio(zoom);
    }

    public void increaseExposure() {
        if (camera == null) {
            return;
        }
        CameraInfo cameraInfo = camera.getCameraInfo();
        int nextIndex = cameraInfo.getExposureState().getExposureCompensationIndex() + 1;
        Range<Integer> range = cameraInfo.getExposureState().getExposureCompensationRange();
        if (range.getUpper() >= nextIndex) {
            setExposureIndex(nextIndex);
        }
    }

    public void decreaseExposure() {
        if (camera == null) {
            return;
        }
        CameraInfo cameraInfo = camera.getCameraInfo();
        int nextIndex = cameraInfo.getExposureState().getExposureCompensationIndex() - 1;
        Range<Integer> range = cameraInfo.getExposureState().getExposureCompensationRange();
        if (range.getLower() <= nextIndex) {
            setExposureIndex(nextIndex);
        }
    }

    public void setExposure(int value) {
        if (camera == null) {
            return;
        }
        setExposureIndex(value);
    }

    private void setExposureIndex(int index) {
        if (camera == null) {
            return;
        }
        camera.getCameraControl().setExposureCompensationIndex(index).addListener(() -> _exposureState.setValue(camera.getCameraInfo().getExposureState()), mainExecutor);
    }
}