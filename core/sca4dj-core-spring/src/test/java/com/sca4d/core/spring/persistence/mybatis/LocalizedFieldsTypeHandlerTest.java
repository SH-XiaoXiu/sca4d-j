package com.sca4d.core.spring.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.i18n.LocalizedFields;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;

/** 验证 LocalizedFieldsTypeHandler 把多语言字段以 JSON 文本写入 JDBC OTHER 参数，并从结果集读回后按语言和字段名查找。 */
class LocalizedFieldsTypeHandlerTest {

    @Test
    @DisplayName("Writes And Reads Localized Fields As Jdbc Other")
    void writesAndReadsLocalizedFieldsAsJdbcOther() throws Exception {
        LocalizedFieldsTypeHandler handler = new LocalizedFieldsTypeHandler(new ObjectMapper());
        LocalizedFields fields = LocalizedFields.of(
                Map.of(
                        Locale.forLanguageTag("zh-CN"),
                        Map.of("name", "管理员")));
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("i18n")).thenReturn("{\"zh-CN\":{\"name\":\"管理员\"}}");

        handler.setNonNullParameter(statement, 1, fields, JdbcType.OTHER);
        LocalizedFields decoded = handler.getNullableResult(resultSet, "i18n");

        verify(statement).setObject(
                1,
                "{\"zh-CN\":{\"name\":\"管理员\"}}",
                JdbcType.OTHER.TYPE_CODE);
        assertThat(decoded.find(Locale.forLanguageTag("zh-CN"), "name")).contains("管理员");
    }
}
