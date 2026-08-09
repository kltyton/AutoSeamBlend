package com.kltyton.autoseamblend.authoring.selector;

import java.util.Optional;

/** 中文：把平台方块标识解析为选择器事实的纯数据边界。 English: Pure data boundary resolving platform block ids into selector facts. */
@FunctionalInterface
public interface NativeBlockSelectorResolver {
    Optional<NativeBlockSelectorFacts> resolve(String blockId);
}
