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
    public static final int MESSAGE_STATE_CHANGE = 10;
    public static final int MESSAGE_TOAST = 11;
    public static final int MESSAGE_CTRLCODE = 12;

    private Handler mHandler;
    private String address;
    private int port;

    private ServerSocket serverSocket = null;
    private Socket socket = null;

    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    private int SocketTimeout = 1000;
    private int SocketAcceptTimeout = 0;
    private int SocketConnectTimeout = 5000;
    private boolean SocketReconnect = true;

    private int socketState = STATE_NONE;
    private int socketMode = STATE_NONE;
    private boolean thread_run = true;

    private recursivelyThreadClass recursivelyThread = null;

    private int heartBeat_Interval = 2000;
    private int heartBeat_TimeOut = 3000;
    private long heartBeat_LastRead = 0L;
    private Object threadLock = null;

    public NetThread(Handler handler, String Address, int Port, int HeartBeatTimeOut, int SocketMode) {
        threadLock = new Object();

        mHandler = handler;
        address = Address;
        port = Port;
        heartBeat_TimeOut = HeartBeatTimeOut;
        socketMode = SocketMode;
    }

    @Override
    public void run() {
        setName("netThread");
        if (D) Log.d(TAG, "netThread start");

        try {
            while(thread_run) {
                if (socketState == STATE_LISTEN)
                    netListen();

                if (socketState == STATE_CONNECTING)
                    netConnecting();

                if (socketState == STATE_CONNECTED)
                    netConnected();

                try {
                    Thread.sleep(10);
                }
                catch (InterruptedException e) {
                    if (D) Log.d(TAG, "InterruptedException");
                }
            }
        }
        finally {
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

    private void netListen() {
        if (D) Log.d(TAG, "netThread listen");

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(SocketAcceptTimeout);
        } catch (IOException e) {
            if (D) Log.e(TAG, "Cannot create ServerSocket", e);
            setConnectionState(STATE_NONE);
            return;
        }

        while (thread_run && socketState == STATE_LISTEN) {
            if (D) Log.d(TAG, "Listen");

            try {
                // This is a blocking call and will only return on a
                // successful connection or timeout on InterruptedIOException
                socket = serverSocket.accept();
                setConnectionState(STATE_CONNECTED);
                serverSocket.close();

                address =  socket.getInetAddress().getHostAddress();
            } catch (IOException e) {
                //ignore Exceptions and try again
            }
        }
    }

    private void netConnecting() {
        if (D) Log.d(TAG, "netThread connecting");

        while (thread_run && socketState == STATE_CONNECTING) {
            if (D) Log.d(TAG, "Connecting...");
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                socket = new Socket();
                socket.connect((new InetSocketAddress(InetAddress.getByName(address), port)), SocketConnectTimeout);
                setConnectionState(STATE_CONNECTED);
                break;
            } catch (IOException ex) {
                //do nothing, try again
                if (D) Log.d(TAG, "IOException" + ex);
                try { Thread.sleep(250); }
                catch (InterruptedException e) {}
            } catch (IllegalArgumentException ex){
                //do nothing, try again
                if (D) Log.d(TAG, "IllegalArgumentException" + ex);
                try { Thread.sleep(250); }
                catch (InterruptedException e) {}
            }
        }
    }

    private void netConnected() {
        if (D) Log.d(TAG, "netThread Connected");

        try {
            socket.setSoTimeout(SocketTimeout);

            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            recursivelyThread = new recursivelyThreadClass(this);
            recursivelyThread.start();

            synchronized (threadLock) {
                this.heartBeat_LastRead = System.currentTimeMillis();
            }
        } catch (IOException e) {
            if (D) Log.e(TAG, "Socket Stream was not created", e);
            setConnectionState(STATE_NONE);
            return;
        }

        //StringBuilder netBuffer = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytes = 0;

        // Buffer for reading data
        byte[] BufferData = new byte[32];
        int BufferIndex = 0;
        byte ByteLength = 0;
        byte CharFound = 0;
        int SizeOfDescriptor = 10;

        while (thread_run && socketState == STATE_CONNECTED) {
            try {
                //inputStream.read() will block the thread until receive or timeout
                bytes = inputStream.read(buffer);
                if (bytes > 0) {
                    //removed to use bytes
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
                    synchronized (threadLock) {
                        heartBeat_LastRead = System.currentTimeMillis();
                    }

                    for (int streamIndex = 0; streamIndex < bytes; streamIndex++) {
                        int byteRead;
                        byteRead = buffer[streamIndex];

                        //region Read Bytes
                        if (BufferIndex > BufferData.length - 1) {
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
                            else if ((byte) byteRead == 0x01) {
                                if (DebugFullInfo) Log.i(TAG, "ByteLength" + "\r\n");
                                ByteLength = 1;
                            }
                        }

                        //prefix found start buffering
                        if ((ByteLength != 0) | (CharFound != 0)) {
                            BufferData[BufferIndex] = (byte) byteRead;

                            if (DebugFullInfo)
                                Log.i(TAG, "#read " + String.valueOf(BufferIndex));
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

                                    Message message = new Message();
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
                        } else if (CharFound != 0) {
                            if (DebugFullInfo)
                                Log.i(TAG, "CharSync " + String.valueOf(BufferIndex));
                            if ((char) BufferData[BufferIndex] == '\n') {
                                if (DebugFullInfo) Log.i(TAG, "read chars" + "\r\n");

                                final String readMessage = new String(BufferData, 0, BufferIndex + 1);

                                if (DebugFullInfo) Log.i(TAG, readMessage + "\r\n");

                                if (readMessage.contains("#PING"))
                                    write(readMessage.replace("PING", "PONG"));
                                else if (readMessage.contains("#chk\n")) {
                                    if (D) Log.i(TAG, "MESSAGE_READ #chk");
                                    write("#beat\n");
                                } else if (readMessage.contains("#beat\n")) {
                                    if (D) Log.i(TAG, "MESSAGE_READ #beat");
                                } else {
                                    Message message = new Message();
                                    message.what = MESSAGE_READ;
                                    message.obj = readMessage;
                                    mHandler.sendMessage(message);
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
            } catch (EOFException e) {
                if (D) Log.e(TAG, "disconnected (EOF)", e);
                setConnectionState(STATE_NONE);
                break;
            } catch (UnknownHostException e) {
                if (D) Log.e(TAG, "ignored UnknownHostException", e);
            } catch (SocketTimeoutException e) {
                //if (D) Log.e(TAG, "SocketTimeoutException", e);
                //setConnectionState(STATE_NONE);
            } catch (SocketException e) {
                if (D) Log.e(TAG, "SocketException", e);
                setConnectionState(STATE_NONE);
                break;
            } catch (IOException e) {
                if (D) Log.e(TAG, "ignored IOException", e);
            }

            //disconnection check
            if (bytes == -1) {
                if (D) Log.e(TAG, "disconnected (imput -1)");
                setConnectionState(STATE_NONE);
                break;
            }

            if (!socket.isConnected()) {
                if (D) Log.e(TAG, "disconnected socket");
                setConnectionState(STATE_NONE);
                break;
            }

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

    public boolean getReconnect(){
        synchronized (threadLock) {
            return SocketReconnect;
        }
    }

    public void setReconnect(boolean Value){
        synchronized (threadLock) {
            SocketReconnect = Value;
        }
    }

    public synchronized void close() {
        if (D) Log.d(TAG, "netThread Close Command");
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
            if (D) Log.e(TAG, "netThread.write(buffer,interval) recursivelyThread is null");
            return;
        }
        recursivelyThread.write(buffer, interval);
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
            if (D) Log.d(TAG, "netThread write String " + data);
        }
    }

    public class recursivelyThreadClass extends Thread {
        private NetThread netThread;
        private final String TAG = "recursivelyNetThread";
        private volatile boolean running = true;

        private byte[] recursivelyBuffer;
        private long recursivelyInterval = 0;
        private Object threadLock = null;
        volatile boolean SendLastTime = false;

        public recursivelyThreadClass(NetThread ControllerActivity) {
            this.netThread = ControllerActivity;
            threadLock = new Object();
        }

        public void run() {
            if (D) Log.d(TAG, "recursivelyNetThreadClass_run");
            setName(TAG);
            long lastSentTime = 0L;

            while (running) {
                if (recursivelyInterval > 0) {
                    long diff = System.currentTimeMillis() - lastSentTime;

                    if (diff >= recursivelyInterval) {
                        synchronized (threadLock) {
                            if (D) Log.i(TAG, "Send Bytes recursively");
                            this.netThread.write(recursivelyBuffer);
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
                        this.netThread.write(buffer);
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
        if (socketState != state) {
            if (state == STATE_NONE && SocketReconnect == true) {
                state = socketMode;
            }

            if (D) Log.d(TAG, "setState() " + socketState + " -> " + state);
            socketState = state;

            // Give the new state to the Handler so the UI Activity can update
            mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        }
    }

    public int getConnectionState() {
        return socketState;
    }

    public String getRemoteAddress(){
        return address;
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
