package com.sca4d.core.api.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class IdempotencyFingerprintsTest {

    private final IdempotencyFingerprints fingerprints = new IdempotencyFingerprints(new ObjectMapper());

    @Test
    @DisplayName("对象字段顺序不影响规范化参数和摘要")
    void canonicalizesObjectFieldsRecursively() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", Map.of("second", 2, "first", 1));
        first.put("a", "value");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", "value");
        second.put("z", Map.of("first", 1, "second", 2));

        assertEquals(
                fingerprints.canonicalArguments(new Object[] {first}),
                fingerprints.canonicalArguments(new Object[] {second}));
        assertEquals(
                fingerprints.argumentsDigest(new Object[] {first}),
                fingerprints.argumentsDigest(new Object[] {second}));
    }

    @Test
    @DisplayName("数组元素顺序仍然属于幂等请求内容")
    void preservesArrayOrder() {
        assertNotEquals(
                fingerprints.argumentsDigest(new Object[] {List.of("first", "second")}),
                fingerprints.argumentsDigest(new Object[] {List.of("second", "first")}));
    }
}
