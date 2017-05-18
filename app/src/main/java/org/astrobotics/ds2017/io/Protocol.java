package org.astrobotics.ds2017.io;
import org.astrobotics.ds2017.R;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.ros.concurrent.CancellableLoop;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import robot_msgs.Status;
import robot_msgs.Teleop;
import robot_msgs.Ping;


/**
 * Implements the network protocol
 */
public class Protocol extends AbstractNodeMain {
    private static final java.lang.String TAG = "ds2017";
    private static final int DEADMAN = KeyEvent.KEYCODE_BUTTON_L1;
    private static InetAddress ROBOT_ADDRESS = null;
    private static java.lang.String TELEOP_TOPIC = "/robot/teleop";
    private static java.lang.String STATUS_TOPIC = "/robot/status";
    private static java.lang.String PING_TOPIC = "/driver/ping";
    private boolean RobotCodeActive = false;
    private boolean AutonomyActive = false;
    private boolean DeadmanPressed = false;
    private DatagramSocket socket_send, socket_ping, socket_receive;
    // instance of current control data
    private ControlData controlData = new ControlData();
    // instance of most recent data received
    // private ReceiveData receiveData;

    private volatile Publisher<robot_msgs.Teleop> publisher;
    private volatile Publisher<robot_msgs.Ping> pingPublisher;

    static {
        try {
            ROBOT_ADDRESS = InetAddress.getByAddress(new byte[]{10, 0, 0, 30});
        } catch (UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("ds2017");
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        //Instantiates the publishers for the Teleop data and Ping data
        publisher = connectedNode.newPublisher(TELEOP_TOPIC, robot_msgs.Teleop._TYPE);
        pingPublisher = connectedNode.newPublisher(PING_TOPIC, robot_msgs.Ping._TYPE);
        //Instantiate status msg object for subscriber and declares
        Subscriber<robot_msgs.Status> statusSubscriber = connectedNode.newSubscriber(STATUS_TOPIC, robot_msgs.Status._TYPE);
        //Adds listener for subscriber
        statusSubscriber.addMessageListener(new MessageListener<robot_msgs.Status>() {
            @Override
            public void onNewMessage(robot_msgs.Status message) {
                //Receives status data and stores in var for display in HUDActivity
                //TODO: Hand off data to HUD Activity
                RobotCodeActive = message.getRobotCodeActive();
                AutonomyActive = message.getAutonomyActive();
                DeadmanPressed = message.getDeadmanPressed();
                //Adds logging messsage to make sure that it is sending data
                Log.d(TAG, "Receiving Status Data");
            }
        });

        //CancellableLoop is made and started
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            //Initalizes a var for the ping msg data
            private byte pingData;
            @Override
            protected void setup(){
                //Declares a var for the ping msg data
                pingData = 0;
            }
            @Override
            //This is what happens when the loop starts
            protected void loop() throws InterruptedException {
                //Instantiate ping msg object
                robot_msgs.Ping ping = pingPublisher.newMessage();
                //Sets the byte to 0
                ping.setData(pingData);
                //Adds ping logging msg to make sure that it is pinging
                Log.d(TAG, "Ping Sent");
            }
        });
    }

    public boolean getStatus(int viewId){
        switch (viewId) {
            case R.id.robot_code_active:
                return RobotCodeActive;

            case R.id.autonomy_active:
                return AutonomyActive;

            case R.id.deadman_pressed:
                return DeadmanPressed;
            default:
                Log.d(TAG, "Problem Getting Status");
                return false;
        }
    }

    //Function for setting the stick given the axis and the value
    public void setStick(int axis, float value) {
        switch (axis) {
            case MotionEvent.AXIS_X:
                controlData.setAxis(ControlIDs.LTHUMBX, value);
                break;
            case MotionEvent.AXIS_Y:
                controlData.setAxis(ControlIDs.LTHUMBY, value);
                break;
            case MotionEvent.AXIS_Z:
                controlData.setAxis(ControlIDs.RTHUMBX, value);
                break;
            case MotionEvent.AXIS_RZ:
                controlData.setAxis(ControlIDs.RTHUMBY, value);
                break;
            case MotionEvent.AXIS_BRAKE:
                controlData.setAxis(ControlIDs.LTRIGGER, value);
                break;
            case MotionEvent.AXIS_GAS:
                controlData.setAxis(ControlIDs.RTRIGGER, value);
                break;
            case MotionEvent.AXIS_HAT_Y:
                controlData.setDpad(MotionEvent.AXIS_HAT_Y, value);
                break;
            case MotionEvent.AXIS_HAT_X:
                controlData.setDpad(MotionEvent.AXIS_HAT_X, value);
                break;
            default:
        }
        // defer sendData until all axes have been set
    }

    // for pressing buttons
    public void sendButton(int keycode, boolean pressed) {
        boolean wasChanged;
        switch (keycode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                wasChanged = controlData.setButton(ControlIDs.A, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                wasChanged = controlData.setButton(ControlIDs.B, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                wasChanged = controlData.setButton(ControlIDs.X, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                wasChanged = controlData.setButton(ControlIDs.Y, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                wasChanged = controlData.setButton(ControlIDs.LB, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                wasChanged = controlData.setButton(ControlIDs.RB, pressed);
                break;
            case KeyEvent.KEYCODE_BACK:
                wasChanged = controlData.setButton(ControlIDs.BACK, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
                wasChanged = controlData.setButton(ControlIDs.START, pressed);
                break;
//            case KeyEvent.KEYCODE_BUTTON_MODE:
//                wasChanged = controlData.setButton(ControlIDs.XBOX, pressed);
//                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                wasChanged = controlData.setButton(ControlIDs.LTHUMBBTN, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                wasChanged = controlData.setButton(ControlIDs.RTHUMBBTN, pressed);
                break;
//            case KeyEvent.KEYCODE_BUTTON_L2:
//                wasChanged = controlData.setButton(ControlIDs.L2, pressed);
//                break;
            default:
                return;
        }

        // send the data on change
        if (wasChanged) {
            sendData();
        }
    }

    public void sendData() {
//        sendQueue.offer(new ControlData(controlData));
        ControlData data = controlData;
        robot_msgs.Teleop robo = publisher.newMessage();
        // Set axes
        for (int i = 0; i < data.data.length; i++) {
            switch (i) {
                case ControlIDs.LTHUMBX:
                    robo.setXLThumb(data.data[i]);
                    break;
                case ControlIDs.LTHUMBY:
                    robo.setYLThumb(data.data[i]);
                    break;
                case ControlIDs.RTHUMBX:
                    robo.setXRThumb(data.data[i]);
                    break;
                case ControlIDs.RTHUMBY:
                    robo.setYRThumb(data.data[i]);
                    break;
                case ControlIDs.RTRIGGER:
                    robo.setRTrig(data.data[i]);
                    break;
                case ControlIDs.LTRIGGER:
                    robo.setLTrig(data.data[i]);
                    break;
                        /*case ControlIDs.DPAD_UP:
                            robo.setData(data.data[i]);
                            break;
                        case ControlIDs.DPAD_DOWN:
                            break;
                            robo.setData(data.data[i]);
                        case ControlIDs.DPAD_LEFT:
                            robo.setData(data.data[i]);
                            break;
                        case ControlIDs.DPAD_RIGHT:
                            robo.setData(data.data[i]);
                            break;*/
            }
        }
        // Set buttons
        for (int i = 0; i < data.buttonData.length; i++) {
            switch (i) {
                case ControlIDs.A:
                    robo.setA(data.buttonData[i]);
                    break;
                case ControlIDs.B:
                    robo.setB(data.buttonData[i]);
                    break;
                case ControlIDs.X:
                    robo.setX(data.buttonData[i]);
                    break;
                case ControlIDs.Y:
                    robo.setY(data.buttonData[i]);
                    break;
                case ControlIDs.LB:
                    robo.setLb(data.buttonData[i]);
                    break;
                case ControlIDs.RB:
                    robo.setRb(data.buttonData[i]);
                    break;
                case ControlIDs.BACK:
                    robo.setBack(data.buttonData[i]);
                    break;
                case ControlIDs.START:
                    robo.setStart(data.buttonData[i]);
                    break;
//                    case ControlIDs.XBOX:
//                        robo.setXbox(data.buttonData[i]);
//                        break;
                case ControlIDs.LTHUMBBTN:
                    robo.setLThumb(data.buttonData[i]);
                    break;
                case ControlIDs.RTHUMBBTN:
                    robo.setRThumb(data.buttonData[i]);
                    break;
//                    case ControlIDs.L2:
//                        robo.setData(data.buttonData[i]);
//                        break;
//                    case ControlIDs.R2:
//                        robo.setData(data.buttonData[i]);
//                        break;
            }
        }
        // Set dpad
        robo.setDpX((byte) data.dpad_x);
        robo.setDpY((byte) data.dpad_y);

        //send data
        publisher.publish(robo);
    }

    private static class ControlIDs {
        // Axes
        public static final int LTHUMBX = 0;
        public static final int LTHUMBY = 1;
        public static final int RTHUMBX = 2;
        public static final int RTHUMBY = 3;
        public static final int RTRIGGER = 4;
        public static final int LTRIGGER = 5;
        public static final int NUM_AXES = 6;

        // Buttons
        public static final int A = 0;
        public static final int B = 1;
        public static final int X = 2;
        public static final int Y = 3;
        public static final int LB = 4;
        public static final int RB = 5;
        public static final int BACK = 6;
        public static final int START = 7;
        public static final int LTHUMBBTN = 8;
        public static final int RTHUMBBTN = 9;
        public static final int NUM_BTNS = 10;
    }

    private static class ControlData {
        // for axis dead zone
        private static final float AXIS_BOUNDS = 0.1f;
        // max/min axis values can be
        private static final float AXIS_MAX = 1.0f;
        // max value float should be
        private static final int AXIS_FLOAT_MAX = 100;
        // for dpad dead zone
        private static final double DPAD_BOUNDS = 0.1;

        // array for data, everything can be stored in float,
        // though for buttons, only one bit will be used
        public float data[] = new float[ControlIDs.NUM_AXES];
        public boolean buttonData[] = new boolean[ControlIDs.NUM_BTNS];
        public int dpad_x = 0;
        public int dpad_y = 0;

        // default constructor
        public ControlData() {
        }

        // copy constructor
        public ControlData(ControlData oldData) {
            this.data = new float[oldData.data.length];
            // deep copy old data
            for (int i = 0; i < oldData.data.length; i++) {
                this.data[i] = oldData.data[i];
            }
        }

        // sets button to on/off
        // assumes they gave an ID of a button
        // returns true if button was changed, false if not
        public boolean setButton(int ID, boolean down) {
            boolean oldval = buttonData[ID];
            if (down) {
                buttonData[ID] = true;
            } else {
                buttonData[ID] = false;
            }
            return oldval != buttonData[ID];
        }

        // assumes the id is for an axis
        // takes value from -1 to 1 and converts to specified range
        public void setAxis(int ID, float value) {
            if (value > AXIS_BOUNDS) {
                // cap at 1.0
                if (value > AXIS_MAX) {
                    value = AXIS_MAX;
                }
                data[ID] = value;
            } else if (value < -AXIS_BOUNDS) {
                // cap at -1.0
                if (value < -AXIS_MAX) {
                    value = -AXIS_FLOAT_MAX;
                }
                data[ID] = value;
            } else {
                data[ID] = 0.0f;
            }
        }

        // dpad comes as a float, but should be set to on or off
        public void setDpad(int eventCode, float value) {
            if (eventCode == MotionEvent.AXIS_HAT_X) {
                if (value > DPAD_BOUNDS) {
                    dpad_x = 1;
                } else if (value < -DPAD_BOUNDS) {
                    dpad_x = -1;
                } else {
                    dpad_x = 0;
                }
            } else if (eventCode == MotionEvent.AXIS_HAT_Y) {
                if (value > DPAD_BOUNDS) {
                    dpad_y = 1;
                } else if (value < -DPAD_BOUNDS) {
                    dpad_y = -1;
                } else {
                    dpad_y = 0;
                }
            }
        }

        // return a printable string, for debugging
        public java.lang.String toString() {
            java.lang.String str = "Axes => ";
            for (int i = 0; i < data.length; i++) {
                str += i + ": " + data[i] + ", ";
            }
            str += "Buttons => ";
            for (int i = 0; i < buttonData.length; i++) {
                str += i + ": " + buttonData[i] + ", ";
            }
            str += "Dpad => x: " + dpad_x + ", y: " + dpad_y;
            return str;
        }
    }
}