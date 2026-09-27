package com.sca4d.core.spring.setting;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.codec.JsonCodec;
import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.Setting;
import com.sca4d.core.api.setting.value.SettingOwner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;

/** 验证文件格式、owner 定位、条件版本和原子保存行为。 */
class FileSettingStoreTest {
    private static final TreeCode CODE = TreeCode.of("TEST:FILE");

    @TempDir
    Path root;

    private final YamlCodec<JsonNode> yaml = new YamlCodec<>(new JsonCodec<>() {
        @Override
        public @NotNull JsonNode encode(@NotNull JsonNode value) {
            return value.deepCopy();
        }

        @Override
        public @NotNull JsonNode decode(@NotNull JsonNode wire) {
            if (!wire.isObject())
                throw new IllegalArgumentException("配置必须是对象");
            return wire.deepCopy();
        }
    });

    private final VersionedCodec<JsonNode, JsonNode> domain = new VersionedCodec<>() {
        @Override
        public @NotNull TreeCode code() {
            return TreeCode.of("TEST:OBJECT");
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public @NotNull Class<JsonNode> valueType() {
            return JsonNode.class;
        }

        @Override
        public @NotNull JsonNode encode(@NotNull JsonNode value) {
            return value.deepCopy();
        }

        @Override
        public @NotNull JsonNode decode(@NotNull JsonNode value) {
            return value.deepCopy();
        }
    };

    @Test
    void savesAndResetsManagedFileWithMonotonicVersions() {
        FileSettingStore store = new FileSettingStore(
                root,
                (setting, owner) -> Path.of(owner.id() + ".yml"),
                yaml,
                new TestLock(),
                null);
        var setting = Setting.builder(CODE, domain)
                .from(store)
                .defaultValue(JsonNodeFactory.instance.objectNode())
                .writeTo(store)
                .buildWritable();
        var owner = SettingOwner.system();
        var first = setting.replace(owner, JsonNodeFactory.instance.objectNode().put("name", "first"));
        assertThat(first.revision().orElseThrow().value()).isEqualTo(1);
        assertThatThrownBy(() -> store.replace(CODE, owner, first.value(), 1, false, new Version(0)))
                .isInstanceOf(OptimisticLockException.class);
        assertThat(setting.get(owner).path("name").textValue()).isEqualTo("first");
        assertThat(setting.reset(owner).revision().orElseThrow().value()).isEqualTo(2);
        assertThat(setting.get(owner)).isEmpty();
        assertThat(setting.replace(owner, first.value()).revision().orElseThrow().value()).isEqualTo(3);
    }

    @Test
    void readsPlainYamlAndRejectsDuplicateKeysAndMultipleDocuments() throws Exception {
        Files.writeString(root.resolve("plugin.yml"), "name: demo\n");
        var reader = new FileSettingReader(root, (setting, owner) -> Path.of("plugin.yml"));
        var setting = Setting.builder(CODE, domain).from(reader, yaml).build();
        assertThat(setting.get(SettingOwner.system()).path("name").textValue()).isEqualTo("demo");
        assertThatThrownBy(() -> yaml.decode("name: a\nname: b\n".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> yaml.decode("name: a\n---\nname: b\n".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutsidePathAndOwnerCollisionWithoutOverwriting() {
        var reader = new FileSettingReader(root, (code, owner) -> Path.of("../outside.yml"));
        var setting = Setting.builder(CODE, domain).from(reader, yaml).build();
        assertThatThrownBy(() -> setting.get(SettingOwner.system())).isInstanceOf(IllegalArgumentException.class);
        var store = new FileSettingStore(root, (code, owner) -> Path.of("same.yml"), yaml, new TestLock(), null);
        var value = JsonNodeFactory.instance.objectNode().put("name", "system");
        store.replace(CODE, SettingOwner.system(), value, 1, false, new Version(0));
        assertThatThrownBy(() -> store.read(CODE, new SettingOwner(TreeCode.of("ACCOUNT"), "123")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.read(CODE, SettingOwner.system()).value()).isEqualTo(value);
    }

    @Test
    void rejectsOversizeWriteAndPreservesPreviousRevision() {
        var store = new FileSettingStore(root, (code, owner) -> Path.of("size.yml"), yaml, new TestLock(), null);
        var owner = SettingOwner.system();
        store.replace(CODE, owner, JsonNodeFactory.instance.objectNode().put("name", "old"), 1, false, new Version(0));
        assertThatThrownBy(
                () -> store.replace(
                        CODE,
                        owner,
                        JsonNodeFactory.instance.objectNode().put("large", "x".repeat(1_048_576)),
                        1,
                        false,
                        new Version(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.read(CODE, owner).revision().orElseThrow().value()).isEqualTo(1);
        assertThat(store.read(CODE, owner).value().path("name").textValue()).isEqualTo("old");
    }

    /** 仅在测试中以共享锁对象模拟两个存储实例的协调入口。 */
    private static final class TestLock implements DistributedLock {
        @Override
        public synchronized <T> T execute(LockKey key, Duration waitTime, Supplier<T> task) {
            return task.get();
        }
    }
}
