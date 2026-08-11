package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import com.kltyton.autoseamblend.fabric.runtime.culling.FabricGlassPaneSeamCulling;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：Loader 级模型生命周期：先捕获基础模型，再在原生包装器阶段安装玻璃板端盖剔除。
 *
 * English: Loader-level model lifecycle: captures base models first, then
 * installs glass-pane cap culling in the native wrapper phase.
 */
public final class FabricModelLifecycle {
    private FabricModelLifecycle() {}

    public static void register() {
        PreparableModelLoadingPlugin.<Long>register(
                (state, executor) ->
                        CompletableFuture.completedFuture(
                                FabricModelCapture.begin()),
                (session, context) ->
                        registerPhases(session, context));
    }

    private static void registerPhases(
            long session,
            net.fabricmc.fabric.api.client.model.loading.v1
                    .ModelLoadingPlugin.Context context) {
        context.modifyModelAfterBake()
                .register(
                        ModelModifier.OVERRIDE_PHASE,
                        (model, modifierContext) -> {
                            BlockState state =
                                    resolveState(
                                            modifierContext);
                            if (state != null) {
                                FabricModelCapture.capture(
                                        session,
                                        state,
                                        model);
                            }
                            return model;
                        });
        context.modifyModelAfterBake()
                .register(
                        ModelModifier.WRAP_PHASE,
                        FabricModelLifecycle::paneCulling);
    }

    private static net.minecraft.client.resources.model.BakedModel
            paneCulling(
                    net.minecraft.client.resources.model.BakedModel
                            model,
                    ModelModifier.AfterBake.Context context) {
        BlockState state = resolveState(context);
        if (state == null
                || !(state.getBlock()
                        instanceof IronBarsBlock)) {
            return model;
        }
        // 中文：首次烘焙时 current() 仍是 bootstrap 空代次（surfaces/methods=0），必须读
        // 同代次 pending 的预缝合方法表，否则自动发现的染色玻璃板永远装不上端盖剔除包装器；
        // NeoForge 在同一烘焙内现算表面，因此没有这个问题。
        // English: On the first bake current() is still the empty bootstrap generation
        // (methods/surfaces=0); read the same-reload pending pre-stitch method table so
        // auto-discovered stained panes get the cap-culling wrapper. NeoForge computes
        // surfaces inside the same bake and therefore has no such gap.
        ReloadPublication.Generation pending =
                ReloadPublication.pendingPreparation()
                        .orElse(null);
        PreparedSurfaceMethods.Snapshot methods =
                pending != null
                        ? pending.preparedMethods()
                        : ReloadPublication.current()
                                .preparedMethods();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.current()
                        .surfaces();
        boolean applies = FabricGlassPaneSeamCulling.applies(
                state.getBlock(),
                pending != null
                        ? pending.selectors()
                        : ReloadPublication.current()
                                .selectors(),
                methods,
                surfaces);
        if (!applies) {
            return model;
        }
        return FabricGlassPaneSeamCulling.wrap(model);
    }

    /**
     * 中文：1.20.1 AfterBake Context 没有 state()，从 id() 的运行时 variant 解析 BlockState：
     * Fabric API 0.92.11 中 AfterBake.Context.id() 声明为 ResourceLocation，但方块状态烘焙
     * 时运行时是携带 variant 的 ModelResourceLocation，因此按 instanceof 提取 getVariant()。
     *
     * English: The 1.20.1 AfterBake Context has no state(); resolve the
     * BlockState from the runtime variant of id(): Fabric API 0.92.11 declares
     * AfterBake.Context.id() as ResourceLocation, but blockstate bakes receive a
     * ModelResourceLocation at runtime, so the variant is extracted via instanceof.
     */
    public static BlockState resolveState(
            ModelModifier.AfterBake.Context context) {
        // 中文：AfterBake.Context.id() 声明为 ResourceLocation；方块状态烘焙的顶层 id 运行时
        // 是 ModelResourceLocation（携带 state 字符串 variant），非方块模型才是纯
        // ResourceLocation。javadoc（0.92.11）："The identifier of this model (may be a
        // ModelResourceLocation)."
        // English: AfterBake.Context.id() is declared as ResourceLocation; for blockstate
        // bakes the top-level id is a ModelResourceLocation carrying the state-string
        // variant, while non-block models use a plain ResourceLocation. Javadoc (0.92.11):
        // "The identifier of this model (may be a ModelResourceLocation)."
        ResourceLocation location =
                context.id();
        // 中文：AfterBake 会为全部烘焙模型回调，包括没有 ModelResourceLocation 的非方块
        // 模型（物品、builtin/entity、特殊模型等），此时 id() 合法为 null；显式
        // 返回 null 让调用方按"无 BlockState"跳过。此类模型不属于任何 BlockState，不会
        // 进入 FabricModelCapture 的按状态基础模型表，也不触发 URI 解析或模型缓存写入。
        // 只短路 null location，其余异常仍照常传播，不吞异常。
        // English: AfterBake fires for every baked model, including non-block models
        // (items, builtin/entity, special models) whose id() is legitimately
        // null; returning null explicitly lets callers skip with "no BlockState".
        // Such models belong to no BlockState, never enter FabricModelCapture's
        // per-state base model table, and trigger no URI resolution or model-cache
        // write. Only the null location is short-circuited; other exceptions still
        // propagate and are never swallowed.
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        String blockPath = path.startsWith("block/")
                ? path.substring("block/".length())
                : path;
        ResourceLocation blockId =
                new ResourceLocation(
                        location.getNamespace(),
                        blockPath);
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        // 中文：只有 ModelResourceLocation 才携带 variant；纯 ResourceLocation（物品/特殊模型）
        // 视为无 variant，与空 topLevelId variant 的语义一致。
        // English: Only ModelResourceLocation carries a variant; a plain ResourceLocation
        // (item/special models) has none, matching empty top-level variant semantics.
        String variant =
                location instanceof ModelResourceLocation
                                modelLocation
                        ? modelLocation.getVariant()
                        : "";
        if (variant == null || variant.isEmpty()) {
            return block.defaultBlockState();
        }
        // 中文：Fabric 物品模型位置约定为 <item-id>#inventory（缺失模型为 #missingno），
        // 从不属于任何 BlockState。必须返回 null，否则物品烘焙会被当作方块状态进入
        // AfterBake 包装链——例如玻璃板物品图标被 Athena 世界 PaneConnectedBlockModel 替换
        // 而空白/坏。普通与染色玻璃板同一语义，不依赖具体 ID。
        // English: Fabric item model locations use <item-id>#inventory (and the missing model
        // uses #missingno), which never belong to any BlockState. Return null so item bakes
        // are never wrapped as block states by the AfterBake chain - e.g. pane item icons must
        // not be replaced by Athena's world PaneConnectedBlockModel and become blank/broken.
        // Plain and stained panes share the same semantics with no ID dependency.
        if ("inventory".equals(variant)
                || "missingno".equals(variant)) {
            return null;
        }
        try {
            return BlockStateParser.parseForBlock(
                            BuiltInRegistries.BLOCK
                                    .asLookup(),
                            blockId + "[" + variant + "]",
                            false)
                    .blockState();
        } catch (CommandSyntaxException exception) {
            return block.defaultBlockState();
        }
    }
}
