package com.sca4d.core.spring.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** 验证单值 MyBatis 类型处理器的公开转换行为。 */
class ValueTypeHandlerTest {

    @Test
    @DisplayName("Maps long values and preserves SQL null")
    void mapsLongValuesAndPreservesSqlNull() throws Exception {
        LongValueTypeHandler<Long> handler = new LongValueTypeHandler<>(Long::valueOf, Long::longValue);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSet nullResultSet = mock(ResultSet.class);
        CallableStatement callableStatement = mock(CallableStatement.class);

        when(resultSet.getLong("role_id")).thenReturn(31L);
        when(resultSet.getLong(2)).thenReturn(32L);
        when(nullResultSet.getLong("role_id")).thenReturn(0L);
        when(nullResultSet.wasNull()).thenReturn(true);
        when(callableStatement.getLong(3)).thenReturn(33L);

        handler.setNonNullParameter(preparedStatement, 1, 30L, JdbcType.BIGINT);

        verify(preparedStatement).setLong(1, 30L);
        assertThat(handler.getNullableResult(resultSet, "role_id")).isEqualTo(31L);
        assertThat(handler.getNullableResult(resultSet, 2)).isEqualTo(32L);
        assertThat(handler.getNullableResult(callableStatement, 3)).isEqualTo(33L);
        assertThat(handler.getNullableResult(nullResultSet, "role_id")).isNull();
    }

    @Test
    @DisplayName("Maps string values and preserves SQL null")
    void mapsStringValuesAndPreservesSqlNull() throws Exception {
        StringValueTypeHandler<String> handler = new StringValueTypeHandler<>(String::new, value -> value);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        CallableStatement callableStatement = mock(CallableStatement.class);

        when(resultSet.getString("realm_key")).thenReturn("platform");
        when(resultSet.getString(2)).thenReturn(null);
        when(callableStatement.getString(3)).thenReturn("customer");

        handler.setNonNullParameter(preparedStatement, 1, "default", JdbcType.VARCHAR);

        verify(preparedStatement).setString(1, "default");
        assertThat(handler.getNullableResult(resultSet, "realm_key")).isEqualTo("platform");
        assertThat(handler.getNullableResult(resultSet, 2)).isNull();
        assertThat(handler.getNullableResult(callableStatement, 3)).isEqualTo("customer");
    }

    @Test
    @DisplayName("Rejects missing value converters")
    @SuppressWarnings("DataFlowIssue")
    void rejectsMissingConverters() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LongValueTypeHandler<>(null, Long::longValue));
        assertThatNullPointerException()
                .isThrownBy(() -> new LongValueTypeHandler<>(Long::valueOf, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new StringValueTypeHandler<>(null, (String value) -> value));
        assertThatNullPointerException()
                .isThrownBy(() -> new StringValueTypeHandler<>(String::new, null));
    }
}
