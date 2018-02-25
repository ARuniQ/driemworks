package com.driemworks.app.graphics.rendering;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.driemworks.common.utils.TagUtils;
import com.driemworks.sensor.utils.OrientationUtils;
import com.driemworks.app.activities.CubeActivity;
import com.driemworks.common.enums.Resolution;
import com.threed.jpct.Camera;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Object3D;
import com.threed.jpct.Primitives;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.World;
import com.threed.jpct.util.MemoryHelper;

import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author Tony
 */
public class GraphicsRenderer extends AbstractRenderer implements GLSurfaceView.Renderer {

    private List<Object3D> spheres;

    /**
     * The tag used for logging
     */
    private final String TAG = TagUtils.getTag(this.getClass());

    /**
     * The current system time (in ms)
     */
    private long time = System.currentTimeMillis();

    /**/////////////////// jPCT  ////////////////////////// */
    private FrameBuffer fb = null;
    private World world = null;
    private Light sun = null;

    private Camera cam;

    private CubeActivity master = null;

    /** //////////////////////// Location and Orientation /////////////////////////// */
    private float[] previousRotationVector = new float[3];
    private float[] deltaRotation = new float[3];

    /** The point to which the cube will be moved */
    private float x = 0;
    private float y = 0;
    private float z = 100;

    private int touchedX;
    private int touchedY;

    private Rect surface;

    private int width = Resolution.RES_STANDARD.getWidth();
    private int height = Resolution.RES_STANDARD.getHeight();

    private static final float MULTIPLIER = 1f;

    private Activity activity;

    private boolean addNewCube = false;

    public void setPreviousRotationVector(float[] previousRotationVector) {
        this.previousRotationVector = previousRotationVector;
    }

    private Object3D cube;

    /**
     * The default constructor
     */
    public GraphicsRenderer(Activity activity) {
        this.activity = activity;
        spheres = new ArrayList<>();
    }

    /**
     * Instantiates the world and objects in it
     *
     * @param g1
     * @param w
     * @param h
     */
    public void onSurfaceChanged(GL10 g1, int w, int h) {
        Log.d(TAG, "surface changed");

        if (fb != null) {
            fb.dispose();
        }

        fb = new FrameBuffer(g1, w, h);
        if (master == null) {
            initWorld();
        }
    }

    private void initWorld() {
        world = new World();
        world.setAmbientLight(20, -30, 40);

        initCubes();

        // setup the camera
        cam = world.getCamera();
        // the camera should be positioned at the "center" of the world
        cam.setPosition(width / 2, height / 2, -200);
        cam.lookAt(new SimpleVector(width / 2, height / 2, 300));

        sun = new Light(world);
        sun.setIntensity(255, 255, 255);
        // setup the sun
        SimpleVector sv = new SimpleVector();
        sv.set(cube.getTransformedCenter());
        sv.y = 0;
        sv.x = width / 2;
        sv.z += 300;
        sun.setPosition(sv);
        MemoryHelper.compact();
    }

    private void initCubes() {
        cube = createCube(RGBColor.WHITE, 30, 0, 0, 0);
        cube.setVisibility(true);
        world.addObject(cube);
    }

    public void onSurfaceCreated(GL10 g1, EGLConfig config) {
        g1.glDisable(GL10.GL_DITHER);
    }

    private AtomicInteger frameCounter = new AtomicInteger(0);

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDrawFrame(GL10 g1) {
        frameCounter.incrementAndGet();
        if (fb != null) {
            fb.clear();
            world.renderScene(fb);
            updateOriginSurface();
            updateVectors();

            if (super.getRotationVector() != null) {
                OrientationUtils.calcDeltaRotation(MULTIPLIER, super.getRotationVector(), previousRotationVector, deltaRotation);
                Log.i(TAG, "Calculated rotation delta rotation: " + deltaRotation);
                if (deltaRotation != null) {
                    updateRotation(deltaRotation);
                }
            }


            world.draw(fb);
            fb.display();
        }
    }

    private void updateRotation(float[] rotation) {
        cam.rotateCameraY(-rotation[0]);
        cam.rotateCameraX(rotation[1]);
        cam.rotateCameraZ(rotation[2]);
    }

    private float calcEuclideanDistanceX(SimpleVector vec1, SimpleVector vec2) {
        return (float)(Math.sqrt(Math.pow(Math.abs(vec1.x - vec2.x), 2)));
    }

    private float calcEuclideanDistance(SimpleVector vec1, SimpleVector vec2) {
        return (float)(Math.sqrt(
                                 Math.pow(Math.abs(vec1.x - vec2.x), 2)
                               + Math.pow(Math.abs(vec1.y - vec2.y), 2)
                               + Math.pow(Math.abs(vec1.z - vec2.z), 2)));
    }

    private Object3D createCube(RGBColor color, float scale, float y, float x, float z) {
        Object3D cube = Primitives.getCube(scale);
        cube.setAdditionalColor(color);
        cube.setOrigin(new SimpleVector(x, y, z));
        return cube;
    }


    private void updateVectors() {
        if (super.getRotationVector() != null) {
            System.arraycopy(super.getRotationVector(), 0, previousRotationVector, 0, 3);
        }
    }

    /** The rate at which spheres are generated */
    private static final int objectCreationRate = 67;

    /**
     *
     */
    private void updateOriginSurface() {
        if (null != super.getSurfaceDataDTO()) {
            Log.d(TAG, "Attempting to find points to update currentCube position");
            // get the detected surface
            if (null != super.getSurfaceDataDTO().getBoundRect()) {
                Log.d(TAG, "bounding rect found. getting points");
                surface = super.getSurfaceDataDTO().getBoundRect();
                // set the currentCube position
                if (surface.area() > 5) {
                    x = (float) surface.tl().x + 90;
                    y = (float) surface.tl().y + 90;
                    // greater area => closer (decrease Z)
                    // less area => farther away (increase Z)
                    // subtract, since Z axis is coming at us
                    z = 350;
                    Log.d("*****", "calculated z: "+ z);


                    if (false) {
                        if (frameCounter.get() % objectCreationRate == 0) {
                            addRandomSphere();
                        }

                        for (Object3D sphere : spheres) {
                            sphere.translate(0, 0, -1 / sphere.getScale());
                        }
                    }

                    cube.setOrigin(new SimpleVector(x, y, z));
                    Log.d(TAG, "Updating currentCube position with coordinate x = " + x + ", y = " + y + ", z = " + z);
                }
            }
        }
    }
//
//    private AssetManager assetManager;
//    private InputStream is;
//
//
//    private Object3D loadModel(String filename, float scale) throws UnsupportedEncodingException {
//
//        InputStream stream = new ByteArrayInputStream(filename.getBytes("UTF-8"));
//        Object3D[] model = Loader.load3DS(stream, scale);
//        Object3D o3d = new Object3D(0);
//        Object3D temp;
//        for (int i = 0; i < model.length; i++) {
//            temp = model[i];
//            temp.setCenter(SimpleVector.ORIGIN);
//            temp.rotateX((float)( -.5*Math.PI));
//            temp.rotateMesh();
//            temp.setRotationMatrix(new Matrix());
//            o3d = Object3D.mergeObjects(o3d, temp);
//            o3d.build();
//        }
//        return o3d;
//    }

    private void addRandomSphere() {
        Object3D sphere;
        Iterator<Object3D> objIterator = spheres.iterator();
        while(objIterator.hasNext()) {
            sphere = objIterator.next();
            // if user touches close enough to sphere, then "pop" the sphere
            if (sphere.getOrigin().x - touchedX <= 10
                    && sphere.getOrigin().y - touchedY <= 10) {
                Log.d(TAG, "Removing sphere " + spheres.indexOf(sphere));
                objIterator.remove();
                world.removeObject(sphere);
            }
        }

        Object3D randSphere = Primitives.getSphere((float)(Math.random() * 30 + 1));
        int randX  = 1 + 50 * (int)(Math.random());
        int randY = 1 + 50 * (int)(Math.random());
        randSphere.setOrigin(new SimpleVector(x + randX, y + randY, z));
        randSphere.setAdditionalColor(RGBColor.GREEN);
        spheres.add(randSphere);
        world.addObject(randSphere);
    }


    private int multiplier = 600;

    /**
     * push the cube into the screen
     * @param pressure
     */
    public void pushCube(float pressure) {
        cube.translate(0, 0, pressure * multiplier);
    }

    /**
     * return cube to starting position after being pulled
     * @param pressure
     */
    public void pullCube(float pressure) {
        cube.translate(0, 0, -(pressure * multiplier));
    }

    public void setAddNewCube(boolean addNewCube) {
        this.addNewCube = addNewCube;
    }

    public boolean isAddNewCube() {
        return addNewCube;
    }

    public int getTouchedX() {
        return touchedX;
    }

    public void setTouchedX(int touchedX) {
        this.touchedX = touchedX;
    }

    public int getTouchedY() {
        return touchedY;
    }

    public void setTouchedY(int touchedY) {
        this.touchedY = touchedY;
    }
}