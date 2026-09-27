package com.xkmxz.prismod.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CommonApiDependencyTest {
    @Test
    void commonAndServerSourcesDoNotReferenceClientOnlyPackages() throws IOException {
        Path root = Path.of("src/main/java/com/xkmxz/prismod");
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/api/common/")
                            || path.toString().replace('\\', '/').contains("/api/server/")
                            || path.toString().replace('\\', '/').contains("/server/"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            assertFalse(source.contains("net.minecraft.client"), path.toString());
                            assertFalse(source.contains("com.xkmxz.prismod.api.client"), path.toString());
                            assertFalse(source.contains("com.xkmxz.prismod.client."), path.toString());
                        } catch (IOException error) {
                            throw new RuntimeException(error);
                        }
                    });
        }
    }
}
