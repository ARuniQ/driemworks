package com.driemworks.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;

import com.driemworks.app.activities.base.AbstractOpenCVActivity;
import com.driemworks.ar.utils.ImageProcessingUtils;
import com.driemworks.app.R;
import com.driemworks.common.enums.Resolution;
import com.driemworks.app.layout.impl.LineGameActivityLayoutManager;
import com.driemworks.app.services.permission.impl.CameraPermissionServiceImpl;
import com.driemworks.common.utils.DisplayUtils;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Tony
 */
public class LineGameActivity extends AbstractOpenCVActivity implements OnTouchListener, CvCameraViewListener2 {

    /** The service to request permission to use camera at runtime */
    private CameraPermissionServiceImpl cameraPermissionService;

    /** The layout manager for this activity */
    private LineGameActivityLayoutManager layoutManager;

    private static final Resolution resolution = Resolution.RES_STANDARD;

    /** The tag used for logging */
    private static final String TAG = "LineGameActivity";

    /** The width of the device screen */
    private int screenWidth;

    /** The height of the device screen */
    private int screenHeight;

    private int currentPositionX = 100;
    private int currentPositionY = 100;

    private double touchedX;
    private double touchedY;

    /** The reference point for determining direction */
    private Point refPt = new Point(Resolution.RES_STANDARD.getWidth() - 100, Resolution.RES_STANDARD.getHeight()/2);

    private static final int VELOCITY = 10;

    /** The default constructor */
    public LineGameActivity() {
        super(R.layout.opencv_layout, R.id.main_surface_view, resolution, true);
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

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraPermissionService = new CameraPermissionServiceImpl(this);
        layoutManager = LineGameActivityLayoutManager.getInstance(this);
        layoutManager.setActivity(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        Log.d(TAG, "Called onPause");
        super.onPause();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "camera view started");
        super.onCameraViewStarted(width, height);
    }

    public void onCameraViewStopped() {
        Log.d(TAG, "camera view stopped");
        super.onCameraViewStopped();
    }

    /**
     * Calculates the coordinate on the screen corrected for resolution and screen size
     * @param event the touch event
     * @return the correct coordinates
     */
    private Point correctCoordinate(MotionEvent event) {
        double x = (event.getX() * com.driemworks.common.enums.Resolution.RES_STANDARD.getWidth()) / screenWidth;
        double y = (event.getY() * com.driemworks.common.enums.Resolution.RES_STANDARD.getHeight()) / screenHeight;
        return new Point(x, y);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        touchedX = correctCoordinate(event).x;
        touchedY = correctCoordinate(event).y;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        // get the images from the input frame
        super.setmRgba(inputFrame.rgba());
        // circle to serve as a reference point for moving the piece
        Imgproc.circle(super.getmRgba(), refPt, 50, new Scalar(0, 0, 255));
        updatePositionBasedOnTouch(super.getmRgba());

        return detectCannyEdges(super.getmRgba());

    }

    /**
     * Update the position of the playing piece using the user input touch
     */
    private void updatePositionBasedOnTouch(Mat mRgba) {
        if (touchedX > 0 || touchedY > 0) {
            Imgproc.circle(mRgba, new Point(touchedX, touchedY), 10, new Scalar(255, 255, 255));
            // set the position of the concentric circles
            // touched - position set by the user (touched on the screen)
            // currentPosition - the current position of the drawn circles (player piece)
            // refPt - the reference point
            if (touchedY == 0) {
                if (touchedX > refPt.x) {
                    currentPositionX += VELOCITY;
                } else if (touchedX < refPt.x) {
                    currentPositionX -= VELOCITY;
                }
            } else if (touchedX == 0) {
                if (touchedY > refPt.y) {
                    currentPositionX += VELOCITY;
                } else if (touchedY < refPt.y) {
                    currentPositionY -= VELOCITY;
                }
            } else if (touchedX > refPt.x) {
                currentPositionX += VELOCITY;
                if (touchedY > refPt.y) {
                    currentPositionY += VELOCITY;
                } else if (touchedY < refPt.y) {
                    currentPositionY -= VELOCITY;
                }
            } else if (touchedX < refPt.x) {
                currentPositionX -= VELOCITY;
                if (touchedY > refPt.y) {
                    currentPositionY += VELOCITY;
                } else if (touchedY < refPt.y) {
                    currentPositionY -= VELOCITY;
                }
            }
        }
    }

    /**
     *
     * @param image
     * @return
     */
    private Mat detectCannyEdges(Mat image) {
        if (image == null) {
            return image;
        }

        Mat mHierarchy = new Mat();

        Mat detectedEdges = new Mat(image.size(), CvType.CV_8UC1);
        Imgproc.cvtColor(image, detectedEdges, Imgproc.COLOR_BGR2GRAY, 40);
        Imgproc.Canny(detectedEdges, detectedEdges, 80, 100);
        Imgproc.threshold(detectedEdges, detectedEdges, 100, 255, Imgproc.THRESH_BINARY);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(detectedEdges, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours.isEmpty()) {
            return image;
        }

        // how will we select which contours to use?
        RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(0).toArray()));

        Map<Integer, Rect> rectMap = ImageProcessingUtils.getBoundedRect(rect, contours);
        int boundPos = (int) rectMap.keySet().toArray()[0];
        Rect boundRect = rectMap.get(boundPos);
//        if (boundRect != null) {
//            Mat solidRect = new Mat(boundRect.size(), CvType.CV_8UC4);
//        }

        MatOfPoint2f pointMat = new MatOfPoint2f();
        Imgproc.approxPolyDP(new MatOfPoint2f(contours.get(boundPos).toArray()), pointMat, 1.7, true);
        contours.set(boundPos, new MatOfPoint(pointMat.toArray()));

        MatOfInt hull = new MatOfInt();
        Imgproc.convexHull(new MatOfPoint(contours.get(boundPos).toArray()), hull);

        // ensure that there are at least three points, since we must have a convex polygon
        if (hull.toArray().length <= 3) {
            return image;
        }

        if (contours.size() >= 2) {
            RotatedRect rect1 = Imgproc.minAreaRect(new MatOfPoint2f(contours.get(1).toArray()));

            Map<Integer, Rect> rectMap1 = ImageProcessingUtils.getBoundedRect(rect1, contours);
            int boundPos1 = (int) rectMap.keySet().toArray()[0];
            Rect boundRect1 = rectMap.get(boundPos1);
//            if (boundRect1 != null) {
//            solidRect = new Mat(boundRect1.size(), CvType.CV_8UC4);
//            }

            MatOfPoint2f pointMat1 = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(contours.get(boundPos).toArray()), pointMat1, 1.7, true);
            contours.set(boundPos1, new MatOfPoint(pointMat1.toArray()));

            MatOfInt hull1 = new MatOfInt();
            Imgproc.convexHull(new MatOfPoint(contours.get(boundPos1).toArray()), hull1);

            // ensure that there are at least three points, since we must have a convex polygon
            if (hull1.toArray().length <= 3) {
                return image;
            }
        }

        updatePosition(boundRect, 10);

        // redraw the circles
        Imgproc.circle(image, new Point(currentPositionX, currentPositionY), 15, new Scalar(255, 0, 0));
        Imgproc.circle(image, new Point(currentPositionX, currentPositionY), 10, new Scalar(0, 0, 255));
        Imgproc.circle(image, new Point(currentPositionX, currentPositionY), 5, new Scalar(0, 255, 0));

        Log.w(TAG, "bound rect null? = " + (boundRect == null));
        return image ;
    }

    /**
     * Update position of the player's piece based on newly calculated surface data
     *
     * The player's piece should not be able to pass through the detected rectangle
     *
     * @param boundRect
     */
    private void updatePosition(Rect boundRect, int thresholdRadius) {

        Point br = boundRect.br();
        Point tl = boundRect.tl();

        // if the piece is within radius of the box, then it will begin to move the opposite direction
        // i.e. we will update the touched direction in which we are traveling

        if (currentPositionX < 0) {currentPositionX = 0;}
        if (currentPositionY < 0) {currentPositionY = 0;}

        // CASE 1: LEFT OF BOX
        if (currentPositionX < tl.x && currentPositionX >= tl.x - thresholdRadius) {
            touchedX = refPt.x - Math.abs(touchedX = refPt.x);
        } else if (currentPositionX > br.x && currentPositionX <= br.x + thresholdRadius) {
            // CASE 3: RIGHT OF BOX
            touchedX = refPt.x + Math.abs(touchedX - refPt.x);
        }

        // CASE 2: TOP OF BOX
        if (currentPositionY < tl.y && currentPositionY >= tl.y - thresholdRadius) {
            touchedY = refPt.y - Math.abs(touchedY - refPt.y);
        } else if (currentPositionY > br.y && currentPositionY <= br.y + thresholdRadius) {
            // CASE 4: BOTTOM OF BOX
            touchedY = refPt.y + Math.abs(touchedY - refPt.y);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CameraPermissionServiceImpl.REQUEST_CODE) {
            cameraPermissionService.handleResponse(requestCode, permissions, grantResults);
        }
    }

}

