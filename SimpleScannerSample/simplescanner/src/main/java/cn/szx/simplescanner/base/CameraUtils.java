package cn.szx.simplescanner.base;

import android.hardware.Camera;
import android.util.Log;

import java.util.List;

public class CameraUtils {
    private static final String TAG = "CameraUtils";

    public static Camera getCameraInstance() {
        return getCameraInstance(getDefaultCameraId());
    }

    /**
     * 返回第一个后置相机的id
     **/
    public static int getDefaultCameraId() {
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int defaultCameraId = -1;
        for (int i = 0; i < numberOfCameras; i++) {
            defaultCameraId = i;
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }
        return defaultCameraId;
    }

    public static Camera getCameraInstance(int cameraId) {
        Camera c = null;
        try {
            if (cameraId == -1) {
                c = Camera.open(); //打开主相机
            } else {
                c = Camera.open(cameraId);
            }
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            Log.e(TAG, e.toString());
        }
        return c; // returns null if camera is unavailable
    }

    public static boolean isFlashSupported(Camera camera) {
        /* Credits: Top answer at http://stackoverflow.com/a/19599365/868173 */
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();

            if (parameters.getFlashMode() == null) {
                return false;
            }

            List<String> supportedFlashModes = parameters.getSupportedFlashModes();
            if (supportedFlashModes == null || supportedFlashModes.isEmpty() || supportedFlashModes.size() == 1 && supportedFlashModes.get(0).equals(Camera.Parameters.FLASH_MODE_OFF)) {
                return false;
            }
        } else {
            return false;
        }

        return true;
    }
}