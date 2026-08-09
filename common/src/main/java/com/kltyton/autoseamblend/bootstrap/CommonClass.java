package com.kltyton.autoseamblend.bootstrap;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.platform.Services;
import com.kltyton.autoseamblend.platform.services.IPlatformHelper;
import java.util.concurrent.atomic.AtomicBoolean;

/** 中文：与 Loader 无关的 AutoSeamBlend 引导器；Loader 入口可以安全地重复调用。 / English: Loader-neutral AutoSeamBlend bootstrap. Loader entrypoints may call this more than once safely. */
public final class CommonClass {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private CommonClass() {}

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        IPlatformHelper platform = Services.platform();
        Constants.LOG.info(
                "Starting {} {} on {} ({})",
                Constants.MOD_NAME,
                Constants.VERSION,
                platform.getPlatformName(),
                platform.getEnvironmentName());
    }

    public static boolean isInitialized() {
        return INITIALIZED.get();
    }

    public static IPlatformHelper platform() {
        return Services.platform();
    }

}
