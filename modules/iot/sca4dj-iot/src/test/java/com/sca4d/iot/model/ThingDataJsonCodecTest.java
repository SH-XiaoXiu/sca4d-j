package com.sca4d.iot.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.iot.api.model.ThingObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/** 验证用于 reported 摘要和幂等判断的 Thing data JSON 不受 Map 插入顺序影响。 */
class ThingDataJsonCodecTest {

    private final ThingDataJsonCodec codec = new ThingDataJsonCodec(new ObjectMapper());

    @Test
    @DisplayName("递归按 code 排序 object 字段")
    void sortsObjectFieldsRecursively() {
        LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
        nested.put("z", 2L);
        nested.put("a", 1L);
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("temperature", 26L);
        values.put("details", new ThingObjectValue(nested));

        assertThat(codec.encodeMap(values))
                .isEqualTo("{\"details\":{\"a\":1,\"z\":2},\"temperature\":26}");
        assertThat(
                codec.encodeMap(
                        Map.of(
                                "details",
                                new ThingObjectValue(Map.of("a", 1L, "z", 2L)),
                                "temperature",
                                26L)))
                .isEqualTo(codec.encodeMap(values));
    }
}
