package com.zyon.zeroid;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

//import com.hoho.android.usbserial.driver.UsbSerialDriver;
//import com.hoho.android.usbserial.driver.UsbSerialPort;
//import com.hoho.android.usbserial.driver.UsbSerialProber;
//import com.hoho.android.usbserial.util.HexDump;


import com.zyon.zeroid.bt.DeviceListActivity;

public final class ZeroidPreferenceActivity extends PreferenceActivity {
	private final String TAG = ZeroidPreferenceActivity.class.getSimpleName();
	private UsbManager mUsbManager;
    private BluetoothAdapter bluetoothAdapter = null;

    //region Intent Declaration
    private static final int REQUEST_CONNECT_BT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    //endregion

	//region Simple container for a UsbDevice and its driver.
//	private static class DeviceEntry {
//		public UsbDevice device;
//		public UsbSerialDriver driver;
//
//		DeviceEntry(UsbDevice device, UsbSerialDriver driver) {
//			this.device = device;
//			this.driver = driver;
//		}
//	}
	//endregion

	public ZeroidPreferenceActivity(){
		super();
	} // constructor()

	@Override
	protected void onCreate(final Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		addPreferencesFromResource(R.xml.pref);

        //region Camera preference
		final ListPreference cameraPreference = (ListPreference) findPreference("cam_index");

		setCameraPreferences(cameraPreference);

		cameraPreference.setOnPreferenceClickListener(new OnPreferenceClickListener(){
			@Override
			public boolean onPreferenceClick(Preference preference) {
				setCameraPreferences(cameraPreference);
				return false;
			}
		});
        //endregion

		//region JPEG size preference
		final ListPreference sizePreference = (ListPreference) findPreference("cam_size");

		setSizePreferences(sizePreference, cameraPreference);

		sizePreference.setOnPreferenceClickListener(new OnPreferenceClickListener(){
			@Override
			public boolean onPreferenceClick(Preference preference) {
				setSizePreferences(sizePreference, cameraPreference);
				return false;
			}
		});
        //endregion

		//region usb device preference
//		final ListPreference usbDevicePreference = (ListPreference) findPreference("usb_device");
//
//		setUsbDevicePreference(usbDevicePreference);
//
//		usbDevicePreference.setOnPreferenceClickListener(new OnPreferenceClickListener(){
//			@Override
//			public boolean onPreferenceClick(Preference preference) {
//				setUsbDevicePreference(usbDevicePreference);
//				return false;
//			}
//		});
        //endregion

        //region Select Bluetooth
        Preference button = (Preference)findPreference("btn_bluetooth");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Launch the DeviceListActivity to see devices and do scan
                Intent btIntent = new Intent(ZeroidPreferenceActivity.this, DeviceListActivity.class);
                startActivityForResult(btIntent, REQUEST_CONNECT_BT_DEVICE);
                return false;
            }
        });
        //endregion
	}

    @Override
    protected void onStart(){
		super.onStart();
        if (bluetoothAdapter == null) {
            // Get local Bluetooth adapter
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            // If the adapter is null, then Bluetooth is not supported
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
                return;
            }else{
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    return;
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_BT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC BluetoothAddress
                    String btAddress = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
					final EditTextPreference prefAdress = (EditTextPreference)findPreference("bt_address");
					prefAdress.setText(btAddress);

                } else {
                    Log.d(TAG, "Device Note Connected");
                    Toast.makeText(this, "Device Note Connected", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "BT not enabled", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

	private void setCameraPreferences(final ListPreference cameraPreference){
		CharSequence[] entries = new CharSequence[1];
		CharSequence[] entryValues = new CharSequence[1];

		final int numberOfCameras = Camera.getNumberOfCameras();
		if(numberOfCameras > 0) {
			final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

			entries = new CharSequence[numberOfCameras];
			entryValues = new CharSequence[numberOfCameras];

			for (int cameraIndex = 0; cameraIndex < numberOfCameras; cameraIndex++){
				Camera.getCameraInfo(cameraIndex, cameraInfo);
				String cameraFacing;
				switch (cameraInfo.facing){
				case Camera.CameraInfo.CAMERA_FACING_BACK:
					cameraFacing = "back";
					break;
				case Camera.CameraInfo.CAMERA_FACING_FRONT:
					cameraFacing = "front";
					break;
				default:
					cameraFacing = "unknown";
				} // switch

				entries[cameraIndex] = "Camara " + cameraIndex + " " + cameraFacing;
				entryValues[cameraIndex] = String.valueOf(cameraIndex);
			} //for
		}
		else {
			entries[0] = "No Camera found";
			entryValues[0] = "-1";
		}

		cameraPreference.setEntries(entries);
		cameraPreference.setEntryValues(entryValues);
	}

	private void setSizePreferences(final ListPreference sizePreference, final ListPreference cameraPreference){
		final String cameraPreferenceValue = cameraPreference.getValue();
		final int cameraIndex;
		if (cameraPreferenceValue != null){
			cameraIndex = Integer.parseInt(cameraPreferenceValue);
		} // if
		else
		{
			cameraIndex = 0;
		} // else

		final Camera camera = Camera.open(cameraIndex);
		final Camera.Parameters params = camera.getParameters();
		camera.release();

		final List<Camera.Size> supportedPreviewSizes = params.getSupportedPreviewSizes();
		CharSequence[] entries = new CharSequence[supportedPreviewSizes.size()];
		CharSequence[] entryValues = new CharSequence[supportedPreviewSizes.size()];

		for (int previewSizeIndex = 0; previewSizeIndex < supportedPreviewSizes.size(); previewSizeIndex++){
			Camera.Size supportedPreviewSize = supportedPreviewSizes.get(previewSizeIndex);
			entries[previewSizeIndex] = supportedPreviewSize.width + "x" + supportedPreviewSize.height;
			entryValues[previewSizeIndex] = String.valueOf(previewSizeIndex);
		} // for

		sizePreference.setEntries(entries);
		sizePreference.setEntryValues(entryValues);
	} // setSizePreferenceData(ListPreference)

//	private void setUsbDevicePreference(final ListPreference usbDevicePreference){
//		//final List<DeviceEntry> result = new ArrayList<DeviceEntry>();
//
//		final CharSequence[] entries;
//		final CharSequence[] entryValues;
//
//    	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
//    		entries = new CharSequence[1];
//    		entryValues = new CharSequence[1];
//
//    		entries[0] = "not supported";
//    		entryValues[0] = "-1";
//        }
//    	else {
////			for (final UsbDevice device : mUsbManager.getDeviceList().values()) {
////				final List<UsbSerialDriver> drivers = UsbSerialProber.probeSingleDevice(mUsbManager, device);
////
////				Log.d(TAG, "Found usb device: " + device);
////				if (drivers.isEmpty()) {
////					Log.d(TAG, " - No UsbSerialDriver available.");
////					result.add(new DeviceEntry(device, null));
////				} else {
////					for (UsbSerialDriver driver : drivers) {
////						Log.d(TAG, " + " + driver);
////						result.add(new DeviceEntry(device, driver));
////					}
////				}
////			}
//
//			final List<UsbSerialDriver> driverList = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
//
//			final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
//			if (driverList.isEmpty()) {
//				Log.d(TAG, " - No UsbSerialDriver available.");
//                entries = new CharSequence[1];
//                entryValues = new CharSequence[1];
//                entries[0] = "not supported";
//                entryValues[0] = "-1";
//			} else {
//                for (final UsbSerialDriver driver : driverList) {
//                    final List<UsbSerialPort> portList = driver.getPorts();
//
//                    Log.d(TAG, String.format("+ %s: %s port%s",
//                            driver, Integer.valueOf(portList.size()), portList.size() == 1 ? "" : "s"));
//
//                    result.addAll(portList);
//                }
//
//                final int numberOfPorts = result.size();
//                entries = new CharSequence[numberOfPorts];
//                entryValues = new CharSequence[numberOfPorts];
//
//                for (int portIndex = 0; portIndex < numberOfPorts; portIndex++) {
//                    entries[portIndex] = String.format(
//                            "Device %s Port %s (Vendor %s Product %s)"
//                            , result.get(portIndex).getDriver().getDevice()
//                            , result.get(portIndex).getPortNumber()
//                            , HexDump.toHexString((short) result.get(portIndex).getDriver().getDevice().getVendorId())
//                            , HexDump.toHexString((short) result.get(portIndex).getDriver().getDevice().getProductId())
//                    );
//
//                    entryValues[portIndex] = String.valueOf(result.get(portIndex).getDriver().getDevice()) + '-' + String.valueOf(result.get(portIndex).getPortNumber());
//                }
//            }
//    	}
//		usbDevicePreference.setEntries(entries);
//		usbDevicePreference.setEntryValues(entryValues);
//	}
} // class PeepersPreferenceActivity