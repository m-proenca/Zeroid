package com.zyon.zeroid.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;
import java.util.UUID;

public class BluetoothThread extends Thread {
    private static final String TAG = BluetoothThread.class.getSimpleName();

    // variables to debug
    private boolean D = false;
    private boolean DebugFullInfo = false;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    public static final int MESSAGE_READ = 4;
    public static final int MESSAGE_STATE_CHANGE = 10;
    public static final int MESSAGE_TOAST = 11;

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    // SPP UUID service
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // Name for the SDP record when creating server socket
    private static final String NAME = "ZeroidBluetoothSocket";

    private Handler mHandler;
    private String address;

    private BluetoothAdapter adapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket = null;

    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    private int SocketTimeout = 1000;
    private int SocketAcceptTimeout = 3000;
    private int SocketConnectTimeout = 3000;

    volatile boolean thread_run = true;
    volatile int SocketState = STATE_NONE;

    private int countValue = 0;
    private recursivelyThreadClass recursivelyThread = null;

    private int heartBeat_Interval = 1000;
    private int heartBeat_TimeOut = 5000;
    private long heartBeat_LastRead = 0L;
    private Object threadLock = null;

    public BluetoothThread(Handler handler, String Address, int HeartBeatTimeOut){
        mHandler = handler;
        address = Address;
        heartBeat_TimeOut = HeartBeatTimeOut;

        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public void run() {
        setName("BluetoothThread");
        if (D) Log.d(TAG, "BluetoothThread start");

        countValue = 0;

        try {
            if (SocketState == STATE_LISTEN)
                btListen();

            if (SocketState == STATE_CONNECTING)
                btConnecting();

            if (SocketState == STATE_CONNECTED)
                btConnected();
        }finally {
            if (D) Log.d(TAG, "BluetoothThread Ending");
            if (recursivelyThread != null) {
                recursivelyThread.close();
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                    inputStream = null;
                } catch (IOException e) {
                    if (D) Log.e(TAG, "unable to close() inputStream", e);
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                    outputStream = null;
                } catch (IOException e) {
                    if (D) Log.e(TAG, "unable to close() outputStream", e);
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                    socket = null;
                } catch (IOException e) {
                    if (D) Log.e(TAG, "unable to close() socket", e);
                }
            }

            if (serverSocket != null) {
                try {
                    serverSocket.close();
                    serverSocket = null;
                } catch (IOException e) {
                    if (D) Log.e(TAG, "unable to close() serverSocket", e);
                }
            }
            if (D) Log.d(TAG, "BluetoothThread released");
        }
    }

    private void btListen(){
        try {
            // Always cancel discovery because it will slow down a connection
            adapter.cancelDiscovery();
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
        } catch (IOException e) {
            if (D) Log.e(TAG, "Cannot create ServerSocket", e);
            setConnectionState(STATE_NONE);
            return;
        }

        countValue = 0;
        if (D) Log.d(TAG, "BluetoothThread listen");

        while (thread_run && SocketState == STATE_LISTEN) {
            countValue++;
            if (D) Log.d(TAG, "Listen: " + countValue);

            try {
                // This is a blocking call and will only return on a
                // successful connection or timeout on InterruptedIOException
                socket = serverSocket.accept(SocketAcceptTimeout);
                setConnectionState(STATE_CONNECTED);
                Log.d(TAG, "Listen: " + "Connected to: " + socket.getRemoteDevice().getName());

            } catch (IOException e) {
                //do nothing, try again
            }

            if (countValue > 9)
                setConnectionState(STATE_NONE);
        }
    }

    private void btConnecting(){
        // Always cancel discovery because it will slow down a connection
        adapter.cancelDiscovery();
        BluetoothDevice device = adapter.getRemoteDevice(address);

        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        } catch (IOException e) {
            if (D) Log.e(TAG, "Cannot create RfcommSocket", e);
            setConnectionState(STATE_NONE);
            return;
        }

        countValue = 0;
        if (D) Log.d(TAG, "BluetoothThread connecting");

        while (thread_run && SocketState == STATE_CONNECTING) {
            countValue++;
            if (D) Log.d(TAG, "Connecting: " + countValue);

            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                socket.connect();
                setConnectionState(STATE_CONNECTED);

                if (D) Log.d(TAG, "Connected: " + "Connected to: " + socket.getRemoteDevice().getName());
            } catch (IOException e) {
                //do nothing and try again
            }

            if(countValue > 9)
                setConnectionState(STATE_NONE);
        }
    }

    private void btConnected() {
        if (D) Log.d(TAG, "BluetoothThread Connected");

        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            if (D) Log.e(TAG, "Socket Stream was not created", e);
            setConnectionState(STATE_NONE);
            return;
        }

        byte[] buffer = new byte[1024];
        int bytes;

        while (thread_run && SocketState == STATE_CONNECTED) {
            StringBuilder netBuffer = new StringBuilder();

            try {
                //inputStream.read() will block the thread until receive, bluetooth has no timeout.
                //inputStream.avaliable() will not block the thread, but not returns the exact mumber of avaliable bytes
                if (inputStream.available() > 0) {
                    bytes = inputStream.read(buffer);

                    if (bytes > 0) {
                        synchronized (threadLock) {
                            heartBeat_LastRead = System.currentTimeMillis();
                        }

                        // construct a string from the valid bytes in the buffer
                        //final String readMessage = new String(buffer, 0, bytes);
                        final String readMessage = new String(buffer, "US-ASCII");
                        if (D) Log.i(TAG, "BluetoothBuffer.append: " + readMessage);

                        netBuffer.append(readMessage);

                        if (netBuffer.toString().contains("\n")) {
                            StringTokenizer tokens = new StringTokenizer(netBuffer.toString(), "\n");
                            while (tokens.hasMoreTokens()) {
                                String tokenData = tokens.nextToken();

                                if (DebugFullInfo) Log.i(TAG, readMessage + "\r\n");

                                if (readMessage.contains("#chk\n")) {
                                    if (D) Log.i(TAG, "MESSAGE_READ #chk");
                                    write("#beat\n");
                                } else if (readMessage.contains("#beat\n")) {
                                    if (D) Log.i(TAG, "MESSAGE_READ #beat");
                                } else {
                                    Message message = new Message();
                                    message.what = MESSAGE_READ;
                                    message.obj = tokenData;
                                    mHandler.sendMessage(message);
                                }
                            }
                            netBuffer.delete(0, netBuffer.length());
                        }
                    }
//                    final byte delimiter = 10; //This is the ASCII code for a newline character
//
//                    int readBufferPosition = 0;
//                    byte[] readBuffer = new byte[1024];
//
//                    int bytesAvailable = inputStream.available();
//                    if(bytesAvailable > 0)
//                    {
//                        byte[] packetBytes = new byte[bytesAvailable];
//                        inputStream.read(packetBytes);
//                        for(int i=0;i<bytesAvailable;i++)
//                        {
//                            byte b = packetBytes[i];
//                            if(b == delimiter)
//                            {
//                                byte[] encodedBytes = new byte[readBufferPosition];
//                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
//                                final String data = new String(encodedBytes, "US-ASCII");
//                                readBufferPosition = 0;
//
//                                handler.post(new Runnable() {
//                                    public void run() {
//                                        myLabel.setText(data);
//                                    }
//                                });
//                            }
//                            else
//                            {
//                                readBuffer[readBufferPosition++] = b;
//                            }
//                        }
//                    }
                }
                else {
                    Thread.sleep((long) 10);
                }
            } catch (EOFException e) {
                if (D) Log.e(TAG, "disconnected (EOF)", e);
                setConnectionState(STATE_NONE);
                break;
            } catch (IOException e) {
                if (D) Log.e(TAG, "InterruptedException", e);
                setConnectionState(STATE_NONE);
                break;
            } catch (InterruptedException e) {
                if (D) Log.e(TAG, "InterruptedException", e);
                setConnectionState(STATE_NONE);
                break;
            }

            //disconnection check
            if (heartBeat_TimeOut > 0) {
                synchronized (threadLock) {
                    long lastBeat = (System.currentTimeMillis() - heartBeat_LastRead);

                    if (lastBeat >= heartBeat_TimeOut) {
                        if (D) Log.e(TAG, "Beat TimeOut");
                        setConnectionState(STATE_NONE);
                        break;
                    } else if (lastBeat >= heartBeat_Interval) {
                        this.write("#chk\n");
                    }
                }
            }
        }
    }

    synchronized public void close(){
        if (D) Log.d(TAG, "btThread Close Command");
        thread_run = false;
        setConnectionState(STATE_NONE);
    }

    public void write(byte[] buffer) {
        try {
            if (outputStream != null && getConnectionState() == STATE_CONNECTED) {
                outputStream.write(buffer);
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
            setConnectionState(STATE_NONE);
        }
    }

    public void write(byte[] buffer, int interval) {
        if (recursivelyThread == null) {
            if (D) Log.e(TAG, "btThread.write(buffer,interval) recursivelyThread is null");
            return;
        }
        recursivelyThread.write(buffer, interval);
    }

    public void write(String data) {
        if (data.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] dataByte = data.getBytes();
            write(dataByte);
            if (D) Log.d(TAG, "btThread write String " + data);
        }
    }

    public void write(String data, int interval) {
        if (data.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] dataByte = data.getBytes();
            write(dataByte, interval);
            if (D) Log.d(TAG, "btThread write String " + data);
        }
    }

    public class recursivelyThreadClass extends Thread {
        private BluetoothThread controllerActivity;
        private final String TAG = "recursivelyBtThread";
        private volatile boolean running = true;

        private byte[] recursivelyBuffer;
        private long recursivelyInterval = 0;
        private Object threadLock = null;
        volatile boolean SendLastTime = false;

        public recursivelyThreadClass(BluetoothThread ControllerActivity) {
            this.controllerActivity = ControllerActivity;
            threadLock = new Object();
        }

        public void run() {
            if (D) Log.d(TAG, "recursivelyBtThreadClass_run");
            setName(TAG);
            long lastSentTime = 0L;

            while (running) {
                if (recursivelyInterval > 0) {
                    long diff = System.currentTimeMillis() - lastSentTime;

                    if (diff >= recursivelyInterval) {
                        synchronized (threadLock) {
                            if (D) Log.i(TAG, "Send Bytes recursively");
                            this.controllerActivity.write(recursivelyBuffer);
                            lastSentTime = System.currentTimeMillis();
                        }

                        if (SendLastTime)
                            recursivelyInterval = 0;
                    }
                } else {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        if (D) Log.d(TAG, "InterruptedException");
                    }
                }
            }
            if (D) Log.d(TAG, "Thread closed");
        }

        public void close() {
            this.running = false;
        }

        public void write(byte[] buffer, int interval){
            synchronized (threadLock) {
                if (interval == 0) {
                    if (recursivelyInterval == 0) {
                        //final byte[] buffer = Buffer;
                        if (D) Log.i(TAG, "netSend Bytes recursively interval == 0");
                        this.controllerActivity.write(buffer);
                    } else {
                        this.recursivelyBuffer = buffer;
                        this.SendLastTime = true;
                    }
                } else {
                    this.SendLastTime = false;
                    this.recursivelyBuffer = buffer;
                    this.recursivelyInterval = interval;
                }
            }
        }
    }

    public void setConnectionState(int state) {
        if (D) Log.d(TAG, "setState() " + SocketState + " -> " + state);
        SocketState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public int getConnectionState() {
        return SocketState;
    }
}