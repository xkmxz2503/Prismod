package com.xkmxz.prismod.api.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterClientApiContractTest {
    @Test
    void exposesOnlySubmitAndQueryAsBusinessEntrypoints() {
        Set<String> methods = Arrays.stream(FilterClientApi.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("submit", "query"), methods);
        assertEquals(2, Arrays.stream(FilterClientApi.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).count());
    }
}
