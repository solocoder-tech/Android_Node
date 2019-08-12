package com.example.sweeper.ldvideodemo;

import android.util.Log;

import java.util.List;

/**
 * Created by zhuwujing on 2018/8/7.
 */

public class LogUtils {
    private static boolean isDebugger = true;//开
//    private static boolean isDebugger = false;//关

    private static final String TAG = "myproject";

    public static void sysout(String msg) {
        if (isDebugger) {
            System.out.println(msg);
        }
    }

    public static void w(String tag, String msg) {
        if (isDebugger) {
            Log.w(tag, msg);
        }
    }

    public static void e(String tag, String msg) {
        if (isDebugger) {
            Log.e(tag, msg);
        }
    }

    public static void e(String msg) {
        if (isDebugger) {
            Log.e(TAG, msg);
        }
    }

    public static <T> void printList(List<T> list) {
        if (isDebugger) {
            if (list == null) {
                sysout("输入的集合为空");
            } else {
                for (int i = 0; i < list.size(); i++) {
                    System.out.println(list.get(i));
                }
            }
        }
    }
}
