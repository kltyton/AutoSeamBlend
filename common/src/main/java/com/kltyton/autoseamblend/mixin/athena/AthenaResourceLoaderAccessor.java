package com.kltyton.autoseamblend.mixin.athena;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import earth.terrarium.athena.impl.loading.AthenaResourceLoader;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：暴露锁定 Athena loader 实际接受的两类数据表。 / English: Exposes the two data tables accepted by the locked Athena loader. */
@Mixin(value = AthenaResourceLoader.class, remap = false)
public interface AthenaResourceLoaderAccessor {
    @Accessor(value = "data", remap = false)
    Map<Identifier, JsonElement> autoseamblend$data();

    @Accessor(value = "blockstateData", remap = false)
    Map<Identifier, JsonObject> autoseamblend$blockstateData();
}
