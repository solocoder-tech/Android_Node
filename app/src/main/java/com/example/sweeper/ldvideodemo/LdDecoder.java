package com.example.sweeper.ldvideodemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;


import com.example.sweeper.libyuv_ld.Key;
import com.example.sweeper.libyuv_ld.YuvUtils;
import com.ldvideo.JniUtils;
import com.ldvideo.MP4EncoderHelper;
import com.ldvideo.SPUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;


//import example.sszpf.x264.x264sdk;


/**
 * 创建时间：2019/8/2  17:07
 * 作者：5#
 * 描述：输入文件的地址，解码成YUV或者RGB24
 */
public class LdDecoder {
    private static final long DEFAULT_TIMEOUT_US = 1000 * 10;
    //要解码的为YUV420
    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private final int encodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private static final String MIME_TYPE = "video/avc";
//    private x264sdk x264;

    private MediaCodec mCodec;
    private MediaCodec.BufferInfo bufferInfo;
    private MediaExtractor mMediaExtractor;

    private LdDecoderLisenter mLdDecoderLisenter;
    private MediaCodec enCodec;
    private Context mContext;
    private boolean haveGetSpsInfo;
    private final String VIDEO_KEY_SPS = "video_sps";
    private final String VIDEO_KEY_PPS = "video_pps";
    private final int VIDEO_WIDTH = 240;
    private final int VIDEO_HEIGHT = 240;
    private final JniUtils mJniUtils;

    public void setLdDecoderLisenter(LdDecoderLisenter ldDecoderLisenter) {
        mLdDecoderLisenter = ldDecoderLisenter;
    }

    /**
     * 构造方法
     *
     * @param context
     */
    public LdDecoder(Context context) {
        mContext = context;
        mJniUtils = new JniUtils();
        try {
            MediaCodecInfo mediaCodecInfo = selectCodec(MIME_TYPE);
            if (mediaCodecInfo == null) {
                LogUtils.e("没有找到合适的编码器");
                return;
            }
            //创建 编码器
            enCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            showSupportedColorFormat(enCodec.getCodecInfo().getCapabilitiesForType(MIME_TYPE));
            MediaFormat format_en = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
            //描述平均位速率（以位/秒为单位）的键。 关联的值是一个整数
            format_en.setInteger(MediaFormat.KEY_BIT_RATE, 2500000);
            //描述视频格式的帧速率（以帧/秒为单位）的键。帧率，一般在15至30之内，太小容易造成视频卡顿
            format_en.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            format_en.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            //不能在honor
            if (isColorFormatSupported(encodeColorFormat, enCodec.getCodecInfo().getCapabilitiesForType(MIME_TYPE))) {
                format_en.setInteger(MediaFormat.KEY_COLOR_FORMAT, encodeColorFormat);
                LogUtils.e("enCodec isColorFormatSupported===" + encodeColorFormat);
            } else {
                LogUtils.e("enCodec isColorFormatSupported===" + encodeColorFormat + " not supported");
            }
            if (Build.BRAND.toLowerCase().equals("xiaomi")) {
                format_en.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
            }
            enCodec.configure(format_en, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if (enCodec == null) {
                return;
            }
            enCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解码方法，运行在子线程中
     * @param srcVideoPath   原始MP4文件
     * @param tarVideoPath   目标MP4文件
     */
    public void startDecode(String srcVideoPath, String tarVideoPath) {
        FileOutputStream yuvOutputStream = null;
        FileOutputStream h264OutputStream = null;
        long sampleTime = 0;
        try {
            MP4EncoderHelper.init(tarVideoPath, 240, 240);
            File yuvFile = new File(Environment.getExternalStorageDirectory() + "/video", "ld_before.yuv");
            File h264File = new File(Environment.getExternalStorageDirectory() + "/video", "ld_after.yuv");
            yuvOutputStream = new FileOutputStream(yuvFile);
            h264OutputStream = new FileOutputStream(h264File);
            mMediaExtractor = new MediaExtractor();//数据解析器
            mMediaExtractor.setDataSource(srcVideoPath);
            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {//遍历数据源音视频轨迹
                MediaFormat format = mMediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
//                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                LogUtils.e("video====" + mime);//video/avc
                if (mime.startsWith("video/")) {
                    mMediaExtractor.selectTrack(i);
                    //创建解码器
                    mCodec = MediaCodec.createDecoderByType(mime);
                    if (isColorFormatSupported(decodeColorFormat, mCodec.getCodecInfo().getCapabilitiesForType(mime))) {
                        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                        LogUtils.e("isColorFormatSupported===" + decodeColorFormat); //2135033992
                    } else {
                        LogUtils.e("isColorFormatSupported===" + decodeColorFormat + " not supported");
                    }
                    mCodec.configure(format, null, null, 0);
                    break;
                }
            }
            if (mCodec == null) {
                return;
            }
            mCodec.start();
            //获取MediaCodec的输入流
            ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mCodec.getOutputBuffers();
            // 每个buffer的元数据包括具体范围偏移及大小 ，及有效数据中相关解码的buffer
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isEOS = false;
            long startMs = System.currentTimeMillis();
            int outputFrameCount = 0;
            while (!Thread.interrupted()) {//只要线程不中断
                if (!isEOS) {
                    //返回用有效输出的buffer的索引,如果没有相关buffer可用，就返回-1
                    //如果传入的timeoutUs为0,将立马返回，如果输入buffer可用，将无限期等待
                    //timeoutUs的单位是微秒
                    //dequeueInputBuffer 从输入流队列中取数据进行编码操作 设置解码等待时间，0为不等待，-1为一直等待，其余为时间单位
                    int inIndex = mCodec.dequeueInputBuffer(10000);//0.01s
                    //填充数据到输入流
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];
                        //把指定通道中的数据按偏移量读取到ByteBuffer中；读取的是一帧数据
                        int sampleSize = mMediaExtractor.readSampleData(buffer, 0);
                        sampleTime = mMediaExtractor.getSampleTime();//读取时间戳
                        LogUtils.e("video====sampleTime==" + sampleTime / 1000 / 1000.0f);
                        if (sampleSize < 0) {
                            // dequeueOutputBuffer
                            mCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            mCodec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0);
                            //读取一帧后必须调用，提取下一帧
                            mMediaExtractor.advance();
                        }
                    }
                }

                int outIndex = mCodec.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED://当buffer变化时，client必须重新指向新的buffer
                        LogUtils.e(">> output buffer changed ");
                        outputBuffers = mCodec.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED://当buffer的封装格式变化,须指向新的buffer格式
                        LogUtils.e(">> output buffer changed ");
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER://当dequeueOutputBuffer超时,会到达此case
                        LogUtils.e(">> dequeueOutputBuffer timeout ");
                        break;
                    default:
                        LogUtils.e(">> dequeueOutputBuffer default ");
                        //解码后的数据在这里输出
                        outputFrameCount++;
                        Image image = mCodec.getOutputImage(outIndex);
                        byte[] bytes = getDataFromImage(image, COLOR_FormatI420); //I420数据
                        yuvOutputStream.write(bytes);
                        //------
                        if (bytes.length > 0) {
                            //这里数据没有问题，已经播放数据确认
                            int width = 640;
                            int height = 480;
                            byte[] rgbaData = new byte[width * height * 4];
                            YuvUtils.I420ToRgba(Key.I420_TO_RGBA, bytes, rgbaData, width, height);
                            //处理rgba数据 -->  透视
                            byte[] result = handleRGBAData(rgbaData);
                            //处理后的YUV数据
                            byte[] yuvData = new byte[VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2];
                            rgba2YUV(result, yuvData);
                            byte[] yuvSpData = new byte[VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2];
                            MediaFormat outputFormat = mCodec.getOutputFormat();
                            switch (outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)) {
                                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411Planar:
                                    LogUtils.e("switch======COLOR_FormatYUV411Planar");
                                    break;
                                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411PackedPlanar:
                                    LogUtils.e("switch======COLOR_FormatYUV411PackedPlanar");
                                    break;
                                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                                    LogUtils.e("switch======COLOR_FormatYUV420PackedPlanar");
                                    break;
                                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                                    LogUtils.e("switch======COLOR_FormatYUV420SemiPlanar"); //honor
                                    //yuv格式之间的转换
                                    mJniUtils.yuv420p2yuv420sp(yuvData,yuvSpData,VIDEO_WIDTH,VIDEO_HEIGHT);
                                    h264OutputStream.write(yuvSpData);
                                    //yuv-->mp4
                                    if (sampleTime != 0) {
                                        yuv2H264(yuvSpData, h264OutputStream);
                                    }
                                    break;
                                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                                    LogUtils.e("switch======COLOR_FormatYUV420PackedSemiPlanar");
                                    break;
                                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                                    LogUtils.e("switch======COLOR_FormatYUV420Planar");//xiaomi
                                    //yuv-->mp4
                                    if (sampleTime != 0) {
                                        yuv2H264(yuvData, h264OutputStream);
                                    }
                                default:
                                    LogUtils.e("switch======default====" + outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT));
                                    mJniUtils.yuv420p2yuv420sp(yuvData,yuvSpData,VIDEO_WIDTH,VIDEO_HEIGHT);
                                    h264OutputStream.write(yuvSpData);
                                    //yuv-->mp4
                                    if (sampleTime != 0) {
                                        yuv2H264(yuvSpData, h264OutputStream);
                                    }
                                    break;
                            }
                        }
                        //-------控制帧率
//                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
//                            try {
//                                sleep(10);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                                break;
//                            }
//                        }
                        image.close();
                        mCodec.releaseOutputBuffer(outIndex, true);
                        break;
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    LogUtils.e("OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    if (mLdDecoderLisenter != null) {
                        mLdDecoderLisenter.devodeFinish();
                    }
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (yuvOutputStream != null) {
                    yuvOutputStream.close();
                }
                if (h264OutputStream != null) {
                    h264OutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            release();
            MP4EncoderHelper.close();
        }
    }

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    /**
     * Image---> I420
     * @param image
     * @param colorFormat
     * @return
     */
    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private static void dumpFile(String fileName, byte[] data) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        try {
            outStream.write(data);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("failed writing data to file " + fileName, ioe);
        }
    }


    /**
     * 将rgba 转成 YUV 数据
     *
     * @param result
     * @param yuvData
     */
    private void rgba2YUV(byte[] result, byte[] yuvData) {
        //将result--> rgba
        Bitmap myBitmap = MyBitmapFactory.createMyBitmap(result, VIDEO_WIDTH, VIDEO_HEIGHT);
        int bytes = myBitmap.getByteCount();
        ByteBuffer buf = ByteBuffer.allocate(bytes);
        myBitmap.copyPixelsToBuffer(buf);
        byte[] byteArray = buf.array();
        YuvUtils.RgbaToI420(Key.RGBA_TO_I420, byteArray, yuvData, VIDEO_WIDTH, VIDEO_HEIGHT);
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    /**
     * yuv-->h264
     *
     * @param yuvData
     * @param h264OutputStream
     */
    private void yuv2H264(byte[] yuvData, FileOutputStream h264OutputStream) {
        try {
            //将yuv数据放到输入缓存区中
            int inputBufferIndex = enCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = null;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    inputBuffer = enCodec.getInputBuffers()[inputBufferIndex];
                } else {
                    inputBuffer = enCodec.getInputBuffer(inputBufferIndex);
                }
                inputBuffer.clear();
                inputBuffer.put(yuvData);
                //输入缓存区入队列
                enCodec.queueInputBuffer(inputBufferIndex, 0, yuvData.length, System.currentTimeMillis(), 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = enCodec.dequeueOutputBuffer(bufferInfo, 0);
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                LogUtils.e("========INFO_OUTPUT_FORMAT_CHANGED====");
            }
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = null;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    outputBuffer = enCodec.getOutputBuffers()[inputBufferIndex];
                } else {
                    outputBuffer = enCodec.getOutputBuffer(outputBufferIndex);
                }
                //h264数据
                byte[] outData = new byte[outputBuffer.remaining()];
                outputBuffer.get(outData, 0, outData.length);
                h264OutputStream.write(outData);

                h2642MP4(outData);

                enCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = enCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * h264 封装成mp4
     *
     * @param datas
     */
    private void h2642MP4(byte[] datas) {
        if (haveGetSpsInfo) {
            LogUtils.e("onFrameData: -->datas[4]:" + datas[4]);
            MP4EncoderHelper.writeH264Data(datas, datas.length);
            return;
        }
        //找sps和pps
        if ((datas[4] & 0x1f) == 7) {//sps
            MP4EncoderHelper.writeH264Data(datas, datas.length);
            SPUtils.saveObject(mContext, VIDEO_KEY_SPS, datas);
        } else if ((datas[4] & 0x1f) == 8) {//pps
            MP4EncoderHelper.writeH264Data(datas, datas.length);
            SPUtils.saveObject(mContext, VIDEO_KEY_PPS, datas);
        } else if ((datas[4] & 0x1f) == 5) {
            //第一帧为I帧
            haveGetSpsInfo = true;
            MP4EncoderHelper.writeH264Data(datas, datas.length);
        }
    }

    /**
     * 处理rgba数据，回调rgb24结果数据
     *
     * @param rgbaData
     */
    private byte[] handleRGBAData(byte[] rgbaData) {
        final Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        ByteBuffer buffer = ByteBuffer.allocate(640 * 480 * 4);
        buffer.put(rgbaData);
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);

        byte[] bytes = MyBitmapFactory.bitmap2RGB2(bitmap);//rgb
        byte[] result = mJniUtils.remap(bytes, 480, 640, 240, 240);
        if (mLdDecoderLisenter != null) {
            mLdDecoderLisenter.onFrame(result);
        }
        return result;
    }


    /**
     * 获取编码器支持的格式
     * COLOR_FormatYUV420SemiPlanar  21
     *
     * @param mediaCodecInfo
     * @return
     */
    private int getColorFormat(MediaCodecInfo mediaCodecInfo) {
        int matchedFormat = 0;
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                mediaCodecInfo.getCapabilitiesForType("video/avc");
        for (int i = 0; i < codecCapabilities.colorFormats.length; i++) {
            int format = codecCapabilities.colorFormats[i];
            if (format >= codecCapabilities.COLOR_FormatYUV420Planar &&
                    format <= codecCapabilities.COLOR_FormatYUV420PackedSemiPlanar) {
                if (format >= matchedFormat) {
                    matchedFormat = format;
                    LogUtils.e("matchedFormat===" + matchedFormat);
                    break;
                }
            }
        }
        return matchedFormat;
    }

    /**
     * 遍历手机所有的编码器，是否有支持mimaType类型的编码器
     *
     * @param mimeType
     * @return
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            // 判断是否为编码器，否则直接进入下一次循环
            if (!codecInfo.isEncoder()) {
                continue;
            }
            // 如果是编码器，判断是否支持Mime类型
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                LogUtils.e("types======" + types[j]);
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * mix2  2141391878	2141391876	2141391880	2141391879	2130708361	2135033992	21
     * vivo 2130706944	2130708361	2130706944	2135033992	19	6	11	16	2130707200	15
     * honor 2135033992	21	2130706433	19	22	25	26	27	28	16	2130706447	15	2130708361
     *
     * @param caps
     */
    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        System.out.print("supported color format: ");
        for (int c : caps.colorFormats) {
            System.out.print(c + "\t");
        }
        System.out.println();
    }

    /**
     * 停止解码器并释放
     */
    public void release() {

        if (null != enCodec) {
            enCodec.stop();
            enCodec.release();
            enCodec = null;
        }
        if (null != mCodec) {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
    }

    public void geth264Data() {

    }

    public void decode(byte[] h264Data) {
        int inputBufferIndex = mCodec.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
            } else {
                inputBuffer = mCodec.getInputBuffers()[inputBufferIndex];
            }
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(h264Data, 0, h264Data.length);
                mCodec.queueInputBuffer(inputBufferIndex, 0, h264Data.length, 0, 0);
            }
        }
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, DEFAULT_TIMEOUT_US);
        ByteBuffer outputBuffer;
        while (outputBufferIndex > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                outputBuffer = mCodec.getOutputBuffer(outputBufferIndex);
            } else {
                outputBuffer = mCodec.getOutputBuffers()[outputBufferIndex];
            }
            if (outputBuffer != null) {
                outputBuffer.position(0);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                byte[] yuvData = new byte[outputBuffer.remaining()];
                outputBuffer.get(yuvData);

//                if (null != onDecodeCallback) {
//                    onDecodeCallback.onFrame(yuvData);
//                }
                mCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBuffer.clear();
            }
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, DEFAULT_TIMEOUT_US);
        }
    }

    public byte[] NV21toRGBA(byte[] data, int width, int height) {
        int size = width * height;
        byte[] bytes = new byte[size * 4];
        int y, u, v;
        int r, g, b;
        int index;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                index = j % 2 == 0 ? j : j - 1;

                y = data[width * i + j] & 0xff;
                u = data[width * height + width * (i / 2) + index + 1] & 0xff;
                v = data[width * height + width * (i / 2) + index] & 0xff;

                r = y + (int) 1.370705f * (v - 128);
                g = y - (int) (0.698001f * (v - 128) + 0.337633f * (u - 128));
                b = y + (int) 1.732446f * (u - 128);

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                bytes[width * i * 4 + j * 4 + 0] = (byte) r;
                bytes[width * i * 4 + j * 4 + 1] = (byte) g;
                bytes[width * i * 4 + j * 4 + 2] = (byte) b;
                bytes[width * i * 4 + j * 4 + 3] = (byte) 255;//透明度
            }
        }
        return bytes;
    }

}
