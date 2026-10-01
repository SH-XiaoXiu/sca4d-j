package com.sca4d.core.spring.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.id.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("Validates Node Range")
    void validatesNodeRange() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024));
    }

    @Test
    @DisplayName("Generates Ordered IDs And Separates Nodes")
    void generatesOrderedIdsAndSeparatesNodes() {
        SnowflakeIdGenerator firstNode = new SnowflakeIdGenerator(7);
        SnowflakeIdGenerator secondNode = new SnowflakeIdGenerator(8);
        Id first = firstNode.nextId();
        Id second = firstNode.nextId();

        assertTrue(first.isGreaterThan(1));
        assertTrue(second.isGreaterThan(first));
        assertFalse(secondNode.nextId().isEqualTo(first));
    }

    @Test
    @DisplayName("Generates Unique IDs Concurrently")
    void generatesUniqueIdsConcurrently() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(9);
        Set<Id> ids = ConcurrentHashMap.newKeySet();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 8_000; index++) {
                executor.submit(() -> ids.add(generator.nextId()));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(8_000, ids.size());
    }
}
