package com.sca4d.common.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.core.EnabledStatus;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** 验证 MyBatis 类型处理器按固定字符串读写 BaseEnum。 */
class BaseEnumTypeHandlerTest {

    @Test
    void shouldWriteEnumValueWithoutJdbcType() throws Exception {
        BaseEnumTypeHandler<EnabledStatus> handler = new BaseEnumTypeHandler<>(EnabledStatus.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, EnabledStatus.ENABLED, null);

        verify(statement).setObject(1, "ENABLED");
    }

    @Test
    void shouldWriteEnumValueWithJdbcType() throws Exception {
        BaseEnumTypeHandler<EnabledStatus> handler = new BaseEnumTypeHandler<>(EnabledStatus.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, EnabledStatus.DISABLED, JdbcType.VARCHAR);

        verify(statement).setObject(1, "DISABLED", JdbcType.VARCHAR.TYPE_CODE);
    }

    @Test
    void shouldReadEnumValueByColumnName() throws Exception {
        BaseEnumTypeHandler<EnabledStatus> handler = new BaseEnumTypeHandler<>(EnabledStatus.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("status")).thenReturn("enabled");

        assertThat(handler.getNullableResult(resultSet, "status")).isEqualTo(EnabledStatus.ENABLED);
    }

    @Test
    void shouldReadNullValue() throws Exception {
        BaseEnumTypeHandler<EnabledStatus> handler = new BaseEnumTypeHandler<>(EnabledStatus.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(1)).thenReturn(null);

        assertThat(handler.getNullableResult(resultSet, 1)).isNull();
    }
}
