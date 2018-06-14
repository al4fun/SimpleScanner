package cn.szx.simplescanner.base;

import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

// This code is mostly based on the top answer here:
// http://stackoverflow.com/questions/18149964/best-use-of-handlerthread-over-other-similar-classes
public class CameraHandlerThread extends HandlerThread {
    private static final String TAG = "CameraHandlerThread";
    private BarcodeScannerView mScannerView;

    public CameraHandlerThread(BarcodeScannerView scannerView) {
        super("CameraHandlerThread");
        mScannerView = scannerView;
        start();
    }

    /**
     * 打开系统相机，并进行基本的初始化
     */
    public void startCamera(final int cameraId) {
        Handler localHandler = new Handler(getLooper());
        localHandler.post(new Runnable() {//在当前线程执行
            @Override
            public void run() {
                final Camera camera = CameraUtils.getCameraInstance(cameraId);//打开camera
                Handler mainHandler = new Handler(Looper.getMainLooper());//切换到主线程
                mainHandler.post(new Runnable() {//在主线程执行
                    @Override
                    public void run() {
                        mScannerView.setupCameraPreview(CameraWrapper.getWrapper(camera, cameraId));
                    }
                });
            }
        });
    }
}