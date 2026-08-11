package com.kltyton.autoseamblend.authoring.export;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 中文：异步系统导出目的地选择端口；实现不得在客户端提交栈内阻塞。
 *
 * English: Asynchronous system export-destination port. Implementations must
 * not block the client submission stack.
 */
@FunctionalInterface
public interface ExportDestinationPort {
    Selection choose();

    record Selection(
            CompletionStage<Optional<Path>> completion,
            Runnable cancel) {
        public Selection {
            Objects.requireNonNull(completion, "completion");
            Objects.requireNonNull(cancel, "cancel");
        }

        public void requestCancel() {
            cancel.run();
        }
    }
}
