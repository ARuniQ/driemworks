package com.driemworks.simplecv.activities;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.OpenCVLoader;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import com.driemworks.ar.MonocularVisualOdometry.services.impl.FeatureServiceImpl;
import com.driemworks.ar.dto.CameraPoseDTO;
import com.driemworks.ar.dto.FeatureWrapper;
import com.driemworks.ar.dto.SequentialFrameFeatures;
import com.driemworks.ar.utils.CvUtils;
import com.driemworks.common.factories.BaseLoaderCallbackFactory;
import com.driemworks.common.utils.ImageConversionUtils;
import com.driemworks.simplecv.R;
import com.driemworks.simplecv.services.permission.impl.CameraPermissionServiceImpl;
import com.driemworks.common.views.CustomSurfaceView;
import com.driemworks.simplecv.utils.DisplayUtils;

/**
 * The Monocular Visual Odometry testing activity
 * @author Tony
 */
public class VOActivity extends Activity implements CvCameraViewListener2 {

    /**
     * The camera pose dto - the current pose of the camera
     */
    private CameraPoseDTO currentPose;

    /* load the opencv lib */
    static {
        System.loadLibrary("opencv_java3");
    }

    /** The service to request permission to use camera at runtime */
    private CameraPermissionServiceImpl cameraPermissionService;

    /** The tag used for logging */
    private static final String TAG = "VOActivity: ";

    /** The input camera frame in RGBA format */
    private Mat mRgba;
    private Mat gray;
    private Mat intermediateMat;
    private Mat output;

    /** The customSurfaceView surface view */
    private CustomSurfaceView customSurfaceView;
    /** The width of the device screen */
    private int screenWidth;

    /** The height of the device screen */
    private int screenHeight;

    /** The base loader callback */
    private BaseLoaderCallback mLoaderCallback;

    /** The feature service */
    private FeatureServiceImpl featureService;

    /** The previous feature wrapper */
    private FeatureWrapper previousWrapper = null;

    /** The current feature wrapper */
    private FeatureWrapper wrapper = null;

    // TODO calculate intrinsic params of camera (camera calibration)
    // the values provided are not actual values
    /**
     * The focal length
     */
    private static final float focal = 718.8560f;
    /**
     * The principal point
     */
    private static final Point pp = new Point(607.1928, 185.2157);

    /** The current sequential frame features */
    private SequentialFrameFeatures sequentialFrameFeatures;

    /** The currently detected points */
    private MatOfPoint2f currentPoints;
    /** The previously detected points */
    private MatOfPoint2f previousPoints;
    /** The essential matrix */
    private Mat essentialMat;
    /** The rotation matrix */
    private Mat rotationMatrix;
    /** The translation matrix */
    private Mat translationMatrix;
    /** The status matrix */
    private MatOfByte status;
    /** The error matrix */
    private MatOfFloat err;
    /** The mask matrix */
    private Mat mask;

    /** The default constructor */
    public VOActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

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

        setContentView(R.layout.main_surface_view);

        // init the matrices
        essentialMat = new Mat(3, 3, CvType.CV_64FC1);
        rotationMatrix = new Mat(3, 3, CvType.CV_64FC1);
        translationMatrix = new Mat(3, 1, CvType.CV_64FC1);

        currentPose = new CameraPoseDTO();

        currentPoints = new MatOfPoint2f();
        previousPoints = new MatOfPoint2f();

        // setup the surface view
        customSurfaceView = (CustomSurfaceView) findViewById(R.id.main_surface_view);
        customSurfaceView.setCvCameraViewListener(this);
        customSurfaceView.setMaxFrameSize(320, 240);

        // init base loader callback
        mLoaderCallback = BaseLoaderCallbackFactory.getBaseLoaderCallback(
                this, customSurfaceView);

        // init service(s)
        featureService = new FeatureServiceImpl();
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
        customSurfaceView.setMaxFrameSize(320, 240);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        customSurfaceView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "camera view started");
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        gray = new Mat(height, width, CvType.CV_8UC1);
        intermediateMat = new Mat(height, width, CvType.CV_8UC3);
        output = new Mat(height, width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "camera view stopped");
        mRgba.release();
        gray.release();
        intermediateMat.release();
        output.release();
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Log.d(TAG, "START - onCameraFrame");
        long startTime = System.currentTimeMillis();
        // get the image from the input frame
        mRgba = inputFrame.rgba();
        gray = inputFrame.gray();
        output = mRgba.clone();

        Imgproc.cvtColor(mRgba, intermediateMat, Imgproc.COLOR_RGBA2RGB);

        wrapper = featureService.featureDetection(mRgba);

        if (true) {
            Features2d.drawKeypoints(intermediateMat, wrapper.getKeyPoints(), intermediateMat);
            Imgproc.cvtColor(intermediateMat, output, Imgproc.COLOR_RGB2RGBA);
        }

        // track into next image
        if (previousWrapper != null && previousWrapper.getFrame() != null
                && !previousWrapper.getKeyPoints().empty()) {
            // I think these can all be initialized in the onCreate function, as well as be local to the class
            status = new MatOfByte();
            err = new MatOfFloat();
            mask = new Mat();

            // track features into next image, filters out bad points
            sequentialFrameFeatures = featureService.featureTracking(
                    previousWrapper.getFrameAsGrayscale(), gray,
                    previousWrapper.getKeyPoints(), status, err);

            // convert the points
            currentPoints = ImageConversionUtils.convertListToMatOfPoint2f(
                    sequentialFrameFeatures.getCurrentFrameFeaturePoints());
            previousPoints = ImageConversionUtils.convertListToMatOfPoint2f(
                    sequentialFrameFeatures.getPreviousFrameFeaturePoints());

            // calculate the essential matrix
            long startTimeNew = System.currentTimeMillis();
            Log.d(TAG, "START - findEssentialMat - numCurrentPoints: "
                    + currentPoints.size() + " numPreviousPoints: " + previousPoints.size());

            // RANSAC was far too costly...using LMEDS
            essentialMat = Calib3d.findEssentialMat(currentPoints, previousPoints,
                    focal, pp, Calib3d.LMEDS, 0.75 , 1, mask);
            Log.d(TAG, "END - findEssentialMat - time elapsed "
                    + (System.currentTimeMillis() - startTimeNew) + " ms");


            // calculate rotation and translation matrices
            if (!essentialMat.empty() && essentialMat.rows() == 3 &&
                    essentialMat.cols() == 3 && essentialMat.isContinuous()) {
                Log.d(TAG, "START - recoverPose");
                startTimeNew = System.currentTimeMillis();
                Calib3d.recoverPose(essentialMat, currentPoints,
                        previousPoints, rotationMatrix, translationMatrix, focal, pp, mask);
                Log.d(TAG, "END - recoverPose - time elapsed " +
                        (System.currentTimeMillis() - startTimeNew) + " ms");
                Log.d(TAG, "Calculated rotation matrix: " + rotationMatrix.toString());
                if (!translationMatrix.empty()) {
                    Point deltaPos = calculateTrajectory(translationMatrix);
                    deltaPos.x += 50;
                    deltaPos.y += 50;
//                    Imgproc.circle(traj, pos, 5, new Scalar(255, 0, 0), 2);
                    Log.d(TAG, "Calculated deltaPos: in orthogonal plane " + deltaPos.toString());
                }

                if (!translationMatrix.empty() && !rotationMatrix.empty()) {
//                    currentPose.update(translationMatrix, rotationMatrix);
                    Log.d(TAG, currentPose.toString());
                    Log.d(TAG, "#= translationMatrix " + CvUtils.printMat(translationMatrix));
                    Log.d(TAG, "#= rotationMatrix " + CvUtils.printMat(rotationMatrix));
                } else {
                    Log.d(TAG, "translation and/or rotation matrix was empty.");
                }

            }

            mask.release();
            err.release();
            status.release();
        }

        if (!wrapper.getFrame().empty()) {
            Log.d(TAG, "Cloning feature wrapper");
            previousWrapper = FeatureWrapper.clone(wrapper);
        }

        Log.d(TAG, "END - onCameraFrame - time elapsed: " +
                (System.currentTimeMillis() - startTime) + " ms");
        return output;
    }

    /**
     * Calculate the change in trajectory in the XY plane
     * @param translationMatrix the translation matrix
     * @return The change in trajectory as a vector
     */
    private Point calculateTrajectory(Mat translationMatrix) {
        return new Point(translationMatrix.get(0, 0)[0],
                translationMatrix.get(2, 0)[0]);
    }

    private void updatePose() {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == CameraPermissionServiceImpl.REQUEST_CODE) {
            cameraPermissionService.handleResponse(requestCode, permissions, grantResults);
        }
    }
}
