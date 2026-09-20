package com.xkmxz.prismod.client.filter;

/** Built-in v1 filter handlers. Unknown values are skipped by the registry. */
public enum FilterType {
    POST_CHAIN("post_chain"),
    LUT3D("lut3d");

    private final String id;

    FilterType(String id) { this.id = id; }

    public String id() { return id; }

    public static FilterType parse(String value) {
        for (FilterType type : values()) if (type.id.equals(value)) return type;
        return null;
    }
}
