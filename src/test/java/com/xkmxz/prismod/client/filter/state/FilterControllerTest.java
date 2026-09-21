package com.xkmxz.prismod.client.filter.state;

import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterKey;
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
        assertEquals(FilterKey.of(FilterId.ORIGINAL), controller.effectiveSelection().key());
        assertEquals(controller.selectedSelection(), controller.effectiveSelection());
        assertFalse(controller.isForced());
        assertTrue(controller.isRenderAvailable());
    }

    @Test
    void forcedFilterOverridesDisabledUserConfig() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.refreshConfig(false, FilterId.defaultOrder(), Map.of(FilterId.COOL, 0.1F));
        controller.setForced(FilterId.COOL, 0.75F);
        assertEquals(FilterKey.of(FilterId.COOL), controller.effectiveSelection().key());
        assertEquals(0.75F, controller.effectiveSelection().strength());
        assertTrue(controller.effectiveSelection().forced());
        assertEquals(FilterKey.of(FilterId.WARM), controller.selectedSelection().key());
    }

    @Test
    void clearingOverrideRestoresLatestUserStrength() {
        FilterController controller = new FilterController();
        controller.select(FilterId.VINTAGE);
        controller.setForced(FilterId.NIGHT_VISION, 0.9F);
        controller.refreshConfig(true, FilterId.defaultOrder(), Map.of(FilterId.VINTAGE, 0.25F));
        assertEquals(0.9F, controller.effectiveSelection().strength());
        controller.clearForced();
        assertEquals(FilterKey.of(FilterId.VINTAGE), controller.effectiveSelection().key());
        assertEquals(0.25F, controller.effectiveSelection().strength());
        assertFalse(controller.effectiveSelection().forced());
    }

    @Test
    void clearingOverrideRespectsDisabledSettingAndKeepsSelection() {
        FilterController controller = new FilterController();
        controller.select(FilterId.GRAYSCALE);
        controller.refreshConfig(false, FilterId.defaultOrder(), Map.of());
        controller.setForced(FilterId.COOL, 0.5F);
        controller.clearForced();
        assertEquals(FilterKey.of(FilterId.ORIGINAL), controller.effectiveSelection().key());
        assertEquals(FilterKey.of(FilterId.GRAYSCALE), controller.selectedSelection().key());
    }

    @Test
    void selectedStrengthChangesImmediatelyOnConfigRefresh() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.refreshConfig(true, FilterId.defaultOrder(), Map.of(FilterId.WARM, 0.125F));
        assertEquals(0.125F, controller.effectiveSelection().strength());
        assertEquals(0.125F, controller.selectedSelection().strength());
    }

    @Test
    void newOverrideReplacesPreviousOverrideInsteadOfStacking() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.setForced(FilterId.COOL, 0.2F);
        controller.setForced(FilterId.VINTAGE, 0.8F);
        assertEquals(FilterKey.of(FilterId.VINTAGE), controller.effectiveSelection().key());
        assertEquals(0.8F, controller.effectiveSelection().strength());
        controller.clearForced();
        assertEquals(FilterKey.of(FilterId.WARM), controller.effectiveSelection().key());
    }

    @Test
    void cycleDoesNotAlterSelectionDuringForcedOverride() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.setForced(FilterId.COOL, 0.5F);
        controller.cycle();
        controller.clearForced();
        assertEquals(FilterKey.of(FilterId.WARM), controller.effectiveSelection().key());
    }

    @Test
    void customCycleOrderWrapsFromLastItemToFirst() {
        FilterController controller = new FilterController();
        List<FilterId> order = List.of(FilterId.WARM, FilterId.COOL, FilterId.ORIGINAL,
                FilterId.VINTAGE, FilterId.GRAYSCALE, FilterId.NIGHT_VISION);
        controller.refreshConfig(true, order, Map.of());
        controller.select(FilterId.NIGHT_VISION);
        controller.cycle();
        assertEquals(FilterKey.of(FilterId.WARM), controller.effectiveSelection().key());
        controller.cycle();
        assertEquals(FilterKey.of(FilterId.COOL), controller.effectiveSelection().key());
    }

    @Test
    void invalidInternalOrderStillLeavesCycleUsable() {
        FilterController controller = new FilterController();
        controller.refreshConfig(true, List.of(FilterId.ORIGINAL), Map.of());
        controller.cycle();
        assertEquals(FilterKey.of(FilterId.GRAYSCALE), controller.effectiveSelection().key());
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
        assertEquals(0.25F, controller.effectiveSelection().strength());
        controller.cycle();
        assertEquals(FilterKey.of(FilterId.COOL), controller.effectiveSelection().key());
    }

    @Test
    void renderFailureReportsOriginalWhileRetainingForcedMarker() {
        FilterController controller = new FilterController();
        controller.setForced(FilterId.NIGHT_VISION, 0.7F);
        controller.setRenderAvailable(false);
        assertEquals(FilterKey.of(FilterId.ORIGINAL), controller.effectiveSelection().key());
        assertEquals(0.0F, controller.effectiveSelection().strength());
        assertTrue(controller.effectiveSelection().forced());
        assertFalse(controller.isRenderAvailable());
        controller.setRenderAvailable(true);
        assertEquals(FilterKey.of(FilterId.NIGHT_VISION), controller.effectiveSelection().key());
    }

    @Test
    void renderFailureAlsoBypassesOrdinarySelection() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        controller.setRenderAvailable(false);
        assertEquals(FilterKey.of(FilterId.ORIGINAL), controller.effectiveSelection().key());
        assertEquals(FilterKey.of(FilterId.WARM), controller.selectedSelection().key());
    }

    @Test
    void leavingWorldClearsOverrideWithoutLosingUserChoice() {
        FilterController controller = new FilterController();
        controller.select(FilterId.COOL);
        controller.setForced(FilterId.GRAYSCALE, 0.4F);
        controller.resetSession();
        assertFalse(controller.isForced());
        assertEquals(FilterKey.of(FilterId.COOL), controller.effectiveSelection().key());
    }

    @Test
    void oldSnapshotsRemainUnchangedAfterFurtherSelections() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        FilterSelection old = controller.effectiveSelection();
        controller.setForced(FilterId.COOL, 0.6F);
        assertEquals(FilterKey.of(FilterId.WARM), old.key());
        assertEquals(1.0F, old.strength());
        assertNotEquals(old, controller.effectiveSelection());
    }

    @Test
    void invalidConfiguredStrengthCannotReachShaderState() {
        FilterController controller = new FilterController();
        controller.select(FilterId.VINTAGE);
        controller.refreshConfig(true, FilterId.defaultOrder(), Map.of(FilterId.VINTAGE, Double.NaN));
        assertEquals(0.0F, controller.effectiveSelection().strength());
    }

    @Test
    void hiddenFilterIsSkippedByCycleAndFallsBackToOriginal() {
        FilterController controller = new FilterController();
        controller.select(FilterId.WARM);
        Set<FilterKey> visible = Set.of(FilterKey.of(FilterId.ORIGINAL), FilterKey.of(FilterId.COOL));
        controller.refreshDynamicConfig(true, FilterId.defaultOrder().stream().map(FilterKey::of).toList(), Map.of(), visible);
        assertEquals(FilterKey.of(FilterId.ORIGINAL), controller.effectiveSelection().key());
        controller.select(FilterId.ORIGINAL);
        controller.cycle();
        assertEquals(FilterKey.of(FilterId.COOL), controller.effectiveSelection().key());
    }

    @Test
    @Timeout(5)
    void backgroundReadsSeeCompleteImmutableSelectionsWithoutGameRuntime() throws InterruptedException {
        FilterController controller = new FilterController();
        FilterSelection warm = new FilterSelection(FilterKey.of(FilterId.WARM), 0.25F, true);
        FilterSelection cool = new FilterSelection(FilterKey.of(FilterId.COOL), 0.75F, true);
        controller.setForced(warm.key(), warm.strength());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            started.countDown();
            try {
                for (int i = 0; i < 25_000; i++) {
                    FilterSelection actual = controller.effectiveSelection();
                    assertTrue(actual.equals(warm) || actual.equals(cool));
                }
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "prismod-state-reader");
        reader.start();
        started.await();
        for (int i = 0; i < 10_000; i++) {
            FilterSelection next = i % 2 == 0 ? cool : warm;
            controller.setForced(next.key(), next.strength());
        }
        reader.join();
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
    }
}
