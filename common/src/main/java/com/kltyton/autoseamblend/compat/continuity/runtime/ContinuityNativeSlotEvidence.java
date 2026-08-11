package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.texture.generation.ContinuityNativeSlotEvidenceClassifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：从 Continuity 已解析精灵逐槽确认 PNG，不重新解释 properties。
 * English: Confirms PNG evidence from Continuity-parsed sprites without reinterpreting properties.
 */
public final class ContinuityNativeSlotEvidence {
    private ContinuityNativeSlotEvidence() {}

    public static List<NativeSlot> capture(
            BaseCtmProperties properties,
            ResourceManager resources) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(resources, "resources");
        List<ResourceLocation> spriteIds = properties.getSpriteIds()
                .stream()
                .map(material -> material.texture())
                .toList();
        ArrayList<ContinuityNativeSlotEvidenceClassifier.Observation> observations =
                new ArrayList<>(spriteIds.size());
        for (int index = 0; index < spriteIds.size(); index++) {
            ResourceLocation spriteId = spriteIds.get(index);
            if (spriteId.equals(BaseCtmProperties.SPECIAL_DEFAULT_ID)) {
                observations.add(ContinuityNativeSlotEvidenceClassifier.defaultMarker(index));
                continue;
            }
            if (spriteId.equals(BaseCtmProperties.SPECIAL_SKIP_ID)) {
                observations.add(ContinuityNativeSlotEvidenceClassifier.skipMarker(index));
                continue;
            }
            boolean pngPresent = resources.getResource(
                    SpriteSource.TEXTURE_ID_CONVERTER.idToFile(spriteId)).isPresent();
            observations.add(
                    ContinuityNativeSlotEvidenceClassifier.sprite(
                            index,
                            spriteId.toString(),
                            pngPresent));
        }
        return ContinuityNativeSlotEvidenceClassifier.classify(observations);
    }
}
