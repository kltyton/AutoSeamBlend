package com.kltyton.autoseamblend.frontend.uilib.component.property;

import com.daqem.uilib.api.client.gui.component.IComponent;
import com.kltyton.autoseamblend.frontend.uilib.component.TextBoxComponent;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.AddNativeSelectorBlock;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleAthenaConnection;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleNativeConnectionBasis;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleNativeRenderLayer;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.MoveNativeSelectorEntry;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.RemoveNativeSelectorEntry;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SetNativeEntryId;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SetNativeProperty;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SetNativeTintBlock;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ToggleNativeFace;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ToggleNativeSelectorProperty;
import com.kltyton.autoseamblend.frontend.layout.property.PropertyFieldLayout;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.AthenaConnection;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Constraint;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Entry;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Field;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Option;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.PropertyValues;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.Selector;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel.SelectorCandidate;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.layout.property.NativePropertyPanelLayout;
import com.kltyton.autoseamblend.frontend.uilib.layout.property.PropertyCanvas;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import com.kltyton.autoseamblend.frontend.uilib.widget.BlockChipWidget;
import com.kltyton.autoseamblend.frontend.uilib.widget.ScrollBody;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * 中文：组织四格式原生属性控件、连接方块选择器和会话草稿，不执行原生文档 I/O。
 *
 * English:
 * Composes the four-format native-property controls, connection-block picker,
 * and session draft without performing native-document I/O.
 */
public final class NativePropertyPanel {
    private static final int MARGIN =
            UilibWorkbenchMetrics.SCREEN_MARGIN;
    private static final int GAP =
            UilibWorkbenchMetrics.PANEL_GAP;
    private static final int CONTENT_INSET = 16;
    private static final int PICKER_RESULT_LIMIT = 96;

    private final Host host;
    private final Function<WorkbenchAction, Boolean> actionSink;
    private final TextBoxComponent searchInput =
            textBox(
                    "gui.autoseamblend.target.search");
    private final TextBoxComponent entryIdInput =
            textBox(
                    "gui.autoseamblend.property.entry_id");

    private static TextBoxComponent textBox(
            String hintKey) {
        TextBoxComponent box =
                new TextBoxComponent(
                        0,
                        0,
                        180,
                        20,
                        "");
        box.setHint(Component.translatable(hintKey));
        return box;
    }

    private String activeEntryKey = "";
    private String modelEntryId = "";
    private NativePropertiesViewModel properties;
    private List<SelectorCandidate> candidates = List.of();
    private PickerTarget pickerTarget;
    private PickerTarget editorTarget;
    private int editorIndex = -1;
    private ScrollBody propertyScroll;
    private ScrollBody pickerScroll;
    private ScrollBody editorScroll;
    private int propertyScrollOffset;
    private int pickerScrollOffset;
    private int editorScrollOffset;

    public NativePropertyPanel(
            Host host,
            Function<WorkbenchAction, Boolean> actionSink) {
        this.host = Objects.requireNonNull(
                host,
                "host");
        this.actionSink = Objects.requireNonNull(
                actionSink,
                "actionSink");
        searchInput.setMaxLength(128);
        searchInput.setResponder(
                ignored -> {
                                host.rebuild();
                });
        entryIdInput.setMaxLength(
                Integer.MAX_VALUE);
    }

    /**
     * 中文：仅在真正进入属性模式或切换目标时重置会话级输入状态。
     *
     * English: Resets session inputs only when entering property mode or
     * changing targets.
     */
    public void open(
            String entryKey,
            NativePropertiesViewModel value) {
        activeEntryKey = requireText(
                entryKey,
                "entryKey");
        properties = Objects.requireNonNull(
                value,
                "value");
        modelEntryId = value.entryId().orElse("");
        entryIdInput.setValue(modelEntryId);
        pickerTarget = null;
        editorTarget = null;
        editorIndex = -1;
        propertyScroll = null;
        pickerScroll = null;
        editorScroll = null;
        propertyScrollOffset = 0;
        pickerScrollOffset = 0;
        editorScrollOffset = 0;
    }

    public boolean isOpenFor(String entryKey) {
        return activeEntryKey.equals(entryKey);
    }

    public void close() {
        activeEntryKey = "";
        properties = null;
        modelEntryId = "";
        pickerTarget = null;
        editorTarget = null;
        editorIndex = -1;
        propertyScroll = null;
        pickerScroll = null;
        editorScroll = null;
        propertyScrollOffset = 0;
        pickerScrollOffset = 0;
        editorScrollOffset = 0;
    }

    /**
     * 中文：Screen 重建 UILib 树之前冻结三个活动滚动容器的位置。
     *
     * English: Captures all three active scroll positions before the Screen
     * rebuilds its UILib tree.
     */
    public void captureScrollState() {
        propertyScrollOffset = propertyScroll != null
                ? propertyScroll.capture()
                : 0;
        pickerScrollOffset = pickerScroll != null
                ? pickerScroll.capture()
                : 0;
        editorScrollOffset = editorScroll != null
                ? editorScroll.capture()
                : 0;
    }

    public void layout(
            TargetRowView row,
            NativePropertiesViewModel value,
            List<SelectorCandidate> propertyCandidates,
            int top,
            int footerTop,
            Runnable back,
            Runnable preview,
            Runnable paint) {
        row = Objects.requireNonNull(row, "row");
        sync(Objects.requireNonNull(value, "value"));
        candidates = List.copyOf(
                Objects.requireNonNull(
                        propertyCandidates,
                        "propertyCandidates"));
        if (pickerTarget != null) {
            layoutPicker(
                    top,
                    footerTop,
                    back,
                    preview,
                    paint);
            return;
        }
        if (editorTarget != null) {
            if (layoutSelectorEditor(
                    top,
                    footerTop,
                    preview,
                    paint)) {
                return;
            }
        }
        layoutMain(
                row,
                top,
                footerTop,
                back,
                preview,
                paint);
    }

    private void sync(NativePropertiesViewModel value) {
        String nextEntryId = value.entryId().orElse("");
        if (!nextEntryId.equals(modelEntryId)) {
            modelEntryId = nextEntryId;
            entryIdInput.setValue(nextEntryId);
        }
        properties = value;
    }

    private void layoutMain(
            TargetRowView row,
            int top,
            int footerTop,
            Runnable back,
            Runnable preview,
            Runnable paint) {
        NativePropertiesViewModel current =
                requireProperties();
        int width = host.width();
        int viewportHeight = Math.max(
                1,
                footerTop - top);
        int contentWidth = Math.max(
                1,
                width - MARGIN * 2);
        int textWidth = Math.max(
                1,
                contentWidth - CONTENT_INSET * 2);
        PropertyCanvas canvas =
                new PropertyCanvas(
                        contentWidth,
                        viewportHeight);
        canvas.addText(
                NativePropertyPanelLayout.fitComponent(
                        row.displayName(),
                        textWidth),
                CONTENT_INSET,
                14,
                UilibWorkbenchTheme.TEXT_PRIMARY);
        canvas.addText(
                NativePropertyPanelLayout.fitComponent(
                        Component.literal(
                                row.blockId().isBlank()
                                        ? row.entryId()
                                        : row.blockId()),
                        textWidth),
                CONTENT_INSET,
                30,
                UilibWorkbenchTheme.TEXT_SECONDARY);
        int documentBottom = addWrappedText(
                canvas,
                Component.translatable(
                        "gui.autoseamblend.property.native_document",
                        current.documentLabel()),
                CONTENT_INSET,
                50,
                UilibWorkbenchTheme.STATUS_NATIVE,
                textWidth);
        int entryIdTop = Math.max(
                72,
                documentBottom + 4);
        if (!current.sourceDocumentPath().isBlank()) {
            int pathY = documentBottom + 2;
            canvas.addText(
                    Component.literal(
                            NativePropertyPanelLayout.compactPath(
                                    current.sourceDocumentPath(),
                                    contentWidth)),
                    CONTENT_INSET,
                    pathY,
                    UilibWorkbenchTheme.TEXT_SECONDARY);
            entryIdTop = Math.max(
                    82,
                    pathY
                            + Minecraft.getInstance()
                                    .font.lineHeight
                            + 9);
        }
        PropertyFieldLayout.EntryField entryField =
                PropertyFieldLayout.entryField(
                        contentWidth,
                        entryIdTop,
                        104);
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.entry_id"),
                entryField.labelX(),
                entryField.labelY(),
                UilibWorkbenchTheme.TEXT_SECONDARY);
        entryIdInput.setX(entryField.inputX());
        entryIdInput.setY(entryField.inputY());
        entryIdInput.setWidth(entryField.inputWidth());
        canvas.addChild(entryIdInput);
        ActionButton applyEntryId =
                NativePropertyPanelLayout.button(
                        "gui.autoseamblend.property.entry_id_apply");
        canvas.placeButton(
                applyEntryId,
                entryField.actionX(),
                entryField.actionY(),
                entryField.actionWidth());
        applyEntryId.setActive(
                current.entryIdEditable()
                        && host.actionsEnabled());
        applyEntryId.setAction(() ->
                dispatch(new SetNativeEntryId(
                        entryIdInput.getValue())));
        int resolvedBottom = addWrappedText(
                canvas,
                Component.translatable(
                        "gui.autoseamblend.property.entry_id_resolved",
                        current.entryId()
                                .filter(value -> !value.isBlank())
                                .orElse(row.entryId())),
                CONTENT_INSET,
                entryField.actionY() + 24,
                UilibWorkbenchTheme.TEXT_SECONDARY,
                textWidth);

        int fieldTop = Math.max(
                entryIdTop
                        + entryField.rowHeight()
                        + 8,
                resolvedBottom + 8);
        for (Field field : current.fields()) {
            if (!field.id().equals("method")
                    && !field.id().equals("compatibility")) {
                continue;
            }
            PropertyFieldLayout.Field geometry =
                    PropertyFieldLayout.field(
                            contentWidth,
                            fieldTop);
            canvas.addText(
                    field.label(),
                    geometry.labelX(),
                    geometry.labelY(),
                    UilibWorkbenchTheme.TEXT_SECONDARY);
            if (field.editable()
                    && !field.options().isEmpty()) {
                Option next = nextOption(field);
                ActionButton valueButton =
                        new ActionButton(
                                field.valueLabel());
                canvas.placeButton(
                        valueButton,
                        geometry.controlX(),
                        geometry.controlY(),
                        geometry.controlWidth());
                valueButton.setActive(
                        host.actionsEnabled());
                valueButton.setAction(() ->
                        dispatch(new SetNativeProperty(
                                field.id(),
                                next.token())));
            } else {
                canvas.addText(
                        NativePropertyPanelLayout.fitComponent(
                                field.valueLabel(),
                                Math.max(
                                        1,
                                        contentWidth
                                                - geometry.controlX()
                                                - CONTENT_INSET)),
                        geometry.controlX(),
                        geometry.controlY() + 7,
                        UilibWorkbenchTheme.TEXT_MUTED);
            }
            fieldTop += geometry.rowHeight();
        }

        int matchingTop = fieldTop + 8;
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.matching_blocks",
                        current.matchingSelector()
                                .entries()
                                .size()),
                CONTENT_INSET,
                matchingTop,
                UilibWorkbenchTheme.TEXT_PRIMARY);
        int matchingEntriesTop = matchingTop + 16;
        if (current.matchingSelector().editable()) {
            ActionButton addMatching =
                    NativePropertyPanelLayout.button(
                            "gui.autoseamblend.property.add_matching_block");
            matchingEntriesTop =
                    NativePropertyPanelLayout.placeSectionAction(
                            canvas,
                            addMatching,
                            matchingTop,
                            164);
            addMatching.setActive(
                    host.actionsEnabled());
            addMatching.setAction(() ->
                    openPicker(
                            PickerTarget.MATCHING));
        }
        int afterMatching = layoutSelectorEntries(
                canvas,
                current.matchingSelector(),
                CONTENT_INSET,
                matchingEntriesTop,
                PickerTarget.MATCHING,
                current.matchingSelector().editable(),
                current.matchingSelector().editable());

        if (hasContinuityControls(current)) {
            layoutContinuity(
                    canvas,
                    row,
                    current,
                    afterMatching + 12);
        } else if (hasAthenaControls(current)) {
            layoutAthena(
                    canvas,
                    current,
                    afterMatching + 12);
        } else {
            layoutPreservedDetails(
                    canvas,
                    current,
                    afterMatching + 12);
        }
        canvas.finish(
                viewportHeight,
                CONTENT_INSET);
        ScrollBody content = new ScrollBody(
                MARGIN,
                top,
                Math.max(
                        1,
                        width - MARGIN * 2),
                viewportHeight,
                0);
        content.addChild(canvas.root());
        content.restore(propertyScrollOffset);
        propertyScroll = content;
        host.addWidget(content.panel());
        host.footer(
                footerTop,
                "gui.autoseamblend.action.back_target",
                back,
                preview,
                paint);
    }

    /**
     * 中文：MCPatcher/Continuity 家族的可编辑连接字段。
     *
     * English: Editable connection fields for the MCPatcher/Continuity family.
     */
    private void layoutContinuity(
            PropertyCanvas canvas,
            TargetRowView row,
            NativePropertiesViewModel current,
            int sectionTop) {
        int width = canvas.width();
        int facesTop = sectionTop;
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.faces"),
                CONTENT_INSET,
                facesTop,
                UilibWorkbenchTheme.TEXT_PRIMARY);
        List<Direction> directions =
                List.of(
                Direction.UP,
                Direction.DOWN,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST);
        int faceGap = 4;
        int faceAreaWidth =
                width - CONTENT_INSET * 2;
        int faceColumns = Math.max(
                1,
                Math.min(
                        directions.size(),
                        (faceAreaWidth + faceGap)
                                / (34 + faceGap)));
        int faceWidth = Math.max(
                1,
                (faceAreaWidth
                                - faceGap
                                        * (faceColumns - 1))
                        / faceColumns);
        for (int index = 0;
                index < directions.size();
                index++) {
            Direction direction = directions.get(index);
            String faceKey =
                    "gui.autoseamblend.preview.face."
                            + direction.name()
                                    .toLowerCase(
                                            Locale.ROOT);
            boolean selected =
                    current.faces()
                            .contains(direction);
            ActionButton face =
                    NativePropertyPanelLayout.button(
                            faceKey);
            face.setMessage(
                    Component.translatable(faceKey)
                            .copy()
                            .append(
                                    selected
                                            ? " \u2713"
                                            : ""));
            canvas.placeButton(
                    face,
                    CONTENT_INSET
                            + (index % faceColumns)
                                    * (faceWidth + faceGap),
                    facesTop
                            + 12
                            + (index / faceColumns) * 24,
                    faceWidth);
            face.setActive(
                    current.facesEditable()
                            && host.actionsEnabled());
            face.setAction(() ->
                    dispatch(new ToggleNativeFace(
                            direction)));
        }
        int faceRows = Math.max(
                1,
                (directions.size()
                                + faceColumns
                                - 1)
                        / faceColumns);
        int policyTop = facesTop
                + 12
                + faceRows * 24
                + 6;
        PropertyFieldLayout.Field basisField =
                PropertyFieldLayout.field(
                        width,
                        policyTop);
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.connection_basis"),
                basisField.labelX(),
                basisField.labelY(),
                UilibWorkbenchTheme.TEXT_SECONDARY);
        ActionButton basis =
                NativePropertyPanelLayout.button(
                        "gui.autoseamblend.property.connection_basis."
                                + current.connectionBasis()
                                        .name()
                                        .toLowerCase(
                                                Locale.ROOT));
        canvas.placeButton(
                basis,
                basisField.controlX(),
                basisField.controlY(),
                basisField.controlWidth());
        basis.setActive(
                current.connectionBasisEditable()
                        && host.actionsEnabled());
        basis.setAction(() ->
                dispatch(new CycleNativeConnectionBasis()));
        int layerTop = policyTop
                + basisField.rowHeight();
        PropertyFieldLayout.Field layerField =
                PropertyFieldLayout.field(
                        width,
                        layerTop);
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.render_layer"),
                layerField.labelX(),
                layerField.labelY(),
                UilibWorkbenchTheme.TEXT_SECONDARY);
        ActionButton layer =
                NativePropertyPanelLayout.button(
                        "gui.autoseamblend.property.render_layer."
                                + current.renderLayer()
                                        .name()
                                        .toLowerCase(
                                                Locale.ROOT));
        canvas.placeButton(
                layer,
                layerField.controlX(),
                layerField.controlY(),
                layerField.controlWidth());
        layer.setActive(
                current.renderLayerEditable()
                        && host.actionsEnabled());
        layer.setAction(() ->
                dispatch(new CycleNativeRenderLayer()));

        int connectionsTop = layerTop
                + layerField.rowHeight()
                + 4;
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.connection_blocks",
                        current.connectionSelector()
                                .entries()
                                .size()),
                CONTENT_INSET,
                connectionsTop,
                UilibWorkbenchTheme.TEXT_PRIMARY);
        int connectionEntriesTop = connectionsTop + 16;
        if (current.connectionSelector().editable()) {
            ActionButton addConnection =
                    NativePropertyPanelLayout.button(
                            "gui.autoseamblend.property.add_connection_block");
            connectionEntriesTop =
                    NativePropertyPanelLayout.placeSectionAction(
                            canvas,
                            addConnection,
                            connectionsTop,
                            164);
            addConnection.setActive(
                    host.actionsEnabled());
            addConnection.setAction(() ->
                    openPicker(
                            PickerTarget.CONNECTION));
        }
        int afterConnections = layoutSelectorEntries(
                canvas,
                current.connectionSelector(),
                CONTENT_INSET,
                connectionEntriesTop,
                PickerTarget.CONNECTION,
                current.connectionSelector().editable(),
                current.connectionSelector().editable());

        int tintTop = afterConnections + 12;
        PropertyFieldLayout.Field tintField =
                PropertyFieldLayout.field(
                        width,
                        tintTop);
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.tint_block"),
                tintField.labelX(),
                tintField.labelY(),
                UilibWorkbenchTheme.TEXT_SECONDARY);
        ActionButton tint =
                NativePropertyPanelLayout.button(
                        current.tintBlockId().isEmpty()
                                ? "gui.autoseamblend.property.tint_none"
                                : "gui.autoseamblend.property.tint_target");
        canvas.placeButton(
                tint,
                tintField.controlX(),
                tintField.controlY(),
                tintField.controlWidth());
        tint.setActive(
                current.tintBlockEditable()
                        && host.actionsEnabled()
                        && (current.tintBlockId().isPresent()
                                || !row.blockId().isBlank()));
        tint.setAction(() ->
                dispatch(new SetNativeTintBlock(
                        current.tintBlockId().isPresent()
                                ? ""
                                : row.blockId())));
    }

    private void layoutAthena(
            PropertyCanvas canvas,
            NativePropertiesViewModel current,
            int sectionTop) {
        int connectionTop = sectionTop + 56;
        PropertyFieldLayout.Field connectionField =
                PropertyFieldLayout.field(
                        canvas.width(),
                        connectionTop);
        canvas.addText(
                Component.translatable(
                        "gui.autoseamblend.property.athena_connection"),
                connectionField.labelX(),
                connectionField.labelY(),
                UilibWorkbenchTheme.TEXT_SECONDARY);
        ActionButton connection =
                NativePropertyPanelLayout.button(
                        "gui.autoseamblend.property.athena_connection."
                                + current.athenaConnection()
                                        .name()
                                        .toLowerCase(
                                                Locale.ROOT));
        canvas.placeButton(
                connection,
                connectionField.controlX(),
                connectionField.controlY(),
                connectionField.controlWidth());
        connection.setActive(
                current.athenaConnectionEditable()
                        && current.athenaConnection()
                                != AthenaConnection.CUSTOM
                        && host.actionsEnabled());
        connection.setAction(() ->
                dispatch(new CycleAthenaConnection()));
        int helpTop = connectionTop
                + connectionField.rowHeight()
                + 8;
        if (current.athenaConnection()
                == AthenaConnection.STATE) {
            int blocksTop = connectionTop
                    + connectionField.rowHeight()
                    + 8;
            canvas.addText(
                    Component.translatable(
                            "gui.autoseamblend.property.connection_blocks",
                            current.connectionSelector()
                                    .entries()
                                    .size()),
                    CONTENT_INSET,
                    blocksTop,
                    UilibWorkbenchTheme.TEXT_PRIMARY);
            ActionButton addConnection =
                    NativePropertyPanelLayout.button(
                            "gui.autoseamblend.property.choose_connection_block");
            int chipsTop =
                    NativePropertyPanelLayout.placeSectionAction(
                            canvas,
                            addConnection,
                            blocksTop,
                            164);
            addConnection.setActive(
                    current.athenaConnectionEditable()
                            && host.actionsEnabled());
            addConnection.setAction(() ->
                    openPicker(
                            PickerTarget.CONNECTION));
            helpTop = layoutBlockChips(
                    canvas,
                    current.connectionSelector(),
                    CONTENT_INSET,
                    chipsTop,
                    PickerTarget.CONNECTION);
        }
        String helpKey =
                current.athenaConnection()
                                == AthenaConnection.CUSTOM
                        ? "gui.autoseamblend.property.athena_connection_custom"
                        : "gui.autoseamblend.property.athena_connection_help";
        addWrappedText(
                canvas,
                Component.translatable(
                        helpKey,
                        current.documentLabel()),
                CONTENT_INSET,
                helpTop,
                UilibWorkbenchTheme.TEXT_SECONDARY,
                Math.max(
                        1,
                        canvas.width()
                                - CONTENT_INSET * 2));
    }

    /**
     * 中文：只读家族字段按验收基线显示原生细节，不增加多余标题。
     *
     * English: Read-only family fields show native details per the accepted
     * baseline without extra headings.
     */
    private void layoutPreservedDetails(
            PropertyCanvas canvas,
            NativePropertiesViewModel current,
            int sectionTop) {
        String statusKey =
                current.athenaConnection()
                                == AthenaConnection.CUSTOM
                        ? "gui.autoseamblend.property.athena_connection_custom"
                        : "gui.autoseamblend.property.family_native_read_only";
        int statusBottom = addWrappedText(
                canvas,
                Component.translatable(
                        statusKey,
                        current.documentLabel()),
                CONTENT_INSET,
                sectionTop + 58,
                UilibWorkbenchTheme.TEXT_SECONDARY,
                Math.max(
                        1,
                        canvas.width()
                                - CONTENT_INSET * 2));
        int detailY = Math.max(
                sectionTop + 78,
                statusBottom + 8);
        for (Map.Entry<String, String> detail :
                current.nativeDetails().entrySet()) {
            int detailBottom = addWrappedText(
                    canvas,
                    Component.literal(
                            detail.getKey()
                                    + ": "
                                    + detail.getValue()),
                    CONTENT_INSET,
                    detailY,
                    UilibWorkbenchTheme.TEXT_SECONDARY,
                    Math.max(
                            1,
                            canvas.width()
                                    - CONTENT_INSET * 2));
            detailY = detailBottom + 6;
        }
    }

    /**
     * 中文：按原始顺序显示全部选择器条目，并把属性编辑、排序和删除拆成明确动作。
     *
     * English:
     * Displays every selector entry in source order and exposes property edit,
     * reorder, and removal as explicit actions.
     */
    private int layoutSelectorEntries(
            PropertyCanvas canvas,
            Selector selector,
            int x,
            int y,
            PickerTarget target,
            boolean entryActionsAllowed,
            boolean propertyEditingAllowed) {
        if (selector.entries().isEmpty()) {
            canvas.addText(
                    Component.translatable(
                            "gui.autoseamblend.property.no_blocks"),
                    x,
                    y + 10,
                    UilibWorkbenchTheme.TEXT_SECONDARY);
            return y + 34;
        }
        int rowY = y;
        for (int index = 0;
                index < selector.entries().size();
                index++) {
            PropertyFieldLayout.SelectorRow rowLayout =
                    PropertyFieldLayout.selectorRow(
                            canvas.width(),
                            x,
                            rowY);
            Entry entry = selector.entries().get(index);
            SelectorVisual visual = visual(entry);
            int selectedIndex = index;
            BlockChipWidget chip =
                    new BlockChipWidget(
                            rowLayout.chipWidth(),
                            visual.icon(),
                            NativePropertyPanelLayout.fitComponent(
                                    entry.opaque()
                                            ? Component.translatable(
                                                    "gui.autoseamblend.property.selector_opaque")
                                            : visual.displayName(),
                                    Math.max(
                                            1,
                                            rowLayout.chipWidth()
                                                    - 40)),
                            NativePropertyPanelLayout.compactSelector(
                                    entry.serialized()),
                            false,
                            () -> openSelectorEditor(
                                    target,
                                    selectedIndex));
            chip.setX(rowLayout.chipX());
            chip.setY(rowLayout.chipY());
            chip.setActive(
                    propertyEditingAllowed
                            && entry.editable()
                            && host.actionsEnabled());
        canvas.addChild(chip);

            int controlX = rowLayout.controlsX();
            int controlY = rowLayout.controlsY();
            ActionButton up =
                    new ActionButton(
                            Component.literal("\u2191"));
            canvas.placeButton(
                    up,
                    controlX,
                    controlY,
                    30);
            up.setActive(
                    entryActionsAllowed
                            && index > 0
                            && host.actionsEnabled());
            up.setAction(() ->
                    dispatch(new MoveNativeSelectorEntry(
                            target.kind,
                            selectedIndex,
                            -1)));
            ActionButton down =
                    new ActionButton(
                            Component.literal("\u2193"));
            canvas.placeButton(
                    down,
                    controlX + 34,
                    controlY,
                    30);
            down.setActive(
                    entryActionsAllowed
                            && index
                                    < selector.entries()
                                                    .size()
                                            - 1
                            && host.actionsEnabled());
            down.setAction(() ->
                    dispatch(new MoveNativeSelectorEntry(
                            target.kind,
                            selectedIndex,
                            1)));
            ActionButton remove =
                    NativePropertyPanelLayout.button(
                            "gui.autoseamblend.property.selector_remove");
            canvas.placeButton(
                    remove,
                    controlX + 68,
                    controlY,
                    56);
            remove.setActive(
                    entryActionsAllowed
                            && host.actionsEnabled());
            remove.setAction(() ->
                    dispatch(new RemoveNativeSelectorEntry(
                            target.kind,
                            selectedIndex)));
            rowY += rowLayout.rowHeight();
        }
        return rowY;
    }

    /**
     * 中文：Athena 指定方块模式按验收基线的块芯片呈现，超过三个只显示余数。
     *
     * English: Athena specific-block mode renders connection blocks as chips
     * with a remaining-count line after three, per the accepted baseline.
     */
    private int layoutBlockChips(
            PropertyCanvas canvas,
            Selector selector,
            int x,
            int y,
            PickerTarget target) {
        int availableWidth = Math.max(
                1,
                canvas.width() - x - CONTENT_INSET);
        int chipWidth = Math.min(
                174,
                availableWidth);
        int columns = Math.max(
                1,
                (availableWidth + 6)
                        / (chipWidth + 6));
        List<Entry> entries = selector.entries();
        int visible = 0;
        for (int index = 0;
                index < entries.size();
                index++) {
            if (visible >= 3) {
                canvas.addText(
                        Component.translatable(
                                "gui.autoseamblend.property.more_blocks",
                                entries.size() - visible),
                        x
                                + (visible % columns)
                                        * (chipWidth + 6),
                        y
                                + (visible / columns) * 38
                                + 10,
                        UilibWorkbenchTheme.TEXT_SECONDARY);
                return y
                        + (visible / columns + 1) * 38;
            }
            int chipIndex = index;
            Entry entry = entries.get(index);
            SelectorVisual visual = visual(entry);
            BlockChipWidget chip =
                    new BlockChipWidget(
                            chipWidth,
                            visual.icon(),
                            NativePropertyPanelLayout.fitComponent(
                                    entry.opaque()
                                            ? Component.translatable(
                                                    "gui.autoseamblend.property.selector_opaque")
                                            : visual.displayName(),
                                    Math.max(
                                            1,
                                            chipWidth - 40)),
                            NativePropertyPanelLayout.compactSelector(
                                    entry.serialized()),
                            true,
                            () -> dispatch(
                                    new RemoveNativeSelectorEntry(
                                            target.kind,
                                            chipIndex)));
            chip.setX(
                    x
                            + (visible % columns)
                                    * (chipWidth + 6));
            chip.setY(
                    y
                            + (visible / columns) * 38);
        canvas.addChild(chip);
            visible++;
        }
        if (entries.isEmpty()) {
            canvas.addText(
                    Component.translatable(
                            "gui.autoseamblend.property.no_blocks"),
                    x,
                    y + 10,
                    UilibWorkbenchTheme.TEXT_SECONDARY);
            return y + 34;
        }
        return y
                + Math.max(
                        1,
                        (visible + columns - 1) / columns)
                        * 38;
    }

    private boolean layoutSelectorEditor(
            int top,
            int footerTop,
            Runnable preview,
            Runnable paint) {
        NativePropertiesViewModel current =
                requireProperties();
        Selector selector = selector(
                current,
                editorTarget);
        if (editorIndex < 0
                || editorIndex
                        >= selector.entries().size()) {
            editorTarget = null;
            editorIndex = -1;
            editorScroll = null;
            return false;
        }
        Entry entry = selector.entries()
                .get(editorIndex);
        int width = host.width();
        int viewportHeight = Math.max(
                80,
                footerTop - top);
        int contentWidth = Math.max(
                1,
                width - MARGIN * 2);
        int textWidth = Math.max(
                1,
                contentWidth - CONTENT_INSET * 2);
        PropertyCanvas canvas =
                new PropertyCanvas(
                        contentWidth,
                        viewportHeight);
        canvas.addText(
                NativePropertyPanelLayout.fitComponent(
                        Component.translatable(
                                editorTarget
                                                == PickerTarget.MATCHING
                                        ? "gui.autoseamblend.property.selector_edit_matching"
                                        : "gui.autoseamblend.property.selector_edit_connection"),
                        textWidth),
                CONTENT_INSET,
                14,
                UilibWorkbenchTheme.TEXT_PRIMARY);
        int serializedBottom = addWrappedText(
                canvas,
                Component.literal(
                        entry.serialized()),
                CONTENT_INSET,
                32,
                UilibWorkbenchTheme.TEXT_SECONDARY,
                textWidth);
        if (entry.opaque() || !entry.editable()) {
            addWrappedText(
                    canvas,
                    Component.translatable(
                            "gui.autoseamblend.property.selector_opaque_help"),
                    CONTENT_INSET,
                    58,
                    UilibWorkbenchTheme.TEXT_SECONDARY,
                    textWidth);
        } else {
            int propertyY = Math.max(
                    58,
                    serializedBottom + 8);
            for (PropertyValues property :
                    orderedProperties(entry)) {
                PropertyFieldLayout.Field propertyField =
                        PropertyFieldLayout.field(
                                contentWidth,
                                propertyY);
                canvas.addText(
                        Component.literal(
                                property.propertyName()),
                        propertyField.labelX(),
                        propertyField.labelY(),
                        UilibWorkbenchTheme.TEXT_SECONDARY);
                int buttonX = propertyField.controlX();
                int buttonY = propertyField.controlY();
                int valueWidth = Math.min(
                        82,
                        propertyField.controlWidth());
                for (String option :
                        property.availableValues()) {
                    if (buttonX + valueWidth
                            > propertyField.controlX()
                                    + propertyField.controlWidth()) {
                        buttonX = propertyField.controlX();
                        buttonY += 26;
                    }
                    boolean selected =
                            property.selectedValues()
                                    .contains(option);
                    ActionButton valueButton =
                            new ActionButton(
                                    Component.literal(
                                            option
                                                    + (selected
                                                            ? " \u2713"
                                                            : "")));
                    canvas.placeButton(
                            valueButton,
                            buttonX,
                            buttonY,
                            valueWidth);
                    valueButton.setActive(
                            host.actionsEnabled());
                    PickerTarget capturedTarget =
                            editorTarget;
                    int capturedIndex = editorIndex;
                    valueButton.setAction(() ->
                            dispatch(
                                    new ToggleNativeSelectorProperty(
                                            capturedTarget.kind,
                                            capturedIndex,
                                            property.propertyName(),
                                            option)));
                    buttonX += valueWidth + 4;
                }
                if (property.availableValues().isEmpty()
                        && !property.selectedValues().isEmpty()) {
                    canvas.addText(
                            Component.literal(
                                    String.join(
                                            ", ",
                                            property.selectedValues())),
                            propertyField.controlX(),
                            propertyField.controlY() + 7,
                            UilibWorkbenchTheme.TEXT_MUTED);
                }
                propertyY = Math.max(
                        propertyY
                                + propertyField.rowHeight(),
                        buttonY + 32);
            }
        }
        canvas.finish(
                viewportHeight,
                CONTENT_INSET);
        ScrollBody content = new ScrollBody(
                MARGIN,
                top,
                Math.max(
                        1,
                        width - MARGIN * 2),
                viewportHeight,
                0);
        content.addChild(canvas.root());
        content.restore(editorScrollOffset);
        editorScroll = content;
        host.addWidget(content.panel());
        host.footer(
                footerTop,
                "gui.autoseamblend.property.selector_done",
                this::closeSelectorEditor,
                preview,
                paint);
        return true;
    }

    private void layoutPicker(
            int top,
            int footerTop,
            Runnable back,
            Runnable preview,
            Runnable paint) {
        NativePropertiesViewModel current =
                requireProperties();
        int width = host.width();
        int controlLeft = MARGIN + 16;
        int controlWidth = Math.max(
                1,
                width - controlLeft * 2);
        host.addComponent(new PanelComponent(
                MARGIN,
                top,
                Math.max(1, width - MARGIN * 2),
                Math.max(1, footerTop - GAP - top),
                UilibWorkbenchTheme.SURFACE_PANEL));
        host.addText(
                NativePropertyPanelLayout.fitComponent(
                        Component.translatable(
                                pickerTarget
                                                == PickerTarget.MATCHING
                                        ? "gui.autoseamblend.property.pick_matching_block"
                                        : "gui.autoseamblend.property.pick_connection_block"),
                        controlWidth),
                MARGIN + 16,
                top + 12,
                UilibWorkbenchTheme.TEXT_PRIMARY);
        boolean sideBySide =
                controlWidth >= 260;
        int doneWidth = sideBySide
                ? 126
                : controlWidth;
        searchInput.setX(controlLeft);
        searchInput.setY(top + 30);
        searchInput.setWidth(sideBySide
                ? controlWidth - doneWidth - 8
                : controlWidth);
        host.addWidget(searchInput);
        ActionButton done =
                NativePropertyPanelLayout.button(
                        "gui.autoseamblend.target.finish_add");
        host.placeButton(
                done,
                sideBySide
                        ? controlLeft
                                + controlWidth
                                - doneWidth
                        : controlLeft,
                sideBySide
                        ? top + 30
                        : top + 54,
                doneWidth);
        done.setAction(this::closePicker);
        int listTop = sideBySide
                ? top + 58
                : top + 82;
        ScrollBody list = new ScrollBody(
                MARGIN + 8,
                listTop,
                Math.max(
                        1,
                        width - MARGIN * 2 - 16),
                Math.max(
                        36,
                        footerTop - listTop - 8),
                UilibWorkbenchMetrics.GRID);
        String needle =
                searchInput.getValue()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT);
        List<String> selected =
                selectedBlocks(
                        current,
                        pickerTarget);
        int shown = 0;
        for (SelectorCandidate candidate :
                candidates) {
            if ((!needle.isEmpty()
                            && !candidate.blockId()
                                    .toLowerCase(
                                            Locale.ROOT)
                                    .contains(needle)
                            && !candidate.displayName()
                                    .getString()
                                    .toLowerCase(
                                            Locale.ROOT)
                                    .contains(needle))
                    || selected.contains(
                            candidate.blockId())) {
                continue;
            }
            if (shown >= PICKER_RESULT_LIMIT) {
                break;
            }
            list.addChild(
                    new NativePropertyBlockChipComponent(
                            Math.max(
                                    1,
                                    width
                                            - MARGIN * 2
                                            - 28),
                            candidate,
                            false,
                            () -> {
                                PickerTarget selectedTarget =
                                        Objects.requireNonNull(
                                                pickerTarget,
                                                "pickerTarget");
                                pickerTarget = null;
                                dispatch(
                                        new AddNativeSelectorBlock(
                                                selectedTarget.kind,
                                                candidate.blockId()));
                            }));
            shown++;
        }
        list.restore(pickerScrollOffset);
        pickerScroll = list;
        host.addWidget(list.panel());
        host.footer(
                footerTop,
                "gui.autoseamblend.action.back_target",
                () -> {
                    pickerTarget = null;
                    back.run();
                },
                preview,
                paint);
    }

    private void openPicker(
            PickerTarget target) {
        pickerTarget = Objects.requireNonNull(
                target,
                "target");
        searchInput.setValue("");
        host.rebuild();
    }

    private void closePicker() {
        pickerTarget = null;
        pickerScroll = null;
        host.rebuild();
    }

    private void openSelectorEditor(
            PickerTarget target,
            int index) {
        editorTarget = Objects.requireNonNull(
                target,
                "target");
        editorIndex = index;
        host.rebuild();
    }

    private void closeSelectorEditor() {
        editorTarget = null;
        editorIndex = -1;
        editorScroll = null;
        host.rebuild();
    }

    private void dispatch(
            WorkbenchAction action) {
        Boolean accepted = actionSink.apply(
                Objects.requireNonNull(
                        action,
                        "action"));
        if (Boolean.TRUE.equals(accepted)) {
            host.rebuild();
        }
    }

    /**
     * 中文：按正文可用宽度逐行绘制换行文本，返回下一行的 y。
     *
     * English: Draws wrapped text line by line within the body's usable width
     * and returns the y of the next line.
     */
    private int addWrappedText(
            PropertyCanvas canvas,
            Component text,
            int x,
            int y,
            int color,
            int maxWidth) {
        int cursor = y;
        for (Component line :
                NativePropertyPanelLayout.wrap(
                        text,
                        maxWidth)) {
            canvas.addText(
                    line,
                    x,
                    cursor,
                    color);
            cursor += Minecraft.getInstance()
                    .font.lineHeight;
        }
        return cursor;
    }

    private SelectorVisual visual(Entry entry) {
        Optional<SelectorCandidate> candidate =
                entry.blockId().flatMap(blockId ->
                        candidates.stream()
                                .filter(value ->
                                        value.blockId()
                                                .equals(blockId))
                                .findFirst());
        if (candidate.isPresent()) {
            SelectorCandidate value =
                    candidate.orElseThrow();
            return new SelectorVisual(
                    value.icon(),
                    value.displayName());
        }
        String label =
                entry.blockId().orElse(
                        entry.serialized());
        return new SelectorVisual(
                new ItemStack(Blocks.BARRIER),
                Component.literal(label));
    }

    private static List<PropertyValues>
            orderedProperties(Entry entry) {
        LinkedHashMap<String, PropertyValues>
                ordered = new LinkedHashMap<>();
        for (Constraint constraint :
                entry.constraints()) {
            ordered.put(
                    constraint.propertyName(),
                    new PropertyValues(
                            constraint.propertyName(),
                            constraint.availableValues(),
                            constraint.selectedValues()));
        }
        for (PropertyValues property :
                entry.availableProperties()) {
            ordered.putIfAbsent(
                    property.propertyName(),
                    property);
        }
        return List.copyOf(
                ordered.values());
    }

    private static List<String> selectedBlocks(
            NativePropertiesViewModel current,
            PickerTarget target) {
        return selector(current, target)
                .entries()
                .stream()
                .map(Entry::blockId)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Selector selector(
            NativePropertiesViewModel current,
            PickerTarget target) {
        return target == PickerTarget.MATCHING
                ? current.matchingSelector()
                : current.connectionSelector();
    }

    private NativePropertiesViewModel
            requireProperties() {
        return Objects.requireNonNull(
                properties,
                "properties");
    }

    private static boolean hasContinuityControls(
            NativePropertiesViewModel current) {
        return current.facesEditable()
                || current.connectionBasisEditable()
                || current.renderLayerEditable()
                || current.tintBlockEditable()
                || current.connectionSelector().editable();
    }

    private static boolean hasAthenaControls(
            NativePropertiesViewModel current) {
        return current.athenaConnectionEditable()
                || current.athenaConnection()
                        != AthenaConnection.CUSTOM
                || current.fields().stream()
                        .anyMatch(field ->
                                field.id()
                                        .equals("connect_to"));
    }

    private static Option nextOption(Field field) {
        List<Option> options = field.options();
        for (int index = 0;
                index < options.size();
                index++) {
            if (options.get(index).token()
                    .equals(field.valueToken())) {
                return options.get(
                        (index + 1) % options.size());
            }
        }
        return options.get(0);
    }

    private static String requireText(
            String value,
            String label) {
        value = Objects.requireNonNull(
                value,
                label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    label + " must not be blank");
        }
        return value;
    }

    private enum PickerTarget {
        MATCHING(
                WorkbenchAction.NativeSelectorKind.MATCHING),
        CONNECTION(
                WorkbenchAction.NativeSelectorKind.CONNECTION);

        private final WorkbenchAction.NativeSelectorKind kind;

        PickerTarget(
                WorkbenchAction.NativeSelectorKind kind) {
            this.kind = kind;
        }
    }

    private record SelectorVisual(
            ItemStack icon,
            Component displayName) {
        private SelectorVisual {
            icon = Objects.requireNonNull(
                            icon,
                            "icon")
                    .copy();
            displayName = Objects.requireNonNull(
                    displayName,
                    "displayName");
        }

        @Override
        public ItemStack icon() {
            return icon.copy();
        }
    }

    public interface Host {
        int width();

        boolean actionsEnabled();

        void addComponent(IComponent component);

        void addWidget(IComponent widget);

        void addText(
                Component text,
                int x,
                int y,
                int color);

        void placeButton(
                ActionButton button,
                int x,
                int y,
                int width);

        void footer(
                int top,
                String backKey,
                Runnable back,
                Runnable preview,
                Runnable paint);

        void rebuild();
    }
}
