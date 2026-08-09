package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import com.kltyton.autoseamblend.texture.geometry.WorldOffset;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import team.chisel.ctm.client.newctm.ConnectionCheck;
import team.chisel.ctm.client.util.CTMLogic;
import team.chisel.ctm.client.util.Dir;

/**
 * 中文：八邻域连接由 CTM Lib 原生 ConnectionCheck/CTMLogic 采样，同时保持
 * AutoSeamBlend 选择器组与外观解析边界为权威；不复制 CTM 状态表或方向算法。
 *
 * English:
 * The eight-neighbor connection map is sampled through CTM Lib's native
 * ConnectionCheck/CTMLogic API while AutoSeamBlend selector groups and the
 * appearance-resolution boundary stay authoritative; no CTM state tables or
 * direction algorithms are copied.
 */
public final class CtmModNativeConnectionSampler {
    /**
     * 中文：由 Loader 提供的方块外观解析边界；被 CTM Lib 原生 ConnectionCheck
     * 的连接采样消费，语义与 BlockState.getAppearance 一致。
     *
     * <p>English: Loader-supplied block-appearance boundary; consumed by CTM
     * Lib's native ConnectionCheck during connection sampling and semantically
     * identical to BlockState.getAppearance.
     */
    @FunctionalInterface
    public interface AppearanceResolver {
        BlockState resolve(
                BlockAndTintGetter level,
                BlockPos pos,
                Direction face,
                BlockState state,
                BlockState otherState,
                BlockPos otherPos);
    }

    private final ConnectionRuleSet<Block> rules;
    private final Block target;
    private final Set<Block> documentConnectionBlocks;
    private final boolean overlay;
    private final AppearanceResolver appearanceResolver;

    public CtmModNativeConnectionSampler(
            TextureAtlasSprite source,
            Block target,
            ConnectionRuleSet<Block> rules,
            boolean overlay,
            AppearanceResolver appearanceResolver) {
        this(source, target, rules, Set.of(), overlay, appearanceResolver);
    }

    public CtmModNativeConnectionSampler(
            TextureAtlasSprite source,
            Block target,
            ConnectionRuleSet<Block> rules,
            Set<Block> documentConnectionBlocks,
            boolean overlay,
            AppearanceResolver appearanceResolver) {
        Objects.requireNonNull(source, "source");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.target = Objects.requireNonNull(target, "target");
        this.documentConnectionBlocks = Set.copyOf(
                Objects.requireNonNull(
                        documentConnectionBlocks,
                        "documentConnectionBlocks"));
        this.overlay = overlay;
        this.appearanceResolver = Objects.requireNonNull(
                appearanceResolver,
                "appearanceResolver");
    }

    /**
     * 中文：标准 47 状态路径；八邻域连接由 CTM Lib 原生 CTMLogic 采样，对角
     * 归一化由下游 Ctm47Mapper 承担。random 仅为调用面兼容保留。
     *
     * English:
     * Standard 47-state path; the eight-neighbor map comes from CTM Lib's
     * native CTMLogic and diagonal normalization happens downstream in
     * Ctm47Mapper. The random argument is retained for call-surface compatibility.
     */
    public NeighborConnections sampleStandard(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureBasis basis,
            RandomSource random) {
        return sample(level, pos, state, face, basis);
    }

    /**
     * 中文：优先轴方法需要独立保存四个对角；原生八邻域映射原样保留全部对角位。
     *
     * <p>English: Priority-axis methods retain all four diagonals independently;
     * the native eight-neighbor map keeps every diagonal bit as-is.
     */
    public NeighborConnections sampleIndependent(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureBasis basis) {
        return sample(level, pos, state, face, basis);
    }

    private NeighborConnections sample(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureBasis basis) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(basis, "basis");
        if (basis.face() != world(face)) {
            basis = TextureBasis.canonical(world(face));
        }
        CTMLogic logic = new CTMLogic();
        logic.connectionCheck = nativeCheck();
        logic.buildConnectionMap(level, pos, state, face);
        int bits = 0;
        for (Dir dir : Dir.VALUES) {
            if (logic.connected(dir)) {
                // 中文：bit() 返回项目的位索引（0..7），必须左移成位掩码后再 OR，
                // 否则 SOUTH 的索引 2 只会把 0x2 写入 bits，无法表达"第 2 位已连接"。
                // English: bit() returns the project bit index (0..7); it must be
                // shifted into a mask before OR, otherwise a SOUTH index of 2 only
                // writes 0x2 and cannot express "bit 2 is connected".
                bits |= 1 << bit(
                        dir.getOffset(face),
                        basis);
            }
        }
        return NeighborConnections.fromBits(bits);
    }

    /**
     * 中文：配置原生 ConnectionCheck：外观经 Loader 注入的解析边界路由，状态
     * 比较使用 AutoSeamBlend 选择器组。与已验收 26.1.2 适配器一致，显式关闭
     * 原生被遮挡面检查（26.1.2 通过覆写连接钩子绕过该检查）。
     *
     * <p>English: Configures the native ConnectionCheck: appearance resolution
     * routes through the Loader-injected boundary and state comparison uses
     * AutoSeamBlend selector groups. The native obscured-face check is
     * explicitly disabled to match the accepted 26.1.2 adapter, which bypasses
     * it by overriding the connectivity hooks.
     */
    private ConnectionCheck nativeCheck() {
        ConnectionCheck check = new AppearanceConnectionCheck(
                appearanceResolver);
        check.disableObscuredFaceCheck = Optional.of(true);
        return check.stateComparator(
                (ignoredCheck,
                        first,
                        second,
                        ignoredFace) ->
                        connects(first, second));
    }

    /**
     * 中文：把原生 ConnectionCheck 的外观解析路由到 Loader 注入的
     * AppearanceResolver。
     *
     * English: Routes the native ConnectionCheck appearance resolution through
     * the Loader-injected AppearanceResolver.
     */
    private static final class AppearanceConnectionCheck
            extends ConnectionCheck {
        private final AppearanceResolver resolver;

        private AppearanceConnectionCheck(
                AppearanceResolver resolver) {
            this.resolver = Objects.requireNonNull(
                    resolver,
                    "resolver");
        }

        @Override
        public BlockState getConnectionState(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                Direction face,
                BlockPos otherPos,
                BlockState otherState) {
            return resolver.resolve(
                    level,
                    pos,
                    face,
                    state,
                    otherState,
                    otherPos);
        }
    }

    private boolean connects(
            BlockState origin,
            BlockState neighbor) {
        if (overlay) {
            return !neighbor.is(Blocks.AIR)
                    && connects(
                            target,
                            neighbor.getBlock());
        }
        if (origin.is(Blocks.AIR)) {
            return false;
        }
        return !neighbor.is(Blocks.AIR)
                && connects(
                        origin.getBlock(),
                        neighbor.getBlock());
    }

    private boolean connects(
            Block current,
            Block neighbor) {
        if (!documentConnectionBlocks.isEmpty()) {
            return documentConnectionBlocks.contains(neighbor);
        }
        return rules.isTarget(current)
                ? rules.connects(current, neighbor)
                : current == neighbor;
    }

    private static int bit(
            BlockPos offset,
            TextureBasis basis) {
        for (TextureEdge edge : TextureEdge.values()) {
            if (same(offset, basis.offset(edge))) {
                return edge.connectionBit();
            }
        }
        for (TextureCorner corner : TextureCorner.values()) {
            if (same(offset, basis.offset(corner))) {
                return corner.connectionBit();
            }
        }
        throw new IllegalArgumentException(
                "CTM Lib direction offset "
                        + offset
                        + " does not lie in "
                        + basis.face()
                        + " texture plane");
    }

    private static boolean same(
            BlockPos offset,
            WorldOffset basisOffset) {
        return offset.getX() == basisOffset.x()
                && offset.getY() == basisOffset.y()
                && offset.getZ() == basisOffset.z();
    }

    private static WorldDirection world(
            Direction direction) {
        return WorldDirection.valueOf(
                direction.name());
    }
}
