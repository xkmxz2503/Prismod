package com.xkmxz.prismod.client.filter.lut;

import org.junit.jupiter.api.Test;
import java.io.StringReader;
import static org.junit.jupiter.api.Assertions.*;

class LutCubeParserTest {
    private static String cube(int size, int points) {
        StringBuilder out = new StringBuilder("TITLE \\\"test\\\"\nLUT_3D_SIZE ").append(size).append('\n');
        for (int i = 0; i < points; i++) out.append("0 0 0\n");
        return out.toString();
    }

    @Test void parses32CubeAndDefaultsDomain() throws Exception {
        Lut3dData data = LutCubeParser.parse(new StringReader(cube(32, 32 * 32 * 32)));
        assertEquals(32, data.size());
        assertEquals(32768 * 3, data.rgb().length);
        assertArrayEquals(new float[] {0, 0, 0}, data.domainMin());
        assertArrayEquals(new float[] {1, 1, 1}, data.domainMax());
    }

    @Test void acceptsVariableSizeWithinSupportedRange() throws Exception {
        Lut3dData data = LutCubeParser.parse(new StringReader(cube(2, 8)));
        assertEquals(2, data.size());
        assertEquals(8, data.pointCount());
        assertEquals(4096, LutCubeParser.parse(new StringReader(cube(16, 4096))).pointCount());
        assertEquals(64, LutCubeParser.parse(new StringReader(cube(64, 64 * 64 * 64))).size());
    }

    @Test void rejectsWrongSizePointCountUnknownDirectiveAndNonFiniteValues() {
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader(cube(0, 0))));
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader(cube(65, 65 * 65 * 65))));
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader(cube(32, 1))));
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader("LUT_3D_SIZE 32\nUNKNOWN x\n")));
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader("LUT_3D_SIZE 32\nUNKNOWN 0 0\n")));
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader("LUT_3D_SIZE 32\nNaN 0 0\n")));
    }

    @Test void validatesDomainAndRange() {
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader("LUT_3D_SIZE 32\nDOMAIN_MIN 1 0 0\nDOMAIN_MAX 0 1 1\n")));
        assertThrows(Exception.class, () -> LutCubeParser.parse(new StringReader("LUT_3D_SIZE 32\n2 0 0\n")));
    }
}
