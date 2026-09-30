package com.sca4d.core.api.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Map;

/** 验证外部文本只在静态字段初始化时读取并固定。 */
class ConfiguredConstantsTest {

    @Test
    void publishesDefaultAndKeepsTheFieldFinal() throws NoSuchFieldException {
        Map<String, String> values = ConfiguredConstants.initialize(Defaults.class, ignored -> null);

        assertEquals("default-name", values.get("tests.config.defaults.name"));
        assertEquals("default-name", Defaults.NAME);
        assertTrue(Modifier.isFinal(Defaults.class.getDeclaredField("NAME").getModifiers()));
    }

    @Test
    void usesExternalValueAndRejectsLaterChanges() {
        Map<String, String> source = Map.of("tests.config.override.name", "external-name");
        ConfiguredConstants.initialize(Override.class, source::get);

        assertEquals("external-name", Override.NAME);
        assertEquals(
                "external-name",
                ConfiguredConstants.initialize(Override.class, source::get)
                        .get("tests.config.override.name"));
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ConfiguredConstants.initialize(Override.class, ignored -> "changed"));
        assertTrue(failure.getMessage().contains("不能在运行中改变"));
    }

    @Test
    void readsEnvironmentVariableFormFromPropertiesFiles() {
        Map<String, String> source = Map.of("TESTS_CONFIG_VARIABLE_NAME", "environment-name");

        ConfiguredConstants.initialize(Variable.class, source::get);

        assertEquals("environment-name", Variable.NAME);
    }

    @Test
    void rejectsConflictingPropertyAndEnvironmentVariable() {
        Map<String, String> source = Map.of(
                "tests.config.conflict.name",
                "canonical-name",
                "TESTS_CONFIG_CONFLICT_NAME",
                "different-name");

        assertThrows(
                ExceptionInInitializerError.class,
                () -> ConfiguredConstants.initialize(Conflict.class, source::get));
    }

    @Test
    void rejectsBlankOverrideWithoutFallingBackToDefault() {
        assertThrows(
                ExceptionInInitializerError.class,
                () -> ConfiguredConstants.initialize(Invalid.class, ignored -> "  "));
    }

    @Test
    void rejectsAccessBeforeConfigurationIsReady() {
        ExceptionInInitializerError failure = assertThrows(ExceptionInInitializerError.class, () -> {
            String ignored = Early.NAME;
        });
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    /** 声明没有外部覆盖值的字段。 */
    private static final class Defaults {
        private static final String NAME = ConfiguredConstants.text("tests.config.defaults.name", "default-name");
    }

    /** 声明存在外部覆盖值的字段。 */
    private static final class Override {
        private static final String NAME = ConfiguredConstants.text("tests.config.override.name", "default-name");
    }

    /** 模拟本地 env 文件中的大写下划线属性。 */
    private static final class Variable {
        private static final String NAME = ConfiguredConstants.text("tests.config.variable.name", "default-name");
    }

    /** 模拟两种配置来源声明了不同值。 */
    private static final class Conflict {
        private static final String NAME = ConfiguredConstants.text("tests.config.conflict.name", "default-name");
    }

    /** 声明非法外部覆盖值的字段。 */
    private static final class Invalid {
        private static final String NAME = ConfiguredConstants.text("tests.config.invalid.name", "default-name");
    }

    /** 模拟任何启动初始化之前的直接字段读取。 */
    private static final class Early {
        private static final String NAME = ConfiguredConstants.text("tests.config.early.name", "default-name");
    }
}
