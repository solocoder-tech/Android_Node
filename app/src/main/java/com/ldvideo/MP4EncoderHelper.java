package com.ldvideo;


public class MP4EncoderHelper {

    public static native void init(String mp4FilePath, int widht, int height);

    public static native int writeH264Data(byte[] data, int size);

    public static native void close();

    static {
        System.loadLibrary("native-lib");
    }
}
