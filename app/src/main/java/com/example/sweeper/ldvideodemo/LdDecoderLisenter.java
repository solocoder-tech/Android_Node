package com.example.sweeper.ldvideodemo;

/**
 * 创建时间：2019/8/3  15:25
 * 作者：5#
 * 描述：TODO
 */
public interface LdDecoderLisenter {
    void onFrame(byte[] result);

    void devodeFinish();
}
