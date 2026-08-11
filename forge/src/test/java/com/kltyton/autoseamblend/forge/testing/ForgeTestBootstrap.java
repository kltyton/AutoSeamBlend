package com.kltyton.autoseamblend.forge.testing;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.network.NetworkEvent;

/**
 * 中文：Forge 独立 JVM 测试的统一引导。与真实游戏不同，普通 JUnit 测试没有 eventbus
 * 的 modlauncher 变换，因此 {@link NetworkEvent} 及其注册的嵌套事件缺少被变换器注入
 * 的无参构造器；若直接调用 {@link Bootstrap#bootStrap()}，Forge 补丁里的
 * {@code NetworkHooks.init()} 会在 EventListenerHelper 实例化事件类时抛
 * NoSuchMethodException。这里先在引导前等价地预填 EventListenerHelper 的监听器缓存
 * （fromInstanceCall=true 走父类链，不实例化事件），与真实游戏
 * EventSubclassTransformer 生成的静态 ListenerList 语义一致。
 *
 * <p>English: Shared bootstrap for standalone Forge JVM tests. Unlike the real game,
 * plain JUnit tests run without the eventbus modlauncher transform, so
 * {@link NetworkEvent} and its registered nested events lack the injected no-arg
 * constructors; calling {@link Bootstrap#bootStrap()} directly makes the Forge-patched
 * {@code NetworkHooks.init()} fail with NoSuchMethodException when EventListenerHelper
 * instantiates the event class. Before bootstrapping, this helper equivalently
 * pre-populates the EventListenerHelper listener cache (the fromInstanceCall=true path
 * walks the superclass chain and never instantiates), matching the static ListenerList
 * semantics the real game's EventSubclassTransformer generates.
 */
public final class ForgeTestBootstrap {
    private ForgeTestBootstrap() {}

    public static void bootStrap() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        LoadingModList.of(List.of(), List.of(), null);
        prepareEventBus();
        Bootstrap.bootStrap();
    }

    private static void prepareEventBus() {
        populateListenerList(NetworkEvent.class);
        populateListenerList(
                NetworkEvent.GatherLoginPayloadsEvent.class);
        populateListenerList(
                NetworkEvent.ChannelRegistrationChangeEvent.class);
    }

    private static void populateListenerList(
            Class<?> eventClass) {
        try {
            Method method = EventListenerHelper.class
                    .getDeclaredMethod(
                            "getListenerListInternal",
                            Class.class,
                            boolean.class);
            method.setAccessible(true);
            method.invoke(null, eventClass, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot prepare eventbus listener list for "
                            + eventClass.getName(),
                    e);
        }
    }
}
