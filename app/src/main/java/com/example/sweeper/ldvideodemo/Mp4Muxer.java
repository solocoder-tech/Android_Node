package com.example.sweeper.ldvideodemo;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * mp4合成器
 */

public class Mp4Muxer {
    private String TAG = getClass().getSimpleName();

    private MediaMuxer mMuxer;
    private int mVideoTrackIndex = -1, mAudioTrackIndex = -1;

    public Mp4Muxer(String outPath) {
        try {
            mMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addVideoTrack(MediaFormat mediaFormat) {
        if (mVideoTrackIndex != -1)
            throw new RuntimeException("already add video tracks");
        mVideoTrackIndex = mMuxer.addTrack(mediaFormat);
    }

    public void addAudioTrack(MediaFormat mediaFormat) {
        if (mAudioTrackIndex != -1)
            throw new RuntimeException("already add audio tracks");
        mAudioTrackIndex = mMuxer.addTrack(mediaFormat);
    }

    public void start() {
        mMuxer.start();
    }

    synchronized
    public void writeVideoData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
        if (mVideoTrackIndex == -1) {
            Log.i(TAG, String.format("pumpStream [%s] but muxer is not start.ignore..", "video"));
            return;
        }
        writeData(outputBuffer, bufferInfo, mVideoTrackIndex);
    }

    synchronized
    public void writeAudioData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo) {
        if (mAudioTrackIndex == -1) {
            Log.i(TAG, String.format("pumpStream [%s] but muxer is not start.ignore..", "audio"));
            return;
        }
        writeData(outputBuffer, bufferInfo, mAudioTrackIndex);
    }

    void writeData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int track) {
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0;
        } else if (bufferInfo.size != 0) {
            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
            mMuxer.writeSampleData(track, outputBuffer, bufferInfo);
            Log.d(TAG, String.format("send [%d] [" + bufferInfo.size + "] with timestamp:[%d] to muxer", track, bufferInfo.presentationTimeUs));
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.i(TAG, "BUFFER_FLAG_END_OF_STREAM received");
            }
        }
    }

    synchronized
    public void stop() {
        if (mMuxer != null) {
            try {
                mMuxer.stop();
                mMuxer.release();
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }
        }
    }
}
