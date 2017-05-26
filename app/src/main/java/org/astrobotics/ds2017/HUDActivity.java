package org.astrobotics.ds2017;

import java.net.URI;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.input.InputManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.astrobotics.ds2017.io.MjpegView;
import org.astrobotics.ds2017.io.Protocol;
import org.ros.android.NodeMainExecutorService;
import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import robot_msgs.MotorFeedback;

public class HUDActivity extends RosActivity {
    private static final java.lang.String TAG = "ds2017";
    private static final int[] AXES = new int[] {MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
    // Resolution 8 = 320x240, Rate 6 = 10 fps

    private static final String url_left = "http://10.0.0.51/videostream.cgi?user=VTAstrobot&pwd=RoVER16&resolution=8&rate=6";
    private static final String url_right = "http://10.0.0.50/videostream.cgi?user=VTAstrobot&pwd=RoVER16&resolution=8&rate=6";
    private static final String url_turn_right = "http://10.0.0.50/decoder_control.cgi?command=1&user=VTAstrobot&pwd=RoVER16";
    private static final String url_turn_left = "http://10.0.0.50/decoder_control.cgi?command=1&user=VTAstrobot&pwd=RoVER16";

    private static final URI ROBOT_ROS_URI = URI.create("http://10.0.0.30:11311");

    private Protocol protocol;
    private MjpegView mjpegView;
    private BroadcastReceiver wifiReceiver;
    private NodeMainExecutor nodeExecutor;
    private boolean oldDeadman = false;

    public HUDActivity() {
        super("Astrobotics", "Driver Station 2017", ROBOT_ROS_URI);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hud);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        protocol = new Protocol();
        protocol.setUpdateListener(new Protocol.UpdateListener() {
            @Override
            public void statusUpdated() {
                updateStatusGui();
            }

            @Override
            public void feedbackReceived(MotorFeedback feedback) {
                updateFeedbackGui(feedback);
            }
        });

        // Initialize indicators
        initIndicator(R.id.robot_status, R.drawable.ic_robot_status);
        initIndicator(R.id.controller_status, R.drawable.ic_controller_status);

        // Register input device listener
        InputManager inputManager = (InputManager) getApplicationContext().getSystemService(Context.INPUT_SERVICE);
        inputManager.getInputDeviceIds(); // required for the device listener to be registered
        inputManager.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                updateGamepadStatus();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                updateGamepadStatus();
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                updateGamepadStatus();
            }
        }, null);

        updateGamepadStatus();
        updateStatusGui();

        wifiReceiver = new WifiChangedReceiver();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        // how to set up GUI
        // take existing activity
        // Add the mjpeg view into activity.xml
        // 3 indicators for left camera, right camera, and no camera
        // start off disabled
        // when one button is pushed, release other two, handle view appropriately
        // maybe seperate into two layers
        // 1 for stream
        // 1 for other stuff
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        nodeExecutor = nodeMainExecutor;
        NodeConfiguration protocolConfig = NodeConfiguration.newPublic(getRosHostname(), getMasterUri());
        nodeMainExecutor.execute(protocol, protocolConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiReceiver);
        ((NodeMainExecutorService)nodeExecutor).forceShutdown();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) {
            int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                      | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                      | View.SYSTEM_UI_FLAG_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            protocol.sendButton(keyCode, true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            protocol.sendButton(keyCode, false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            for(int axis : AXES) {
                protocol.setStick(axis, event.getAxisValue(axis));
            }
            // always send data for axis changes, after all axes updated
            protocol.sendData();
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void setDeadmanPressed(final boolean deadman) {
        if(deadman != oldDeadman) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setIndicator(R.id.robot_status, deadman);
                }
            });
            oldDeadman = deadman;
        }
    }

    private void setAutonomyEnabled(boolean enabled) {
        TextView autoText = (TextView)findViewById(R.id.autonomy_active);
        if(enabled) {
            autoText.setText(R.string.autonomy_enabled);
            autoText.setTextColor(Color.GREEN);
        } else {
            autoText.setText(R.string.autonomy_disabled);
            autoText.setTextColor(Color.RED);
        }
    }

    // Sets indicator icon
    @SuppressWarnings("deprecation")
    private void initIndicator(int viewId, int iconId) {
        LayerDrawable layers = (LayerDrawable) findViewById(viewId).getBackground();
        layers.setDrawableByLayerId(R.id.indicator_icon, getResources().getDrawable(iconId));
    }

    // Change background color of indicator shape
    private void setIndicator(int viewId, boolean activated) {
        LayerDrawable layers = (LayerDrawable) findViewById(viewId).getBackground();
        Drawable shape = layers.findDrawableByLayerId(R.id.indicator_bg);
        if(activated) {
            shape.setLevel(1);
        } else {
            shape.setLevel(0);
        }
    }

    // true = connecting, false = connected
    private void setSpinner(boolean spinning){
        ProgressBar spinner = (ProgressBar)findViewById(R.id.active_publisher_spinner);
        TextView textView = (TextView) findViewById(R.id.active_publisher);
        if (spinning){
            spinner.setVisibility(View.VISIBLE);
            textView.setText(R.string.publisher_connecting);
            textView.setTextColor(textView.getTextColors().getDefaultColor());
        }
        else{
            spinner.setVisibility(View.GONE);
            textView.setText(R.string.publisher_active);
            textView.setTextColor(Color.GREEN);
        }
    }

    private void updateStatusGui() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Deadman indicator
                setDeadmanPressed(protocol.getDeadmanPressed());
                // Autonomy indicator
                setAutonomyEnabled(protocol.getAutonomyActive());
                // Publisher active indicator
                setSpinner(!protocol.isPublisherActive());
                // Storage-lift limit override
                int visibility = (protocol.getLimitsOverride() ? View.VISIBLE : View.INVISIBLE);
                findViewById(R.id.limit_override).setVisibility(visibility);
            }
        });
    }

    private void updateFeedbackGui(final MotorFeedback feedback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView voltage = (TextView)findViewById(R.id.battery_voltage);
                voltage.setText("BATTERY VOLTAGE: " + feedback.getBatVoltage() + "V");

                TextView liftAngle = (TextView)findViewById(R.id.lift_angle);
                liftAngle.setText(((int)(feedback.getLiftPos()*100)/100.0f) + "°");
                if(feedback.getLiftDownLimit() || feedback.getLiftUpLimit()) {
                    liftAngle.setTextColor(Color.RED);
                } else {
                    liftAngle.setTextColor(liftAngle.getTextColors().getDefaultColor());
                }

                TextView liftCurrent = (TextView)findViewById(R.id.lift_current);
                liftCurrent.setText(feedback.getLiftCurrent() + "A");

                TextView drumRpm = (TextView)findViewById(R.id.drum_rpm);
                drumRpm.setText(feedback.getDrumRPM() + " RPM");

                TextView drumCurrent = (TextView)findViewById(R.id.drum_current);
                drumCurrent.setText(feedback.getDrumCurrent() + "A");

                TextView driveCurrent = (TextView)findViewById(R.id.drive_current);
                String driveStr = "L " + feedback.getLeftTreadRPM() + "A"
                                + " ⋅ R " + feedback.getRightTreadRPM() + "A";
                driveCurrent.setText(driveStr);
            }
        });
    }

    // Update gamepad status indicator
    private void updateGamepadStatus() {
        int gamepadCheck = InputDevice.SOURCE_GAMEPAD
                | InputDevice.SOURCE_JOYSTICK;
//                         | InputDevice.SOURCE_DPAD;
        for(int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            int sources = device.getSources();
            if((sources & gamepadCheck) == gamepadCheck) {
                setIndicator(R.id.controller_status, true);
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void updateWifiInfo(WifiInfo info) {
        String wifiText = "Wifi: ";
        if(info == null) {
            wifiText += "<font color='red'>DISCONNECTED</font>";
        } else {
            String ssid = info.getSSID();
            wifiText += "<font color='green'>" + ssid.substring(1, ssid.length() - 1) + "</font>";
        }
        ((TextView)findViewById(R.id.wifiLabel)).setText(Html.fromHtml(wifiText));
    }

    private class WifiChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(!intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                return;
            }
            NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if(netInfo.getState() == NetworkInfo.State.CONNECTED) {
                WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if(wifiInfo.getSSID().equals("<unknown ssid>")) {
                    updateWifiInfo(null);
                } else {
                    updateWifiInfo(wifiInfo);
                }
            } else if(netInfo.getState() == NetworkInfo.State.DISCONNECTED){
                updateWifiInfo(null);
            }
        }
    }
}
