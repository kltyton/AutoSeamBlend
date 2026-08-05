package com.kltyton.autoseamblend.frontend.uilib.component.property;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Field;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Option;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Selector;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.SelectorCandidate;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * 中文：把 Loader 已完成原生转换的值组装成 Common 属性视图，不读取资源或注册表。
 *
 * English: Assembles a Common property view from values already converted by a
 * Loader; it performs no resource or registry I/O.
 */
public final class NativePropertyViewProjection {
    private NativePropertyViewProjection() {}

    public static NativePropertiesViewModel project(Source source) {
        Objects.requireNonNull(source, "source");
        return new NativePropertiesViewModel(
                Component.literal(source.family().formatId()),
                "",
                List.of(
                        methodField(source.authoringMethod()),
                        compatibilityField(source.authoringCompatibility())),
                Component.translatable(
                        "gui.autoseamblend.property.family_native_read_only",
                        source.family().formatId()),
                source.entryId(),
                source.entryIdEditable(),
                source.matchingSelector(),
                source.connectionSelector(),
                source.faces(),
                source.facesEditable(),
                source.connectionBasis(),
                source.connectionBasisEditable(),
                source.renderLayer(),
                source.renderLayerEditable(),
                source.tintBlockId(),
                source.tintBlockEditable(),
                source.athenaConnection(),
                source.athenaConnectionEditable(),
                source.nativeDetails());
    }

    /**
     * 中文：把目标库行转换为属性选择器候选，过滤无接收方的 targetless 行。
     *
     * English: Converts target-library rows into property-selector candidates,
     * filtering rows without a receiver.
     */
    public static List<SelectorCandidate> selectorCandidates(
            List<TargetRowView> rows) {
        return Objects.requireNonNull(rows, "rows").stream()
                .filter(row -> !row.blockId().isBlank())
                .map(row -> new SelectorCandidate(
                        row.blockId(),
                        row.displayName(),
                        row.icon()))
                .toList();
    }

    private static Field methodField(ConnectionMethod selected) {
        List<Option> options = java.util.Arrays.stream(ConnectionMethod.values())
                .map(method -> new Option(
                        method.serializedName(),
                        Component.translatable(
                                "config.autoseamblend.method."
                                        + method.serializedName())))
                .toList();
        String token = selected.serializedName();
        Component value = options.stream()
                .filter(option -> option.token().equals(token))
                .map(Option::label)
                .findFirst()
                .orElse(Component.literal(token));
        return new Field(
                "method",
                Component.translatable("gui.autoseamblend.property.method"),
                value,
                token,
                options,
                true);
    }

    private static Field compatibilityField(boolean selected) {
        String token = Boolean.toString(selected);
        List<Option> options = List.of(
                new Option(
                        "true",
                        Component.translatable(
                                "gui.autoseamblend.property.complete_missing")),
                new Option(
                        "false",
                        Component.translatable(
                                "gui.autoseamblend.property.native_exclusive")));
        Component value = options.stream()
                .filter(option -> option.token().equals(token))
                .map(Option::label)
                .findFirst()
                .orElse(Component.literal(token));
        return new Field(
                "compatibility",
                Component.translatable("gui.autoseamblend.property.compatibility"),
                value,
                token,
                options,
                true);
    }

    /**
     * 中文：Loader 只实现已解析原生字段到 Common DTO 的转换。
     *
     * English: Loaders only implement conversion from parsed native fields to
     * Common DTOs.
     */
    public interface Source {
        EngineFamily family();

        ConnectionMethod authoringMethod();

        boolean authoringCompatibility();

        Optional<String> entryId();

        boolean entryIdEditable();

        Selector matchingSelector();

        Selector connectionSelector();

        Set<Direction> faces();

        boolean facesEditable();

        NativePropertiesViewModel.ConnectionBasis connectionBasis();

        boolean connectionBasisEditable();

        NativePropertiesViewModel.RenderLayer renderLayer();

        boolean renderLayerEditable();

        Optional<String> tintBlockId();

        boolean tintBlockEditable();

        NativePropertiesViewModel.AthenaConnection athenaConnection();

        boolean athenaConnectionEditable();

        Map<String, String> nativeDetails();
    }
}
