package com.kltyton.autoseamblend.authoring.export;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：导出工作线程使用的不可变引擎分区捕获；不拥有 Loader 线程或文件系统。
 *
 * English: Immutable engine-partition capture consumed by export workers; it
 * owns neither Loader threading nor filesystem access.
 *
 * @param partitions 中文：按引擎 ID 分区的不可变导出草稿。 / English: Immutable export drafts partitioned by engine id.
 */
public record ManagedExportPartitionCapture(
        Map<String, List<ExportDraft>> partitions) {
    public ManagedExportPartitionCapture {
        LinkedHashMap<String, List<ExportDraft>> copy =
                new LinkedHashMap<>();
        Objects.requireNonNull(partitions, "partitions")
                .forEach((engineId, drafts) -> {
                    if (engineId == null || engineId.isBlank()) {
                        throw new IllegalArgumentException(
                                "partition engine id must not be blank");
                    }
                    copy.put(
                            engineId,
                            List.copyOf(Objects.requireNonNull(
                                    drafts,
                                    "drafts")));
                });
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(
                    "partition capture must not be empty");
        }
        partitions = Collections.unmodifiableMap(copy);
    }
}
