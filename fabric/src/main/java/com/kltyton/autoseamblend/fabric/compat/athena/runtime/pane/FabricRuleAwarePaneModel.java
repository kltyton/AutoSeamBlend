package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaStateProjection;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import earth.terrarium.athena.impl.client.models.PaneConnectedBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：Athena 4.7.3 Fabric 原生玻璃板模型的规则感知子类，逐段移植已验收 26.1.2 NeoForge
 * RuleAwarePaneModel（连接角开关 connectCorners=true 保留原生无缝单臂玻璃板分支）：
 * 竖直盖板按连接组剔除、isConnected 跨 ID 标签组复用原生形状判定；1.21.1 ce33d6c
 * FabricRuleAwarePaneModel 同序。
 *
 * <p>English: Rule-aware subclass of Athena 4.7.3's native Fabric pane model, porting the
 * accepted 26.1.2 NeoForge RuleAwarePaneModel stage by stage (connectCorners=true keeps the
 * native seamless one-arm pane branch): vertical caps are culled by connection group and
 * isConnected reuses Athena's native neighbor-shape test across cross-id tag groups, in the
 * same order as the 1.21.1 ce33d6c FabricRuleAwarePaneModel.
 */
final class FabricRuleAwarePaneModel
        extends PaneConnectedBlockModel {
    private final ConnectionRuleSet<Block> rules;

    FabricRuleAwarePaneModel(
            Int2ObjectMap<Material> materials,
            ConnectionRuleSet<Block> rules) {
        // 中文：启用 Athena 4.7.3 原生的无缝单臂玻璃板分支；该分支仍使用原生 CtmState、
        // 形状判定和 Quad 生命周期。
        // English: Enable Athena 4.7.3's native seamless one-arm pane branch; it still uses
        // native CtmState, shape checks, and quad lifecycle.
        super(materials, true);
        this.rules = Objects.requireNonNull(
                rules,
                "rules");
    }

    @Override
    public List<AthenaQuad> getQuads(
            AppearanceAndTintGetter getter,
            BlockState state,
            BlockPos pos,
            Direction face) {
        // 中文：Athena 原实现按 BlockState 引用剔除上下盖板；项目合同要求同一连接组的
        // 属性变体也连续。
        // English: Athena culls vertical caps by BlockState identity; the product contract
        // also joins property variants in one connection group.
        if (face.getAxis().isVertical()
                && AthenaStateProjection.connects(
                        rules,
                        state.getBlock(),
                        getter.getBlockState(
                                pos.relative(face))
                                .getBlock())) {
            return List.of();
        }
        return super.getQuads(
                getter,
                state,
                pos,
                face);
    }

    @Override
    protected boolean isConnected(
            BlockState neighbor,
            BlockState origin,
            Direction direction) {
        boolean nativeConnected = super.isConnected(
                neighbor,
                origin,
                direction);
        if (nativeConnected) {
            return true;
        }
        boolean rulesConnected = AthenaStateProjection.connects(
                rules,
                origin.getBlock(),
                neighbor.getBlock());
        if (!rulesConnected) {
            return false;
        }
        if (origin.getBlock() == neighbor.getBlock()) {
            return true;
        }
        // 中文：跨 ID 标签组仍复用 Athena 对邻居玻璃板形状的原生判定，不复制其方向算法。
        // English: Cross-id tag groups still reuse Athena's native neighbor-pane shape test
        // without copying its direction algorithm.
        return super.isConnected(
                neighbor,
                neighbor,
                direction);
    }
}
