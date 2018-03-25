package com.driemworks.app.layout.impl;

import android.content.Intent;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.driemworks.common.cs.Constants;
import com.driemworks.common.dto.ConfigurationDTO;
import com.driemworks.common.utils.TagUtils;
import com.driemworks.app.activities.ConfigurationActivity;
import com.driemworks.app.activities.CubeActivity;
import com.driemworks.app.layout.LayoutManager;
import com.driemworks.app.views.OpenCVSurfaceView;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Tony
 */
public class ConfigurationLayoutManager implements LayoutManager {

    private final String TAG = TagUtils.getTag(this);
    /** Android elements */
    private OpenCVSurfaceView customSurfaceView;
    public static Button setConfiguration = null;

    public static TextView xRotation = null;
    public static TextView yRotation = null;
    public static TextView zRotation = null;

    /** element names */

    public static final String CONFIG_BUTTON = "Config button";
    public static final String X_VIEW = "x textview";
    public static final String Y_VIEW = "y textview";
    public static final String Z_VIEW = "z textview";

    /** The main activity */
    private static ConfigurationActivity configurationActivity = null;
    private static ConfigurationLayoutManager configurationLayoutManager = null;

    /**
     * The constructor is private since this is a singleton
     */
    private ConfigurationLayoutManager() {}

    @Override
    public <T extends View> void setup(String name, T layoutElement) {
        if (null != configurationActivity) {
            if (name.equals(CONFIG_BUTTON)) {
                setConfiguration = (Button) layoutElement;
                setConfigurationOnTouchListener();
            } else if (name.equals(X_VIEW)) {
                xRotation = (TextView) layoutElement;
                xRotation.setTextColor(45);
                xRotation.setText("X");
            } else if (name.equals(Y_VIEW)) {
                yRotation = (TextView) layoutElement;
                yRotation.setTextColor(50);
                yRotation.setText("Y");
            } else if (name.equals(Z_VIEW)) {
                zRotation = (TextView) layoutElement;
                zRotation.setTextColor(55);
                zRotation.setText("Z");
            }
        }
    }

    /**
     * The onTouchListener for the backButton button
     */
    private void setConfigurationOnTouchListener() {
        if (setConfiguration != null) {
            setConfiguration.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (null != configurationActivity) {
                        if (null != configurationActivity.getmBlobColorHsv()) {
                            ConfigurationDTO configurationDTO = new ConfigurationDTO(
                                    0, configurationActivity.getmBlobColorHsv()
                            );
                            Log.d(TAG, "created config dto -> beginning tracking");
                            Log.d(TAG, "creating intent to start game activity");
                            Map<String, ConfigurationDTO> configDTOMap = new HashMap<>();
                            configDTOMap.put(Constants.CONFIG, configurationDTO);
                            Intent intent = new Intent(configurationActivity, CubeActivity.class);
//                            Intent intent = new Intent(configurationActivity, MainActivity.class);
                            intent.putExtra(Constants.CONFIG, configurationDTO);
                            configurationActivity.startActivity(intent);
                            configurationActivity.finish();
                        }
                    }
                    return false;
                }
            });
        }
    }

    /**
     * Get the instance of the ConfigurationLayoutManager
     * @return
     */
    public static ConfigurationLayoutManager getInstance() {
        if (configurationLayoutManager == null) {
            configurationLayoutManager = new ConfigurationLayoutManager();
        }
        return configurationLayoutManager;
    }

    public void setActivity(ConfigurationActivity configurationActivity) {
        this.configurationActivity = configurationActivity;
    }

    public void updateX(String value) {
        xRotation.setText(value);
    }

    public void updateY(String value) {
        yRotation.setText(value);
    }

    public void updateZ(String value) {
        zRotation.setText(value);
    }


}