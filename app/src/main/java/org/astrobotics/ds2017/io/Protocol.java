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

    private static final int DEADMAN = KeyEvent.KEYCODE_BUTTON_L1;
    private static final int ROBOT_PORT_SEND = 6800, ROBOT_PORT_RECEIVE = 6850;
    private static final int ROBOT_PING_SEND = 6900;
    private static final int PING_FREQUENCY = 200; // 5 times per second
    private static final int CONNCHECK_FREQUENCY = 2000; // every 2 seconds
    private static InetAddress ROBOT_ADDRESS = null;

    private DatagramSocket socket_send, socket_ping, socket_receive;
    private LinkedBlockingQueue<ControlData> sendQueue;
    private Thread sendThread, pinging, receiving, connCheck;

    // instance of current control data
    private ControlData controlData;
    // instance of most recent data received
    private ReceiveData receiveData;

    static {
        try {
            ROBOT_ADDRESS = InetAddress.getByAddress(new byte[] {10, 0, 0, 30});
        } catch(UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("ds2017");
    }

    public void onStart(final ConnectedNode connectedNode) {
        //std_msgs.String._TYPE
        final Publisher<std_msgs.String> publisher =
                connectedNode.newPublisher("/robot/teleop", robot_msgs.Teleop._TYPE);
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
            case KeyEvent.KEYCODE_BUTTON_MODE:
                wasChanged = controlData.setButton(ControlIDs.XBOX, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                wasChanged = controlData.setButton(ControlIDs.LTHUMBBTN, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                wasChanged = controlData.setButton(ControlIDs.RTHUMBBTN, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                wasChanged = controlData.setButton(ControlIDs.L2, pressed);
                break;
            default:
                return;
        }

        // send the data on change
        if(wasChanged) {
            sendData();
            // send twice if the button was released
            if(!pressed) {
                sendData();
                // send again if the button released was deadman
                if(keycode == DEADMAN) {
                    sendData();
                }
            }
        }
    }

    public void sendData() {
        sendQueue.offer(new ControlData(controlData));
    }

    // ***NOTE*** change size if IDs are changed
    private static class ControlIDs {
        public static final int LTHUMBX = 0;
        public static final int LTHUMBY = 1;
        public static final int RTHUMBX = 2;
        public static final int RTHUMBY = 3;
        public static final int RTRIGGER = 4;
        public static final int LTRIGGER = 5;
        public static final int A = 6;
        public static final int B = 7;
        public static final int X = 8;
        public static final int Y = 9;
        public static final int LB = 10;
        public static final int RB = 11;
        public static final int BACK = 12;
        public static final int START = 13;
        public static final int XBOX = 14;
        public static final int LTHUMBBTN = 15;
        public static final int RTHUMBBTN = 16;
        public static final int L2 = 17;
        public static final int R2 = 18;
        public static final int DPAD_UP = 19;
        public static final int DPAD_DOWN = 20;
        public static final int DPAD_LEFT = 21;
        public static final int DPAD_RIGHT = 22;
        public static final int SIZE = 23;
    }

    // holds details given from the robot to DS
    private static class ReceiveData {
        public static final int SIZE = 4;

        // if the dead man's switch is on or off
        private boolean isDeadMansDown;
        // holds the battery voltage
        private float voltage = 0x0;

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
    }

    private static class ControlData {
        // for if the axis doesn't return to exactly 0 used + or -
        private static final double AXIS_BOUNDS = 0.1;
        // max/min axis values can be
        private static final double AXIS_MAX = 1.0;
        // max value float should be
        private static final int AXIS_FLOAT_MAX = 100;
        // for the dead zone in the dpad
        private static final double DPAD_BOUNDS = 0.1;

        // array for data, everything can be stored in float,
        // though for buttons, only one bit will be used
        public float data[];
        public boolean buttonData[];

        // default constructor
        public ControlData() {
            this.data = new float[ControlIDs.SIZE];
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
        public void setAxis(int ID, double value) {
            if(value > AXIS_BOUNDS) {
                // truncate and make for 0 to 100
                int tVal = (int) (AXIS_FLOAT_MAX * (value / AXIS_MAX));
                if(tVal > AXIS_FLOAT_MAX) {
                    data[ID] = AXIS_FLOAT_MAX;
                } else {
                    data[ID] = ((float) tVal);
                }
            } else if(value < -AXIS_BOUNDS) {
                int tVal = (int) (AXIS_FLOAT_MAX * (value / -AXIS_MAX));
                if(tVal > AXIS_FLOAT_MAX) {
                    data[ID] = -AXIS_FLOAT_MAX;
                } else {
                    data[ID] = ((float) -tVal);
                }
            } else {
                data[ID] = 0;
            }
        }

        // dpad comes as a float, but should be set to on or off
        public void setDpad(int eventCode, float value) {
            if(eventCode == MotionEvent.AXIS_HAT_X) {
                if(value > DPAD_BOUNDS) {
                    buttonData[ControlIDs.DPAD_RIGHT] = true;
                } else if(value < -DPAD_BOUNDS) {
                    buttonData[ControlIDs.DPAD_LEFT] = true;
                } else {
                    buttonData[ControlIDs.DPAD_LEFT] = false;
                    buttonData[ControlIDs.DPAD_RIGHT] = false;
                }
            } else if(eventCode == MotionEvent.AXIS_HAT_Y) {
                if(value > DPAD_BOUNDS) {
                    buttonData[ControlIDs.DPAD_DOWN] = true;
                } else if(value < -DPAD_BOUNDS) {
                    buttonData[ControlIDs.DPAD_UP] = true;
                } else {
                    buttonData[ControlIDs.DPAD_UP] = false;
                    buttonData[ControlIDs.DPAD_DOWN] = false;
                }
            }
        }

        // return a printable string, for debugging
        public java.lang.String toString() {
            java.lang.String str = "";
            for(int i = 0; i < data.length; i++) {
                str = str + "\n" + i + ": " + data[i];
            }
            return str;
        }
    }

    private class PingWorker implements Runnable {
        @Override
        public void run() {
        }
    }

    // recieve data from robot
    private class ReceiveWorker implements Runnable {
        @Override
        public void run() {
            // initialize socket on dedicated thread
            try {
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
            }
        }
    }
    // send the data from the queue in a thread
    private class SendWorker implements Runnable {
        @Override
        public void run() {
            ControlData data;

            // while the thread can send
            while(!Thread.interrupted() && !socket_send.isClosed()) {
                // keep running if something is taken from stack
                try {
                    data = sendQueue.take();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
//                Log.d(TAG, "Sending Data");
                //float[] datafloats = data.toBits();
                //try {
                //    socket_send.send(new DatagramPacket(datafloats, datafloats.length, ROBOT_ADDRESS, ROBOT_PORT_SEND));
                //} catch(IOException e) {
                 //   e.printStackTrace();
                //}
            }
        }
    }

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