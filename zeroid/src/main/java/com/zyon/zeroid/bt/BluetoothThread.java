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
    private boolean D = false;
    private boolean DebugFullInfo = false;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    public static final int MESSAGE_READ = 4;
    public static final int MESSAGE_INFO = 5;
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

    volatile boolean thread_run = true;
    volatile boolean CallBack = false;
    volatile int mState = STATE_NONE;
    private recursivelyThreadClass recursivelyThread = null;

    private int keepAliveInterval = 0;
    private volatile long keepAliveLastSent = 0L;

    public BluetoothThread(Handler handler, String Address, int KeepAliveInterval){
        mHandler = handler;
        address = Address;
        keepAliveInterval = KeepAliveInterval;

        adapter = BluetoothAdapter.getDefaultAdapter();
        adapter.cancelDiscovery();
        BluetoothDevice device = adapter.getRemoteDevice(address);
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        setName("BluetoothThread");
        if (D) Log.d(TAG, "BluetoothThread start");

        int countValue = 0;

        byte[] buffer = new byte[1024];
        int bytes;

        try {
            Message message;

            //region prepare listen or connecting
            if (mState != STATE_NONE) {
                // Always cancel discovery because it will slow down a connection
                //adapter = BluetoothAdapter.getDefaultAdapter();
                adapter.cancelDiscovery();
            }

            try {
                if (mState == STATE_LISTEN) {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
                } else if (mState == STATE_CONNECTING) {
                    //BluetoothDevice device = adapter.getRemoteDevice(address);
                    //socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                }
            } catch (IOException e) {
                if (D) Log.e(TAG, "Cannot create ServerSocket", e);
                setConnectionState(STATE_NONE);
            }
            //endregion

            //region listen
            countValue = 0;
            if(mState == STATE_LISTEN)
                if (D) Log.d(TAG, "BluetoothThread listen");

            while (thread_run && mState == STATE_LISTEN) {
                countValue++;

                if (CallBack) {
                    message = new Message();
                    message.what = MESSAGE_INFO;
                    message.obj = "Listen: " + countValue;
                    mHandler.sendMessage(message);
                } else {
                    if (D) Log.d(TAG, "Listen: " + countValue);
                }

                try {
                    // This is a blocking call and will only return on a
                    // successful connection or timeout on InterruptedIOException
                    socket = serverSocket.accept();
                    setConnectionState(STATE_CONNECTED);

                    if (CallBack) {
                        message = new Message();
                        message.what = MESSAGE_INFO;
                        message.obj = "Connected to: " + socket.getRemoteDevice().getName();
                        mHandler.sendMessage(message);
                    } else {
                        if (D)
                            Log.d(TAG, "Listen: " + "Connected to: " + socket.getRemoteDevice().getName());
                    }
                } catch (IOException e) {
                }
            }
            //endregion

            //region connecting
            countValue = 0;
            if(mState == STATE_CONNECTING)
                if (D) Log.d(TAG, "BluetoothThread connecting");

            while (thread_run && mState == STATE_CONNECTING) {
                countValue++;
                if (CallBack) {
                    message = new Message();
                    message.what = MESSAGE_INFO;
                    message.obj = "Connecting: " + countValue;
                    mHandler.sendMessage(message);
                } else {
                    if (D) Log.d(TAG, "Connecting: " + countValue);
                }

                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket.connect();
                    setConnectionState(STATE_CONNECTED);

                    if (CallBack) {
                        message = new Message();
                        message.what = MESSAGE_INFO;
                        message.obj = "Connected to: " + socket.getRemoteDevice().getName();
                        mHandler.sendMessage(message);
                    } else {
                        if (D)
                            Log.d(TAG, "Connected: " + "Connected to: " + socket.getRemoteDevice().getName());
                    }
                } catch (IOException e) {
                    //do nothing and try again
                    //if (D) Log.d(TAG, "connect failed, retry");
                    //setConnectionState(STATE_NONE);
                    //return;
                }

                if(countValue > 19)
                    setConnectionState(STATE_NONE);
            }
            //endregion

            //region Connected
            if (mState == STATE_CONNECTED) {
                if (D) Log.d(TAG, "BluetoothThread Connected");

                try {
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                } catch (IOException e) {
                    setConnectionState(STATE_NONE);
                    if (D) Log.e(TAG, "Socket Stream was not created", e);
                }

                while (thread_run && mState == STATE_CONNECTED) {
                    StringBuilder netBuffer = new StringBuilder();

                    try {
                        //inputStream.read will block the thread until receive, bluetooth has no timeout. inputStream.available is more indicated
                        //avaliable() returns not exact mumber of avaliable bytes, becouse of it we create a predefined buffer
                        if (inputStream.available() > 0) {
                            bytes = inputStream.read(buffer);

                            if(bytes > 0) {
                                // construct a string from the valid bytes in the buffer
                                final String readMessage = new String(buffer, 0, bytes);
                                if (D) Log.i(TAG, "BluetoothBuffer.append: " + readMessage);

                                netBuffer.append(readMessage);

                                if (netBuffer.toString().contains("\n")) {
                                    StringTokenizer tokens = new StringTokenizer(netBuffer.toString(), "\n");
                                    while (tokens.hasMoreTokens()) {
                                        String tokenData = tokens.nextToken();

                                        message = new Message();
                                        message.what = MESSAGE_READ;
                                        message.obj = tokenData;
                                        mHandler.sendMessage(message);
                                    }
                                    netBuffer.delete(0, netBuffer.length());
                                }
                            }

			            /* encode US-ASCII needed?
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);

			                for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    //The variable data now contains our full command
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }*/
                        } else {
                            Thread.sleep((long)10);
                        }

                        //disconnection check
                        if (keepAliveInterval > 0){
                            if ((System.currentTimeMillis() - keepAliveLastSent) >= keepAliveInterval)
                                this.write("#chk\n");
                        }
                    } catch (EOFException e) {
                        if (D) Log.e(TAG, "disconnected (EOF)", e);
                        setConnectionState(STATE_NONE);
                    } catch (IOException e) {
                        if (D) Log.e(TAG, "InterruptedException", e);
                        setConnectionState(STATE_NONE);
                    } catch (InterruptedException e) {
                        if (D) Log.e(TAG, "InterruptedException", e);
                        setConnectionState(STATE_NONE);
                    }
                }
            }
            //endregion
        }finally {
            if (D) Log.d(TAG, "BluetoothThread Ending");
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

    synchronized public void close(){
        if (D) Log.d(TAG, "BluetoothThread Close Command");
        thread_run = false;
        setConnectionState(STATE_NONE);
    }

    public void write(byte[] buffer) {
        try {
            if (outputStream != null && getConnectionState() == STATE_CONNECTED) {
                outputStream.write(buffer);
                outputStream.flush();

                if (keepAliveInterval > 0) {
                    synchronized (this) {
                        this.keepAliveLastSent = System.currentTimeMillis();
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        } catch (NullPointerException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void write(byte[] buffer, int interval) {
        if(recursivelyThread == null)
            return;

        synchronized (recursivelyThread) {
            if (interval == 0) {
                if (recursivelyThread.recursivelyInterval == 0) {
                    //final byte[] buffer = Buffer;
                    if (D) Log.i(TAG, "netSend Bytes");
                    write(buffer);
                }

                recursivelyThread.recursivelyBuffer = buffer;
                recursivelyThread.SendLastTime = true;
            } else {
                recursivelyThread.SendLastTime = false;
                recursivelyThread.recursivelyBuffer = buffer;
                recursivelyThread.recursivelyInterval = interval;
            }
        }
    }

    public void write(String data) {
        if (data.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] dataByte = data.getBytes();
            write(dataByte);
        }
    }

    public void write(String data, int interval) {
        if (data.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] dataByte = data.getBytes();
            write(dataByte, interval);
        }
    }

    public class recursivelyThreadClass extends Thread {
        private BluetoothThread controllerActivity;
        private final String TAG = "recursivelyBtThread";
        private volatile boolean running = true;

        public volatile byte[] recursivelyBuffer;
        public volatile long recursivelyInterval = 0;
        public volatile long lastSentTime = 0L;

        volatile boolean SendLastTime = false;

        public recursivelyThreadClass(BluetoothThread ControllerActivity) {
            this.controllerActivity = ControllerActivity;
        }

        public void run() {
            if (D) Log.d(TAG, "starting");
            setName(TAG);
            long lastSentTime = 0L;

            while (running) {
                if (recursivelyInterval > 0) {
                    long diff = System.currentTimeMillis() - lastSentTime;

                    if (diff >= recursivelyInterval) {
                        synchronized (controllerActivity) {
                            controllerActivity.write(recursivelyBuffer);
                            lastSentTime = System.currentTimeMillis();
                        }

                        if (D) Log.i(TAG, "netSend Bytes recursively");
                        if (SendLastTime)
                            recursivelyInterval = 0;
                    }
                } else {
                    try {
                        Thread.sleep(10);
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
    }

    public void setConnectionState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public int getConnectionState() {
        return mState;
    }
}