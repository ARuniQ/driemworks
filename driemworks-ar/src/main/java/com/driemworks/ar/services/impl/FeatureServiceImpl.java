package com.driemworks.ar.services.impl;

import android.util.Log;

import com.driemworks.ar.services.FeatureService;
import com.driemworks.common.utils.ImageConversionUtils;
import com.driemworks.common.utils.OpenCvUtils;
import com.driemworks.common.utils.TagUtils;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.video.Video;

import java.util.LinkedList;
import java.util.List;

/**
 * An implementation of the FeatureService
 * By Default, it uses FAST/ORB/Brute force Hamming distance
 * @author Tony
 */
public class FeatureServiceImpl implements FeatureService {

    /**
     * The tag used for logging
     */
    private final String TAG = TagUtils.getTag(this);

    /**
     * The detector
     */
    private FeatureDetector detector;

    /**
     * The descriptor descriptorExtractor
     */
    private DescriptorExtractor descriptorExtractor;

    /**
     * The descriptorMatcher
     */
    private DescriptorMatcher descriptorMatcher;

    /**
     * The term criteria
     */
    private TermCriteria termCriteria;

    /** The size */
    private Size size;

    /** The maxmimum level */
    private static final int MAX_LEVEL = 2;

    /**
     * The minimum eigen threshold
     */
    private static final double MIN_EIGEN_THRESHOLD = 0.001;

    /**
     * The maximum count
     */
    private static final int MAX_COUNT = 10;

    /**
     * The epsilon
     */
    private static final double EPISILON = 0.01;

    /**
     * Constructor for the FeatureServiceImpl with default params (FAST/ORB/HAMMING)
     */
    public FeatureServiceImpl() {
        // FAST feature detector
        detector = FeatureDetector.create(FeatureDetector.FAST);
        // ORB descriptor extraction
        descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        // brute force hamming metric
        descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        size = new Size(29, 29);
        termCriteria = new TermCriteria(TermCriteria.EPS | TermCriteria.MAX_ITER, MAX_COUNT, EPISILON);
    }

    /**
     * Constructor for the FeatureServiceImpl
     * @param detector The FeatureDetector
     * @param descriptorExtractor The DescriptorExtractor
     * @param descriptorMatcher The DescriptorMatcher
     */
    public FeatureServiceImpl(FeatureDetector detector, DescriptorExtractor descriptorExtractor, DescriptorMatcher descriptorMatcher) {
        this.detector = detector;
        this.descriptorExtractor = descriptorExtractor;
        this.descriptorMatcher = descriptorMatcher;
    }

    /**
     * {@inheritDoc}
     */
    public MatOfKeyPoint featureDetection(Mat frame) {
        Log.d(TAG, "START - featureDetection");
        long startTime = System.currentTimeMillis();
        MatOfKeyPoint mKeyPoints = new MatOfKeyPoint();
        Mat sharpenedFrame = OpenCvUtils.sharpenImage(frame);
        detector.detect(sharpenedFrame, mKeyPoints);
        Log.d(TAG, "END - featureDetection - time elapsed: " + (System.currentTimeMillis() - startTime) + " ms");
        return mKeyPoints;
    }

    /**
     * {@inheritDoc}
     */
    public MatOfKeyPoint featureTracking(Mat previousFrameGray, Mat currentFrameGray, MatOfKeyPoint previousKeyPoints) {
        Log.d(TAG, "START - featureTracking");
        long startTime = System.currentTimeMillis();

        MatOfByte status = new MatOfByte();
        MatOfFloat err = new MatOfFloat();

        MatOfPoint2f previousKeyPoints2f = ImageConversionUtils.convertMatOfKeyPointsTo2f(previousKeyPoints);
        MatOfPoint2f currentKeyPoints2f = new MatOfPoint2f();
        // previous => current
        Video.calcOpticalFlowPyrLK(previousFrameGray, currentFrameGray,
                previousKeyPoints2f, currentKeyPoints2f,
                status, err, size, MAX_LEVEL, termCriteria,
                Video.OPTFLOW_LK_GET_MIN_EIGENVALS, MIN_EIGEN_THRESHOLD);

        Log.d(TAG, "END - featureTracking - time elapsed: " + (System.currentTimeMillis() - startTime) + " ms");
        List<Point> featuresList = filterPointsOnlyKeypoints(previousKeyPoints2f.toList(), currentKeyPoints2f.toList(), status.toArray());
        status.release();
        err.release();
        return ImageConversionUtils.convertListOfPointsToMatOfKeypoint(featuresList, 0,0);
    }

    /**
     * Filters out points which fail tracking, or which were tracked off screen
     * @param previousKeypoints The list of keypoints in the previous image
     * @param currentKeypoints The list of keypoints in the current image
     * @param statusArray The status array
     */
    private List<Point> filterPointsOnlyKeypoints(List<Point> previousKeypoints, List<Point> currentKeypoints, byte[] statusArray) {
        int indexCorrection = 0;
        // copy lists
        LinkedList<Point> currentCopy = new LinkedList<>(currentKeypoints);
        LinkedList<Point> previousCopy = new LinkedList<>(previousKeypoints);
        for (int i = 0; i < currentKeypoints.size(); i++) {
            Point pt = currentKeypoints.get(i - indexCorrection);
            if (statusArray[i] == 0 || (pt.x == 0 || pt.y == 0)) {
                // removes points which are tracked off screen
                if (pt.x == 0 || pt.y == 0) {
                    statusArray[i] = 0;
                }

                // remove points for which tracking has failed
                currentCopy.remove(i - indexCorrection);
                previousCopy.remove(i - indexCorrection);
                indexCorrection++;
            }
        }

        return currentCopy;
    }

    /**
     * Getter for the FeatureDetector
     * @return The FeatureDetector
     */
    public FeatureDetector getDetector() {
        return detector;
    }

    /**
     * Setter for the FeatureDetector
     * @param detector the FeatureDetector
     */
    public void setDetector(FeatureDetector detector) {
        this.detector = detector;
    }

    /**
     * Getter for the DescriptorExtractor
     * @return the descriptorExtractor
     */
    public DescriptorExtractor getDescriptorExtractor() {
        return descriptorExtractor;
    }

    /**
     * Setter for the setDescriptorExtractor
     * @param descriptorExtractor The setDescriptorExtractor to set
     */
    public void setDescriptorExtractor(DescriptorExtractor descriptorExtractor) {
        this.descriptorExtractor = descriptorExtractor;
    }

    /**
     * Getter for the descriptorMatcher
     * @return the descriptorMatcher
     */
    public DescriptorMatcher getDescriptorMatcher() {
        return descriptorMatcher;
    }

    /**
     * Setter for the descriptorMatcher
     * @param descriptorMatcher The descriptorMatcher to set
     */
    public void setDescriptorMatcher(DescriptorMatcher descriptorMatcher) {
        this.descriptorMatcher = descriptorMatcher;
    }
}
