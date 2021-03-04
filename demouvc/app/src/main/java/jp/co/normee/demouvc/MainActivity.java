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

    private SimpleUVCCameraTextureView mUVCCameraView;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUVCCameraView = (SimpleUVCCameraTextureView) findViewById(R.id.UVCCameraTextureView1);
        mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUVCCameraView.openCamera();
    }

    @Override
    protected void onStop() {
        Log.d("USB", "onStop: ");
        mUVCCameraView.closeCamera();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d("USB", "onDestroy: ");
        super.onDestroy();
    }
}