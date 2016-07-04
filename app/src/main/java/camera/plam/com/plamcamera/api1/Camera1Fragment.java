package camera.plam.com.plamcamera.api1;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.io.ByteArrayOutputStream;

import camera.plam.com.plamcamera.R;
import camera.plam.com.plamcamera.Utils.CameraUtils;
import camera.plam.com.plamcamera.listener.ICameraIO;

/**
 * Created by PunK _|_ RuLz on 01/07/16.
 */
public class Camera1Fragment extends Fragment {

    public static final String LOG_TAG = Camera1Fragment.class.getSimpleName();

    private Camera mCamera;
    private CameraPreview mCameraView;
    private FrameLayout mCameraPreviewContainer;
    private RelativeLayout mImagePreviewLayout;
    private RelativeLayout mCameraActionsLayout;
    private ImageButton mCameraFlashImageButton;
    private ImageButton mCameraCaptureImageButton;
    private ImageButton mSwitchCameraImageButton;
    private ImageView mCapturedImageView;

    private ICameraIO mCameraIOListener;
    private OrientationEventListener mOrientationListener;

    private CameraUtils.CameraFlashState mCameraFlashState;
    private Bitmap mBitmap;
    private CameraUtils.ScreenOrientation mCurrentOrientation;

    public static Camera1Fragment getCamera1FragmentInstance() {
        Camera1Fragment fragment = new Camera1Fragment();
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (null != mCamera) {
            mCamera.startPreview();
        } else {
            if (CameraUtils.mCurrentCameraId == -1) {
                startCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
            } else {
                startCamera(CameraUtils.mCurrentCameraId);
            }
        }
        mOrientationListener = new OrientationEventListener(getActivity()) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (null != CameraUtils.getOrientation(orientation)) {
                    mCurrentOrientation = CameraUtils.getOrientation(orientation);
                }
            }
        };
        mOrientationListener.enable();
    }

    @Override
    public void onStop() {
        super.onStop();
        removeViewsCallbacks();
        releaseCamera();
        mOrientationListener.disable();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.camera_api1_layout, container, false);

        //reference views
        mCameraPreviewContainer = (FrameLayout) view.findViewById(R.id.camera_preview_container);
        mCameraFlashImageButton = (ImageButton) view.findViewById(R.id.camera_flash_image_button);
        mCameraCaptureImageButton = (ImageButton) view.findViewById(R.id.capture_camera_image_button);
        mSwitchCameraImageButton = (ImageButton) view.findViewById(R.id.switch_camera_image_button);
        mCapturedImageView = (ImageView) view.findViewById(R.id.captured_image_view);
        mCameraActionsLayout = (RelativeLayout) view.findViewById(R.id.camera_action_layout);
        mImagePreviewLayout = (RelativeLayout) view.findViewById(R.id.image_preview_layout);
        mImagePreviewLayout.setVisibility(View.GONE);

        //click listeners
        mCapturedImageView.setOnClickListener(capturedImageClicked);
        mCameraFlashImageButton.setOnClickListener(cameraFlashListener);
        mCameraCaptureImageButton.setOnClickListener(captureImageListener);
        mSwitchCameraImageButton.setOnClickListener(switchCameraListener);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    View.OnClickListener cameraFlashListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mCameraFlashState = mCameraFlashState.getNext();
            switch (mCameraFlashState) {
                case ON: {
                    setCameraFlashOn();
                    break;
                }
                case OFF: {
                    setCameraFlashOff();
                    break;
                }
            }
        }
    };

    View.OnClickListener switchCameraListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (CameraUtils.mCurrentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                startFrontCamera();
            } else {
                startBackCamera();
            }
        }
    };

    View.OnClickListener captureImageListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            manuallyTurnOnFlash();
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if (null != data) {
                        new LoadImageTask(mCurrentOrientation).
                                executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, data);
                    } else {
                        restartCamera();
                    }
                    mCamera.setPreviewCallback(null);
                    manuallyTurnOffFlash();
                }
            });
        }
    };

    private void startBackCamera() {
        mSwitchCameraImageButton.setImageDrawable(ContextCompat.
                getDrawable(getActivity(), R.drawable.ic_camera_front_white_24dp));
        startCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    private void startFrontCamera() {
        mSwitchCameraImageButton.setImageDrawable(ContextCompat.
                getDrawable(getActivity(), R.drawable.ic_camera_rear_white_24dp));
        startCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    private void manuallyTurnOffFlash() {
        if (mCameraFlashState.ordinal() == CameraUtils.CameraFlashState.ON.ordinal()) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(parameters);
        }
    }

    private void manuallyTurnOnFlash() {
        if (mCameraFlashState.ordinal() == CameraUtils.CameraFlashState.ON.ordinal()) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(parameters);
        }
    }

    View.OnClickListener capturedImageClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //do nothing here
        }
    };

    private void setCameraFlashOff() {
        mCameraFlashImageButton.setImageDrawable(
                ContextCompat.getDrawable(getActivity(), R.drawable.ic_flash_off_white_24dp));
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        mCamera.setParameters(params);
    }

    private void setCameraFlashOn() {
        mCameraFlashImageButton.setImageDrawable(
                ContextCompat.getDrawable(getActivity(), R.drawable.ic_flash_on_white_24dp));
        Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
        mCamera.setParameters(params);
    }

    public void setCameraIOListener(ICameraIO listener) {
        this.mCameraIOListener = listener;
    }

    private void removeViewsCallbacks() {
        mCameraPreviewContainer.removeAllViews();
        if (null != mCameraView) {
            mCameraView.getHolder().removeCallback(mCameraView);
            mCameraView = null;
        }
    }

    private void restartCamera() {
        mCamera.startPreview();
    }

    private void recycleBitmap() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void startCamera(int cameraId) {
        if (null != mCamera) {
            releaseCamera();
        }
        if (null != mCameraView) {
            mCameraPreviewContainer.removeAllViews();
            mCameraView = null;
        }
        mCamera = CameraUtils.getCameraInstance(getActivity(), cameraId);
        if (null != mCamera) {
            if (null == mCameraView && null != mCamera) {
                mCameraView = new CameraPreview(getActivity(), mCamera);
                mCameraPreviewContainer.addView(mCameraView);
            }
            if (CameraUtils.isCameraFlashAvailable()) {
                mCameraFlashImageButton.setVisibility(View.VISIBLE);
            } else {
                mCameraFlashImageButton.setVisibility(View.GONE);
            }
            mCameraFlashState = CameraUtils.CameraFlashState.OFF;
        } else {
            if (null != mCameraIOListener) {
                mCameraIOListener.onCameraOpenFailure();
            }
        }
    }

    public boolean isImagePreviewVisible() {
        return View.VISIBLE == mImagePreviewLayout.getVisibility();
    }

    public void resumeCamera() {
        mImagePreviewLayout.setVisibility(View.GONE);
        mCameraActionsLayout.setVisibility(View.VISIBLE);
        mCameraPreviewContainer.setVisibility(View.VISIBLE);
        recycleBitmap();
        restartCamera();
    }

    private class LoadImageTask extends AsyncTask<byte[], Void, Boolean> {

        CameraUtils.ScreenOrientation screenOrientation;
        Camera.CameraInfo info;

        public LoadImageTask(CameraUtils.ScreenOrientation orientation) {
            this.screenOrientation = orientation;
            info = new Camera.CameraInfo();
            Camera.getCameraInfo(CameraUtils.mCurrentCameraId, info);
        }

        @Override
        protected Boolean doInBackground(byte[]... params) {
            byte[] data = params[0];
            Camera.Parameters parameters = mCamera.getParameters();
            int imageFormat = parameters.getPreviewFormat();
            if (ImageFormat.NV21 == imageFormat) {
                Rect rect = new Rect(0, 0, parameters.getPreviewSize().width,
                        parameters.getPreviewSize().height);
                ByteArrayOutputStream BAOS = new ByteArrayOutputStream();
                YuvImage img = new YuvImage(data, ImageFormat.NV21,
                        parameters.getPreviewSize().width, parameters.getPreviewSize().height, null);
                img.compressToJpeg(rect, 100, BAOS);
                byte[] bytes = BAOS.toByteArray();
                mBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                Matrix matrix = new Matrix();

                int rotationValue = getImageRotationValue();

                if (rotationValue != 0) {
                    matrix.postRotate(rotationValue);
                    mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(),
                            mBitmap.getHeight(), matrix, false);
                }
                return true;
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            if (aBoolean) {
                Log.d(LOG_TAG, "onPostExecute");
                mCameraPreviewContainer.setVisibility(View.GONE);
                mCameraActionsLayout.setVisibility(View.GONE);
                mImagePreviewLayout.setVisibility(View.VISIBLE);
                mCapturedImageView.setImageBitmap(mBitmap);
            }
        }

        private int getImageRotationValue() {
            int rotationValue = 0;
            //either the application is using the back camera
            //if the application is using the front camera and it's in either portrait or upside portrait mode
            //for application using front camera and orientation is left landscape. It works fine automatically.
            if ((CameraUtils.mCurrentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) ||
                    (CameraUtils.mCurrentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT &&
                            (screenOrientation.ordinal() == CameraUtils.ScreenOrientation.PORTRAIT.ordinal() ||
                                    screenOrientation.ordinal() == CameraUtils.ScreenOrientation.PORTRAIT_UPSIDE_DOWN.ordinal()))) {

                //this is for reverse landscape for phones like Nexus 5X.
                if (info.orientation == CameraUtils.DEGREE_TWO_SEVENTY) {
                    rotationValue = -CameraUtils.DEGREE_NINETY;
                } else {
                    rotationValue = CameraUtils.DEGREE_NINETY;
                }
                switch (screenOrientation) {
                    case LEFT_LANDSCAPE: {
                        rotationValue -= CameraUtils.DEGREE_NINETY;
                        break;
                    }
                    case RIGHT_LANDSCAPE: {
                        rotationValue += CameraUtils.DEGREE_NINETY;
                        break;
                    }
                    case PORTRAIT_UPSIDE_DOWN: {
                        rotationValue += CameraUtils.DEGREE_ONE_EIGHTY;
                        break;
                    }
                }
            } else if (CameraUtils.mCurrentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT &&
                    screenOrientation.ordinal() == CameraUtils.ScreenOrientation.RIGHT_LANDSCAPE.ordinal()) {
                rotationValue += CameraUtils.DEGREE_ONE_EIGHTY;
            }
            return rotationValue;
        }
    }
}
