package com.sca4d.core.spring.persistence.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.i18n.LocalizedFields;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.jetbrains.annotations.NotNull;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** 在 PostgreSQL JSONB 与 `LocalizedFields` 之间执行严格 JSON 转换。 */
@MappedTypes(LocalizedFields.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class LocalizedFieldsTypeHandler extends BaseTypeHandler<LocalizedFields> {

    private final ObjectMapper objectMapper;

    /**
     * 创建只处理 `LocalizedFields` 的 JSONB 类型处理器。
     *
     * @param objectMapper 脚手架内部严格 JSON 映射器
     */
    public LocalizedFieldsTypeHandler(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            LocalizedFields parameter,
            JdbcType jdbcType) throws SQLException {
        try {
            statement.setObject(index, objectMapper.writeValueAsString(parameter), JdbcType.OTHER.TYPE_CODE);
        } catch (JsonProcessingException exception) {
            throw new SQLException("LocalizedFields 无法编码为 JSONB", exception);
        }
    }

    @Override
    public LocalizedFields getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return decode(resultSet.getString(columnName));
    }

    @Override
    public LocalizedFields getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return decode(resultSet.getString(columnIndex));
    }

    @Override
    public LocalizedFields getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return decode(statement.getString(columnIndex));
    }

    /** 把 JDBC 返回的 JSON 文本转换为结构化多语言字段。 */
    private LocalizedFields decode(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LocalizedFields.class);
        } catch (JsonProcessingException exception) {
            throw new SQLException("JSONB 无法解码为 LocalizedFields", exception);
        }
    }
}
