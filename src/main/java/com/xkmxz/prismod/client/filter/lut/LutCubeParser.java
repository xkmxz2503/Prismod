package com.xkmxz.prismod.client.filter.lut;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Strict parser for the supported Adobe .cube 3D LUT subset. */
public final class LutCubeParser {
    private LutCubeParser() { }

    public static Lut3dData parse(Reader reader) throws IOException {
        int size = -1;
        String title = null;
        float[] min = null;
        float[] max = null;
        List<Float> values = new ArrayList<>(Lut3dData.POINT_COUNT * 3);
        try (BufferedReader buffered = reader instanceof BufferedReader b ? b : new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                int comment = trimmed.indexOf('#');
                if (comment >= 0) trimmed = trimmed.substring(0, comment).trim();
                if (trimmed.isEmpty()) continue;
                String[] parts = trimmed.split("\\s+");
                String directive = parts[0];
                try {
                    switch (directive) {
                        case "TITLE" -> {
                            if (parts.length < 2 || title != null) throw new IllegalArgumentException("invalid TITLE");
                            title = trimmed.substring("TITLE".length()).trim();
                        }
                        case "LUT_3D_SIZE" -> {
                            if (parts.length != 2 || size != -1) throw new IllegalArgumentException("invalid LUT_3D_SIZE");
                            size = Integer.parseInt(parts[1]);
                            if (size != Lut3dData.SIZE) throw new IllegalArgumentException("LUT_3D_SIZE must be 32");
                        }
                        case "DOMAIN_MIN", "DOMAIN_MAX" -> {
                            if (parts.length != 4) throw new IllegalArgumentException("invalid " + directive);
                            float[] target = new float[3];
                            for (int i = 0; i < 3; i++) target[i] = finite(parts[i + 1]);
                            if ("DOMAIN_MIN".equals(directive)) { if (min != null) throw new IllegalArgumentException("duplicate DOMAIN_MIN"); min = target; }
                            else { if (max != null) throw new IllegalArgumentException("duplicate DOMAIN_MAX"); max = target; }
                        }
                        default -> {
                            if (size == -1) throw new IllegalArgumentException("LUT_3D_SIZE must precede data");
                            if (!looksNumeric(directive)) throw new IllegalArgumentException("unknown directive: " + directive);
                            if (parts.length != 3) throw new IllegalArgumentException("unknown directive or invalid data");
                            for (String part : parts) {
                                float value = finite(part);
                                if (value < 0.0F || value > 1.0F) throw new IllegalArgumentException("LUT value out of range");
                                values.add(value);
                            }
                        }
                    }
                } catch (RuntimeException exception) {
                    throw new IOException("invalid .cube line " + lineNumber + ": " + exception.getMessage(), exception);
                }
            }
        }
        if (size != Lut3dData.SIZE) throw new IOException("missing LUT_3D_SIZE 32");
        if (values.size() != Lut3dData.POINT_COUNT * 3) throw new IOException("expected 32768 LUT data points");
        float[] rgb = new float[values.size()];
        for (int i = 0; i < rgb.length; i++) rgb[i] = values.get(i);
        float[] domainMin = min == null ? new float[] {0, 0, 0} : min;
        float[] domainMax = max == null ? new float[] {1, 1, 1} : max;
        for (int i = 0; i < 3; i++) if (!(domainMax[i] > domainMin[i])) throw new IOException("invalid LUT domain range");
        return new Lut3dData(rgb, domainMin, domainMax, title);
    }

    private static float finite(String value) {
        float parsed = Float.parseFloat(value);
        if (!Float.isFinite(parsed)) throw new IllegalArgumentException("non-finite number");
        return parsed;
    }

    private static boolean looksNumeric(String value) {
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
