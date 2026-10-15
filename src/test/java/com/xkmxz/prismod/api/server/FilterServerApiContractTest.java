package com.xkmxz.prismod.api.server;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterServerApiContractTest {
    @Test
    void exposesOnlySubmitAndQuery() {
        var methods = Arrays.stream(FilterServerApi.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).toList();
        assertEquals(Set.of("submit", "query"), methods.stream()
                .map(java.lang.reflect.Method::getName).collect(Collectors.toSet()));
        assertEquals(2, methods.size());
    }
}
