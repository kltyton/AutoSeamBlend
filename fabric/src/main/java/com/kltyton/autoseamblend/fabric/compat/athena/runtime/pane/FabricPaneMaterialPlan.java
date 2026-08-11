package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan.Role;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.compat.athena.generation.AthenaGeneratedSpritePlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：26.1.2 Fabric pane 角色材质计划与运行时方法解析；角色位掩码委托 26.1.2 common
 * AthenaPaneTilePlan，47 槽生成精灵 ID 复用 NeoForge 已验收的
 * AthenaPhysicalTilePlan.selectSlot + AthenaGeneratedSpritePlan.generatedId 语义。
 * resolveRuntimeMethod 移植 1.21.1 ce33d6c AthenaPaneTilePlan.resolveRuntimeMethod。
 *
 * <p>English: The 26.1.2 Fabric pane role material plan and runtime method resolution; role
 * bit masks delegate to the 26.1.2 common AthenaPaneTilePlan while the 47-slot generated
 * sprite IDs reuse the accepted NeoForge AthenaPhysicalTilePlan.selectSlot plus
 * AthenaGeneratedSpritePlan.generatedId semantics. resolveRuntimeMethod ports the 1.21.1
 * ce33d6c AthenaPaneTilePlan.resolveRuntimeMethod.
 */
public final class FabricPaneMaterialPlan {
    private FabricPaneMaterialPlan() {}

    /** 中文：返回需要生成连接材质的四个 pane 角色位掩码。 / English: Returns the connection bit mask for the four generated pane roles. */
    public static int connectionBits(Role role) {
        return AthenaPaneTilePlan.generatedConnectionBits(
                Objects.requireNonNull(role, "role"));
    }

    /** 中文：configured=AUTO 且推断为 NONE 时降级为 CTM；显式方法绝不被改写。 / English: configured=AUTO with an inferred NONE degrades to CTM; explicit methods are never rewritten. */
    public static ConnectionMethod resolveRuntimeMethod(
            ConnectionMethod configured,
            ConnectionMethod inferred) {
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(inferred, "inferred");
        if (configured != ConnectionMethod.AUTO) {
            return configured;
        }
        return inferred == ConnectionMethod.NONE
                ? ConnectionMethod.CTM
                : inferred;
    }

    /** 中文：原生角色材质；forceTranslucent 保留接收面的透明玻璃层。 / English: Native role material; forceTranslucent keeps the receiver's transparent glass layer. */
    static Material blockMaterial(
            ResourceLocation spriteId,
            boolean forceTranslucent) {
        // 1.20.1 Material carries the atlas location and sprite id (no translucent variant).
        return new Material(
                TextureAtlas.LOCATION_BLOCKS,
                Objects.requireNonNull(
                        spriteId,
                        "spriteId"));
    }

    /** 中文：按角色连接位掩码解析 47 槽生成材质，与 NeoForge generatedMaterial 同序。 / English: Resolves the 47-slot generated material from the role's connection bits, same order as the NeoForge generatedMaterial. */
    static Material generatedMaterial(
            ResourceLocation source,
            Role role,
            boolean forceTranslucent) {
        int slot = AthenaPhysicalTilePlan.roleFor(
                        NeighborConnections.fromBits(
                                connectionBits(role)))
                .ordinal();
        return new Material(
                TextureAtlas.LOCATION_BLOCKS,
                AthenaGeneratedSpritePlan.generatedId(
                        Objects.requireNonNull(
                                source,
                                "source"),
                        ConnectionMethod.CTM,
                        slot));
    }
}
