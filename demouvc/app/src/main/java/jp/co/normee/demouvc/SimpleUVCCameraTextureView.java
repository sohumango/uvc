package jp.co.normee.demouvc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.nio.ByteBuffer;
import java.util.List;

class SimpleUVCCameraTextureView extends TextureView	// API >= 14
        implements AspectRatioViewInterface {

    private double mRequestedAspect = -1.0;
    private final Object mSync = new Object();
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private Surface mPreviewSurface;
    private List<DeviceFilter> mFilter;

    private void registerUSB() {
        if (mUSBMonitor != null) {
            mUSBMonitor.register();
        }
    }

    private void unregisterUSB() {
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
        }
    }
    private void startPreview() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.startPreview();
            }
        }
    }

    private void stopPreview() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
            }
            if (mUSBMonitor != null) {
                mUSBMonitor.unregister();
            }
        }
    }

    private void releaseUSB(){
        synchronized (mSync) {
            releaseCamera();
            if (mUSBMonitor != null) {
                mUSBMonitor.destroy();
                mUSBMonitor = null;
            }
        }
    }

    public void openCamera(){
        registerUSB();
        startPreview();
    }
    public void closeCamera(){
        stopPreview();
        unregisterUSB();
        releaseUSB();
    }
    public SimpleUVCCameraTextureView(final Context context) {
        this(context, null, 0);
    }

    public SimpleUVCCameraTextureView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SimpleUVCCameraTextureView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        mFilter= DeviceFilter.getDeviceFilters(context, com.serenegiant.uvccamera.R.xml.device_filter);
        mUSBMonitor = new USBMonitor(context, mOnDeviceConnectListener);
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void setAspectRatio(final double aspectRatio) {
        if (aspectRatio < 0) {
            throw new IllegalArgumentException();
        }
        if (mRequestedAspect != aspectRatio) {
            mRequestedAspect = aspectRatio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        if (mRequestedAspect > 0) {
            int initialWidth = MeasureSpec.getSize(widthMeasureSpec);
            int initialHeight = MeasureSpec.getSize(heightMeasureSpec);

            final int horizPadding = getPaddingLeft() + getPaddingRight();
            final int vertPadding = getPaddingTop() + getPaddingBottom();
            initialWidth -= horizPadding;
            initialHeight -= vertPadding;

            final double viewAspectRatio = (double)initialWidth / initialHeight;
            final double aspectDiff = mRequestedAspect / viewAspectRatio - 1;

            if (Math.abs(aspectDiff) > 0.01) {
                if (aspectDiff > 0) {
                    // width priority decision
                    initialHeight = (int) (initialWidth / mRequestedAspect);
                } else {
                    // height priority decison
                    initialWidth = (int) (initialHeight * mRequestedAspect);
                }
                initialWidth += horizPadding;
                initialHeight += vertPadding;
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }


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
                    final SurfaceTexture st = getSurfaceTexture();
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
        }
    };
}
