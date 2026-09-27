package com.sca4d.core.api.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Optional;

class VersionedCodecTest {

    private static final TreeCode CODE = TreeCode.of("TEST/VALUE");

    @Test
    @DisplayName("按 wire 版本选择读取 Codec，并使用当前 Codec 写入")
    void selectsVersionAndWritesCurrentCodec() {
        VersionedCodec<String, String> v1 = codec(1);
        VersionedCodec<String, String> v2 = codec(2);
        VersionedCodecCatalog<String, String> catalog = new VersionedCodecCatalog<>(
                v2,
                List.of(v1, v2));
        VersionedCodecAdapter<String, String> adapter = new VersionedCodecAdapter<>(
                catalog,
                wire -> Integer.parseInt(wire.substring(1, 2)));

        assertEquals("v2:value", adapter.encode("value"));
        assertEquals("value", adapter.decode("v1:value"));
        assertEquals("value", adapter.decodeAt(1, "v1:value"));
        assertEquals(Optional.empty(), catalog.find(3));
    }

    @Test
    @DisplayName("按相邻版本步骤执行完整迁移链")
    void migratesOneVersionAtATime() {
        VersionedCodec<String, String> v1 = codec(1);
        VersionedCodec<String, String> v2 = codec(2);
        VersionedCodec<String, String> v3 = codec(3);
        VersionedCodecMigrator<String> migrator = new VersionedCodecMigrator<>(
                List.of(
                        new CodecMigrationStep<>(v1, v2),
                        new CodecMigrationStep<>(v2, v3)));

        assertEquals("v3:value", migrator.migrate(1, "v1:value", 3));
        assertEquals("v2:value", migrator.migrate(2, "v2:value", 2));
    }

    @Test
    @DisplayName("拒绝重复版本、缺少中间步骤和不支持版本")
    void rejectsInvalidVersionConfiguration() {
        VersionedCodec<String, String> v1 = codec(1);
        VersionedCodec<String, String> v2 = codec(2);

        assertMessageContains(
                () -> new VersionedCodecCatalog<>(v1, List.of(v1, codec(1))),
                "重复");
        assertMessageContains(
                () -> new VersionedCodecCatalog<>(v1, List.of(v2)),
                "当前 Codec");
        assertMessageContains(
                () -> new VersionedCodecMigrator<>(List.of(new CodecMigrationStep<>(v1, v2)))
                        .migrate(1, "v1:value", 3),
                "缺少迁移步骤");
        assertMessageContains(
                () -> new VersionedCodecMigrator<>(List.of(new GapMigration(1, 3))),
                "相邻");
    }

    /** 执行预期抛出非法参数异常的操作，并检查异常文案包含指定片段。 */
    private static void assertMessageContains(Executable action, String expected) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertTrue(exception.getMessage().contains(expected));
    }

    private static VersionedCodec<String, String> codec(int version) {
        return new VersionedCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return CODE;
            }

            @Override
            public int version() {
                return version;
            }

            @Override
            public @NotNull Class<String> valueType() {
                return String.class;
            }

            @Override
            public @NotNull String encode(@NotNull String value) {
                return "v" + version + ":" + value;
            }

            @Override
            public @NotNull String decode(@NotNull String wire) {
                String prefix = "v" + version + ":";
                if (!wire.startsWith(prefix)) {
                    throw new IllegalArgumentException("wire 版本不匹配");
                }
                return wire.substring(prefix.length());
            }
        };
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class GapMigration implements Migratable<String> {
        private final int source;

        private final int target;

        private GapMigration(int source, int target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public int sourceVersion() {
            return source;
        }

        @Override
        public int targetVersion() {
            return target;
        }

        @Override
        public @NotNull String migrate(@NotNull String sourceWire) {
            return sourceWire;
        }
    }
}
