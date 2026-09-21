package com.xkmxz.prismod.client.filter.lut;

/** Immutable Adobe 3D LUT data in file order. */
public record Lut3dData(int size, float[] rgb, float[] domainMin, float[] domainMax, String title) {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 64;
    /** @deprecated use {@link #size()} for the parsed LUT size. */
    @Deprecated public static final int SIZE = 32;
    /** @deprecated use {@link #pointCount()} for the parsed LUT point count. */
    @Deprecated public static final int POINT_COUNT = SIZE * SIZE * SIZE;

    /** Compatibility constructor for callers that provide the default 32^3 LUT. */
    public Lut3dData(float[] rgb, float[] domainMin, float[] domainMax, String title) {
        this(SIZE, rgb, domainMin, domainMax, title);
    }

    public Lut3dData {
        if (size < MIN_SIZE || size > MAX_SIZE) throw new IllegalArgumentException("LUT size must be between 1 and 64");
        long pointCount = (long) size * size * size;
        if (rgb == null || rgb.length != pointCount * 3) throw new IllegalArgumentException("LUT data point count does not match size");
        rgb = rgb.clone();
        domainMin = domainMin == null ? new float[] {0, 0, 0} : domainMin.clone();
        domainMax = domainMax == null ? new float[] {1, 1, 1} : domainMax.clone();
    }

    public int pointCount() { return size * size * size; }

    @Override public float[] rgb() { return rgb.clone(); }
    @Override public float[] domainMin() { return domainMin.clone(); }
    @Override public float[] domainMax() { return domainMax.clone(); }
}
