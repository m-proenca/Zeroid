package com.felhr.usbserial;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.util.Log;

public class CP2102SerialDevice extends UsbSerialDevice
{
	private static final String CLASS_ID = CP2102SerialDevice.class.getSimpleName();
	
	private static final int CP210x_IFC_ENABLE = 0x00;
	private static final int CP210x_SET_BAUDDIV = 0x01;
	private static final int CP210x_SET_LINE_CTL = 0x03;
	private static final int CP210x_GET_LINE_CTL = 0x04;
	private static final int CP210x_SET_MHS = 0x07;
	private static final int CP210x_SET_BAUDRATE = 0x1E;
	private static final int CP210x_SET_FLOW = 0x13;
	private static final int CP210x_SET_XON = 0x09;
	private static final int CP210x_SET_XOFF = 0x0A;
	private static final int CP210x_SET_CHARS = 0x19;
	
	private static final int CP210x_REQTYPE_HOST2DEVICE = 0x41;
	private static final int CP210x_REQTYPE_DEVICE2HOST = 0xC1;
	
	/***
	 *  Default Serial Configuration
	 *  Baud rate: 9600
	 *  Data bits: 8
	 *  Stop bits: 1
	 *  Parity: None
	 *  Flow Control: Off
	 */
	private static final int CP210x_UART_ENABLE = 0x0001;
	private static final int CP210x_UART_DISABLE = 0x0000;
	private static final int CP210x_LINE_CTL_DEFAULT = 0x0800;
	private static final int CP210x_MHS_DEFAULT = 0x0000;
	private static final int CP210x_MHS_DTR = 0x0001;
	private static final int CP210x_MHS_RTS = 0x0010;
	private static final int CP210x_MHS_ALL = 0x0011;
	private static final int CP210x_XON = 0x0000;
	private static final int CP210x_XOFF = 0x0000;
	private static final int DEFAULT_BAUDRATE = 9600;
	
	private UsbInterface mInterface;
	private UsbEndpoint inEndpoint;
	private UsbEndpoint outEndpoint;
	private UsbRequest requestIN;

	public CP2102SerialDevice(UsbDevice device, UsbDeviceConnection connection) 
	{
		this(device, connection, -1);
	}

	public CP2102SerialDevice(UsbDevice device, UsbDeviceConnection connection, int iface)
	{
		super(device, connection);
		mInterface = device.getInterface(iface >= 0 ? iface : 0);
	}

	@Override
	public boolean open() 
	{
		if(connection.claimInterface(mInterface, true))
		{
			Log.i(CLASS_ID, "Interface succesfully claimed");
		}else
		{
			Log.i(CLASS_ID, "Interface could not be claimed");
			return false;
		}
		
		// Assign endpoints
		int numberEndpoints = mInterface.getEndpointCount();
		for(int i=0;i<=numberEndpoints-1;i++)
		{
			UsbEndpoint endpoint = mInterface.getEndpoint(i);
			if(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
					&& endpoint.getDirection() == UsbConstants.USB_DIR_IN)
			{
				inEndpoint = endpoint;
			}else
			{
				outEndpoint = endpoint;
			}
		}
		
		
		// Default Setup
		if(setControlCommand(CP210x_IFC_ENABLE, CP210x_UART_ENABLE, null) < 0)
			return false;
		setBaudRate(DEFAULT_BAUDRATE);
		if(setControlCommand(CP210x_SET_LINE_CTL, CP210x_LINE_CTL_DEFAULT,null) < 0)
			return false;
		setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
		if(setControlCommand(CP210x_SET_MHS, CP210x_MHS_DEFAULT, null) < 0)
			return false;
		
		// Initialize UsbRequest
		requestIN = new UsbRequest();
		requestIN.initialize(connection, inEndpoint);
		
		// Restart the working thread if it has been killed before and  get and claim interface
		restartWorkingThread();
		restartWriteThread();
		
		// Pass references to the threads
		setThreadsParams(requestIN, outEndpoint);
		
		return true;
	}

	@Override
	public void close() 
	{
		setControlCommand(CP210x_IFC_ENABLE, CP210x_UART_DISABLE, null);
		killWorkingThread();
		killWriteThread();
		connection.releaseInterface(mInterface);
	}

	@Override
	public void setBaudRate(int baudRate) 
	{
		byte[] data = new byte[] {
				(byte) (baudRate & 0xff),
				(byte) (baudRate >> 8 & 0xff),
				(byte) (baudRate >> 16 & 0xff),
				(byte) (baudRate >> 24 & 0xff)
		};
		setControlCommand(CP210x_SET_BAUDRATE, 0, data);
	}

	@Override
	public void setDataBits(int dataBits) 
	{
		byte[] data = getCTL();
		switch(dataBits)
		{
		case UsbSerialInterface.DATA_BITS_5:
			data[1] = 5;
			break;
		case UsbSerialInterface.DATA_BITS_6:
			data[1] = 6;
			break;
		case UsbSerialInterface.DATA_BITS_7:
			data[1] = 7;
			break;
		case UsbSerialInterface.DATA_BITS_8:
			data[1] = 8;
			break;
		default:
			return;
		}
		byte wValue =  (byte) ((data[1] << 8) | (data[0] & 0xFF));
		setControlCommand(CP210x_SET_LINE_CTL, wValue, null);
		
	}

	@Override
	public void setStopBits(int stopBits) 
	{
		byte[] data = getCTL();
		switch(stopBits)
		{
		case UsbSerialInterface.STOP_BITS_1:
			data[0] &= ~1;
			data[0] &= ~(1 << 1);
			break;
		case UsbSerialInterface.STOP_BITS_15:
			data[0] |= 1;
			data[0] &= ~(1 << 1) ;
			break;
		case UsbSerialInterface.STOP_BITS_2:
			data[0] &= ~1;
			data[0] |= (1 << 1);
			break;
		default:
			return;
		}
		byte wValue =  (byte) ((data[1] << 8) | (data[0] & 0xFF));
		setControlCommand(CP210x_SET_LINE_CTL, wValue, null);
	}

	@Override
	public void setParity(int parity) 
	{
		byte[] data = getCTL();
		switch(parity)
		{
		case UsbSerialInterface.PARITY_NONE:
			data[0] &= ~(1 << 4);
			data[0] &= ~(1 << 5);
			data[0] &= ~(1 << 6);
			data[0] &= ~(1 << 7);
			break;
		case UsbSerialInterface.PARITY_ODD:
			data[0] |= (1 << 4);
			data[0] &= ~(1 << 5);
			data[0] &= ~(1 << 6);
			data[0] &= ~(1 << 7);
			break;
		case UsbSerialInterface.PARITY_EVEN:
			data[0] &= ~(1 << 4);
			data[0] |= (1 << 5);
			data[0] &= ~(1 << 6);
			data[0] &= ~(1 << 7);
			break;
		case UsbSerialInterface.PARITY_MARK:
			data[0] |= (1 << 4);
			data[0] |= (1 << 5);
			data[0] &= ~(1 << 6);
			data[0] &= ~(1 << 7);
			break;
		case UsbSerialInterface.PARITY_SPACE:
			data[0] &= ~(1 << 4);
			data[0] &= ~(1 << 5);
			data[0] |= (1 << 6);
			data[0] &= ~(1 << 7);
			break;
		default:
			return;
		}
		byte wValue =  (byte) ((data[1] << 8) | (data[0] & 0xFF));
		setControlCommand(CP210x_SET_LINE_CTL, wValue, null);
	}

	@Override
	public void setFlowControl(int flowControl) 
	{
		switch(flowControl)
		{
		case UsbSerialInterface.FLOW_CONTROL_OFF:
			byte[] dataOff = new byte[]{
					(byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
					(byte) 0x40, (byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x00
			};
			setControlCommand(CP210x_SET_FLOW, 0, dataOff);
			break;
		case UsbSerialInterface.FLOW_CONTROL_RTS_CTS:
			byte[] dataRTSCTS = new byte[]{
					(byte) 0x09, (byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x00
			};
			setControlCommand(CP210x_SET_FLOW, 0, dataRTSCTS);
			break;
		case UsbSerialInterface.FLOW_CONTROL_DSR_DTR:
			byte[] dataDSRDTR = new byte[]{
					(byte) 0x12, (byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x40, (byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x00
			};
			setControlCommand(CP210x_SET_FLOW, 0, dataDSRDTR);
			break;
		case UsbSerialInterface.FLOW_CONTROL_XON_XOFF:
			byte[] dataXONXOFF = new byte[]{
					(byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x43, (byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x00
			};
			
			byte[] dataChars = new byte[]{
					(byte) 0x00, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x11, (byte) 0x13
			};
			setControlCommand(CP210x_SET_CHARS, 0, dataChars);
			setControlCommand(CP210x_SET_FLOW, 0, dataXONXOFF);
			break;
		default:
			return;
		}
	}
	
	private int setControlCommand(int request, int value, byte[] data)
	{
		int dataLength = 0;
		if(data != null)
		{
			dataLength = data.length;
		}
		int response = connection.controlTransfer(CP210x_REQTYPE_HOST2DEVICE, request, value, mInterface.getId(), data, dataLength, USB_TIMEOUT);
		Log.i(CLASS_ID,"Control Transfer Response: " + String.valueOf(response));
		return response;
	}
	
	private byte[] getCTL()
	{
		byte[] data = new byte[2];
		int response = connection.controlTransfer(CP210x_REQTYPE_DEVICE2HOST, CP210x_GET_LINE_CTL, 0, mInterface.getId(), data, data.length,  USB_TIMEOUT );
		Log.i(CLASS_ID,"Control Transfer Response: " + String.valueOf(response));
		return data;
	}

}
