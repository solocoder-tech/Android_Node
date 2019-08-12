package com.ldvideo;

public class JniUtils {
    static {
        System.loadLibrary("native-ld-video");
    }


    public native byte[] remap(byte[] pSrcData, int inHeight, int inWidth, int outHeight, int outWidth);

    public native int[] yuv2rgb(byte[] yuvData,int width,int height);

    public native void yuv420p2yuv420sp(byte[] yuv420p,byte[] yuv420sp,int width,int height);

}
