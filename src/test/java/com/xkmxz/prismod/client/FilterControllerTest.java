package com.xkmxz.prismod.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FilterControllerTest {
    @Test
    void startsWithOnlyOriginalSelected() {
        FilterController controller = new FilterController();
        assertEquals(FilterId.ORIGINAL, controller.effectiveState().id());
        assertEquals(controller.selectedState(), controller.effectiveState());
        assertFalse(controller.isForced());
    }

    @Test
    void forcedFilterOverridesDisabledUserConfig() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.refreshConfig(false, FilterId.defaultOrder(), Map.of(FilterId.COOL, 0.1F));
        controller.setForced(FilterId.COOL, 0.75F);
        assertEquals(new FilterState(FilterId.COOL, 0.75F, true), controller.effectiveState());
        assertEquals(FilterId.WARM, controller.selectedState().id());
    }

    @Test
    void clearingOverrideRestoresLatestUserStrength() {
        FilterController controller = new FilterController();
        controller.select(FilterId.VINTAGE);
        controller.setForced(FilterId.NIGHT_VISION, 0.9F);
        controller.refreshConfig(true, FilterId.defaultOrder(), Map.of(FilterId.VINTAGE, 0.25F));
        assertEquals(0.9F, controller.effectiveState().strength());
        controller.clearForced();
        assertEquals(new FilterState(FilterId.VINTAGE, 0.25F, false), controller.effectiveState());
    }

    @Test
    void clearingOverrideRespectsDisabledSettingAndKeepsSelection() {
        FilterController controller = new FilterController();
        controller.select(FilterId.GRAYSCALE);
        controller.refreshConfig(false, FilterId.defaultOrder(), Map.of());
        controller.setForced(FilterId.COOL, 0.5F);
        controller.clearForced();
        assertEquals(new FilterState(FilterId.ORIGINAL, 0.0F, false), controller.effectiveState());
        assertEquals(FilterId.GRAYSCALE, controller.selectedState().id());
    }

    @Test
    void selectedStrengthChangesImmediatelyOnConfigRefresh() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.refreshConfig(true, FilterId.defaultOrder(), Map.of(FilterId.WARM, 0.125F));
        assertEquals(0.125F, controller.effectiveState().strength());
        assertEquals(0.125F, controller.selectedState().strength());
    }

    @Test
    void newOverrideReplacesPreviousOverrideInsteadOfStacking() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.setForced(FilterId.COOL, 0.2F);
        controller.setForced(FilterId.VINTAGE, 0.8F);
        assertEquals(new FilterState(FilterId.VINTAGE, 0.8F, true), controller.effectiveState());
        controller.clearForced();
        assertEquals(FilterId.WARM, controller.effectiveState().id());
    }

    @Test
    void cycleDoesNotAlterSelectionDuringForcedOverride() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.setForced(FilterId.COOL, 0.5F);
        controller.cycle();
        controller.clearForced();
        assertEquals(FilterId.WARM, controller.effectiveState().id());
    }

    @Test
    void customCycleOrderWrapsFromLastItemToFirst() {
        FilterController controller = new FilterController();
        List<FilterId> order = List.of(FilterId.WARM, FilterId.COOL, FilterId.ORIGINAL,
                FilterId.VINTAGE, FilterId.GRAYSCALE, FilterId.NIGHT_VISION);
        controller.refreshConfig(true, order, Map.of());
        controller.select(FilterId.NIGHT_VISION);
        controller.cycle();
        assertEquals(FilterId.WARM, controller.effectiveState().id());
        controller.cycle();
        assertEquals(FilterId.COOL, controller.effectiveState().id());
    }

    @Test
    void invalidInternalOrderStillLeavesCycleUsable() {
        FilterController controller = new FilterController();
        controller.refreshConfig(true, List.of(FilterId.ORIGINAL), Map.of());
        controller.cycle();
        assertEquals(FilterId.GRAYSCALE, controller.effectiveState().id());
    }

    @Test
    void configRefreshDefensivelyCopiesCallerOwnedData() {
        FilterController controller = new FilterController();
        List<FilterId> order = new ArrayList<>(FilterId.defaultOrder());
        EnumMap<FilterId, Float> strengths = new EnumMap<>(FilterId.class);
        strengths.put(FilterId.WARM, 0.25F);
        controller.refreshConfig(true, order, strengths);
        order.clear();
        strengths.put(FilterId.WARM, 0.9F);
        controller.select(FilterId.WARM);
        assertEquals(0.25F, controller.effectiveState().strength());
        controller.cycle();
        assertEquals(FilterId.COOL, controller.effectiveState().id());
    }

    @Test
    void renderFailureReportsOriginalWhileRetainingForcedMarker() {
        FilterController controller = new FilterController();
        controller.setForced(FilterId.NIGHT_VISION, 0.7F);
        controller.setRenderAvailable(false);
        assertEquals(new FilterState(FilterId.ORIGINAL, 0.0F, true), controller.effectiveState());
        assertTrue(controller.isForced());
        controller.setRenderAvailable(true);
        assertEquals(new FilterState(FilterId.NIGHT_VISION, 0.7F, true), controller.effectiveState());
    }

    @Test
    void renderFailureAlsoBypassesOrdinarySelection() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.setRenderAvailable(false);
        assertEquals(new FilterState(FilterId.ORIGINAL, 0.0F, false), controller.effectiveState());
        assertEquals(FilterId.WARM, controller.selectedState().id());
    }

    @Test
    void leavingWorldClearsOverrideWithoutLosingUserChoice() {
        FilterController controller = new FilterController();
        controller.select(FilterId.COOL);
        controller.setForced(FilterId.GRAYSCALE, 0.4F);
        controller.resetSession();
        assertFalse(controller.isForced());
        assertEquals(FilterId.COOL, controller.effectiveState().id());
    }

    @Test
    void oldSnapshotsRemainUnchangedAfterFurtherSelections() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        FilterState old = controller.effectiveState();
        controller.setForced(FilterId.COOL, 0.6F);
        assertEquals(new FilterState(FilterId.WARM, 1.0F, false), old);
        assertNotEquals(old, controller.effectiveState());
    }

    @Test
    void invalidConfiguredStrengthCannotReachShaderState() {
        FilterController controller = new FilterController();
        controller.select(FilterId.VINTAGE);
        controller.refreshConfig(true, FilterId.defaultOrder(), Map.of(FilterId.VINTAGE, Double.NaN));
        assertEquals(0.0F, controller.effectiveState().strength());
    }

    @Test
    void hiddenFilterIsSkippedByCycleAndFallsBackToOriginal() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        Set<FilterKey> visible = Set.of(FilterKey.of(FilterId.ORIGINAL), FilterKey.of(FilterId.COOL));
        controller.refreshDynamicConfig(true, FilterId.defaultOrder().stream().map(FilterKey::of).toList(), Map.of(), visible);
        assertEquals(FilterId.ORIGINAL, controller.effectiveState().id());
        controller.select(FilterId.ORIGINAL);
        controller.cycle();
        assertEquals(FilterId.COOL, controller.effectiveState().id());
    }

    @Test
    @Timeout(5)
    void backgroundReadsSeeCompleteImmutableStatesWithoutGameRuntime() throws InterruptedException {
        FilterController controller = new FilterController();
        FilterState warm = new FilterState(FilterId.WARM, 0.25F, true);
        FilterState cool = new FilterState(FilterId.COOL, 0.75F, true);
        controller.setForced(warm.id(), warm.strength());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            started.countDown();
            try {
                for (int i = 0; i < 25_000; i++) {
                    FilterState actual = controller.effectiveState();
                    assertTrue(actual.equals(warm) || actual.equals(cool));
                }
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "prismod-state-reader");
        reader.start();
        started.await();
        for (int i = 0; i < 10_000; i++) {
            FilterState next = i % 2 == 0 ? cool : warm;
            controller.setForced(next.id(), next.strength());
        }
        reader.join();
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
    }
}
