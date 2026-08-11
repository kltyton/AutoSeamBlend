package com.kltyton.autoseamblend.authoring.selector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 中文：把版本共用的 Minecraft 注册表与方块状态投影为原生选择器事实。
 *
 * <p>English: Projects the version-shared Minecraft block registry and block
 * states into native-selector facts.
 */
public final class MinecraftNativeBlockSelectorResolver
        implements NativeBlockSelectorResolver {
    /**
     * 中文：Fabric 工作台只需要默认代表状态，避免为整个注册表物化状态笛卡尔积。
     * English: Fabric workbench projection keeps only the default representative
     * state and avoids materializing the registry-wide state Cartesian product.
     */
    public static final MinecraftNativeBlockSelectorResolver DEFAULT_ONLY =
            new MinecraftNativeBlockSelectorResolver(StateProjection.DEFAULT_ONLY);

    /**
     * 中文：NeoForge 已验收属性编辑需要完整候选状态，以便约束选择代表状态。
     * English: The accepted NeoForge property editor needs every candidate state
     * so selector constraints can choose a representative state.
     */
    public static final MinecraftNativeBlockSelectorResolver ALL_STATES =
            new MinecraftNativeBlockSelectorResolver(StateProjection.ALL_STATES);

    private final StateProjection projection;

    private MinecraftNativeBlockSelectorResolver(StateProjection projection) {
        this.projection = projection;
    }

    @Override
    public Optional<NativeBlockSelectorFacts> resolve(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return Optional.empty();
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || block == Blocks.AIR) {
            return Optional.empty();
        }
        LinkedHashMap<String, List<String>> properties = new LinkedHashMap<>();
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            properties.put(property.getName(), possibleValues(property));
        }
        NativeBlockSelectorState defaultState = state(block.defaultBlockState());
        List<NativeBlockSelectorState> states = projection == StateProjection.ALL_STATES
                ? block.getStateDefinition().getPossibleStates().stream()
                        .map(MinecraftNativeBlockSelectorResolver::state)
                        .toList()
                : List.of(defaultState);
        return Optional.of(new NativeBlockSelectorFacts(
                BuiltInRegistries.BLOCK.getKey(block).toString(),
                properties,
                defaultState,
                states));
    }

    /**
     * 中文：校验并返回注册表中的规范方块 ID。
     * English: Validates and returns a canonical registered block ID.
     */
    public static String requireRegisteredBlockId(String value, String label) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException(label + " is not a registered block id");
        }
        return BuiltInRegistries.BLOCK.getKey(
                BuiltInRegistries.BLOCK.getOptional(id).orElse(null)).toString();
    }

    /**
     * 中文：校验可加入选择器的非空气方块 ID。
     * English: Validates a non-air block ID eligible for selectors.
     */
    public static String requireSelectableBlockId(String value, String label) {
        String canonical = requireRegisteredBlockId(value, label);
        return DEFAULT_ONLY.resolve(canonical)
                .map(NativeBlockSelectorFacts::blockId)
                .orElseThrow(() -> new IllegalArgumentException(
                        label + " is not a selectable block id"));
    }

    /**
     * 中文：把可选择方块 ID 转换为已验证的 Minecraft 标识。
     * English: Converts a selectable block ID into a validated Minecraft identifier.
     */
    public static Optional<ResourceLocation> identifier(String blockId) {
        return ALL_STATES.resolve(blockId)
                .map(NativeBlockSelectorFacts::blockId)
                .map(ResourceLocation::tryParse);
    }

    /**
     * 中文：为属性编辑器投影一个可编辑条目的有序候选属性。
     * English: Projects ordered candidate properties for one editable entry.
     */
    public static Map<String, List<String>> availableProperties(
            NativeBlockSelectorEntry entry) {
        return entry.blockId()
                .flatMap(ALL_STATES::resolve)
                .map(entry::availableProperties)
                .orElse(Map.of());
    }

    /**
     * 中文：把无 Loader 选择器字段投影为 Minecraft 代表状态，供真实预览使用。
     * English: Projects a Loader-neutral selector field into representative
     * Minecraft states for the real preview pipeline.
     */
    public static List<BlockState> representativeStates(
            NativeBlockSelectorField field) {
        return field.representativeStates(ALL_STATES).stream()
                .map(MinecraftNativeBlockSelectorResolver::blockState)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * 中文：把 Minecraft 状态转换为无 Loader 类型的有序状态事实。
     * English: Converts a Minecraft state into ordered, Loader-neutral state facts.
     */
    public static NativeBlockSelectorState state(BlockState state) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), stateValueName(state, property));
        }
        return new NativeBlockSelectorState(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                values);
    }

    private static Optional<BlockState> blockState(
            NativeBlockSelectorState selected) {
        ResourceLocation id = ResourceLocation.tryParse(selected.blockId());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return Optional.empty();
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || block == Blocks.AIR) {
            return Optional.empty();
        }
        return block.getStateDefinition().getPossibleStates().stream()
                .filter(state -> state(state).equals(selected))
                .findFirst();
    }

    private static List<String> possibleValues(Property<?> property) {
        ArrayList<String> values = new ArrayList<>();
        for (Comparable<?> value : property.getPossibleValues()) {
            values.add(propertyValueName(property, value));
        }
        return List.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propertyValueName(
            Property<?> property,
            Comparable<?> value) {
        Property<T> typed = (Property<T>) property;
        return typed.getName((T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String stateValueName(
            BlockState state,
            Property<?> property) {
        Property<T> typed = (Property<T>) property;
        return typed.getName(state.getValue(typed));
    }

    private enum StateProjection {
        DEFAULT_ONLY,
        ALL_STATES
    }
}
