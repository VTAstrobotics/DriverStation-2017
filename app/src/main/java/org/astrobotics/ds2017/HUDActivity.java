package org.astrobotics.ds2017;

import java.io.IOException;
import java.net.URI;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.input.InputManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.astrobotics.ds2017.io.MjpegView;
import org.astrobotics.ds2017.io.Protocol;
import org.astrobotics.ds2017.io.Talker;
import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

public class HUDActivity extends RosActivity {
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
    private boolean oldRobotUp = false;

    public HUDActivity() {
        super("", "", ROBOT_ROS_URI);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hud);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        RadioGroup streamButtons;

        // set radio buttons
        streamButtons = (RadioGroup) findViewById(R.id.stream_buttons);
        streamButtons.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId == R.id.cam_left) {
                    Log.d("HUDActivity", "cam_left selected");
                    stopStream();
                    loadStream(url_left);
                } else if(checkedId == R.id.cam_right) {
                    Log.d("HUDActivity", "cam_right selected");
                    stopStream();
                    loadStream(url_right);
                } else if(checkedId == R.id.cam_none) {
                    Log.d("HUDActivity", "cam_none selected");
                    stopStream();
                }
            }
        });

        try {
            protocol = new Protocol();
            protocol.startConnChecker(this);
        } catch(IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing network protocol", Toast.LENGTH_LONG).show();
            finish();
        }

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
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiReceiver);
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        Talker talker = new Talker();

        NodeConfiguration talkerConfig = NodeConfiguration.newPrivate(getMasterUri());
        nodeMainExecutor.execute(talker, talkerConfig);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if(hasFocus) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
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
            protocol.sendData();
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    public void setRobotUp(final boolean robotUp) {
        if(robotUp != oldRobotUp) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setIndicator(R.id.robot_status, robotUp);
                }
            });
            oldRobotUp = robotUp;
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

    private void loadStream(String url) {
        mjpegView = (MjpegView) findViewById(R.id.stream);
        try {
            mjpegView.setSource(url);
            mjpegView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            mjpegView.showFps(true);
        } catch(Exception e) {
            e.printStackTrace();
        }
        mjpegView.setVisibility(View.VISIBLE);
    }

    private void stopStream() {
        // stop both streams
        // and don't waste data
        // hopefully this works
        if(mjpegView != null) {
            mjpegView.stopPlayback();
            mjpegView.setVisibility(View.INVISIBLE);
        }
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
        setIndicator(R.id.controller_status, false);
    }

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
            NetworkInfo netInfo = (NetworkInfo)intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if(netInfo.getState() == NetworkInfo.State.CONNECTED) {
                WifiInfo wifiInfo = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if(wifiInfo.getSSID().equals("<unknown ssid>")) {
                    updateWifiInfo(null);
                } else {
                    updateWifiInfo(wifiInfo);
                }
            } else if(netInfo.getState() == NetworkInfo.State.DISCONNECTED){
                updateWifiInfo(null);
            }
        }

        //implements the ability to turn camera
        public void onCameraControl (MotionEvent event){
            double  DPAD_BOUNDS = 0.1;
            if((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                if(event.getAxisValue(MotionEvent.AXIS_HAT_X)>DPAD_BOUNDS){
                    loadStream(url_turn_right);
                }
                if(event.getAxisValue(MotionEvent.AXIS_HAT_X)<DPAD_BOUNDS){
                    loadStream(url_turn_left);
                }
            }
        }


    }
}
