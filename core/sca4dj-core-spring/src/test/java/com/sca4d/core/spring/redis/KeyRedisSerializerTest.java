package com.sca4d.core.spring.redis;

import static org.assertj.core.api.Assertions.*;

import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 验证 Key 序列化结果带当前 App 前缀，声明 hash tag 的 Key 进入同一 Redis Cluster 槽，并拒绝其他 App 前缀、伪造 hash tag 和非规范格式的输入。 */
class KeyRedisSerializerTest {

    /** 在序列化设施创建前固定当前测试进程的 App 名。 */
    @BeforeAll
    static void initializeAppName() {
        AppName.publish("sca4d-test");
    }

    @Test
    void prefixesKeysAndKeepsLuaKeysInTheDeclaredSlot() {
        KeyRedisSerializer serializer = new KeyRedisSerializer(List.of(new RedisHashTag(TreeCode.of("IOT"))));
        Key session = Key.of(TreeCode.of("AUTH.SESSION"), 4L);
        assertThat(new String(serializer.serialize(session), StandardCharsets.UTF_8))
                .isEqualTo("SCA4D_TEST:AUTH:SESSION:4");
        Key connection = Key.of(TreeCode.of("IOT.CONNECTION"), "a:b");
        assertThat(new String(serializer.serialize(connection), StandardCharsets.UTF_8))
                .isEqualTo("SCA4D_TEST:{SCA4D_TEST:IOT}:IOT:CONNECTION:V_613A62");
        assertThat(serializer.deserialize(serializer.serialize(connection))).isEqualTo(connection);
        assertThat(serializer.deserialize(serializer.serialize(session))).isEqualTo(session);
    }

    @Test
    void rejectsForeignPrefixesForgedSlotsAndNoncanonicalValues() {
        KeyRedisSerializer serializer = new KeyRedisSerializer(List.of(new RedisHashTag(TreeCode.of("IOT"))));
        assertThatThrownBy(() -> serializer.deserialize("OTHER:AUTH:SESSION:4".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> serializer
                        .deserialize("SCA4D_TEST:{OTHER:IOT}:IOT:CONNECTION:4".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.deserialize("SCA4D_TEST:IOT:CONNECTION:4".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(serializer.serialize(null)).isNull();
        assertThat(serializer.deserialize(null)).isNull();
    }
}
