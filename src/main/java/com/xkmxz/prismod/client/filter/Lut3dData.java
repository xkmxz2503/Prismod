package com.xkmxz.prismod.client.filter;

/** Immutable Adobe 3D LUT data in file order. */
public record Lut3dData(float[] rgb, float[] domainMin, float[] domainMax, String title) {
    public static final int SIZE = 32;
    public static final int POINT_COUNT = SIZE * SIZE * SIZE;

    public Lut3dData {
        if (rgb == null || rgb.length != POINT_COUNT * 3) throw new IllegalArgumentException("LUT data must contain 32768 RGB points");
        rgb = rgb.clone();
        domainMin = domainMin == null ? new float[] {0, 0, 0} : domainMin.clone();
        domainMax = domainMax == null ? new float[] {1, 1, 1} : domainMax.clone();
    }

    @Override public float[] rgb() { return rgb.clone(); }
    @Override public float[] domainMin() { return domainMin.clone(); }
    @Override public float[] domainMax() { return domainMax.clone(); }
}
