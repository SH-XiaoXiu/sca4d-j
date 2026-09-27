package com.sca4d.core.api.setting;

import static org.junit.jupiter.api.Assertions.*;

import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.codec.SettingCodec;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/** 验证实际取值、Codec 版本、所属及覆盖行为，不依赖 Spring 或存储实现。 */
class SettingTest {
    private static final TreeCode CODE = TreeCode.of("TEST:NAME");

    private static final SettingOwner ACCOUNT = new SettingOwner(TreeCode.of("ACCOUNT"), "17");

    private static final SettingCodec<String> TEXT = SettingCodec.text(40);

    @Test
    void selectsFirstPresentValueAndRecordsInheritedOwner() {
        TestStore account = new TestStore("ACCOUNT");
        TestStore system = new TestStore("SYSTEM");
        system.values.put(SettingOwner.system(), "inherited");
        Setting<String> setting = Setting.builder(CODE, TEXT)
                .from(account)
                .inherit(system, SettingOwner.system())
                .defaultValue("default")
                .build();
        assertEquals("inherited", setting.get(ACCOUNT));
        assertEquals(SettingOwner.system(), setting.inspect(ACCOUNT).origins().get(1).owner());
        assertTrue(setting.inspect(ACCOUNT).revision().isEmpty());
        account.values.put(ACCOUNT, "own");
        assertEquals("own", setting.get(ACCOUNT));
        assertThrows(IllegalStateException.class, setting::get);
    }

    @Test
    void rejectsInvalidPriorityValueRatherThanFallingBack() {
        TestStore store = new TestStore("INVALID");
        store.values.put(ACCOUNT, " ");
        var setting = Setting.builder(CODE, TEXT).from(store).defaultValue("default").build();
        assertThrows(IllegalArgumentException.class, () -> setting.get(ACCOUNT));
    }

    @Test
    void failsWhenRequiredSettingHasNoValueOrFixedOwnerDoesNotMatch() {
        var setting = Setting.builder(CODE, TEXT).from(new TestStore("EMPTY")).owner(SettingOwner.system()).build();
        assertThrows(IllegalStateException.class, setting::get);
        assertThrows(IllegalArgumentException.class, () -> setting.get(ACCOUNT));
    }

    @Test
    void writesOnlyDeclaredTargetAndResetRestoresInheritance() {
        TestStore target = new TestStore("TARGET");
        TestStore parent = new TestStore("PARENT");
        parent.values.put(SettingOwner.system(), "parent");
        var setting = Setting.builder(CODE, TEXT)
                .from(target)
                .inherit(parent, SettingOwner.system())
                .writeTo(target)
                .buildWritable();
        setting.replace(ACCOUNT, "own");
        assertEquals("own", setting.get(ACCOUNT));
        assertEquals("parent", parent.values.get(SettingOwner.system()));
        assertEquals("parent", setting.reset(ACCOUNT).value());
    }

    @Test
    void selectsStoredCodecVersionAndWritesCurrentVersionWithoutImplicitMigration() {
        TestStore store = new TestStore("VERSIONS");
        store.values.put(ACCOUNT, "old");
        VersionedCodec<String, String> current = new VersionedCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return TEXT.code();
            }

            @Override
            public int version() {
                return 2;
            }

            @Override
            public @NotNull Class<String> valueType() {
                return String.class;
            }

            @Override
            public @NotNull String encode(@NotNull String value) {
                return "v2:" + value;
            }

            @Override
            public @NotNull String decode(@NotNull String wire) {
                if (!wire.startsWith("v2:"))
                    throw new IllegalArgumentException("缺少 V2 前缀");
                return wire.substring(3);
            }
        };
        var setting = Setting.builder(CODE, current).readVersion(TEXT).from(store).writeTo(store).buildWritable();
        assertEquals("old", setting.get(ACCOUNT));
        assertEquals("old", store.values.get(ACCOUNT));
        setting.replace(ACCOUNT, "new");
        assertEquals("v2:new", store.values.get(ACCOUNT));
        assertEquals(2, store.codecVersion);
        store.codecVersion = 99;
        assertThrows(IllegalArgumentException.class, () -> setting.get(ACCOUNT));
    }

    @Test
    void appliesPlainPatchDataAndValidatesFinalValue() {
        TestStore patches = new TestStore("PATCH");
        VersionedCodec<Change, String> codec = new VersionedCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("TEST:CHANGE");
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public @NotNull Class<Change> valueType() {
                return Change.class;
            }

            @Override
            public @NotNull String encode(@NotNull Change change) {
                return change.name.isIgnore() ? "IGNORE" : change.name.isClear() ? "CLEAR" : change.name.value();
            }

            @Override
            public @NotNull Change decode(@NotNull String wire) {
                return new Change(
                        wire.equals("IGNORE")
                                ? Patch.ignore()
                                : wire.equals("CLEAR") ? Patch.clear() : Patch.set(wire));
            }
        };
        var setting = Setting.builder(CODE, TEXT).defaultValue("base").overlay(patches, codec, Change::apply).build();
        patches.values.put(ACCOUNT, "IGNORE");
        assertEquals("base", setting.get(ACCOUNT));
        patches.values.put(ACCOUNT, "replacement");
        assertEquals("replacement", setting.get(ACCOUNT));
        patches.values.put(ACCOUNT, "CLEAR");
        assertThrows(IllegalArgumentException.class, () -> setting.get(ACCOUNT));
    }

    /** 测试中的普通修改数据，直接使用已有 Patch 表达字段意图。 */
    private static final class Change {
        private final Patch<String> name;

        private Change(Patch<String> name) {
            this.name = name;
        }

        /** CLEAR 清空字段，由设置 Codec 决定空字段是否合法。 */
        private static String apply(Change change, String value) {
            return change.name.isIgnore() ? value : change.name.isClear() ? "" : change.name.value();
        }
    }

    /** 保存测试值并记录实际写入 Codec 版本，读取不修改内容。 */
    private static final class TestStore implements SettingReader<String>, SettingWriter<String> {
        private final TreeCode code;

        private final Map<SettingOwner, String> values = new HashMap<>();

        private int codecVersion = 1;

        private TestStore(String name) {
            code = TreeCode.of("TEST:" + name);
        }

        @Override
        public @NotNull TreeCode code() {
            return code;
        }

        @Override
        public @NotNull SettingValue<String> read(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
            return SettingValue.read(code, setting, owner, values.get(owner), codecVersion, new Version(0), false);
        }

        @Override
        public void replace(
                @NotNull TreeCode setting,
                @NotNull SettingOwner owner,
                @NotNull String value,
                int codecVersion,
                boolean secret,
                @NotNull Version expected) {
            values.put(owner, value);
            this.codecVersion = codecVersion;
        }

        @Override
        public void reset(
                @NotNull TreeCode setting,
                @NotNull SettingOwner owner,
                int codecVersion,
                @NotNull Version expected) {
            values.remove(owner);
        }

        @Override
        public void erase(@NotNull TreeCode setting, @NotNull SettingOwner owner) {
            values.remove(owner);
        }
    }
}
