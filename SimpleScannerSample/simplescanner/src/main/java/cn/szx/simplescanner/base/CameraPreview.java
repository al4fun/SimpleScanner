package cn.szx.simplescanner.base;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Handler;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.util.List;

/**
 * 相机预览
 * <p>
 * 运行的主线为SurfaceHolder.Callback的三个回调方法
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CameraPreview";

    private CameraWrapper cameraWrapper;//当相机被释放时会被置为null
    private Camera.PreviewCallback previewCallback;//当相机被释放时会被置为null
    private FocusAreaSetter focusAreaSetter;

    private Handler autoFocusHandler = new Handler();
    private boolean previewing = true;//是否正在预览
    private boolean surfaceCreated = false;//surface是否已创建
    private float aspectTolerance = 0.1f;//允许的实际宽高比和理想宽高比之间的最大差值

    public interface FocusAreaSetter {
        void setAutoFocusArea();
    }

    public CameraPreview(Context context, CameraWrapper cameraWrapper,
                         Camera.PreviewCallback previewCallback, FocusAreaSetter focusAreaSetter) {
        super(context);
        setCamera(cameraWrapper, previewCallback);
        this.focusAreaSetter = focusAreaSetter;

        getHolder().addCallback(this);//surface生命周期的回调
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setCamera(CameraWrapper cameraWrapper, Camera.PreviewCallback previewCallback) {
        this.cameraWrapper = cameraWrapper;
        this.previewCallback = previewCallback;
    }

//--------------------------------------------------------------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        surfaceCreated = true;
        startCameraPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        surfaceCreated = false;
        stopCameraPreview();
    }

//--------------------------------------------------------------------------------------------------

    /**
     * 开始预览
     */
    public void startCameraPreview() {
        if (cameraWrapper != null) {
            try {
                previewing = true;
                setupCameraParameters();//设置相机参数
                cameraWrapper.camera.setPreviewDisplay(getHolder());//设置在当前surfaceView中进行相机预览
                cameraWrapper.camera.setDisplayOrientation(getDisplayOrientation());//设置相机预览图像的旋转角度
                cameraWrapper.camera.setOneShotPreviewCallback(previewCallback);//设置一次性的预览回调
                cameraWrapper.camera.startPreview();//开始预览
                safeAutoFocus();//自动对焦
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 停止预览
     */
    public void stopCameraPreview() {
        if (cameraWrapper != null) {
            try {
                previewing = false;
                getHolder().removeCallback(this);
                cameraWrapper.camera.cancelAutoFocus();
                cameraWrapper.camera.setOneShotPreviewCallback(null);
                cameraWrapper.camera.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 尝试自动对焦
     */
    private void safeAutoFocus() {
        if (cameraWrapper != null && previewing && surfaceCreated) {
            try {
                focusAreaSetter.setAutoFocusArea();
                cameraWrapper.camera.autoFocus(autoFocusCB);
            } catch (Exception e) {
                e.printStackTrace();
                scheduleAutoFocus();//如果对焦失败，则1s后重试
            }
        }
    }

    /**
     * 一秒之后尝试自动对焦
     */
    private void scheduleAutoFocus() {
        autoFocusHandler.postDelayed(new Runnable() {
            public void run() {
                safeAutoFocus();
            }
        }, 1000);
    }

    Camera.AutoFocusCallback autoFocusCB = new Camera.AutoFocusCallback() {
        //自动对焦完成时此方法被调用
        public void onAutoFocus(boolean success, Camera camera) {
            scheduleAutoFocus();//一秒之后再次自动对焦
        }
    };

    /**
     * 设置相机参数
     */
    private void setupCameraParameters() {
        if (cameraWrapper != null) {
            Camera.Parameters parameters = cameraWrapper.camera.getParameters();
            Camera.Size optimalSize = getOptimalPreviewSize();
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            cameraWrapper.camera.setParameters(parameters);
        }
    }

    /**
     * 要使相机图像的方向与手机中窗口的方向一致，相机图像需要顺时针旋转的角度
     * <p>
     * 此方法由google官方提供，详见Camera类中setDisplayOrientation的方法说明
     */
    public int getDisplayOrientation() {
        if (cameraWrapper == null) {
            return 0;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        if (cameraWrapper.cameraId == -1) {
            Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        } else {
            Camera.getCameraInfo(cameraWrapper.cameraId, info);
        }

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        int rotation = display.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    /**
     * 找到一个合适的previewSize（根据控件的尺寸）
     */
    private Camera.Size getOptimalPreviewSize() {
        if (cameraWrapper == null) {
            return null;
        }

        //相机图像默认都是横屏(即宽>高)
        List<Camera.Size> sizes = cameraWrapper.camera.getParameters().getSupportedPreviewSizes();
        if (sizes == null) return null;
        int w, h;
        if (DisplayUtils.getScreenOrientation(getContext()) == Configuration.ORIENTATION_LANDSCAPE) {
            w = getWidth();
            h = getHeight();
        } else {
            w = getHeight();
            h = getWidth();
        }

        double targetRatio = (double) w / h;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > aspectTolerance) continue;
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
}