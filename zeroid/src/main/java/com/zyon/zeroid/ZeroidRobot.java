package com.zyon.zeroid;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Set;

//import com.hoho.android.usbserial.driver.UsbSerialDriver;
//import com.hoho.android.usbserial.driver.UsbSerialPort;
//import com.hoho.android.usbserial.driver.UsbSerialProber;
//import com.hoho.android.usbserial.util.HexDump;
//import com.hoho.android.usbserial.util.SerialInputOutputManager;

import com.zyon.zeroid.bt.BluetoothThread;
import com.zyon.zeroid.bt.DeviceListActivity;
import com.zyon.zeroid.camerastream.CameraStreamer;
import com.zyon.zeroid.net.NetThread;

public final class ZeroidRobot extends Activity implements SurfaceHolder.Callback{
	private static final String TAG = ZeroidRobot.class.getSimpleName();
	private static final boolean D = true;
	private static final String TOAST = "toast";

	//region UI Declarations
	private SurfaceHolder surfaceHolder = null;

	private TextView txtStatus;
	public TextView txtMessage;

	private Button buttonUp;
	private Button buttonUpLeft;
	private Button buttonUpRight;
	private Button buttonDown;
	private Button buttonDownLeft;
	private Button buttonDownRight;
	private Button buttonRight;
	private Button buttonLeft;

	private String CurrentIp = "";
	private String btStatus = "BT: None";
    private String netStatus = "NET: None";

	boolean ui_feedback = false;
	boolean Keep_Screen_On = false;

	PowerManager.WakeLock wakeLock = null;
	WifiManager.WifiLock wifiLock = null;
	//endregion

	//region Intent Declaration
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private static final int REQUEST_DISCOVERABLE_BT = 3;
	//endregion

	//region Controller Declarations
	public static final int STICK_NONE = 0;
	public static final int STICK_UP = 1;
	public static final int STICK_UPRIGHT = 2;
	public static final int STICK_RIGHT = 3;
	public static final int STICK_DOWNRIGHT = 4;
	public static final int STICK_DOWN = 5;
	public static final int STICK_DOWNLEFT = 6;
	public static final int STICK_LEFT = 7;
	public static final int STICK_UPLEFT = 8;

    private int mcu_conn_type = 1;
	//endregion

	//region Camera Stream Declarations
    private CameraStreamer cameraStreamer = null;
    
	int cameraPort = 8080;
    int cameraIndex = 0;
	int cameraPreviewSizeIndex = 0;
	int cameraJpegQuality = 60;
	
	boolean cameraPreview = false;
	boolean cameraConnect = false;
	//endregion

	//region Net Declarations
	NetThread netThread = null;

	private boolean netReconnect = true;
	int heartBeat_TimeOut = 2000;
	//endregion

	//region Bluetooth Declarations
	private BluetoothAdapter bluetoothAdapter = null;
	private BluetoothThread btThread = null;
	private String btAddress = "07:12:05:16:69:44";
	private String btState = "OFF";
	private boolean btReconnect = true;
	//endregion

	//region USBSerial Declarations
    private UsbService usbService;
    private UsbSerialHandler usbSerialHandler;

	//endregion

	//region Activity
	public ZeroidRobot(){
		super();
	}
	
	@Override
    protected void onCreate(final Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        usbSerialHandler = new UsbSerialHandler(this);

        GetPreferences();
        CreateUI();
	}

    @Override
	protected void onStart(){
        super.onStart();
        Log.i(TAG, "onStart");

        CurrentIp = netThread.tryGetIpV4Address();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MyWakelockTag");
            wakeLock.acquire();
        }

        if (wifiLock == null) {
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "MyWifiLock");
            if (!wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        }

        if (netReconnect) netStart();

        if (mcu_conn_type == 1) {
            setFilters();  // Start listening notifications from UsbService
            startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
        } else {
            if (btReconnect)
                btStart();
        }
    }

    //@Override
    //protected void onPause() {
    @Override
    public synchronized void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
		try{
			if(wakeLock != null)
				wakeLock.release();
			if(wifiLock != null)
				wifiLock.release();
		}
		catch (final RuntimeException e){
		}

        try{
            unregisterReceiver(mUsbReceiver);
            unbindService(usbConnection);
        }
        catch (final RuntimeException e){
        }
    }

    @Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy");

		netReconnect = false;
		netStop();

		btReconnect = false;
		btStop();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(D) Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
			case REQUEST_CONNECT_DEVICE:
				// When DeviceListActivity returns with a device to connect
				if (resultCode == Activity.RESULT_OK) {
					// Get the device MAC BluetoothAddress
					btAddress = data.getExtras()
							.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				}
				else {
					btReconnect = false;
					Log.d(TAG, "Device Note Connected");
				}
				break;
			case REQUEST_DISCOVERABLE_BT:
				if (resultCode != Activity.RESULT_CANCELED) {
					btReconnect = false;
				}
				break;
			case REQUEST_ENABLE_BT:
				// When the request to enable Bluetooth returns
				if (resultCode == Activity.RESULT_OK) {
					// Bluetooth is now enabled, so set up a chat session
				} else {
					// User did not enable Bluetooth or an error occured
					Log.d(TAG, "BT not enabled");
					Toast.makeText(this, "BT not enabled, check in configurations", Toast.LENGTH_SHORT).show();
                    btReconnect = false;
				}
				break;
		}
	}
	//endregion Activity
    
    //region Surface
    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
		Log.i(TAG, "surfaceCreated");
		if(cameraConnect){
			tryStartCameraStreamer();
		}
    }
    
    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
		Log.i(TAG, "surfaceDestroyed");
    	ensureCameraStreamerStopped();
    }
    
    @Override
    public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height){
    	Log.i(TAG, "surfaceChanged");
    }
    //endregion Surface
    
    //region Preferences
    private void GetPreferences(){
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mcu_conn_type = getPrefInt(mPrefs, "mcu_conn_type", Integer.parseInt(getResources().getString(R.string.def_mcu_conn_type)));
        cameraPort = getPrefInt(mPrefs, "cam_port", Integer.parseInt(getResources().getString(R.string.def_cam_port)));

        btAddress = mPrefs.getString("bt_address", btAddress);
        
        cameraPort = getPrefInt(mPrefs, "cam_port", Integer.parseInt(getResources().getString(R.string.def_cam_port)));
        cameraIndex = getPrefInt(mPrefs, "cam_index", Integer.parseInt(getResources().getString(R.string.def_cam_index)));
        cameraPreview = mPrefs.getBoolean("cam_preview", getResources().getBoolean(R.bool.def_cam_preview));
        cameraPreviewSizeIndex = getPrefInt(mPrefs, "cam_size", Integer.parseInt(getResources().getString(R.string.def_cam_size)));
        cameraJpegQuality = getPrefInt(mPrefs, "cam_quality", Integer.parseInt(getResources().getString(R.string.def_cam_quality)));
		ui_feedback = mPrefs.getBoolean("ui_feedback", getResources().getBoolean(R.bool.def_ui_feedback));
		Keep_Screen_On = mPrefs.getBoolean("keep_screen_on", getResources().getBoolean(R.bool.def_keep_screen_on));
		heartBeat_TimeOut = getPrefInt(mPrefs, "heart_beat_timeout", Integer.parseInt(getResources().getString(R.string.def_heart_beat_timeout)));
    }
    
    private final int getPrefInt(SharedPreferences mPrefs, final String key, final int defValue){
        // We can't just call getInt because the preference activity
        // saves everything as a string.
        try{
            return Integer.parseInt(mPrefs.getString(key, null /* defValue */));
        } // try
        catch (final NullPointerException e){
            return defValue;
        } // catch
        catch (final NumberFormatException e){
            return defValue;
        } // catch
    } // getPrefInt(String, int)
    //endregion
    
	//region Net
	private final NetHandler netHandler = new NetHandler(this);
	private static class NetHandler extends Handler {
		private final WeakReference<ZeroidRobot> mActivity;

		public NetHandler(ZeroidRobot activity) {
			mActivity = new WeakReference<ZeroidRobot>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			ZeroidRobot activity = mActivity.get();

			if(activity != null) {
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
                        netStatus = "NET: Connected to " + netThread.getRemoteAddress();
						updateUI();
						break;
					case NetThread.STATE_CONNECTING:
                        netStatus = "NET: Connecting to " + netThread.getRemoteAddress();
						updateUI();
						break;
					case NetThread.STATE_LISTEN:
                        netStatus = "NET: Listen";
						updateUI();
						break;
					case NetThread.STATE_NONE:
                        netStatus = "NET: None";
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
				if (D) Log.i(TAG, "MESSAGE_READ: " + strMessage);
				NetMessageRead(strMessage);
				break;
			case NetThread.MESSAGE_CTRLCODE:
				ControlCode_t ControlCode = (ControlCode_t) msg.obj;
                sendToMicro(ControlCode.toBytes());
                break;
			case NetThread.MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
						Toast.LENGTH_SHORT).show();
				break;
		}
	}

	private void NetMessageRead(String data){
		int direction_state = -1;
		int speed = 0;

		if(data.equals("#CAMON\n")) {
			if(!cameraConnect) {
				tryStartCameraStreamer();
				cameraConnect = true;
			}
            netSendString("#CAMON\n");
		}
		else if(data.equals("#CAMOFF\n")) {
			if(cameraConnect) {
				ensureCameraStreamerStopped();
				cameraConnect = false;
			}
			netSendString("#CAMOFF\n");
		}
		else if(data.equals("#LEDON\n")) {
			if(cameraStreamer != null)
				cameraStreamer.LedOn();
			if (ui_feedback)
				txtMessage.setText("LED ON");
		}
		else if(data.equals("#LEDOFF\n")) {
			if(cameraStreamer != null)
				cameraStreamer.LedOff();
			if (ui_feedback)
				txtMessage.setText("LED OFF");
		}
		else if(data.equals("#FOCUS\n")) {
			if(cameraStreamer != null)
				cameraStreamer.autoFocus();
			if (ui_feedback)
				txtMessage.setText("FOCUS");
		}
		else if (data.length() >= 2) {
			if (ui_feedback)
				txtMessage.setText(data);

			String Command = data.substring(0, 2);

			if (Command.equals("#D(")){
				if (ui_feedback) {
					direction_state = Integer.parseInt(data.substring(3, data.indexOf(',') -1));

					switch (direction_state) {
						case STICK_UP:
							buttonUpRight.setPressed(true);
							break;
						case STICK_UPRIGHT:
							buttonUpRight.setPressed(true);
							break;
						case STICK_UPLEFT:
							buttonUpLeft.setPressed(true);
							break;
						case STICK_DOWN:
							buttonDown.setPressed(true);
							break;
						case STICK_DOWNRIGHT:
							buttonDownRight.setPressed(true);
							break;
						case STICK_DOWNLEFT:
							buttonDownLeft.setPressed(true);
							break;
						case STICK_RIGHT:
							buttonRight.setPressed(true);
							break;
						case STICK_LEFT:
							buttonLeft.setPressed(true);
							break;
					}
				}

				sendToMicro(data);
			}
			else if (Command.equals("#M(")) {
                sendToMicro(data);
			}
			else if (Command.equals("#OnOff")) {
                sendToMicro("#OnOff\n");
			}
			else if (Command.equals("#Canon")) {
                sendToMicro("#CN\n");
			}
			else if (Command.equals("#MachineGun")) {
                sendToMicro("#MG\n");
			}

			else {
				//Log.i(TAG, "Data >= 2 = UNKNOW: " + data);
			}
		}
		else {
			//Log.i(TAG, "Data UNKNOW: " + data);
		}
	}

	private void netStart() {
		if (netThread == null) {
			netThread = new NetThread(netHandler,"",21111, heartBeat_TimeOut);
			netThread.setConnectionState(NetThread.STATE_LISTEN);
			netThread.start();
		}
	}

	private void netStop(){
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
//						netThread.sleep(10);
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

	private void netSendString(String data) {
        if (netThread != null) {
            if (data.length() > 0) {
                // Get the message bytes and tell the BluetoothChatService to write
                byte[] dataByte = data.getBytes();
                netThread.write(dataByte);
            }
        }
	}
    //endregion Net Server

	//region Bluetooth
	private final BluetoothHandler bluetoothHandler = new BluetoothHandler(this);
	private static class BluetoothHandler extends Handler {
		private final WeakReference<ZeroidRobot> mActivity;

		public BluetoothHandler(ZeroidRobot activity) {
			mActivity = new WeakReference<ZeroidRobot>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			ZeroidRobot activity = mActivity.get();

			if(activity != null) {
				activity.MessageFromBtHandler(msg);
			}
		}
	}

	private void MessageFromBtHandler(Message msg) {
		switch (msg.what) {
			case BluetoothThread.MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
					case BluetoothThread.STATE_CONNECTED:
						btStatus = "BT: Connected";

//						btSendThread = new BtSendThread(this);
//						btSendThread.start();

						updateUI();
						break;
					case BluetoothThread.STATE_CONNECTING:
						btStatus = "BT: Connecting";
						updateUI();
						break;
					case BluetoothThread.STATE_LISTEN:
						btStatus = "BT: Listen";
						updateUI();
						break;
					case BluetoothThread.STATE_NONE:
						btStatus = "BT: None";
						updateUI();
						btStop();

						if (btReconnect) {
							new Handler().postDelayed(new Runnable() {
								@Override
								public void run() {
									btStart();
								}
							}, 3000);
							break;
						}
				}
				break;
			case BluetoothThread.MESSAGE_READ:
				String strMessage = (String) msg.obj;
				if (D) Log.i(TAG, "MESSAGE_READ: " + strMessage);

				break;
			case BluetoothThread.MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
						Toast.LENGTH_SHORT).show();
				break;
		}
	}

	private void btStart() {
		if (bluetoothAdapter == null) {
			// Get local Bluetooth adapter
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			// If the adapter is null, then Bluetooth is not supported
			if (bluetoothAdapter == null) {
				Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
				return;
			}
		}

		if (!bluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			return;
		}

		/*Listen
			if (D) Log.d(TAG, "Ensure Bluetooth Discoverable");
			if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
				startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
				return;
			}
		*/

		if (btAddress != null && btAddress.length() != 0) {
			if (btThread == null) {
				Log.d(TAG, "create new btThread");

				btThread = new BluetoothThread(bluetoothHandler, btAddress,2000);
				btThread.setConnectionState(BluetoothThread.STATE_CONNECTING);
				btThread.start();
			}
		} else {
			// Launch the DeviceListActivity to see devices and do scan
//			Intent serverIntent = new Intent(this, DeviceListActivity.class);
//			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			btStatus = "BT: OFF";
			return;
		}
	}

	private void btStop(){
		if (btThread != null) {
			btThread.close();
			btThread = null;
		}
	}
	//endregion Bluetooth

	//region CommToMicro
    private void sendToMicro(byte[] data){
        if(mcu_conn_type == 1){
            if(usbService != null) // if UsbService was correctly binded, Send data
                usbService.write(data);
        } else {
            if(btThread != null)
                btThread.write(data);
        }
    }

    private void sendToMicro(String data){
        if (ui_feedback)
            txtMessage.setText(data);

        if(mcu_conn_type == 1){
            if(usbService != null) // if UsbService was correctly binded, Send data
                usbService.write(data.getBytes());
        } else {
            if(btThread != null)
                btThread.write(data.getBytes());
        }
    }
	//endregion CommToMicro

    //region UsbSerial
    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras){
        if(UsbService.SERVICE_CONNECTED == false)        {
            Intent startService = new Intent(this, service);
            if(extras != null && !extras.isEmpty())            {
                Set<String> keys = extras.keySet();
                for(String key: keys)                {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class UsbSerialHandler extends Handler    {
        private final WeakReference<ZeroidRobot> mActivity;

        public UsbSerialHandler(ZeroidRobot activity)        {
            mActivity = new WeakReference<ZeroidRobot>(activity);
        }

        @Override
        public void handleMessage(Message msg){
            ZeroidRobot activity = mActivity.get();

            switch(msg.what){
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    activity.txtMessage.setText(data);
                    Log.i(TAG, "Usb Message: " + data);
                    break;
            }
        }
    }

    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context arg0, Intent arg1)
        {
            if(arg1.getAction().equals(UsbService.ACTION_USB_PERMISSION_GRANTED)) // USB PERMISSION GRANTED
            {
                Toast.makeText(arg0, "USB Ready", Toast.LENGTH_SHORT).show();
            }else if(arg1.getAction().equals(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)) // USB PERMISSION NOT GRANTED
            {
                Toast.makeText(arg0, "USB Permission not granted", Toast.LENGTH_SHORT).show();
            }else if(arg1.getAction().equals(UsbService.ACTION_NO_USB)) // NO USB CONNECTED
            {
                Toast.makeText(arg0, "No USB connected", Toast.LENGTH_SHORT).show();
            }else if(arg1.getAction().equals(UsbService.ACTION_USB_DISCONNECTED)) // USB DISCONNECTED
            {
                Toast.makeText(arg0, "USB disconnected", Toast.LENGTH_SHORT).show();
            }else if(arg1.getAction().equals(UsbService.ACTION_USB_NOT_SUPPORTED)) // USB NOT SUPPORTED
            {
                Toast.makeText(arg0, "USB device not supported", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final ServiceConnection usbConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1)
        {
            usbService = ((UsbService.UsbBinder)arg1).getService();
            usbService.setHandler(usbSerialHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            usbService = null;
        }
    };
    //endregion

	//region UI
	//@SuppressWarnings("deprecation")
	private void CreateUI(){
        //requestWindowFeature(Window.FEATURE_NO_TITLE);

		if(Keep_Screen_On)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
					| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.zeroidbot);
        
        surfaceHolder = ((SurfaceView) findViewById(R.id.camera)).getHolder();
        surfaceHolder.addCallback(this);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
        	surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); //needed by 2.3/deprecated in Android 3.0
        
        buttonUp = (Button)findViewById(R.id.buttonUp);
        buttonUpLeft = (Button)findViewById(R.id.buttonUpLeft);
        buttonUpRight = (Button)findViewById(R.id.buttonUpRight);
        buttonDown = (Button)findViewById(R.id.buttonDown);
        buttonDownLeft = (Button)findViewById(R.id.buttonDownLeft);
        buttonDownRight = (Button)findViewById(R.id.buttonDownRight);
        buttonRight = (Button)findViewById(R.id.buttonRight);
        buttonLeft = (Button)findViewById(R.id.buttonLeft);
        
        txtStatus = (TextView) findViewById(R.id.txtStatus);
        txtMessage = (TextView) findViewById(R.id.txtmessage);
	}
	
    public void clearCheckBox() {
    	buttonUp.setPressed(false);
    	buttonUpLeft.setPressed(false);
    	buttonUpRight.setPressed(false);
    	buttonDown.setPressed(false);
    	buttonDownLeft.setPressed(false);
    	buttonDownRight.setPressed(false);
    	buttonRight.setPressed(false);
    	buttonLeft.setPressed(false);
    }

    public void updateUI(){
		txtStatus.setText("IP: " + CurrentIp + " " + netStatus + " " + btStatus);
    }
	//endregion Block
    
	//region Camera Stream
	private void tryStartCameraStreamer(){
		// if device support camera?
        PackageManager pm = this.getPackageManager();
		if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			Log.e("err", "Device has no camera!");
			return;
		}

		if(cameraStreamer == null) {
			cameraStreamer = new CameraStreamer(cameraIndex, cameraPort,
					cameraPreviewSizeIndex, cameraJpegQuality, surfaceHolder, cameraPreview);

			cameraStreamer.start();
		}
    } // tryStartCameraStreamer()
	
    private void ensureCameraStreamerStopped(){
        if (cameraStreamer != null){
            cameraStreamer.stop();
            cameraStreamer = null;
        } // if
    }
	//endregion
}