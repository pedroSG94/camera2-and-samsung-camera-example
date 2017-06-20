package com.droiders.camera2apitest.cameraapi2;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import java.util.Arrays;

/**
 * Created by pedro on 22/02/17.
 */

public class Camera2ApiManager extends CameraDevice.StateCallback
    implements ImageReader.OnImageAvailableListener {

  private final String TAG = "Camera2ApiManager";

  private CameraDevice cameraDevice;
  private Surface surface;
  private CameraManager cameraManager;
  private Handler cameraHandler;

  //output
  private ImageReader imageReader;
  private int width = 640;
  private int height = 480;
  private int fps = 30;
  private int imageFormat = ImageFormat.YUV_420_888;

  public Camera2ApiManager(Surface surface, Context context) {
    this.surface = surface;
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
  }

  public void setSize(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public void setFps(int fps) {
    this.fps = fps;
  }

  public void setImageFormat(int imageFormat){
    this.imageFormat = imageFormat;
  }

  private void startPreview(CameraDevice cameraDevice) {
    try {
      cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
          new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
              try {
                cameraCaptureSession.setRepeatingBurst(
                    Arrays.asList(createCaptureRequest(), drawPreview()), null, cameraHandler);
              } catch (CameraAccessException e) {
                e.printStackTrace();
              }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
              cameraCaptureSession.close();
              Log.e(TAG, "configuration failed");
            }
          }, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  private CaptureRequest drawPreview() {
    try {
      CaptureRequest.Builder captureRequestBuilder =
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      captureRequestBuilder.addTarget(surface);
      captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
      return captureRequestBuilder.build();
    } catch (CameraAccessException e) {
      e.printStackTrace();
      return null;
    }
  }

  private CaptureRequest createCaptureRequest() {
    try {
      CaptureRequest.Builder builder =
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      builder.addTarget(imageReader.getSurface());
      return builder.build();
    } catch (CameraAccessException e) {
      Log.e(TAG, e.getMessage());
      return null;
    }
  }

  public void openCamera() {
    openCameraId(0);
  }

  public void openCameraId(Integer cameraId) {
    HandlerThread cameraHandlerThread = new HandlerThread(TAG + " Id = " + cameraId);
    cameraHandlerThread.start();
    cameraHandler = new Handler(cameraHandlerThread.getLooper());
    try {
      imageReader = ImageReader.newInstance(width, height, imageFormat, fps);
      imageReader.setOnImageAvailableListener(this, cameraHandler);
      cameraManager.openCamera(cameraId.toString(), this, cameraHandler);
    } catch (CameraAccessException | SecurityException e) {
      e.printStackTrace();
    }
  }

  public void openCameraFront() {
    try {
      if (cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.LENS_FACING)
          == CameraCharacteristics.LENS_FACING_FRONT) {
        openCameraId(0);
      } else {
        openCameraId(1);
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  public void openCameraBack() {
    try {
      if (cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.LENS_FACING)
          == CameraCharacteristics.LENS_FACING_BACK) {
        openCameraId(0);
      } else {
        openCameraId(1);
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  public void switchCamera() {
    if (cameraDevice != null) {
      int cameraId = Integer.parseInt(cameraDevice.getId()) == 1 ? 0 : 1;
      closeCamera();
      openCameraId(cameraId);
    }
  }

  public void closeCamera() {
    if (cameraDevice != null) {
      cameraDevice.close();
      cameraDevice = null;
    }
    if (imageReader != null) {
      imageReader.close();
      imageReader = null;
    }
    cameraHandler.getLooper().quitSafely();
  }

  @Override
  public void onOpened(@NonNull CameraDevice cameraDevice) {
    this.cameraDevice = cameraDevice;
    startPreview(cameraDevice);
    Log.i(TAG, "camera opened");
  }

  @Override
  public void onDisconnected(@NonNull CameraDevice cameraDevice) {
    cameraDevice.close();
    Log.i(TAG, "camera disconnected");
  }

  @Override
  public void onError(@NonNull CameraDevice cameraDevice, int i) {
    cameraDevice.close();
    Log.e(TAG, "open failed");
  }

  @Override
  public void onImageAvailable(ImageReader imageReader) {
    Log.i(TAG, "new frame");
    Image image = imageReader.acquireLatestImage();
    image.close();
  }
}
