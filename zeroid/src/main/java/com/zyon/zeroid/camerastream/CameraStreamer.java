package com.zyon.zeroid.camerastream;

import java.io.IOException;
import java.util.List;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;

import com.zyon.zeroid.Util.MemoryOutputStream;
import com.zyon.zeroid.Util.MovingAverage;

@SuppressWarnings("deprecation")
public final class CameraStreamer extends Object{
    private static final String TAG = CameraStreamer.class.getSimpleName();
    
    private static final int MESSAGE_TRY_START_STREAMING = 0;
    private static final int MESSAGE_SEND_PREVIEW_FRAME = 1;
    
    private static final long OPEN_CAMERA_POLL_INTERVAL_MS = 1000L;
    
    private final Object mLock = new Object();
    private final MovingAverage mAverageSpf = new MovingAverage(50 /* numValues */);
    
    private final int mCameraIndex;
    private final boolean mUseCameraPreview;
    private final int mPort;
    private final int mPreviewSizeIndex;
    private final int mJpegQuality;
    private final SurfaceHolder mPreviewDisplay;
    
    private boolean mRunning = false;
    private Looper mLooper = null;
    private Handler mWorkHandler = null;
    private Camera mCamera = null;
    private int mPreviewFormat = Integer.MIN_VALUE;
    private int mPreviewWidth = Integer.MIN_VALUE;
    private int mPreviewHeight = Integer.MIN_VALUE;
    private Rect mPreviewRect = null;
    private int mPreviewBufferSize = Integer.MIN_VALUE;
    private MemoryOutputStream mJpegOutputStream = null;
    private MjpegHttpStreamer mMJpegHttpStreamer = null;
    
    private long mNumFrames = 0L;
    private long mLastTimestamp = Long.MIN_VALUE;
    
    public CameraStreamer(final int cameraIndex
    				, final int port
    				, final int previewSizeIndex, final int jpegQuality
    				, final SurfaceHolder previewDisplay
    				, final boolean useCameraPreview){
        super();
        
        if (previewDisplay == null){
            throw new IllegalArgumentException("previewDisplay must not be null");
        } // if
        
        mCameraIndex = cameraIndex;
        mPort = port;
        mPreviewSizeIndex = previewSizeIndex;
        mJpegQuality = jpegQuality;
        mPreviewDisplay = previewDisplay;
        mUseCameraPreview = useCameraPreview;
    } // constructor(SurfaceHolder)
    
    private final class WorkHandler extends Handler {
        private WorkHandler(final Looper looper) {
            super(looper);
        } // constructor(Looper)

        @Override
        public void handleMessage(final Message message) {
            switch (message.what) {
                case MESSAGE_TRY_START_STREAMING:
                    tryStartStreaming();
                    break;
                case MESSAGE_SEND_PREVIEW_FRAME:
                    final Object[] args = (Object[]) message.obj;
                    sendPreviewFrame((byte[]) args[0], (Camera) args[1], (Long) args[2]);
                    break;
                default:
                    throw new IllegalArgumentException("cannot handle message");
            } // switch
        } // handleMessage(Message)
    } // class WorkHandler
    
    public void autoFocus(){
    	if(mCamera != null)
    		mCamera.autoFocus(new Camera.AutoFocusCallback() {
    			@Override
    			public void onAutoFocus(boolean success, Camera camera) {
    				//foce loop to keep led on is a bad idea
    				//camera.autoFocus(this);
    			}
		});
    }
    
    public void LedOn(){
        if(mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            if (params.getSupportedFlashModes() != null) {
                final List<String> camFlashMode = params.getSupportedFlashModes();

//			for (int Index = 0; Index < camFlashMode.size(); Index++) {
//				Log.w(TAG, "Flash: " + camFlashMode.get(Index));
//			}
//			off
//			auto
//			on

//    		final List<String> camFocusMode = params.getSupportedFocusModes();
//    		for (int Index = 0; Index < camFocusMode.size(); Index++) {
//    			Log.w(TAG,"Focus: " + camFocusMode.get(Index));
//    		}
//    		auto
//    		marco

                if (camFlashMode != null && camFlashMode.contains("on")) {
                    Log.d(TAG, "LED ON");
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);

//				params.set("flash-mode", "on");
//				params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
//				mCamera.setParameters(params);
//				mCamera.autoFocus(new Camera.AutoFocusCallback() {
//	                public void onAutoFocus(boolean success, Camera camera) {
//	                }
//	            });
                } else if (camFlashMode != null && camFlashMode.contains("torch")) {
                    Log.d(TAG, "LED TORCH");
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }

                mCamera.setParameters(params);
            }
        }
    }
    
    public void LedOff(){
    	Log.d(TAG, "LED OFF");
    	Camera.Parameters params = mCamera.getParameters();
        params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
        mCamera.setParameters(params);
    }
    
    public void start() {
        synchronized (mLock) {
            if (mRunning) {
            	Log.d(TAG, "CameraStreamer is already running");
                throw new IllegalStateException("CameraStreamer is already running");
            } // if
            mRunning = true;
        } // synchronized
        
        Log.d(TAG, "Start");
        
        final HandlerThread worker = new HandlerThread(TAG, Process.THREAD_PRIORITY_MORE_FAVORABLE);
        worker.setDaemon(true);
        worker.start();
        mLooper = worker.getLooper();
        mWorkHandler = new WorkHandler(mLooper);
        mWorkHandler.obtainMessage(MESSAGE_TRY_START_STREAMING).sendToTarget();
    } // start()
    
    /**
     *  Stop the image streamer. The camera will be released during the
     *  execution of stop() or shortly after it returns. stop() should
     *  be called on the main thread.
     */
    public void stop() {
        synchronized (mLock) {
            //if (!mRunning)
            //{
            //    throw new IllegalStateException("CameraStreamer is already stopped");
            //} // if
        	
        	Log.d(TAG, "Stop");
        	
            mRunning = false;
            if (mMJpegHttpStreamer != null) {
                mMJpegHttpStreamer.stop();
                mMJpegHttpStreamer = null;
            } // if
            
            if (mCamera != null) {
                mCamera.release();
                mCamera = null;
            } // if
        } // synchronized
        mLooper.quit();
    } // stop()
    
    private void tryStartStreaming() {
        try {
        	Log.d(TAG, "tryStartStreaming");
            while (true) {
                try {
                    startStreamingIfRunning();
                } //try
                catch (final RuntimeException openCameraFailed) {
                    Log.d(TAG, "Open camera failed, retying in " + OPEN_CAMERA_POLL_INTERVAL_MS
                            + "ms", openCameraFailed);
                    Thread.sleep(OPEN_CAMERA_POLL_INTERVAL_MS);
                    continue;
                } // catch
               break;
            } // while
        } // try
        catch (final Exception startPreviewFailed) {
            // Captures the IOException from startStreamingIfRunning and
            // the InterruptException from Thread.sleep.
            Log.w(TAG, "Failed to start camera preview", startPreviewFailed);
        } // catch
    } // tryStartStreaming()
    
    private void startStreamingIfRunning() throws IOException {
    	Log.d(TAG, "startStreamingIfRunning");
    	
        // Throws RuntimeException if the camera is currently opened
        // by another application.
    	mCamera = Camera.open(mCameraIndex);
        final Camera.Parameters params = mCamera.getParameters();
        
        final List<Camera.Size> supportedPreviewSizes = params.getSupportedPreviewSizes();
        final Camera.Size selectedPreviewSize = supportedPreviewSizes.get(mPreviewSizeIndex);
        params.setPreviewSize(selectedPreviewSize.width, selectedPreviewSize.height);
        
//        if (mUseFlashLight) {
//            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//        	//params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
//        } // if
        
        // Set Preview FPS range. The range with the greatest maximum
        // is returned first.
        final List<int[]> supportedPreviewFpsRanges = params.getSupportedPreviewFpsRange();
        // XXX: However sometimes it returns null. This is a known bug
        // https://code.google.com/p/android/issues/detail?id=6271
        // In which case, we just don't set it.
        if (supportedPreviewFpsRanges != null) {
            final int[] range = supportedPreviewFpsRanges.get(0);
            params.setPreviewFpsRange(range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                    range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
            mCamera.setParameters(params);
        } // if
        
        // Set up preview callback
        mPreviewFormat = params.getPreviewFormat();
        final Camera.Size previewSize = params.getPreviewSize();
        mPreviewWidth = previewSize.width;
        mPreviewHeight = previewSize.height;
        final int BITS_PER_BYTE = 8;
        final int bytesPerPixel = ImageFormat.getBitsPerPixel(mPreviewFormat) / BITS_PER_BYTE;
        // XXX: According to the documentation the buffer size can be
        // calculated by width * height * bytesPerPixel. However, this
        // returned an error saying it was too small. It always needed
        // to be exactly 1.5 times larger.
        mPreviewBufferSize = mPreviewWidth * mPreviewHeight * bytesPerPixel * 3 / 2 + 1;
        mCamera.addCallbackBuffer(new byte[mPreviewBufferSize]);
        mPreviewRect = new Rect(0, 0, mPreviewWidth, mPreviewHeight);
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
        
        // We assumed that the compressed image will be no bigger than
        // the uncompressed image.
        mJpegOutputStream = new MemoryOutputStream(mPreviewBufferSize);
        
        mMJpegHttpStreamer = new MjpegHttpStreamer(mPort, mPreviewBufferSize);
        mMJpegHttpStreamer.start();
        
        synchronized (mLock) {
            if (!mRunning) {
            	mMJpegHttpStreamer.stop();
            	mCamera.release();
                
                return;
            } // if
            
            try {
            	if(mUseCameraPreview){
            		mCamera.setPreviewDisplay(mPreviewDisplay);
            	}
            } // try
            catch (final IOException e) {
            	Log.d(TAG, "IOException camera.setPreviewDisplay");
            	mMJpegHttpStreamer.stop();
            	mCamera.release();
                throw e;
            } // catch
            
            mCamera.startPreview();
        } // synchronized
    } // startStreamingIfRunning()
    
    private final Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(final byte[] data, final Camera camera)
        {
            final Long timestamp = SystemClock.elapsedRealtime();
            final Message message = mWorkHandler.obtainMessage();
            message.what = MESSAGE_SEND_PREVIEW_FRAME;
            message.obj = new Object[]{ data, camera, timestamp };
            message.sendToTarget();
        } // onPreviewFrame(byte[], Camera)
    }; // mPreviewCallback
    
    private void sendPreviewFrame(final byte[] data, final Camera camera, final long timestamp) {
        // Calcalute the timestamp
        final long MILLI_PER_SECOND = 1000L;
        final long timestampSeconds = timestamp / MILLI_PER_SECOND;
        
        // Update and log the frame rate
        final long LOGS_PER_FRAME = 10L;
        mNumFrames++;
        if (mLastTimestamp != Long.MIN_VALUE) {
            mAverageSpf.update(timestampSeconds - mLastTimestamp);
            if (mNumFrames % LOGS_PER_FRAME == LOGS_PER_FRAME - 1){
                Log.d(TAG, "FPS: " + 1.0 / mAverageSpf.getAverage());
            } // if
        } // else
        
        mLastTimestamp = timestampSeconds;
        
        // Create JPEG
        final YuvImage image = new YuvImage(data, mPreviewFormat, mPreviewWidth, mPreviewHeight,
                null /* strides */);
        image.compressToJpeg(mPreviewRect, mJpegQuality, mJpegOutputStream);
        if(mMJpegHttpStreamer != null)
        	mMJpegHttpStreamer.streamJpeg(mJpegOutputStream.getBuffer(), mJpegOutputStream.getLength(), timestamp);

        // Clean up
        mJpegOutputStream.seek(0);
        // XXX: I believe that this is thread-safe because we're not
        // calling methods in other threads. I might be wrong, the
        // documentation is not clear.
        camera.addCallbackBuffer(data);
   } // sendPreviewFrame(byte[], camera, long)

    // Thank you, ApiDemos :)
   private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;
        
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        
        int targetHeight = h;
        
        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        
        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        
        return optimalSize;
    }
} // class CameraStreamer
