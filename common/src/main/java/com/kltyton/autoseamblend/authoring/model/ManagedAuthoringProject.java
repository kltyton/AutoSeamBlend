package com.kltyton.autoseamblend.authoring.model;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：不可变的首次保存载荷。初始化只包含原生文档；PNG 实体化属于后续显式编辑或导出操作。
 *
 * English:
 * Immutable first-save payload.
 *
 * <p>Initialization contains native documents only. PNG materialization belongs to a later,
 * explicit edit or export action.
 *
 * @param family 中文：目标引擎族。 / English: Target engine family.
 * @param documents 中文：首次保存的原生文档列表。 / English: Native documents for the first save.
 */
public record ManagedAuthoringProject(
        EngineFamily family,
        List<ManagedAuthoringFile> documents) {
    public ManagedAuthoringProject {
        Objects.requireNonNull(family, "family");
        documents = List.copyOf(
                Objects.requireNonNull(documents, "documents"));
        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                    "Managed authoring project needs at least one native document");
        }
        LinkedHashMap<String, String> spellings =
                new LinkedHashMap<>();
        for (ManagedAuthoringFile document : documents) {
            String path = document.relativePath();
            if (path.toLowerCase(Locale.ROOT).endsWith(".png")) {
                throw new IllegalArgumentException(
                        "first Managed save must not materialize PNG: " + path);
            }
            String folded = path.toLowerCase(Locale.ROOT);
            String previous = spellings.putIfAbsent(
                    folded,
                    path);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate or case-colliding Managed documents: "
                                + previous + " and " + path);
            }
        }
    }

    public Map<String, byte[]> transactionFiles() {
        LinkedHashMap<String, byte[]> files =
                new LinkedHashMap<>();
        for (ManagedAuthoringFile document : documents) {
            files.put(
                    document.relativePath(),
                    document.content());
        }
        return files;
    }
}
