package com.kltyton.autoseamblend.frontend.paint;

import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet.Slot;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot.RegionEdit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：在公共直通 ARGB 绘画计划与纹理载体之间转换。
 *
 * English: Adapts common straight-ARGB paint plans to texture carriers.
 */
public final class TexturePaintAdapter {
    private final TexturePaintDocument document;
    private final Map<String, TextureSourceSnapshot>
            carriersByContentKey;

    public TexturePaintAdapter(
            ConnectionTextureSet sources) {
        sources = Objects.requireNonNull(sources, "sources");
        LinkedHashMap<String, ArrayList<CarrierBinding>>
                bindingsByPath = new LinkedHashMap<>();
        LinkedHashMap<String, TextureSourceSnapshot>
                carriers = new LinkedHashMap<>();
        ArrayList<PaintDocumentSource.Slot> slots =
                new ArrayList<>();
        for (Slot slot : sources.slots()) {
            String contentKey = contentKey(
                    slot,
                    bindingsByPath,
                    carriers);
            slots.add(new PaintDocumentSource.Slot(
                    slot.logicalIndex(),
                    slot.physicalIndex(),
                    slot.outputPath(),
                    contentKey,
                    slot.cellX(),
                    slot.cellY(),
                    slot.cellWidth(),
                    slot.cellHeight(),
                    slot.nativeIntent(),
                    slot.synthetic(),
                    slot.source().firstFrameRegion(
                            slot.cellX(),
                            slot.cellY(),
                            slot.cellWidth(),
                            slot.cellHeight())));
        }
        document = new TexturePaintDocument(
                new PaintDocumentSource(
                        sources.tilesExpression(),
                        slots));
        carriersByContentKey = Map.copyOf(carriers);
    }

    public TexturePaintDocument document() {
        return document;
    }

    public Map<String, byte[]> editedFiles()
            throws IOException {
        LinkedHashMap<String, byte[]> result =
                new LinkedHashMap<>();
        for (CarrierEditPlan edit : document.carrierEdits()) {
            TextureSourceSnapshot merged =
                    merge(edit);
            result.put(
                    edit.outputPath(),
                    merged.materializeCarrier().png());
            byte[] metadata = merged.sourceMetadata();
            if (metadata.length > 0) {
                result.put(
                        edit.outputPath() + ".mcmeta",
                        metadata);
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 中文：把未保存的公共绘画计划还原成导出专用不可变源快照。
     *
     * English: Rehydrates unsaved common paint plans as immutable source
     * snapshots for export.
     */
    public Map<String, TextureSourceSnapshot>
            editedSources() {
        LinkedHashMap<String, TextureSourceSnapshot>
                result = new LinkedHashMap<>();
        for (CarrierEditPlan edit : document.carrierEdits()) {
            TextureSourceSnapshot merged = merge(edit);
            result.put(merged.sourceTextureId(), merged);
        }
        return Map.copyOf(result);
    }

    private TextureSourceSnapshot merge(
            CarrierEditPlan edit) {
        TextureSourceSnapshot source =
                Objects.requireNonNull(
                        carriersByContentKey.get(
                                edit.carrierContentKey()),
                        "paint carrier source");
        List<RegionEdit> regions = edit.regions()
                .stream()
                .map(region -> new RegionEdit(
                        region.x(),
                        region.y(),
                        region.width(),
                        region.height(),
                        region.straightArgb()))
                .toList();
        return source.withRegions(regions);
    }

    private static String contentKey(
            Slot slot,
            LinkedHashMap<String, ArrayList<CarrierBinding>>
                    bindingsByPath,
            LinkedHashMap<String, TextureSourceSnapshot>
                    carriers) {
        ArrayList<CarrierBinding> bindings =
                bindingsByPath.computeIfAbsent(
                        slot.outputPath(),
                        ignored -> new ArrayList<>());
        for (CarrierBinding binding : bindings) {
            if (binding.source().sameCarrierContent(
                    slot.source())) {
                return binding.contentKey();
            }
        }
        String key = "carrier:" + carriers.size();
        CarrierBinding binding = new CarrierBinding(
                key,
                slot.source());
        bindings.add(binding);
        carriers.put(key, slot.source());
        return key;
    }

    private record CarrierBinding(
            String contentKey,
            TextureSourceSnapshot source) {
        private CarrierBinding {
            Objects.requireNonNull(contentKey, "contentKey");
            Objects.requireNonNull(source, "source");
        }
    }
}
