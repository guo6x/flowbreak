package com.flowbreak.app;

import android.app.Application;

/**
 * 自定义 Application 类，在进程启动时安装 CrashLogger，
 * 确保 BootReceiver 拉起服务或服务被 START_STICKY 重建时也能捕获崩溃。
 * 此前 CrashLogger 只在 MainActivity.onCreate 安装，导致服务进程崩溃无记录。
 */
public class FlowBreakApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashLogger.install(this);
    }
}
