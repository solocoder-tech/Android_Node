package com.example.sweeper.ldvideodemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;

/**
 * 创建时间：2019/8/3  16:31
 * 作者：5#
 * 描述：TODO
 */
public class LdSurfaceView extends SurfaceView {

    private SurfaceHolder mSurfaceHolder;
    private byte[] currentRGB;
    private Rect m_srcRect;
    private Rect m_dstRect;
    private Paint mPaint;

    public LdSurfaceView(Context context) {
        this(context, null);
    }

    public LdSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LdSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mSurfaceHolder = this.getHolder();
        m_srcRect = new Rect(0, 0, 640, 480);
        m_dstRect = new Rect(800, 50, 800 + 640, 50 + 480);

        mPaint = new Paint();
    }

    public void setCurrentRGB(byte[] currentRGB) {
        this.currentRGB = currentRGB;
        draw(null);
    }

    @Override
    public void draw(Canvas canvas) {
        boolean isCanvasNull = true;
        try {
            if (canvas == null)
                canvas = mSurfaceHolder.lockCanvas();
            else
                isCanvasNull = false;

            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//清空画布
                if (currentRGB != null) {
                    ByteBuffer buffer = ByteBuffer.wrap(currentRGB);
                    Bitmap videoBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
                    videoBitmap.copyPixelsFromBuffer(buffer);
                    canvas.drawBitmap(videoBitmap, m_srcRect, m_dstRect, mPaint);
                }
                if (isCanvasNull) {
                    mSurfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        } catch (Exception e) {
            if (isCanvasNull)
                mSurfaceHolder.unlockCanvasAndPost(canvas);
        }

    }
}
