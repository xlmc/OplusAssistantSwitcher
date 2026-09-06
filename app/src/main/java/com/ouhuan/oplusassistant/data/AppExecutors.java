package com.ouhuan.oplusassistant.data;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App 侧统一后台执行器。 */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO;
    }
}
