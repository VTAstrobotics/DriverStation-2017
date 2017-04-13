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

/**
 * Implements the network protocol
 */
public class Protocol {

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

    public Protocol() throws IOException {
        // send socket creation
        socket_send = new DatagramSocket();
        socket_send.setReuseAddress(true);
        // instantiate sendqueue
        sendQueue = new LinkedBlockingQueue<>();
        // send thread instantaite and begin
        sendThread = new Thread(new SendWorker(), "Send Thread");
        sendThread.start();
        // create the control data object
        controlData = new ControlData();

        // ping socket creation
        socket_ping = new DatagramSocket();
        socket_ping.setReuseAddress(true);
        // ping thread instantiate and begin
        pinging = new Thread(new PingWorker(), "Ping Thread");
        pinging.start(); // TODO verify pinging works

        // receive socket creation
        socket_receive = new DatagramSocket();
        socket_receive.setReuseAddress(true);
        // receiving thread instantate and begin
        receiving = new Thread(new ReceiveWorker(), "Receive Thread");
//        receiving.start(); // TODO verify receiving works
        // create the receive data
        receiveData = new ReceiveData();
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
        private byte voltage = 0x0;

        public ReceiveData() {
            this.isDeadMansDown = false;
            this.voltage = 0;
        }

        public void setDeadMansDown(boolean b) {
            this.isDeadMansDown = b;
        }

        public void setVoltage(byte b) {
            this.voltage = b;
        }

        // returns voltage as an int
        public int getVoltage() {
            return ((int) (this.voltage));
        }

        public boolean getIsDeadMansDown() {
            return isDeadMansDown;
        }

        public String toString() {
            return "Dead Man's: " + isDeadMansDown + " , Voltage: " + voltage;
        }
    }

    private static class ControlData {
        // for if the axis doesn't return to exactly 0 used + or -
        private static final double AXIS_BOUNDS = 0.1;
        // max/min axis values can be
        private static final double AXIS_MAX = 1.0;
        // max value byte should be
        private static final int AXIS_BYTE_MAX = 100;
        // for the dead zone in the dpad
        private static final double DPAD_BOUNDS = 0.1;

        // array for data, everything can be stored in byte,
        // though for buttons, only one bit will be used
        public byte data[];
        public boolean buttonData[];

        // default constructor
        public ControlData() {
            this.data = new byte[ControlIDs.SIZE];
        }

        // copy constructor
        public ControlData(ControlData oldData) {
            this.data = new byte[oldData.data.length];
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
                int tVal = (int) (AXIS_BYTE_MAX * (value / AXIS_MAX));
                if(tVal > AXIS_BYTE_MAX) {
                    data[ID] = AXIS_BYTE_MAX;
                } else {
                    data[ID] = ((byte) tVal);
                }
            } else if(value < -AXIS_BOUNDS) {
                int tVal = (int) (AXIS_BYTE_MAX * (value / -AXIS_MAX));
                if(tVal > AXIS_BYTE_MAX) {
                    data[ID] = -AXIS_BYTE_MAX;
                } else {
                    data[ID] = ((byte) -tVal);
                }
            } else {
                data[ID] = 0x00;
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

        // create the binary string with crc at the end
        public byte[] toBits() {
//            Log.d(TAG, "Data: " + Arrays.toString(data));
            byte[] bits = new byte[11];

            // do stuff to array
            // 6 bytes for axes
            bits[0] = data[ControlIDs.LTHUMBX];
            bits[1] = data[ControlIDs.LTHUMBY];
            bits[2] = data[ControlIDs.RTHUMBX];
            bits[3] = data[ControlIDs.RTHUMBY];
            bits[4] = data[ControlIDs.LTRIGGER];
            bits[5] = data[ControlIDs.RTRIGGER];

            // 2 bytes for buttons
            byte buttons1 = 0, buttons2 = 0;
            buttons2 += data[ControlIDs.LTHUMBBTN];
            buttons2 = (byte) (buttons2 << 1);
            buttons2 += data[ControlIDs.RTHUMBBTN];
            bits[7] = buttons2;
            buttons1 += data[ControlIDs.START];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.BACK];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.RB];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.LB];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.Y];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.X];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.B];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.A];
            bits[6] = buttons1;

            // 1 byte for dpad
            byte dpad = 0;
            dpad += data[ControlIDs.DPAD_UP];
            dpad = (byte) (dpad << 2);
            dpad += data[ControlIDs.DPAD_DOWN];
            dpad = (byte) (dpad << 2);
            dpad += data[ControlIDs.DPAD_LEFT];
            dpad = (byte) (dpad << 2);
            dpad += data[ControlIDs.DPAD_RIGHT];
            bits[8] = dpad;

            // the 2 bit crc
            byte[] dataBare = new byte[9];
            for(int i = 0; i < dataBare.length; i++) {
                dataBare[i] = bits[i];
            }
            short crc16 = (short) CRC16CCITT.crc16(dataBare);
            bits[9] = (byte) (crc16 & 0xff);
            bits[10] = (byte) ((crc16 >> 8) & 0xff);

            return bits;
        }

        // return a printable string, for debugging
        public String toString() {
            String str = "";
            for(int i = 0; i < data.length; i++) {
                str = str + "\n" + i + ": " + data[i];
            }
            return str;
        }
    }

    private class PingWorker implements Runnable {
        @Override
        public void run() {
            // variables
            double lastTime = System.currentTimeMillis();
            // while thread can send
            while(!Thread.interrupted() && !socket_ping.isClosed()) {
                if(System.currentTimeMillis() - lastTime > PING_FREQUENCY) {
                    //ping
                    // magic number
                    byte[] b = {((byte) (216))};
                    try {
                        socket_send.send(new DatagramPacket(b, 1, ROBOT_ADDRESS, ROBOT_PING_SEND));
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                    //reset time
                    lastTime = System.currentTimeMillis();
                }
                // sleep for majority of the remaining frequency
                try {
                    long timeToWait = (long) (lastTime + PING_FREQUENCY - System.currentTimeMillis());
                    if(timeToWait > 0) {
                        Thread.sleep(timeToWait);
                    }
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
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
                // 2-3 = crc
                // TODO check the crc
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
                byte[] dataBytes = data.toBits();
                try {
                    socket_send.send(new DatagramPacket(dataBytes, dataBytes.length, ROBOT_ADDRESS, ROBOT_PORT_SEND));
                } catch(IOException e) {
                    e.printStackTrace();
                }
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
