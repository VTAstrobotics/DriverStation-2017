package org.astrobotics.ds2017.io;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.astrobotics.ds2017.HUDActivity;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;
import robot_msgs.Teleop;

/**
 * Implements the network protocol
 */
public class Protocol extends AbstractNodeMain {
    private static final String TAG = "ds2017";
    private static final int DEADMAN = KeyEvent.KEYCODE_BUTTON_L1;
    private static final int ROBOT_PORT_SEND = 6800, ROBOT_PORT_RECEIVE = 6850;
    private static final int ROBOT_PING_SEND = 6900;
    private static final int PING_FREQUENCY = 200; // 5 times per second
    private static final int CONNCHECK_FREQUENCY = 2000; // every 2 seconds
    private static InetAddress ROBOT_ADDRESS = null;
    private static String TELEOP_TOPIC = "/robot/teleop";

    private DatagramSocket socket_send, socket_ping, socket_receive;
//    private LinkedBlockingQueue<ControlData> sendQueue = new LinkedBlockingQueue<>();
    private Thread sendThread, pinging, receiving, connCheck;

    // instance of current control data
    private ControlData controlData = new ControlData();
    // instance of most recent data received
//    private ReceiveData receiveData;

    private volatile Publisher<robot_msgs.Teleop> publisher;

    static {
        try {
            ROBOT_ADDRESS = InetAddress.getByAddress(new byte[] {10, 0, 0, 30});
        } catch(UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public Protocol() {
//        sendThread = new Thread(new SendWorker(), "Send Thread");
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("ds2017");
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        publisher = connectedNode.newPublisher(TELEOP_TOPIC, robot_msgs.Teleop._TYPE);
//        sendThread.start();
    }

    public void startConnChecker(HUDActivity hudActivity) {
        if(connCheck != null) {
            connCheck.interrupt();
        }
        connCheck = new Thread(new ConnCheckWorker(hudActivity), "Connectivity Check Thread");
        connCheck.start();
    }

    //Function for setting the stick given the axis and the value
    public void setStick(int axis, float value) {
        switch(axis) {
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
        // always send data for axis changes
        sendData();
    }

    // for pressing buttons
    public void sendButton(int keycode, boolean pressed) {
        boolean wasChanged;
        switch(keycode) {
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
        if(wasChanged) {
            sendData();
            // probably don't need the stuff below
/*            // send twice if the button was released
            if(!pressed) {
                sendData();
                // send again if the button released was deadman
                if(keycode == DEADMAN) {
                    sendData();
                }*/
        }
    }

    public void sendData() {
//        sendQueue.offer(new ControlData(controlData));
        ControlData data = controlData;
        robot_msgs.Teleop robo = publisher.newMessage();
        // Set axes
        for(int i = 0; i < data.data.length; i++)
        {
            switch(i) {
                case ControlIDs.LTHUMBX :
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
        for(int i = 0; i < data.buttonData.length; i++)
        {
            switch(i) {
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
        robo.setDpX((byte)data.dpad_x);
        robo.setDpY((byte)data.dpad_y);
        //Adds logging messsage to make sure that it is sending data
        Log.d(TAG, "Sending Data");
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

    // holds details given from the robot to DS
/*    private static class ReceiveData {
        public static final int SIZE = 4;

        // if the dead man's switch is on or off
        private boolean isDeadMansDown;
        // holds the battery voltage
        private float voltage = 0;

        public ReceiveData() {
            this.isDeadMansDown = false;
            this.voltage = 0;
        }

        public void setDeadMansDown(boolean b) {
            this.isDeadMansDown = b;
        }

        public void setVoltage(float b) {
            this.voltage = b;
        }

        // returns voltage as an int
        public int getVoltage() {
            return ((int) (this.voltage));
        }

        public boolean getIsDeadMansDown() {
            return isDeadMansDown;
        }

        public java.lang.String toString() {
            return "Dead Man's: " + isDeadMansDown + " , Voltage: " + voltage;
        }
    }*/

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
            for(int i = 0; i < oldData.data.length; i++) {
                this.data[i] = oldData.data[i];
            }
        }

        // sets button to on/off
        // assumes they gave an ID of a button
        // returns true if button was changed, false if not
        public boolean setButton(int ID, boolean down) {
            boolean oldval = buttonData[ID];
            if(down) {
                buttonData[ID] = true;
            } else {
                buttonData[ID] = false;
            }
            return oldval != buttonData[ID];
        }

        // assumes the id is for an axis
        // takes value from -1 to 1 and converts to specified range
        public void setAxis(int ID, float value) {
            if(value > AXIS_BOUNDS) {
                // cap at 1.0
                if(value > AXIS_MAX) {
                    value = AXIS_MAX;
                }
                data[ID] = value;
            } else if(value < -AXIS_BOUNDS) {
                // cap at -1.0
                if(value < -AXIS_MAX) {
                    value = -AXIS_FLOAT_MAX;
                }
                data[ID] = value;
            } else {
                data[ID] = 0.0f;
            }
        }

        // dpad comes as a float, but should be set to on or off
        public void setDpad(int eventCode, float value) {
            if(eventCode == MotionEvent.AXIS_HAT_X) {
                if(value > DPAD_BOUNDS) {
                    dpad_x = 1;
                } else if(value < -DPAD_BOUNDS) {
                    dpad_x = -1;
                } else {
                    dpad_x = 0;
                }
            } else if(eventCode == MotionEvent.AXIS_HAT_Y) {
                if(value > DPAD_BOUNDS) {
                    dpad_y = 1;
                } else if(value < -DPAD_BOUNDS) {
                    dpad_y = -1;
                } else {
                    dpad_y = 0;
                }
            }
        }

        // return a printable string, for debugging
        public String toString() {
            String str = "Axes => ";
            for(int i = 0; i < data.length; i++) {
                str += i + ": " + data[i] + ", ";
            }
            str += "Buttons => ";
            for(int i = 0; i < buttonData.length; i++) {
                str += i + ": " + buttonData[i] + ", ";
            }
            str += "Dpad => x: " + dpad_x + ", y: " + dpad_y;
            return str;
        }
    }

    private class PingWorker implements Runnable {
        @Override
        public void run() {
            // TODO something for pinging in loop
            // doesn't need to be a thread, can use ROS cancellable loop or similar
        }
    }

    // recieve data from robot
    private class ReceiveWorker implements Runnable {
        @Override
        public void run() {
            // initialize socket on dedicated thread
            // TODO handle receiving Status msg - subscriber in onStart
/*            try {
                socket_receive.bind(new InetSocketAddress(ROBOT_PORT_RECEIVE));
            } catch(SocketException e) {
                e.printStackTrace();
                return;
            }

            // while the thread can work
            while(!Thread.interrupted() && !socket_receive.isClosed()) {
                byte[] temp_bytes = new byte[ReceiveData.SIZE];
                DatagramPacket temp_data = new DatagramPacket(temp_bytes, temp_bytes.length);

                // receive the data
                try {
                    socket_receive.receive(temp_data);
                } catch(IOException e) {
                    Log.d("tag", "error in receive data occured or no data received.... not sure");
                    continue;
                }

                // take it apart
                // 0 = deadman
                // 1 = voltage
                // handle the actual data
                receiveData.setDeadMansDown(temp_bytes[0] != 0);
                receiveData.setVoltage(temp_bytes[1]);
            }*/
        }
    }

    // send the data from the queue in a thread
/*    private class SendWorker implements Runnable {
        @Override
        public void run() {
            ControlData data;
            // while the thread can send
            while(!Thread.interrupted()) {
                try {
                    data = sendQueue.take();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                robot_msgs.Teleop robo = publisher.newMessage();
                // Set axes
                for(int i = 0; i < data.data.length; i++)
                {
                    switch(i) {
                        case ControlIDs.LTHUMBX :
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
                    /*}
                }
                // Set buttons
                for(int i = 0; i < data.buttonData.length; i++)
                {
                    switch(i) {
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
                robo.setDpX((byte)data.dpad_x);
                robo.setDpY((byte)data.dpad_y);
                //Adds logging messsage to make sure that it is sending data
                Log.d(TAG, "Sending Data");
                //send data
                publisher.publish(robo);
            }
        }
    }*/

    private class ConnCheckWorker implements Runnable {
        private HUDActivity hudActivity;

        public ConnCheckWorker(HUDActivity hudActivity) {
            this.hudActivity = hudActivity;
        }

        @Override
        public void run() {
            while(!Thread.interrupted()) {
                boolean robotUp = checkHost(ROBOT_ADDRESS);
                hudActivity.setRobotUp(robotUp);
                try {
                    Thread.sleep(CONNCHECK_FREQUENCY);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Check by testing port 22 (SSH) on the given host
         */
        public boolean checkHost(InetAddress addr) {
            try {
                Socket test = new Socket();
                test.connect(new InetSocketAddress(addr, 22), 200); // timeout after 200ms
                test.close();
                return true;
            } catch(SocketTimeoutException | SocketException e) {
                // Ignore these
            } catch(IOException e) {
                e.printStackTrace();
            }
            return false;
        }
    }
}