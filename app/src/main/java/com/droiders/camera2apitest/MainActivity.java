package com.droiders.camera2apitest;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import com.droiders.camera2apitest.cameraapi2.Camera2ApiManager;

public class MainActivity extends AppCompatActivity {

  private Camera2ApiManager cameraApi2Manager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    TextureView textureView = (TextureView) findViewById(R.id.texture);
    textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
      @Override
      public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        cameraApi2Manager = new Camera2ApiManager(new Surface(surfaceTexture), MainActivity.this);
        cameraApi2Manager.openCameraBack();
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

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.texture_2);
    surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
      @Override
      public void surfaceCreated(SurfaceHolder surfaceHolder) {

        Camera2ApiManager cameraApi2Manager2 =
            new Camera2ApiManager(surfaceHolder.getSurface(), getApplicationContext());
        cameraApi2Manager2.openCameraFront();
      }

      @Override
      public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

      }

      @Override
      public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

      }
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    cameraApi2Manager.closeCamera();
  }
}
