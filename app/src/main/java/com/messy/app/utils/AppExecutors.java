package com.messy.app.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {
    private static final ExecutorService DISK_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static void execute(Runnable runnable) {
        DISK_IO.execute(runnable);
    }

    public static void postToMain(Runnable runnable) {
        MAIN.post(runnable);
    }
}