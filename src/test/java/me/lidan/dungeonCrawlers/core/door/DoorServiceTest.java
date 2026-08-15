package me.lidan.dungeonCrawlers.core.door;

import me.lidan.dungeonCrawlers.core.template.TemplateModels.Facing;
import me.lidan.dungeonCrawlers.core.template.TemplateModels.Point;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorServiceTest {
    @Test
    void everyOrientationHasNineBlocksAndLockedDoorDoesNotOpen() {
        for (Facing facing : Facing.values()) {
            DoorService service = new DoorService();
            UUID instance = UUID.randomUUID();
            Point center = new Point(10, 64, -4);
            service.register(instance, center, facing);
            assertEquals(9, service.info(instance).orElseThrow().blocks().size());
            assertTrue(service.blockAt(instance, center).isPresent());
            assertFalse(service.open(instance, () -> { }).opened());
            service.setReady(instance);
            assertTrue(service.open(instance, () -> { }).opened());
            assertEquals(DoorService.DoorState.OPEN, service.info(instance).orElseThrow().state());
        }
    }

    @Test
    void concurrentOpensInvokeCallbackOnceAndRepeatedOpenIsIdempotent() throws Exception {
        DoorService service = new DoorService();
        UUID instance = UUID.randomUUID();
        service.register(instance, new Point(0, 0, 0), Facing.NORTH);
        service.setReady(instance);
        AtomicInteger callbacks = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Thread first = new Thread(() -> open(service, instance, callbacks, start));
        Thread second = new Thread(() -> open(service, instance, callbacks, start));
        first.setDaemon(true); second.setDaemon(true);
        first.start(); second.start(); start.countDown();
        first.join(1_000); second.join(1_000);

        assertFalse(first.isAlive() || second.isAlive());
        assertEquals(1, callbacks.get());
        assertEquals(DoorService.DoorState.OPEN, service.info(instance).orElseThrow().state());
    }

    @Test
    void failedOpenCallbackRestoresReadyState() {
        DoorService service = new DoorService();
        UUID instance = UUID.randomUUID();
        service.register(instance, new Point(0, 0, 0), Facing.NORTH);
        service.setReady(instance);

        assertThrows(IllegalStateException.class, () -> service.open(instance,
                () -> { throw new IllegalStateException("render failed"); }));
        assertEquals(DoorService.DoorState.READY, service.info(instance).orElseThrow().state());
    }

    private static void open(DoorService service, UUID instance, AtomicInteger callbacks, CountDownLatch start) {
        try { start.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        service.open(instance, callbacks::incrementAndGet);
    }
}
