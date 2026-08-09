package com.kltyton.autoseamblend.foundation.diagnostic;

import java.util.Objects;

/** 中文：跨领域失败消息的稳定回退规则。 / English: Stable fallback policy for cross-domain failure messages. */
public final class FailureDetails {
    private FailureDetails() {}

    public static String message(
            Throwable failure,
            String fallback) {
        Objects.requireNonNull(failure, "failure");
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalArgumentException("fallback must not be blank");
        }
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? fallback
                : message;
    }
}
