package com.zyon.zeroid;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.zyon.zeroid.Multitouch.IMultitouchListener;
import com.zyon.zeroid.Multitouch.MultitouchHandler;
import com.zyon.zeroid.Util.ByteExt;
import com.zyon.zeroid.camerastream.MjpegInputStream;
import com.zyon.zeroid.camerastream.MjpegView;
import com.zyon.zeroid.net.NetThread;
import com.zyon.zeroid.Util.zMath;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ZeroidController extends Activity {
    private static final String TAG = ZeroidController.class.getSimpleName();
    private static final boolean D = true;
    private static final String TOAST = "toast";

    //region UI Declarations
    private TextView txtStatus;
    private TextView txtMessage;

    private String CurrentIpAddress = "";
    private String RemoteIP = "";

    boolean ui_feedback = false;
    boolean keep_screen_on = false;

    MultitouchHandler multitouch_handler;
    Button buttonCanon;
    Button buttonPower;

    Button buttonFocus;
    Button buttonCamOnOff;
    CheckBox chkFlash;

    JoystickView joystickViewL;
    JoystickView joystickViewR;
    //endregion

    //region Net Declarations
    private NetThread netThread = null;
    private boolean netReconnect = true;

    boolean camConnected = false;

    private String robot_address = "192.168.0.107";
    //endregion

    //region Control
    RelativeLayout Multitouch_View = null;

    // Control Motor Definitions
    private static final int LF = 20;
    private static final int LR = 21;
    private static final int RF = 22;
    private static final int RR = 23;

    private int mcu_com_prot = 2;
    private int send_Interval = 300;
    private int stick_size = 3;

    ControlCode_t ControlCode = new ControlCode_t();

    private int MinSpeed = 120;
    private int MaxSpeed = 255;

    ArrayList<String> PressedKeys;
    //endregion

    //region MjpegView Declarations
    private MjpegView mv = null;
    private String cam_address = "http://192.168.0.107:8080";
    private String cam_ctrl_address = "http://192.168.0.107:8080";

    CameraAsyncTask cameraAsyncTask = null;
    //endregion

    //region Activity
    public ZeroidController() {
        super();

        PressedKeys = new ArrayList<String>();
    }// constructor()

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        GetPreferences();
        CreateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                netStart();
            }
        }, 3000);
    }

    @Override
    protected void onStart(){
        super.onStart();
        Log.i(TAG, "onStart");

        CurrentIpAddress = netThread.tryGetIpV4Address();
        updateUI();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE | newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.i(TAG, "onConfigurationChanged");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        CameraView_Stop();

        netReconnect = false;
        netStop();
    }
    //endregion

    //region Net Socket
    private final NetHandler netHandler = new NetHandler(this);

    private static class NetHandler extends Handler {
        private final WeakReference<ZeroidController> mActivity;

        public NetHandler(ZeroidController activity) {
            mActivity = new WeakReference<ZeroidController>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ZeroidController activity = mActivity.get();

            if (activity != null) {
                activity.MessageFromNetHandler(msg);
            }
        }
    }

    private void MessageFromNetHandler(Message msg) {
        switch (msg.what) {
            case NetThread.MESSAGE_STATE_CHANGE:
                if (D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                    case NetThread.STATE_CONNECTED:
                    RemoteIP = "Connected";
                        updateUI();
                        break;
                    case NetThread.STATE_CONNECTING:
                        RemoteIP = "Connecting";
                        updateUI();
                        break;
                    case NetThread.STATE_LISTEN:
                        RemoteIP = "Listen";
                        updateUI();
                        break;
                    case NetThread.STATE_NONE:
                        RemoteIP = "None";
                        updateUI();
                        netStop();

                        if (netReconnect) {
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    netStart();
                                }
                            }, 2000);
                        }
                        break;
                }
                break;
            case NetThread.MESSAGE_READ:
                String strMessage = (String) msg.obj;
                //if (D) Log.i(TAG, "MESSAGE_READ: " + strMessage);

                if (strMessage.contains("#PING")) {
                    NetMessage(strMessage);
                    netSend(strMessage.replace("PING", "PONG") + "\n");
                }
                else
                    NetMessage(strMessage);
                break;

            case NetThread.MESSAGE_INFO:
                String texto = (String) msg.obj;

                if(texto.contains("Connected to: /")) {
                    RemoteIP = texto.replace("Connected to: /","");
                    updateUI();
                } else {
                    txtMessage.setText(texto);
                }
                break;
            case NetThread.MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void NetMessage(String data) {
        if (data.equals("#CAMON\n")) {
            Toast.makeText(getApplicationContext()
                    , "Camera Started"
                    , Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!camConnected) {
                        camConnected = true;
                        CameraView_Start();
                    }
                }
            }, 2000);

        } else if (data.equals("#CAMOFF\n")) {
            Toast.makeText(getApplicationContext()
                    , "Camera Stoped"
                    , Toast.LENGTH_SHORT).show();

            if (camConnected) {
                camConnected = false;
                CameraView_Stop();
            }
        } else if (data.equals("#NoFlash\n")) {
            Toast.makeText(getApplicationContext()
                    , "Device not support flash"
                    , Toast.LENGTH_SHORT).show();
        }
//        else {
//            Log.i(TAG, "Data UNKNOW: " + data);
//        }
    }

    private void netStart() {
        if (netThread == null) {
            netThread = new NetThread(netHandler, robot_address, 21111, 0);
            netThread.setConnectionState(NetThread.STATE_CONNECTING);
            netThread.start();
        }
        //if (numberCores > 4) myExecutor.excute(myRunnable); else myRunnable.run()
    }

    private void netStop() {
        if (netThread != null) {
            netThread.close();

//			long CloseTime = System.currentTimeMillis();
//			Log.i(TAG, "waiting for netThread isAlive return false");
//
//			while (netThread.isAlive()){
//				if((System.currentTimeMillis() - CloseTime) >= 3000L){
//					CloseTime = System.currentTimeMillis();
//					Log.i(TAG, "netThread isAlive after 3 seconds, interrupting it");
//
//					try {
//						netThread.sleep(50);
//						netThread.interrupt();
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
//				}
//			}
//
//			Log.i(TAG, "netStop concluded");
            netThread = null;
        }
    }

    private void netSend(String data, int interval) {
        if (netThread != null) {
            netThread.write(data, interval);
        }
    }

    private void netSend(String data) {
        if (netThread != null) {
            netThread.write(data);
        }
    }

    private void netSend(byte[] data, int interval) {
        if (netThread != null) {
            netThread.write(data, interval);
        }
    }

    private void SendWithCRC(boolean sendStop, int interval) {
        byte[] data = ControlCode.toBytes();

        char crc = ByteExt.getCRCMODBUS(data, ControlCode.Size - 2);
        data[ControlCode.Size - 1] = (byte)((crc & 0xFF00) >> 8);
        data[ControlCode.Size - 2] = (byte)(crc & 0x00FF);

        netSend(data, interval);

        if (sendStop) {
            byte lastCtrl = ControlCode.headerType;
            ControlCode.Clear();
            ControlCode.headerType = lastCtrl;
            SendWithCRC(false, 0);
        }
    }
    //endregion

    //region UI
    private void CreateUI() {
//		requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (keep_screen_on)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        setContentView(R.layout.zeroidcontroller);

        txtStatus = (TextView) findViewById(R.id.txtStatus);
        txtMessage = (TextView) findViewById(R.id.txtmessage);

        Multitouch_View = (RelativeLayout) findViewById(R.id.multitouch_view);

        buttonPower = (Button) findViewById(R.id.btnPower);
        buttonCanon = (Button) findViewById(R.id.btnCanon);
        buttonFocus = (Button) findViewById(R.id.btnAutoFocus);
        buttonCamOnOff = (Button) findViewById(R.id.btnPhotoSnap);
        chkFlash = (CheckBox) findViewById(R.id.chkFlash);
        //ace 320 x 480 pixels (~165 ppi pixel density)
        //Moto (G 2014) 720 x 1280 pixels (~294 ppi pixel density)

        Display display = getWindowManager().getDefaultDisplay();
        int JoystickSize = display.getHeight() / stick_size;
        int beetleSize = JoystickSize / 2;
        int StickMinDist = beetleSize / 10;

        joystickViewL = new JoystickView(this, Multitouch_View, beetleSize,JoystickSize,StickMinDist);
        joystickViewR = new JoystickView(this, Multitouch_View, beetleSize,JoystickSize,StickMinDist);

        multitouch_handler = new MultitouchHandler(Multitouch_View, JoystickSize / 40);
        multitouch_handler.addMultiTouchView((View) buttonPower);
        multitouch_handler.addMultiTouchView((View) buttonCanon);

        multitouch_handler.addMultiTouchView((View) chkFlash);
        multitouch_handler.addMultiTouchView((View) buttonCamOnOff);
        multitouch_handler.addMultiTouchView((View) buttonFocus);
        multitouch_handler.setPopupLeftView((View) joystickViewL, JoystickSize);
        multitouch_handler.setPopupRightView((View) joystickViewR, JoystickSize);

        Multitouch_View.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                multitouch_handler.onTouchEvent(Multitouch_View, event);
                return true;
            }
        });

        multitouch_handler.setListener(new IMultitouchListener() {
            public void onMultiTouch(View view, int maskedAction, int xyX, int xyY) {
                if (view == buttonPower) {
                    if (maskedAction == MotionEvent.ACTION_DOWN) {
                        if (mcu_com_prot == 1 /*String*/) {
                            netSend("#OnOff\n");
                        } else {
                            ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 0); //P1-1 Power
                            SendCommand();
                        }
                    } else if (maskedAction == MotionEvent.ACTION_UP) {
                        if (mcu_com_prot != 1 /*String*/) {
                            ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 0); //P1-1 Power
                            SendCommand();
                        }
                    }
                } else if (view == buttonCanon) {
                    if (maskedAction == MotionEvent.ACTION_DOWN) {
                        if (mcu_com_prot == 1 /*String*/) {
                            netSend("#Canon\n");
                        } else {
                            ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 1); //P1-2 FIRE
                            SendCommand();
                        }
                    } else if (maskedAction == MotionEvent.ACTION_UP) {
                        if (mcu_com_prot != 1 /*String*/) {
                            ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 1); //P1-2 FIRE
                            SendCommand();
                        }
                    }
                } else if (view == buttonFocus) {
                    if (maskedAction == MotionEvent.ACTION_DOWN)
                        netSend("#FOCUS\n");
                } else if (view == buttonCamOnOff) {
                    if (maskedAction == MotionEvent.ACTION_DOWN)
                        if (!camConnected) {
                            netSend("#CAMON\n");
                        } else {
                            netSend("#CAMOFF\n");
                        }
                } else if (view == chkFlash) {
                    if (maskedAction == MotionEvent.ACTION_DOWN)
                        if (!chkFlash.isChecked()) {
                            netSend("#LEDON\n");
                            chkFlash.setChecked(true);
                        } else {
                            netSend("#LEDOFF\n");
                            chkFlash.setChecked(false);
                        }
                } else if (view == joystickViewL) {
                    joystickViewL.onTouch(maskedAction, xyX, xyY);
                } else if (view == joystickViewR) {
                    joystickViewR.onTouch(maskedAction, xyX, xyY);
                }
            }
        });

        joystickViewL.setListener(new IJoyTouchListener() {
            public void onJoyTouch(int Degrees, int Radius, int X, int Y) {
                if (D) Log.i(TAG, "L angle: " + String.valueOf(Degrees)
                        + ", Radius: " + String.valueOf(Radius)
                        + ", X: " + String.valueOf(X)
                        + ", Y: " + String.valueOf(Y));

                if (mcu_com_prot == 2 /*Byte Buttons*/) {
                    setDirKeysFromDegrees(Degrees, Radius);
                    SendCommand();
                } else /*1 = String, 3 = LdRdLsRs*/{
                    SetBytesLdRdLsRs(Radius, Degrees, X, Y);
                    SendCommand();
                }
            }
        });

        joystickViewR.setListener(new IJoyTouchListener() {
            public void onJoyTouch(int Degrees, int Radius, int X, int Y) {
                if (D) Log.i(TAG, "R angle: " + String.valueOf(Degrees)
                        + ", Radius: " + String.valueOf(Radius)
                        + ", X: " + String.valueOf(X)
                        + ", Y: " + String.valueOf(Y));

                if (mcu_com_prot == 2 /*Byte Buttons*/) {
                    setTurretKeysFromDegrees(Degrees, Radius);
                    SendCommand();
                }
            }
        });
    }

    public void updateUI() {
        txtStatus.setText("IP: " + CurrentIpAddress + " Remote: " + RemoteIP);
    }
    //endregion

    //region Preferences
    private void GetPreferences() {
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        robot_address = mPrefs.getString("robot_address", getResources().getString(R.string.def_robot_address));
        cam_address = mPrefs.getString("cam_address", getResources().getString(R.string.def_cam_address));
        cam_ctrl_address = mPrefs.getString("cam_ctrl_address", getResources().getString(R.string.def_cam_ctrl_address));

        //TODO: Add connection type between Robot and Controller
        mcu_com_prot = getPrefInt(mPrefs, "mcu_com_prot", Integer.parseInt(getResources().getString(R.string.def_mcu_com_prot)));
        send_Interval = getPrefInt(mPrefs, "send_interval", Integer.parseInt(getResources().getString(R.string.def_send_interval)));
        stick_size = getPrefInt(mPrefs, "stick_size", Integer.parseInt(getResources().getString(R.string.def_stick_size)));

        ui_feedback = mPrefs.getBoolean("ui_feedback", getResources().getBoolean(R.bool.def_ui_feedback));
        keep_screen_on = mPrefs.getBoolean("keep_screen_on", getResources().getBoolean(R.bool.def_keep_screen_on));

//        Log.i(TAG,
//                "mcu_com_prot" + String.valueOf(mcu_com_prot) + "\r\n" +
//                "pref_robot_address" + String.valueOf(robot_address) + "\r\n" +
//                "pref_send_interval" + String.valueOf(send_Interval) + "\r\n" +
//                "pref_cam_address" + String.valueOf(cam_address) + "\r\n" +
//                "pref_cam_ctrl_address" + String.valueOf(cam_ctrl_address) + "\r\n" +
//                "ui_feedback" + String.valueOf(ui_feedback) + "\r\n" +
//                "keep_screen_on" + String.valueOf(keep_screen_on) + "\r\n"
//        );
    }

    private final int getPrefInt(SharedPreferences mPrefs, final String key, final int defValue) {
        // We can't just call getInt because the preference activity
        // saves everything as a string.
        try {
            return Integer.parseInt(mPrefs.getString(key, null /* defValue */));
        } // try
        catch (final NullPointerException e) {
            return defValue;
        } // catch
        catch (final NumberFormatException e) {
            return defValue;
        } // catch
    } // getPrefInt(String, int)
    //endregion

    //region Controller
    private void setDirKeysFromDegrees(int angle, int distance) {
        ControlCode.headerType = 0x01;
        ControlCode.Type = 0x01;

        ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 4);
        ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 5);
        ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 6);
        ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 7);

        if(distance > 0) {
            if (angle >= 247.5 && angle < 292.5) {
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 4);//P1-4 UP
            } else if (angle >= 292.5 && angle < 337.5) {
                //return STICK_UPRIGHT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 4);//P1-4 UP
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 7); //P1-7 RIGHT
            } else if (angle >= 337.5 || angle < 22.5) {
                //return STICK_RIGHT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 7); //P1-7 RIGHT
            } else if (angle >= 22.5 && angle < 67.5) {
                //return STICK_DOWNRIGHT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 7); //P1-7 RIGHT
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 5); //P1-5 DOWN
            } else if (angle >= 67.5 && angle < 112.5) {
                //return STICK_DOWN;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 5); //P1-5 DOWN
            } else if (angle >= 112.5 && angle < 157.5) {
                //return STICK_DOWNLEFT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 5); //P1-5 DOWN
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 6); //P1-6 LEFT
            } else if (angle >= 157.5 && angle < 202.5) {
                //return STICK_LEFT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 6); //P1-6 LEFT
            } else if (angle >= 202.5 && angle < 247.5) {
                //return STICK_UPLEFT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 6); //P1-6 LEFT
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 4);//P1-4 UP
            }
        }
    }

    private void SendCommand(){
        if (mcu_com_prot == 1 /*String*/) {
            if((ControlCode.b5 > 0) | (ControlCode.b6 > 0)) {
                netSend("#M(" + String.valueOf(ControlCode.b3) + "," + String.valueOf(ControlCode.b4) + "," + String.valueOf(ControlCode.b5) + "," + String.valueOf(ControlCode.b6) + ")\n", send_Interval);
            } else {
                netSend("#M(" + String.valueOf(ControlCode.b3) + "," + String.valueOf(ControlCode.b4) + "," + String.valueOf(ControlCode.b5) + "," + String.valueOf(ControlCode.b6) + ")\n", 0);
            }
        } else {
            boolean recursively = false;

            if (mcu_com_prot == 2 /*byte[] Buttons*/) {
                ControlCode.headerType = 0x01;
                ControlCode.Type = 0x01;

                if ((ControlCode.b1 != 0x00 /*00000000*/) && (ControlCode.b1 != 0x01 /*00000001*/))
                    recursively = true;

            } else if (mcu_com_prot == 3 /*Byte LdRdLsRs*/) {
                ControlCode.headerType = 0x01;
                ControlCode.Type = 0x02;

                if (((ControlCode.b1 != 0x00 /*00000000*/) && (ControlCode.b1 != 0x01 /*00000001*/)) | (ControlCode.b5 != 0x00) | (ControlCode.b6 != 0x00))
                    recursively = true;
            }

            if(recursively) {
                SendWithCRC(false, send_Interval);
            }
            else {
                SendWithCRC(false, 0);
            }
        }
    }

    private void setTurretKeysFromDegrees(int angle, int distance) {
        //ControlCode.Clear();
        ControlCode.headerType = 0x01;
        ControlCode.Type = 0x01;

        ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 2);
        ControlCode.b1 = ByteExt.ClearBit(ControlCode.b1, 3);

        if(distance > 0) {
            if (angle >= 337.5 || angle <= 22.5) {
                //return STICK_RIGHT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 2);//P1-3 TURRET RIGHT
            } else if (angle >= 157.5 && angle <= 202.5) {
                //return STICK_LEFT;
                ControlCode.b1 = ByteExt.SetBit(ControlCode.b1, 3);//P1-3 TURRET LEFT
            }
        }
    }

    public void SetBytesLdRdLsRs(int distance, int angle, int xyX, int xyY) {
        int LD = 0, RD = 0, LS = 0, RS = 0;
        if(distance > 0) {
            if (angle >= 181 && angle <= 270) {
                LS = zMath.map(angle, 180, 270, MinSpeed, MaxSpeed);
                RS = 255;
                LD = LF;
                RD = RF;
            } else if (angle >= 271 && angle <= 360) {
                RS = zMath.map(angle, 271, 360, MaxSpeed, MinSpeed);
                LS = 255;
                LD = LF;
                RD = RF;
            } else if (angle >= 91 && angle <= 180) {
                LS = zMath.map(angle, 91, 180, MaxSpeed, MinSpeed);
                RS = 255;
                LD = LR;
                RD = RR;
            } else if (angle >= 1 && angle <= 90) {
                RS = zMath.map(angle, 1, 90, MinSpeed, MaxSpeed);
                LS = 255;
                LD = LR;
                RD = RR;
            }

            RS = zMath.map(distance, 1, 127, MinSpeed, RS);
            LS = zMath.map(distance, 1, 127, MinSpeed, LS);
        }

        //if (D)
            Log.i(TAG,
                ", LD: " + (LD == LF ? "LF" : (LD == LR ? "LR" : "SS")) + String.valueOf(LS) +
                ", RD: " + (RD == RF ? "RF" : (RD == RR ? "RR" : "SS")) + String.valueOf(RS));

        ControlCode.b3 = (byte) (LD);
        ControlCode.b4 = (byte) (RD);
        ControlCode.b5 = (byte) (LS);
        ControlCode.b6 = (byte) (RS);
    }
    //endregion

    //region Mjpeg View
    private void CameraView_Start() {
        mv = new MjpegView(this);
        mv = (MjpegView) findViewById(R.id.mjpegview);

        cameraAsyncTask = new CameraAsyncTask();
        cameraAsyncTask.execute(cam_address);
    }

    private void CameraView_Stop() {
        if (mv != null) {
            mv.stopPlayback();
        }

        if (cameraAsyncTask != null) {
            cameraAsyncTask.cancel(true);

            while (!cameraAsyncTask.isCancelled()) {
                Log.e("TAG", "not is isCancelled!");
            }
            Log.d(TAG, "1. doRead cancelled");
        }
    }

    public class CameraAsyncTask extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            Log.d(TAG, "doInBackground");
            //TODO: if camera has authentication deal with it and don't just not work
            //http://stackoverflow.com/questions/24114576/android-mjpeg-stream
            HttpResponse res = null;
            DefaultHttpClient httpclient = new DefaultHttpClient();

            HttpParams httpParameters = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParameters, 5000);
            HttpConnectionParams.setSoTimeout(httpParameters, 5000);
            httpclient.setParams(httpParameters);

            Log.d(TAG, "1. Sending http request");
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
                if (res.getStatusLine().getStatusCode() == 401) {
                    Log.d(TAG, "Error 401: " + url[0]);
                    //You must turn off camera User Access Control before this will work
                    return null;
                }

                return new MjpegInputStream(res.getEntity().getContent());
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-ClientProtocolException", e);
                //Error connecting to camera
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-IOException", e);
                //Error connecting to camera
            }
            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            mv.setSource(result);
            mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);   //SIZE_STANDARD
            mv.showFps(true);
        }
    }
    //endregion
}
