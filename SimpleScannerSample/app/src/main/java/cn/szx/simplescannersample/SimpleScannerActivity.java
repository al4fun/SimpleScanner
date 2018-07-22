package cn.szx.simplescannersample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;
import android.widget.Toast;

import cn.szx.simplescanner.zbar.Result;
import cn.szx.simplescanner.zbar.ZBarScannerView;

/**
 * 最简单的使用示例
 */
public class SimpleScannerActivity extends AppCompatActivity implements ZBarScannerView.ResultHandler {
    private static final String TAG = "SimpleScannerActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 0;
    private ZBarScannerView zBarScannerView;
    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_simple_scanner);

        initView();
    }

    private void initView() {
        ViewGroup container = findViewById(R.id.container);

        //ViewFinderView是根据需求自定义的视图，会被覆盖在相机预览画面之上，通常包含扫码框、扫描线、扫码框周围的阴影遮罩等
        zBarScannerView = new ZBarScannerView(this, new ViewFinderView(this), this);
        //zBarScannerView.setShouldAdjustFocusArea(true);//自动调整对焦区域

        container.addView(zBarScannerView);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            zBarScannerView.startCamera();//打开系统相机，并进行基本的初始化
        } else {//没有相机权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        handler.removeCallbacksAndMessages(null);
        zBarScannerView.stopCamera();//释放相机资源等各种资源
    }

    @Override
    public void handleResult(Result rawResult) {
        Toast.makeText(this, "Contents = " + rawResult.getContents() + ", Format = " + rawResult.getBarcodeFormat().getName(), Toast.LENGTH_SHORT).show();

        //2秒后再次识别
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                zBarScannerView.getOneMoreFrame();//再获取一帧图像数据进行识别
            }
        }, 2000);
    }
}