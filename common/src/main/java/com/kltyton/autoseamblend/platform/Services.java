package com.kltyton.autoseamblend.platform;

import com.kltyton.autoseamblend.platform.services.IPlatformHelper;
import java.util.ServiceLoader;

/** 中文：延迟解析 Loader 提供的唯一平台服务。 / English: Lazily resolves the one Loader-provided platform service. */
public final class Services {
    private static final class Holder {
        private static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    }

    private Services() {}

    public static IPlatformHelper platform() {
        return Holder.PLATFORM;
    }

    private static <T> T load(Class<T> serviceClass) {
        return ServiceLoader.load(serviceClass)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Loader service registered for " + serviceClass.getName()));
    }
}
