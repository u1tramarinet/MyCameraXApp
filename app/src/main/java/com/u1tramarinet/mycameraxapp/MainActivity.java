package com.u1tramarinet.mycameraxapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import com.u1tramarinet.mycameraxapp.databinding.ActivityMainBinding;
import com.u1tramarinet.mycameraxapp.viewmodel.MainViewModel;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CODE = 777;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        ActivityMainBinding viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        if (isAllPermissionGranted()) {
            viewModel.initializeCamera();
        } else {
            requestPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (isAllPermissionGranted()) {
                viewModel.initializeCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
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
}