package cn.szx.simplescanner.zbar;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import cn.szx.simplescanner.base.BarcodeScannerView;
import cn.szx.simplescanner.base.IViewFinder;

/**
 * zbar扫码视图，继承自基本扫码视图BarcodeScannerView
 * <p>
 * BarcodeScannerView内含CameraPreview（相机预览）和ViewFinderView（扫码框、阴影遮罩等）
 */
public class ZBarScannerView extends BarcodeScannerView {
    private static final String TAG = "ZBarScannerView";
    private ImageScanner imageScanner;
    private List<BarcodeFormat> formats;
    private ResultHandler resultHandler;

    public interface ResultHandler {
        void handleResult(Result rawResult);
    }

    /*
     * 加载zbar动态库
     * zbar.jar中的类会用到
     */
    static {
        System.loadLibrary("iconv");
    }

    public ZBarScannerView(@NonNull Context context, @NonNull IViewFinder viewFinderView, @Nullable ResultHandler resultHandler) {
        super(context, viewFinderView);
        this.resultHandler = resultHandler;
        setupScanner();//创建ImageScanner（zbar扫码器）并进行基本设置（如支持的码格式）
    }

    /**
     * 创建ImageScanner并进行基本设置（如支持的码格式）
     */
    public void setupScanner() {
        imageScanner = new ImageScanner();

        imageScanner.setConfig(0, Config.X_DENSITY, 3);
        imageScanner.setConfig(0, Config.Y_DENSITY, 3);

        imageScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);

        for (BarcodeFormat format : getFormats()) {//设置支持的码格式
            imageScanner.setConfig(format.getId(), Config.ENABLE, 1);
        }
    }

    /**
     * Called as preview frames are displayed.<br/>
     * This callback is invoked on the event thread open(int) was called from.<br/>
     * (此方法与Camera.open运行于同一线程，在本项目中，就是CameraHandlerThread线程)
     */
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (resultHandler == null) return;

        try {
            Camera.Parameters parameters = camera.getParameters();
            int previewWidth = parameters.getPreviewSize().width;
            int previewHeight = parameters.getPreviewSize().height;

            //根据ViewFinderView和preview的尺寸之比，缩放扫码区域
            Rect rect = getScaledRect(previewWidth, previewHeight);

            /*
             * 方案一：旋转图像数据
             */
            //int rotationCount = getRotationCount();//相机图像需要被顺时针旋转几次（每次90度）
            //if (rotationCount == 1 || rotationCount == 3) {//相机图像需要顺时针旋转90度或270度
            //    //交换宽高
            //    int tmp = previewWidth;
            //    previewWidth = previewHeight;
            //    previewHeight = tmp;
            //}
            ////旋转数据
            //data = rotateData(data, camera);

            /*
             * 方案二：旋转截取区域
             */
            rect = getRotatedRect(previewWidth, previewHeight, rect);

            //从preView的图像中截取扫码区域
            Image barcode = new Image(previewWidth, previewHeight, "Y800");
            barcode.setData(data);
            barcode.setCrop(rect.left, rect.top, rect.width(), rect.height());

            //使用zbar库识别扫码区域
            int result = imageScanner.scanImage(barcode);
            if (result != 0) {//识别成功
                SymbolSet syms = imageScanner.getResults();
                final Result rawResult = new Result();
                for (Symbol sym : syms) {
                    // In order to retreive QR codes containing null bytes we need to
                    // use getDataBytes() rather than getData() which uses C strings.
                    // Weirdly ZBar transforms all data to UTF-8, even the data returned
                    // by getDataBytes() so we have to decode it as UTF-8.
                    String symData;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        symData = new String(sym.getDataBytes(), StandardCharsets.UTF_8);
                    } else {
                        symData = sym.getData();
                    }
                    if (!TextUtils.isEmpty(symData)) {
                        rawResult.setContents(symData);
                        rawResult.setBarcodeFormat(BarcodeFormat.getFormatById(sym.getType()));
                        break;//识别成功一个就跳出循环
                    }
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {//切换到主线程
                    @Override
                    public void run() {
                        if (resultHandler != null) {
                            resultHandler.handleResult(rawResult);
                        }
                    }
                });
            } else {//识别失败
                getOneMoreFrame();//再获取一帧图像数据进行识别（会再次触发onPreviewFrame方法）
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

//--------------------------------------------------------------------------------------------------

    /**
     * 设置支持的码格式
     */
    public void setFormats(@NonNull List<BarcodeFormat> formats) {
        this.formats = formats;
        setupScanner();
    }

    public Collection<BarcodeFormat> getFormats() {
        if (formats == null) {
            return BarcodeFormat.ALL_FORMATS;
        }
        return formats;
    }

    /**
     * 再获取一帧图像数据进行识别（会再次触发onPreviewFrame方法）
     */
    public void getOneMoreFrame() {
        if (cameraWrapper != null) {
            cameraWrapper.camera.setOneShotPreviewCallback(this);
        }
    }
}