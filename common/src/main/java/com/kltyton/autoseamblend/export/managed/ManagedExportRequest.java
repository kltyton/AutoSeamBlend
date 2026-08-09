package com.kltyton.autoseamblend.export.managed;

import java.nio.file.Path;
import java.util.Objects;

public record ManagedExportRequest(
        ManagedExportProfile profile,
        Path destination,
        boolean zip,
        boolean overwrite) {
    public ManagedExportRequest {
        Objects.requireNonNull(profile, "profile");
        destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
    }
}
