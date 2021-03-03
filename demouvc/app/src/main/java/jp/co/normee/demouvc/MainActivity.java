package jp.co.normee.demouvc;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final Object mSync = new Object();
    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private SimpleUVCCameraTextureView mUVCCameraView;

    private Surface mPreviewSurface;
    private List<DeviceFilter> mFilter;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageButton mCameraButton = (ImageButton)findViewById(R.id.camera_button);
        mFilter= DeviceFilter.getDeviceFilters(this, com.serenegiant.uvccamera.R.xml.device_filter);
        mCameraButton.setOnClickListener(mOnClickListener);
        mUVCCameraView = (SimpleUVCCameraTextureView) findViewById(R.id.UVCCameraTextureView1);
        mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.startPreview();
            }
        }
    }

    @Override
    protected void onStop() {
        Log.d("USB", "onStop: ");
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
            }
            if (mUSBMonitor != null) {
                mUSBMonitor.unregister();
            }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d("USB", "onDestroy: ");
        synchronized (mSync) {
            releaseCamera();
            if (mUSBMonitor != null) {
                mUSBMonitor.destroy();
                mUSBMonitor = null;
            }
        }
        mUVCCameraView = null;
        super.onDestroy();
    }

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            synchronized (mSync) {
                if (mUVCCamera == null) {
                    List<UsbDevice> usbCams = mUSBMonitor.getDeviceList(mFilter.get(0));
                    if(usbCams.size()>0){
                        UsbDevice item = usbCams.get(0);
                        if (item instanceof UsbDevice) {
                            mUSBMonitor.requestPermission((UsbDevice)item);
                        }
                    }
                } else {
                    releaseCamera();
                }
            }
        }
    };

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Log.d("USB", "USB_DEVICE_ATTACHED");
            synchronized (mSync) {
                if (mUVCCamera == null) {
                    List<UsbDevice> usbCams = mUSBMonitor.getDeviceList(mFilter.get(0));
                    if(usbCams.size()>0){
                        UsbDevice item = usbCams.get(0);
                        if (item instanceof UsbDevice) {
                            mUSBMonitor.requestPermission((UsbDevice)item);
                        }
                    }
                } else {
                    releaseCamera();
                }
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            releaseCamera();
            new Runnable() {
                @Override
                public void run() {
                    final UVCCamera camera = new UVCCamera();
                    camera.open(ctrlBlock);
                    camera.setStatusCallback(new IStatusCallback() {
                        @Override
                        public void onStatus(final int statusClass, final int event, final int selector,
                                             final int statusAttribute, final ByteBuffer data) {
                            Log.d("USB", "onStatus(statusClass=" + statusClass
                                    + "; " +
                                    "event=" + event + "; " +
                                    "selector=" + selector + "; " +
                                    "statusAttribute=" + statusAttribute + "; " +
                                    "data=...)");
                        }
                    });
//					camera.setPreviewTexture(camera.getSurfaceTexture());
                    if (mPreviewSurface != null) {
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }
                    try {
                        camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                    } catch (final IllegalArgumentException e) {
                        // fallback to YUV mode
                        try {
                            camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                        } catch (final IllegalArgumentException e1) {
                            camera.destroy();
                            return;
                        }
                    }
                    final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
                    if (st != null) {
                        mPreviewSurface = new Surface(st);
                        camera.setPreviewDisplay(mPreviewSurface);
//						camera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565/*UVCCamera.PIXEL_FORMAT_NV21*/);
                        camera.startPreview();
                        camera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565);
                        Log.d("USB", "setFrameCallback: ");
                    }
                    synchronized (mSync) {
                        mUVCCamera = camera;
                    }
                }
            }.run();
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            // XXX you should check whether the coming device equal to camera device that currently using
            releaseCamera();
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Log.d("USB", "USB_DEVICE_DETACHED");
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    private synchronized void releaseCamera() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                try {
                    mUVCCamera.setStatusCallback(null);
                    mUVCCamera.setButtonCallback(null);
                    mUVCCamera.close();
                    mUVCCamera.destroy();
                } catch (final Exception e) {
                    //
                }
                mUVCCamera = null;
            }
            if (mPreviewSurface != null) {
                mPreviewSurface.release();
                mPreviewSurface = null;
            }
        }
    }

    // if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
    // if you need to create Bitmap in IFrameCallback, please refer following snippet.
    final Bitmap bitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);
    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            try {
                Thread.sleep(1000);
            }catch (Exception e){}
            frame.clear();
            synchronized (bitmap) {
                bitmap.copyPixelsFromBuffer(frame);
            }
            ImageView mImageView = findViewById(R.id.img1);
            if(mImageView!=null) {
                mImageView.post(mUpdateImageTask);
            }
        }
    };

    private final Runnable mUpdateImageTask = new Runnable() {
        @Override
        public void run() {
            synchronized (bitmap) {
                ImageView mImageView = findViewById(R.id.img1);
                if(mImageView!=null) {
                    mImageView.setImageBitmap(bitmap);
                }
            }
        }
    };
}