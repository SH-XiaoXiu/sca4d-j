package com.sca4d.core.api.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class LocalizedFieldsTest {

    private static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");

    private static final Locale EN_US = Locale.forLanguageTag("en-US");

    private final LocaleCatalog locales = new LocaleCatalog(List.of(ZH_CN, EN_US), ZH_CN);

    @Test
    @DisplayName("Copies Values And Uses Bcp 47 Json Keys")
    void copiesValuesAndUsesBcp47JsonKeys() throws Exception {
        Map<String, String> chinese = new LinkedHashMap<>();
        chinese.put("name", "平台管理员");
        Map<Locale, Map<String, String>> values = new LinkedHashMap<>();
        values.put(ZH_CN, chinese);

        LocalizedFields fields = LocalizedFields.of(values);
        chinese.put("name", "已修改");
        values.clear();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(fields);
        LocalizedFields decoded = mapper.readValue(json, LocalizedFields.class);

        assertEquals("{\"zh-CN\":{\"name\":\"平台管理员\"}}", json);
        assertEquals("平台管理员", decoded.find(ZH_CN, "name").orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> overwriteName(decoded.fields(ZH_CN)));
    }

    @Test
    @DisplayName("Rejects Invalid Locale Key And Text")
    void rejectsInvalidLocaleKeyAndText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizedFields.fromJson(Map.of("", Map.of("name", "名称"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizedFields.of(Map.of(ZH_CN, Map.of("", "名称"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizedFields.of(Map.of(ZH_CN, Map.of("name", " "))));
    }

    @Test
    @DisplayName("Accepts Arbitrary Locales And Validates Default Required Allowed And Length Rules")
    void acceptsArbitraryLocalesAndValidatesDefaultRequiredAllowedAndLengthRules() {
        LocalizedFieldsSchema schema = new LocalizedFieldsSchema(
                Set.of("name"),
                Set.of("name"),
                Map.of("name", 8));

        LocalizedFields valid = LocalizedFields.of(
                Map.of(
                        ZH_CN,
                        Map.of("name", "管理员"),
                        EN_US,
                        Map.of("name", "Admin"),
                        Locale.FRANCE,
                        Map.of("name", "Admin")));

        LocalizedFields empty = LocalizedFields.of(Map.of());
        assertSame(valid, schema.validate(valid));
        assertSame(empty, schema.validate(empty));
        assertSame(valid, schema.validate(valid, locales.defaultLocale()));
        assertMessageContains(
                assertThrows(
                        IllegalArgumentException.class,
                        () -> schema.validate(
                                LocalizedFields.of(Map.of(EN_US, Map.of("name", "Admin"))),
                                locales.defaultLocale())),
                "默认 Locale");
        assertMessageContains(
                assertThrows(
                        IllegalArgumentException.class,
                        () -> schema.validate(
                                LocalizedFields.of(Map.of(ZH_CN, Map.of("description", "说明"))),
                                locales.defaultLocale())),
                "未声明");
        assertMessageContains(
                assertThrows(
                        IllegalArgumentException.class,
                        () -> schema.validate(
                                LocalizedFields.of(Map.of(ZH_CN, Map.of("name", "123456789"))),
                                locales.defaultLocale())),
                "最大长度");
    }

    /** 检查异常消息包含能够定位失败规则的文本。 */
    private static void assertMessageContains(IllegalArgumentException exception, String expected) {
        assertTrue(exception.getMessage().contains(expected), exception::getMessage);
    }

    /** 尝试修改读取结果，供不可变性断言观察实际异常。 */
    private static void overwriteName(Map<String, String> fields) {
        fields.put("name", "修改");
    }
}
