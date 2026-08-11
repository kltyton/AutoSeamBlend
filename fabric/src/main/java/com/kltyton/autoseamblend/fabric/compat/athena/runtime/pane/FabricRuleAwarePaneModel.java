package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaStateProjection;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import earth.terrarium.athena.api.client.utils.AthenaUtils;
import earth.terrarium.athena.api.client.utils.CtmState;
import earth.terrarium.athena.api.client.utils.CtmUtils;
import earth.terrarium.athena.impl.client.models.PaneConnectedBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：Athena Fabric 原生玻璃板模型的规则感知子类，逐段移植已验收 1.21.1 NeoForge
 * RuleAwarePaneModel：竖直盖板按连接组剔除、allTrue 走原生 CENTER、非 allTrue 且恰一侧
 * 连接时用 4.7.3-true 单侧替换、isConnected 跨 ID 标签组复用原生形状判定。
 *
 * <p>English: Rule-aware subclass of Athena's native Fabric pane model, porting the accepted
 * 1.21.1 NeoForge RuleAwarePaneModel stage by stage: vertical caps are culled by connection
 * group, allTrue takes the native CENTER, a non-allTrue exactly-one-side connection uses the
 * 4.7.3-true single-side replacement, and isConnected reuses Athena's native neighbor-shape
 * test across cross-id tag groups.
 */
final class FabricRuleAwarePaneModel
        extends PaneConnectedBlockModel {
    private final ConnectionRuleSet<Block> rules;

    FabricRuleAwarePaneModel(
            Int2ObjectMap<Material> materials,
            ConnectionRuleSet<Block> rules) {
        // 中文：Athena 4.0.6 Fabric 玻璃板模型使用原生 CtmState、形状判定和 Quad 生命周期。
        // English: Athena 4.0.6 Fabric pane model uses native CtmState, shape checks, and
        // quad lifecycle.
        super(materials);
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
        if (!face.getAxis().isVertical()) {
            // 中文：与 4.7.3 true 精确同序：先构造 CtmState，allTrue 必须走原生 CENTER
            // （super），再判 cw/ccw；仅非 allTrue 且恰一侧连接才早返回替换，禁止在
            // super 结果后追加。4.0.6 无 connectCorners 开关（等价 false），单侧时原生
            // 返回一条 full-height sprite-0 旧条带，此处以公开 API（CtmState.from 完成
            // 八邻域朝向，isConnected 为受保护原生谓词）复刻 4.7.3 true 的单侧替换。
            // 双侧、无侧与垂直面仍走 super。
            // English: Exactly aligned with 4.7.3 true: build CtmState first; allTrue must
            // take the native CENTER (super) path; then evaluate cw/ccw. Only a non-allTrue
            // exactly-one-side connection takes the early-return replacement, never an
            // append after super. 4.0.6 has no connectCorners switch (equivalent to false)
            // and returns one full-height sprite-0 legacy strip on a single side; public
            // APIs (CtmState.from owns the eight-neighbor orientation, isConnected is the
            // protected native predicate) replicate the 4.7.3 true single-side replacement.
            // Both-sides, no-side, and vertical faces keep the super path.
            CtmState ctmState = CtmState.from(
                    getter,
                    state,
                    pos,
                    face,
                    (neighborPos, neighborState, neighborAppearance) ->
                            isConnected(
                                    neighborAppearance,
                                    state,
                                    face));
            if (ctmState.allTrue()) {
                return super.getQuads(
                        getter,
                        state,
                        pos,
                        face);
            }
            boolean cw = AthenaUtils.getFromDir(
                    state,
                    face.getClockWise());
            boolean ccw = AthenaUtils.getFromDir(
                    state,
                    face.getCounterClockWise());
            if (cw != ccw) {
                float arm = AthenaUtils.getFromDir(
                        state,
                        face)
                        ? 0.5625F
                        : 0.4375F;
                return cw
                        ? singleSideCorners(
                                ctmState,
                                arm,
                                true)
                        : singleSideCorners(
                                ctmState,
                                arm,
                                false);
            }
        }
        return super.getQuads(
                getter,
                state,
                pos,
                face);
    }

    /**
     * 中文：4.7.3 connectCorners=true 的单侧替换 quad 对。leftSide=true 为 CW 侧左列
     * [0,1-arm]，leftSide=false 为 CCW 侧右列 [arm,1]；每侧为上下两个半 quad，槽位
     * 用 CtmUtils.getTexture 真值表取该象限角色。数值逐值取自 1.21.1 NeoForge 已验收
     * 实现（4.7.3 字节码 true 分支）。
     *
     * <p>English: The 4.7.3 connectCorners=true single-side replacement quad pair.
     * leftSide=true is the CW-side left column [0,1-arm]; leftSide=false is the CCW-side
     * right column [arm,1]; each side is a top/bottom half-quad pair whose slots come from
     * the CtmUtils.getTexture truth table. Values are taken verbatim from the accepted
     * 1.21.1 NeoForge implementation (the 4.7.3 bytecode true branches).
     */
    static List<AthenaQuad> singleSideCorners(
            CtmState state,
            float arm,
            boolean leftSide) {
        if (leftSide) {
            return List.of(
                    new AthenaQuad(
                            CtmUtils.getTexture(
                                    state.up(),
                                    state.left(),
                                    state.upLeft()),
                            0.0F,
                            1.0F - arm,
                            1.0F,
                            0.5F,
                            Rotation.NONE,
                            0.4375F),
                    new AthenaQuad(
                            CtmUtils.getTexture(
                                    state.down(),
                                    state.left(),
                                    state.downLeft()),
                            0.0F,
                            1.0F - arm,
                            0.5F,
                            0.0F,
                            Rotation.NONE,
                            0.4375F));
        }
        return List.of(
                new AthenaQuad(
                        CtmUtils.getTexture(
                                state.up(),
                                state.right(),
                                state.upRight()),
                        arm,
                        1.0F,
                        1.0F,
                        0.5F,
                        Rotation.NONE,
                        0.4375F),
                new AthenaQuad(
                        CtmUtils.getTexture(
                                state.down(),
                                state.right(),
                                state.downRight()),
                        arm,
                        1.0F,
                        0.5F,
                        0.0F,
                        Rotation.NONE,
                        0.4375F));
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
