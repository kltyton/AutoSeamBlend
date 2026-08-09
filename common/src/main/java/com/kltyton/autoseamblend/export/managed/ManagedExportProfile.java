package com.kltyton.autoseamblend.export.managed;

public enum ManagedExportProfile {
    AUTHORING("authoring"),
    BAKED("baked");

    private final String serialized;

    ManagedExportProfile(String serialized) {
        this.serialized = serialized;
    }

    public String serialized() {
        return serialized;
    }

    public static ManagedExportProfile parse(String value) {
        for (ManagedExportProfile profile : values()) {
            if (profile.serialized.equals(value)) return profile;
        }
        throw new IllegalArgumentException("unknown managed export profile: " + value);
    }
}
