package com.droiders.camera2apitest.samsung;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.camera.SCamera;
import com.samsung.android.sdk.camera.SCameraCaptureSession;
import com.samsung.android.sdk.camera.SCameraCharacteristics;
import com.samsung.android.sdk.camera.SCameraDevice;
import com.samsung.android.sdk.camera.SCameraManager;
import com.samsung.android.sdk.camera.SCaptureRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by pedro on 22/02/17.
 */

public class SamsungApiManager {

  private SCamera mSCamera;
  private SCameraManager mSCameraManager;
  private SCameraDevice mSCameraDevice;
  private SCameraCaptureSession mSCameraSession;
  private SCameraCharacteristics mCharacteristics;
  private SCaptureRequest.Builder mPreviewBuilder;
  /**
   * Current Preview Size.
   */
  private Size mPreviewSize;
  /**
   * Current Picture Size.
   */
  private Size mPictureSize;
  /**
   * ID of the current {@link com.samsung.android.sdk.camera.SCameraDevice}.
   */
  private String mCameraId;
  /**
   * for camera preview.
   */
  private TextureView mTextureView;
  /**
   * A camera related listener/callback will be posted in this handler.
   */
  private Handler mBackgroundHandler;
  private HandlerThread mBackgroundHandlerThread;
  /**
   * A image saving worker Runnable will be posted to this handler.
   */
  private HandlerThread mImageSavingHandlerThread;
  /**
   * An orientation listener for jpeg orientation
   */
  private int mLastOrientation = 0;
  private Semaphore mCameraOpenCloseLock = new Semaphore(1);
  /**
   * Lens facing. Camera with this facing will be opened
   */
  private int mLensFacing;
  private List<Integer> mLensFacingList;
  private Activity context;

  public SamsungApiManager(Activity context, TextureView textureView, int cameraId) {
    this.context = context;
    this.mTextureView = textureView;
    startBackgroundThread();

    // initialize SCamera
    mSCamera = new SCamera();
    try {
      mSCamera.initialize(context);
    } catch (SsdkUnsupportedException e) {
      e.printStackTrace();
      return;
    }
    createUI();
    checkRequiredFeatures(cameraId);
    openCamera(mLensFacing);
  }

  private void checkRequiredFeatures(int cameraId) {
    try {
      // Find available lens facing value for this device
      Set<Integer> lensFacings = new HashSet<>();
      for (String id : mSCamera.getSCameraManager().getCameraIdList()) {
        SCameraCharacteristics cameraCharacteristics =
            mSCamera.getSCameraManager().getCameraCharacteristics(id);
        lensFacings.add(cameraCharacteristics.get(SCameraCharacteristics.LENS_FACING));
      }
      mLensFacingList = new ArrayList<>(lensFacings);

      mLensFacing = mLensFacingList.get(cameraId);

      setDefaultJpegSize(mSCamera.getSCameraManager(), mLensFacing);
    } catch (CameraAccessException e) {
      e.printStackTrace();
      Log.e("Camera", "Cannot access the camera.", e);
    }
  }

  /**
   * Starts back ground thread that callback from camera will posted.
   */
  private void startBackgroundThread() {
    mBackgroundHandlerThread = new HandlerThread("Background Thread");
    mBackgroundHandlerThread.start();
    mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());

    mImageSavingHandlerThread = new HandlerThread("Saving Thread");
    mImageSavingHandlerThread.start();
  }

  /**
   * Starts a preview.
   */
  synchronized private void startPreview() {
    if (mSCameraSession == null) return;
    try {
      // Starts displaying the preview.
      mSCameraSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
    } catch (CameraAccessException e) {
      Log.e("Error", e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Stops back ground thread.
   */
  private void stopBackgroundThread() {
    if (mBackgroundHandlerThread != null) {
      mBackgroundHandlerThread.quitSafely();
      try {
        mBackgroundHandlerThread.join();
        mBackgroundHandlerThread = null;
        mBackgroundHandler = null;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    if (mImageSavingHandlerThread != null) {
      mImageSavingHandlerThread.quitSafely();
      try {
        mImageSavingHandlerThread.join();
        mImageSavingHandlerThread = null;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private void setDefaultJpegSize(SCameraManager manager, int facing) {
    try {
      for (String id : manager.getCameraIdList()) {
        SCameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(id);
        if (cameraCharacteristics.get(SCameraCharacteristics.LENS_FACING) == facing) {
          List<Size> jpegSizeList = new ArrayList<>();

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
              && cameraCharacteristics.get(SCameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
              .getHighResolutionOutputSizes(ImageFormat.JPEG) != null) {
            jpegSizeList.addAll(Arrays.asList(
                cameraCharacteristics.get(SCameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getHighResolutionOutputSizes(ImageFormat.JPEG)));
          }
          jpegSizeList.addAll(Arrays.asList(
              cameraCharacteristics.get(SCameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                  .getOutputSizes(ImageFormat.JPEG)));
          mPictureSize = jpegSizeList.get(0);
        }
      }
    } catch (CameraAccessException e) {
      Log.e("Camera", "Cannot access the camera.", e);
    }
  }

  /**
   * Opens a {@link com.samsung.android.sdk.camera.SCameraDevice}.
   */
  synchronized public void openCamera(int facing) {
    try {
      if (!mCameraOpenCloseLock.tryAcquire(3000, TimeUnit.MILLISECONDS)) {
        Log.e("Error", "time out");
      }

      mSCameraManager = mSCamera.getSCameraManager();

      mCameraId = null;

      // Find camera device that facing to given facing parameter.
      for (String id : mSCamera.getSCameraManager().getCameraIdList()) {
        SCameraCharacteristics cameraCharacteristics =
            mSCamera.getSCameraManager().getCameraCharacteristics(id);
        if (cameraCharacteristics.get(SCameraCharacteristics.LENS_FACING) == facing) {
          mCameraId = id;
          break;
        }
      }

      if (mCameraId == null) {
        Log.e("Error", "no id found");
        return;
      }

      // acquires camera characteristics
      mCharacteristics = mSCamera.getSCameraManager().getCameraCharacteristics(mCameraId);

      StreamConfigurationMap streamConfigurationMap =
          mCharacteristics.get(SCameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      // Acquires supported preview size list that supports SurfaceTexture
      mPreviewSize =
          getOptimalPreviewSize(streamConfigurationMap.getOutputSizes(SurfaceTexture.class),
              (double) mPictureSize.getWidth() / mPictureSize.getHeight());

      Log.d("Camera",
          "Picture Size: " + mPictureSize.toString() + " Preview Size: " + mPreviewSize.toString());

      if (contains(mCharacteristics.get(SCameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
          SCameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
        List<Size> rawSizeList = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && streamConfigurationMap.getHighResolutionOutputSizes(ImageFormat.RAW_SENSOR)
            != null) {
          rawSizeList.addAll(Arrays.asList(
              streamConfigurationMap.getHighResolutionOutputSizes(ImageFormat.RAW_SENSOR)));
        }
        rawSizeList.addAll(
            Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.RAW_SENSOR)));
      }

      // Opening the camera device here
      mSCameraManager.openCamera(mCameraId, new SCameraDevice.StateCallback() {
        @Override
        public void onDisconnected(SCameraDevice sCameraDevice) {
          mCameraOpenCloseLock.release();
        }

        @Override
        public void onError(SCameraDevice sCameraDevice, int i) {
          mCameraOpenCloseLock.release();
        }

        public void onOpened(SCameraDevice sCameraDevice) {
          mCameraOpenCloseLock.release();
          mSCameraDevice = sCameraDevice;
          createPreviewSession();
        }
      }, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
      Log.e("Camera", "Cannot open the camera.", e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  /**
   * Closes a camera and release resources.
   */
  synchronized private void closeCamera() {
    try {
      mCameraOpenCloseLock.acquire();

      if (mSCameraSession != null) {
        mSCameraSession.close();
        mSCameraSession = null;
      }

      if (mSCameraDevice != null) {
        mSCameraDevice.close();
        mSCameraDevice = null;
      }

      mSCameraManager = null;
    } catch (InterruptedException e) {
      Log.e("Camera", "Interrupted while trying to lock camera closing.", e);
    } finally {
      mCameraOpenCloseLock.release();
    }
  }

  /**
   * Configures requires transform {@link android.graphics.Matrix} to TextureView.
   */
  private void configureTransform(int viewWidth, int viewHeight) {
    if (null == mTextureView || null == mPreviewSize) {
      return;
    }

    int rotation = context.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale = Math.max((float) viewHeight / mPreviewSize.getHeight(),
          (float) viewWidth / mPreviewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else {
      matrix.postRotate(90 * rotation, centerX, centerY);
    }

    mTextureView.setTransform(matrix);
    mTextureView.getSurfaceTexture()
        .setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
  }

  private boolean contains(final int[] array, final int key) {
    for (final int i : array) {
      if (i == key) {
        return true;
      }
    }
    return false;
  }

  /**
   * Create a {@link com.samsung.android.sdk.camera.SCameraCaptureSession} for preview.
   */
  synchronized private void createPreviewSession() {

    if (null == mSCamera
        || null == mSCameraDevice
        || null == mSCameraManager
        || null == mPreviewSize
        || !mTextureView.isAvailable()) {
      return;
    }

    try {
      SurfaceTexture texture = mTextureView.getSurfaceTexture();

      // Set default buffer size to camera preview size.
      texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

      Surface surface = new Surface(texture);

      // Creates SCaptureRequest.Builder for preview with output target.
      mPreviewBuilder = mSCameraDevice.createCaptureRequest(SCameraDevice.TEMPLATE_PREVIEW);
      mPreviewBuilder.addTarget(surface);

      // Creates SCaptureRequest.Builder for still capture with output target.
      //mCaptureBuilder = mSCameraDevice.createCaptureRequest(SCameraDevice.TEMPLATE_STILL_CAPTURE);

      // Creates a SCameraCaptureSession here.
      List<Surface> outputSurface = new ArrayList<Surface>();
      outputSurface.add(surface);
      //outputSurface.add(mJpegReader.getSurface());

      mSCameraDevice.createCaptureSession(outputSurface, new SCameraCaptureSession.StateCallback() {
        @Override
        public void onConfigureFailed(SCameraCaptureSession sCameraCaptureSession) {

        }

        @Override
        public void onConfigured(SCameraCaptureSession sCameraCaptureSession) {
          mSCameraSession = sCameraCaptureSession;
          startPreview();
        }
      }, mBackgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Prepares an UI, like button, dialog, etc.
   */
  private void createUI() {
    // Set SurfaceTextureListener that handle life cycle of TextureView
    mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
      @Override
      public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        // "onSurfaceTextureAvailable" is called, which means that SCameraCaptureSession is not created.
        // We need to configure transform for TextureView and crate SCameraCaptureSession.
        configureTransform(width, height);
        createPreviewSession();
      }

      @Override
      public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
      }

      @Override
      public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // SurfaceTexture size changed, we need to configure transform for TextureView, again.
        configureTransform(width, height);
      }

      @Override
      public void onSurfaceTextureUpdated(SurfaceTexture surface) {
      }
    });
  }

  /**
   * Returns required orientation that the jpeg picture needs to be rotated to be displayed upright.
   */
  private int getJpegOrientation() {
    int degrees = mLastOrientation;

    if (mCharacteristics.get(SCameraCharacteristics.LENS_FACING)
        == SCameraCharacteristics.LENS_FACING_FRONT) {
      degrees = -degrees;
    }

    return (mCharacteristics.get(SCameraCharacteristics.SENSOR_ORIENTATION) + degrees + 360) % 360;
  }

  /**
   * find optimal preview size for given targetRatio
   */
  private Size getOptimalPreviewSize(Size[] sizes, double targetRatio) {
    final double ASPECT_TOLERANCE = 0.001;

    Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;

    Display display = context.getWindowManager().getDefaultDisplay();
    Point displaySize = new Point();
    display.getSize(displaySize);
    int targetHeight = Math.min(displaySize.y, displaySize.x);

    // Try to find an size match aspect ratio and size
    for (Size size : sizes) {
      double ratio = (double) size.getWidth() / size.getHeight();
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.getHeight() - targetHeight);
      }
    }

    // Cannot find the one match the aspect ratio. This should not happen.
    // Ignore the requirement.
    if (optimalSize == null) {
      Log.w("Camera", "No preview size match the aspect ratio");
      minDiff = Double.MAX_VALUE;
      for (Size size : sizes) {
        if (Math.abs(size.getHeight() - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.getHeight() - targetHeight);
        }
      }
    }

    return optimalSize;
  }
}
