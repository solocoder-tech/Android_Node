package com.example.sweeper.ldvideodemo;

import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MainActivity extends AppCompatActivity implements LdDecoderLisenter {
    @BindView(R.id.layoutVideo)
    LinearLayout layoutVideo;
    @BindView(R.id.parentRl)
    RelativeLayout parentRl;
    @BindView(R.id.re_code)
    Button reCodeBtn;
    private VideoPlayViewGL mVideoPlayViewGL;
    private LdDecoder mLdDecoder;
    private boolean isEncoding = false;

    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mVideoPlayViewGL = new VideoPlayViewGL(this);
        layoutVideo.addView(mVideoPlayViewGL);
        mLdDecoder = new LdDecoder(this);
        mLdDecoder.setLdDecoderLisenter(this);

        MainActivityPermissionsDispatcher.requestSDWriteAndReadWithCheck(this);
    }


    @Override
    public void onResume() {
        super.onResume();

    }


    // 权限
    @NeedsPermission({Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE})
    public void requestSDWriteAndRead() {
        Toast.makeText(this, "申请权限成功", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onFrame(byte[] result) {
        isEncoding = true;
        mVideoPlayViewGL.DrawBitmap(result, 1, 1, 240, 240, 0);
    }

    @Override
    public void devodeFinish() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "解码完成", Toast.LENGTH_LONG).show();
            }
        });

    }

    @OnClick(R.id.re_code)
    public void onClicked(View view) {
        switch (view.getId()) {
            case R.id.re_code:
                if (isEncoding) {
                    //已经在编解码了
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String srcVideoPath = Environment.getExternalStorageDirectory() + "/video/2.mp4";
                        String tarVideoPath = Environment.getExternalStorageDirectory() + "/video/ld.mp4";
                        mLdDecoder.startDecode(srcVideoPath, tarVideoPath);
                    }
                }).start();
                break;
        }
    }
}
