package com.droiders.camera2apitest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;
import com.droiders.camera2apitest.cameraapi2.Camera2ApiManager;
import com.droiders.camera2apitest.samsung.SamsungApiManager;

public class MainActivity extends AppCompatActivity {

  /**
   * If you want use samsung api change openCamera() to openCameraSamsung()
   */
  //camera2 api
  private Camera2ApiManager cameraApi2Manager;
  private SurfaceView surface;
  //samsung api
  private SamsungApiManager samsungApiManager;
  private TextureView tetureView;

  private final String[] PERMISSIONS = {
      Manifest.permission.CAMERA
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surface = findViewById(R.id.surfaceView);
    tetureView = findViewById(R.id.textureView);

    if (!hasPermissions(this, PERMISSIONS)) {
      ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
    } else {
      openCamera();
      //openCameraSamsung();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      openCamera();
      //openCameraSamsung();
    } else {
      Toast.makeText(this, "This app need camera permission, closing app..", Toast.LENGTH_SHORT)
          .show();
      finish();
    }
  }

  /**
   * Example Camera2ApiManager
   */
  private void openCamera() {
    surface.setVisibility(View.VISIBLE);
    surface.getHolder().addCallback(new SurfaceHolder.Callback() {
      @Override
      public void surfaceCreated(SurfaceHolder surfaceHolder) {
        cameraApi2Manager =
            new Camera2ApiManager(surfaceHolder.getSurface(), getApplicationContext());
        cameraApi2Manager.openCameraBack();
      }

      @Override
      public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

      }

      @Override
      public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

      }
    });
  }

  /**
   * Example SamsungApiManager
   */
  private void openCameraSamsung() {
    tetureView.setVisibility(View.VISIBLE);
    tetureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
      @Override
      public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        samsungApiManager =
            new SamsungApiManager(MainActivity.this, tetureView, 0);
      }

      @Override
      public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

      }

      @Override
      public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
      }

      @Override
      public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

      }
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (cameraApi2Manager != null) cameraApi2Manager.closeCamera();
  }

  private boolean hasPermissions(Context context, String... permissions) {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && context != null
        && permissions != null) {
      for (String permission : permissions) {
        if (ActivityCompat.checkSelfPermission(context, permission)
            != PackageManager.PERMISSION_GRANTED) {
          return false;
        }
      }
    }
    return true;
  }
}
