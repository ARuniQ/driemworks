package com.driemworks.simplecv.activities;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;

import com.driemworks.ar.imageProcessing.ColorBlobDetector;
import com.driemworks.ar.services.SurfaceDetectionService;
import com.driemworks.common.dto.SurfaceDataDTO;
import com.driemworks.common.factories.BaseLoaderCallbackFactory;
import com.driemworks.common.utils.TagUtils;
import com.driemworks.simplecv.R;
import com.driemworks.common.enums.Resolution;
import com.driemworks.simplecv.layout.impl.ConfigurationLayoutManager;
import com.driemworks.simplecv.services.permission.impl.LocationPermissionServiceImpl;
import com.driemworks.simplecv.services.permission.impl.CameraPermissionServiceImpl;
import com.driemworks.common.views.CustomSurfaceView;
import com.driemworks.common.utils.DisplayUtils;

import org.opencv.core.Size;

/**
 * @author Tony
 */
public class ConfigurationActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {

    /** load the opencv lib */
    static {
        System.loadLibrary("opencv_java3");
    }

    /** The service to request permission to use camera at runtime */
    private CameraPermissionServiceImpl cameraPermissionService;

    /** The service to request permission to get location data at runtime */
    private LocationPermissionServiceImpl locationPermissionService;

    /** The layout manager for this activity */
    private ConfigurationLayoutManager layoutManager;

    /** The tag used for logging */
    private final String TAG = TagUtils.getTag(this.getClass());

    /** The input camera frame in RGBA format */
    private Mat mRgba;

    /** The customSurfaceView surface view */
    private CustomSurfaceView customSurfaceView;

    /** The color of the detected blob in HSV format */
    private Scalar mBlobColorHsv;

    /** The color blob detector */
    private ColorBlobDetector mDetector;

    /** The color spectrum */
    private Mat mSpectrum;

    /** Boolean flag to tell if the color is or isn't selected in the detector */
    private boolean mIsColorSelected = false;

    /** The size of the spectrum */
    private Size SPECTRUM_SIZE;

    /** The surface Detection service */
    private SurfaceDetectionService surfaceDetector;

    /** The width of the device screen */
    private int screenWidth;

    /** The height of the device screen */
    private int screenHeight;

    /** The base loader callback */
    private BaseLoaderCallback mLoaderCallback;

    /** The default constructor */
    public ConfigurationActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        // get screen dimensions
        android.graphics.Point size = DisplayUtils.getScreenSize(this);
        screenWidth = size.x;
        screenHeight = size.y;

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpvenCVLoader", "OvenCVLoader successful: false");
        } else {
            Log.d("OpenCVLoader", "OpenCVLoader successful");
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraPermissionService = new CameraPermissionServiceImpl(this);

        locationPermissionService = new LocationPermissionServiceImpl(this);

        setContentView(R.layout.main_surface_view);

        customSurfaceView = (CustomSurfaceView) findViewById(R.id.main_surface_view);
        customSurfaceView.setCvCameraViewListener(this);
        customSurfaceView.setOnTouchListener(ConfigurationActivity.this);
        customSurfaceView.setMaxFrameSize(800, 480);

        layoutManager = ConfigurationLayoutManager.getInstance();
        layoutManager.setActivity(this);
        layoutManager.setup(ConfigurationLayoutManager.CONFIG_BUTTON, findViewById(R.id.mode_btn));

        mLoaderCallback = BaseLoaderCallbackFactory.getBaseLoaderCallback(this, customSurfaceView, Resolution.RES_STANDARD);
    }



    @Override
    public void onPause() {
        Log.d(TAG, "Called onPause");
        super.onPause();
        if (customSurfaceView != null) {
            customSurfaceView.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        customSurfaceView.setMaxFrameSize(Resolution.RES_STANDARD.getWidth(), Resolution.RES_STANDARD.getHeight());
    }

    public void onDestroy() {
        super.onDestroy();
        customSurfaceView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "camera view started");
        mRgba = new Mat();
        initFields(width, height);
    }

    /**
     * @param width
     * @param height
     */
    private void initFields(int width, int height) {
        surfaceDetector = new SurfaceDetectionService(new Scalar(255, 255, 255, 255),
                new Scalar(222, 040, 255), new Mat(), new Size(200, 64), null);
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
    }

    public void onCameraViewStopped() {
        Log.d(TAG, "camera view stopped");
        mRgba.release();
    }

    /**
     * The onTouch method -> samples the color of the touch region
     * @param v
     * @param event
     * @return
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // the value used to bound the size of the area to be sampled
        int sizeThreshold = 10;

        Point correctedCoordinate = DisplayUtils.correctCoordinate(event, screenWidth, screenHeight);
        Rect touchedRect = new Rect((int)correctedCoordinate.x, (int)correctedCoordinate.y, sizeThreshold, sizeThreshold);
        if (null == touchedRect) {
            return false;
        }

        // get the rectangle around the point that was touched
        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        // format to hsv
        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width * touchedRect.height;

        for (int i = 0; i < mBlobColorHsv.val.length; i++) {
            mBlobColorHsv.val[i] /= pointCount;
        }

        mDetector = surfaceDetector.getColorBlobDetector();
        mDetector.setHsvColor(mBlobColorHsv);
        surfaceDetector.setColorBlobDetector(mDetector);
        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;
        Log.d(TAG, "color has been set");

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false;
    }

    /**
     *
     * @param inputFrame
     * @return
     */
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        // get the images from the input frame
        mRgba = inputFrame.rgba();
            // do nothing if no color has been selected
            if (!mIsColorSelected) {
                Log.d(TAG, "No color selected, return input image");
                return mRgba;
            }

            SurfaceDataDTO surfaceData = surfaceDetector.detect(mRgba, 0, true);
            return surfaceData.getmRgba();

    }

    /**
     * Handle the results of a permissions request
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CameraPermissionServiceImpl.REQUEST_CODE) {
            cameraPermissionService.handleResponse(requestCode, permissions, grantResults);
        } else if (requestCode == LocationPermissionServiceImpl.REQUEST_CODE) {
            locationPermissionService.handleResponse(requestCode, permissions, grantResults);
        }
    }

    public Scalar getmBlobColorHsv()  {
        return mBlobColorHsv;
    }

}
