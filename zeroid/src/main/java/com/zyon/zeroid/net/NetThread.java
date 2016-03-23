package com.zyon.zeroid.net;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.zyon.zeroid.ControlCode_t;
import com.zyon.zeroid.Util.ByteExt;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Locale;

public class NetThread extends Thread {
    private static final String TAG = NetThread.class.getSimpleName();

    // variables to debug
    private boolean D = true;
    private boolean DebugFullInfo = false;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    public static final int MESSAGE_READ = 4;
    public static final int MESSAGE_CTRLCODE = 12;
    public static final int MESSAGE_INFO = 5;
    public static final int MESSAGE_STATE_CHANGE = 10;
    public static final int MESSAGE_TOAST = 11;

    private Handler mHandler;
    private String address;
    private int port;

    private ServerSocket serverSocket = null;
    private Socket socket = null;

    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    volatile boolean thread_run = true;
    volatile boolean CallBack = false;
    volatile int mState = STATE_NONE;

    private int countValue = 0;
    private recursivelyThreadClass recursivelyThread = null;

    private int keepAliveInterval = 1000;
    private volatile long keepAliveLastSent = 0L;

    public NetThread(Handler handler, String Address, int Port, int KeepAliveInterval) {
        mHandler = handler;
        address = Address;
        port = Port;
        keepAliveInterval = KeepAliveInterval;
    }

    @Override
    public void run() {
        setName("netThread");
        if (D) Log.d(TAG, "netThread start");

        try {
            Message message;

            //region prepare listen or connecting
            if (mState == STATE_LISTEN) {
                try {
                    serverSocket = new ServerSocket(port);
                    serverSocket.setSoTimeout(3000);
                } catch (IOException e) {
                    if (D) Log.e(TAG, "Cannot create ServerSocket", e);
                    setConnectionState(STATE_NONE);
                }
            }
            //endregion

            //region listen
            countValue = 0;
            if (mState == STATE_LISTEN)
                if (D) Log.d(TAG, "netThread listen");

            while (thread_run && mState == STATE_LISTEN) {
                countValue++;

                if (DebugFullInfo) {
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
                    socket.setSoTimeout(3000);
                    setConnectionState(STATE_CONNECTED);

                    serverSocket.close(); //no new connections is desired

                    message = new Message();
                    message.what = MESSAGE_INFO;
                    message.obj = "Connected to: " + socket.getInetAddress();
                    mHandler.sendMessage(message);
                } catch (IOException e) {
                    //ignore all Exceptions and try again
                }

                if(countValue > 19)
                    setConnectionState(STATE_NONE);
            }
            //endregion

            //region connecting
            countValue = 0;
            if (mState == STATE_CONNECTING) {
                if (D) Log.d(TAG, "netThread connecting");
                socket = new Socket();
            }

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

                    socket.connect((new InetSocketAddress(InetAddress.getByName(address), port)), 2000);
                    setConnectionState(STATE_CONNECTED);

                    message = new Message();
                    message.what = MESSAGE_INFO;
                    message.obj = "Connected to: " + socket.getInetAddress();
                    mHandler.sendMessage(message);
                } catch (IOException e) {
                    //do nothing, try again after 1 sec
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }

                if(countValue > 9)
                    setConnectionState(STATE_NONE);
            }
            //endregion

            //region Connected
            if (mState == STATE_CONNECTED) {
                if (D) Log.d(TAG, "netThread Connected");

                try {
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();

                    recursivelyThread = new recursivelyThreadClass(this);
                    recursivelyThread.start();
                } catch (IOException e) {
                    setConnectionState(STATE_NONE);
                    if (D) Log.e(TAG, "Socket Stream was not created", e);
                }
            }

            //StringBuilder netBuffer = new StringBuilder();
            byte[] buffer = new byte[1024];
            int bytes;

            // Buffer for reading data
            byte[] BufferData = new byte[32];
            int BufferIndex = 0;
            byte ByteLength = 0;
            byte CharFound = 0;
            int SizeOfDescriptor = 10;

            while (thread_run && mState == STATE_CONNECTED) {
                try {
                    //inputStream.read will block the thread until receive or timeout
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
//removed to use object
//                        if(bytes > 1024){
//                            if (D) Log.i(TAG, "Stream buffer overflow" + "\r\n");
//                        }
//                        // construct a string from the valid bytes in the buffer
//                        final String readMessage = new String(buffer, 0, bytes);
//                        if (D) Log.i(TAG, "netBuffer.append: " + readMessage);
//
//                        netBuffer.append(readMessage);
//
//                        if (netBuffer.toString().contains("\n")) {
//                            StringTokenizer tokens = new StringTokenizer(netBuffer.toString(), "\n");
//                            while (tokens.hasMoreTokens()) {
//                                String tokenData = tokens.nextToken();
//
//                                message = new Message();
//                                message.what = MESSAGE_READ;
//                                message.obj = tokenData;
//                                mHandler.sendMessage(message);
//                            }
//                            netBuffer.delete(0, netBuffer.length());
//                        }

                        for (int streamIndex = 0; streamIndex < bytes; streamIndex++) {
                            int byteRead;
                            byteRead = buffer[streamIndex];

                            //region Read Bytes
                            if (BufferIndex > BufferData.length -1) {
                                if (D) Log.i(TAG, "Buffer overflow");

                                BufferData = new byte[32];
                                BufferIndex = 0;
                                ByteLength = 0;
                                CharFound = 0;
                            }

                            if (ByteLength == 0 && CharFound == 0) /*wait for sync byte*/ {
                                /*if char # (DEC 35, HEX 0x23) found, will wait for '\n'*/
                                if ((byte) byteRead == 0x23 /*# - 35 - 0x23*/) {
                                    if (DebugFullInfo) Log.i(TAG, "CharFound" + "\r\n");
                                    CharFound = 1;
                                }
                                /*if char SOH (DEC 1, HEX 0x21 found, will wait for SizeOfDescriptor*/
                                else if ((byte)byteRead == 0x01){
                                    if (DebugFullInfo) Log.i(TAG, "ByteLength" + "\r\n");
                                    ByteLength = 1;
                                }
                            }

                            //prefix found start buffering
                            if ((ByteLength != 0) | (CharFound != 0)) {
                                BufferData[BufferIndex] = (byte) byteRead;

                                if (DebugFullInfo) Log.i(TAG,"#read " + String.valueOf(BufferIndex));
                            }

                            /*wait for a full pack*/
                            if (ByteLength != 0) {
                                if (DebugFullInfo)
                                    Log.i(TAG, "ByteSync " + String.valueOf(BufferIndex));

                                if (BufferIndex >= SizeOfDescriptor - 1) {
                                    if (D) Log.i(TAG, "read bytes");

                                    int byteStart = BufferIndex - (SizeOfDescriptor - 1);
                                    int crc = ((BufferData[BufferIndex] & 0xff) << 8) | (BufferData[BufferIndex - 1] & 0xff);
                                    int crccheck = ByteExt.getCRCMODBUS(BufferData, byteStart, SizeOfDescriptor - 2);

                                    if (DebugFullInfo) {
                                        if (D)
                                            Log.i(TAG, "from byte: " + String.valueOf(byteStart) + " to byte: " + String.valueOf(BufferIndex) + "\r\n");

                                        StringBuilder sb = new StringBuilder(32);

                                        int bytePos = 0;
                                        for (int i = byteStart; i < SizeOfDescriptor + byteStart; i++) {
                                            sb.append(" byte" + String.valueOf(bytePos) + ": " + ByteExt.printBits(BufferData[i]));
                                            bytePos++;
                                        }

                                        sb.append(" CRC: " + String.valueOf(crc) + ", CRC check: " + String.valueOf(crccheck) + "\r\n");
                                        if (D) Log.i(TAG, sb.toString());
                                    }

                                    //check for CRC
                                    if (crccheck == crc) {
                                        ControlCode_t ControlCode = new ControlCode_t();

                                        ControlCode.fromBytes(BufferData);

                                        if (D) Log.i(TAG,
                                                "headerType: " + String.valueOf(ControlCode.headerType) + "\n" +
                                                        "Type: " + String.valueOf(ControlCode.Type) + "\n" +
                                                        "b1: " + String.valueOf(ControlCode.b1) + "\n" +
                                                        "b2: " + String.valueOf(ControlCode.b2) + "\n" +
                                                        "b3: " + String.valueOf(ControlCode.b3) + "\n" +
                                                        "b4: " + String.valueOf(ControlCode.b4) + "\n" +
                                                        "b5: " + String.valueOf(ControlCode.b5) + "\n" +
                                                        "b6: " + String.valueOf(ControlCode.b6) + "\n" +
                                                        "CRC: " + String.valueOf((int) ControlCode.CRC) + "\n"
                                        );

                                        message = new Message();
                                        message.what = MESSAGE_CTRLCODE;
                                        message.obj = ControlCode;
                                        mHandler.sendMessage(message);

                                        BufferData = new byte[32];
                                        BufferIndex = 0;
                                        ByteLength = 0;
                                        CharFound = 0;
                                    } else {
                                        if (D) Log.i(TAG, "Invalid CRC");
                                    }
                                }
                            }
                            else if (CharFound != 0) {
                                if (DebugFullInfo) Log.i(TAG,"CharSync " + String.valueOf(BufferIndex));
                                if ((char) BufferData[BufferIndex] == '\n') {
                                    if (DebugFullInfo) Log.i(TAG,"read chars" + "\r\n");

                                    final String readMessage = new String(BufferData, 0, BufferIndex + 1);

                                    try {
                                        if (DebugFullInfo) Log.i(TAG,readMessage + "\r\n");

                                        message = new Message();
                                        message.what = MESSAGE_READ;
                                        message.obj = readMessage;
                                        mHandler.sendMessage(message);
                                    } catch (NullPointerException npe) {
                                        if (D) Log.e(TAG, "ignored SocketTimeoutException", npe);
                                    }
                                    BufferData = new byte[32];
                                    BufferIndex = 0;
                                    ByteLength = 0;
                                    CharFound = 0;
                                }
                            }

                            //set next buffer index
                            if ((ByteLength != 0) | (CharFound != 0)) {
                                BufferIndex++;
                            }
                            //endregion ReadBytes
                        }
                    }
                    else if (bytes == -1) {
                        if (D) Log.e(TAG, "disconnected (imput -1)");
                        setConnectionState(STATE_NONE);
//                    }
//                    else (){
//                        BufferData = new byte[32];
//                        BufferIndex = 0;
//                        ByteLength = 0;
//                        CharFound = 0;
                    }


                } catch (EOFException e) {
                    if (D) Log.e(TAG, "disconnected (EOF)", e);
                    setConnectionState(STATE_NONE);
                } catch (UnknownHostException e) {
                    if (D) Log.e(TAG, "ignored UnknownHostException", e);
                } catch (SocketTimeoutException e) {
                    if (D) Log.e(TAG, "SocketTimeoutException", e);
                    setConnectionState(STATE_NONE);
                } catch (SocketException e){
                    if (D) Log.e(TAG, "SocketException", e);
                    setConnectionState(STATE_NONE);
                } catch (IOException e) {
                    if (D) Log.e(TAG, "ignored IOException", e);
                }

                if (!socket.isConnected()) {
                    Log.e(TAG, "Listner: Disconected");
                    setConnectionState(STATE_NONE);
                }

                //disconnection check
                if (keepAliveInterval > 0){
                    if ((System.currentTimeMillis() - keepAliveLastSent) >= keepAliveInterval)
                        this.write("#chk\n");
                }
            }
            //endregion
        } finally {
            if (recursivelyThread != null) {
                recursivelyThread.close();
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                    inputStream = null;
                } catch (IOException e2) {
                    if (D) Log.e(TAG, "unable to close() inputStream", e2);
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                    outputStream = null;
                } catch (IOException e2) {
                    if (D) Log.e(TAG, "unable to close() socket", e2);
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                    socket = null;
                } catch (IOException e2) {
                    if (D) Log.e(TAG, "unable to close() socket", e2);
                }
            }

            if (serverSocket != null) {
                try {
                    serverSocket.close();
                    serverSocket = null;
                } catch (IOException e2) {
                    if (D) Log.e(TAG, "unable to close() socket", e2);
                }
            }
            if (D) Log.d(TAG, "netThread end");
        }
    }

    public synchronized void close() {
        if (D) Log.d(TAG, "netThread Close Command");
        thread_run = false;
        setConnectionState(STATE_NONE);
    }

    public void write(byte[] buffer) {
        try {
            if(outputStream != null && getConnectionState() == STATE_CONNECTED) {
                outputStream.write(buffer);
                outputStream.flush();

                if(keepAliveInterval > 0) {
                    synchronized (this) {
                        this.keepAliveLastSent = System.currentTimeMillis();
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        } catch (NullPointerException e){
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
            if (D) Log.d(TAG, "netThread write String " + data);
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
        private NetThread controllerActivity;
        private final String TAG = "recursivelyNetThread";
        private volatile boolean running = true;

        public volatile byte[] recursivelyBuffer;
        public volatile long recursivelyInterval = 0;
        public volatile long lastSentTime = 0L;

        volatile boolean SendLastTime = false;

        public recursivelyThreadClass(NetThread ControllerActivity) {
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

    public static String tryGetIpV4Address() {
        try {
            final Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()){
                final NetworkInterface intf = en.nextElement();
                final Enumeration<InetAddress> enumIpAddr =
                        intf.getInetAddresses();
                while (enumIpAddr.hasMoreElements()){
                    final  InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()){
                        final String addr = inetAddress.getHostAddress().toUpperCase(Locale.getDefault());
                        if (InetAddressUtils.isIPv4Address(addr)){
                            return addr;
                        }
                    }
                }
            }
        }
        catch (final Exception e){
            // Ignore
        }
        return "";
    }
}
