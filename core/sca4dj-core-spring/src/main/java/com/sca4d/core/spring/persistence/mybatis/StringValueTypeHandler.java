package com.sca4d.core.spring.persistence.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

/**
 * 把由单一字符串值构造的类型映射为 SQL VARCHAR。
 *
 * @param <T> 需要读写的 Java 类型
 */
public final class StringValueTypeHandler<T> extends BaseTypeHandler<T> {

    private final @NotNull Function<String, T> factory;

    private final @NotNull Function<T, String> valueReader;

    /**
     * 创建 VARCHAR 与目标类型之间的 MyBatis 转换器。
     *
     * @param factory 根据数据库中的字符串创建目标对象
     * @param valueReader 从目标对象读取字符串值
     * @throws NullPointerException 任一转换函数为空时抛出
     */
    public StringValueTypeHandler(@NotNull Function<String, T> factory, @NotNull Function<T, String> valueReader) {
        this.factory = Objects.requireNonNull(factory, "对象构造函数不能为空");
        this.valueReader = Objects.requireNonNull(valueReader, "对象取值函数不能为空");
    }

    @Override
    public void setNonNullParameter(
            @NotNull PreparedStatement statement,
            int index,
            @NotNull T parameter,
            @Nullable JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, valueReader.apply(parameter));
    }

    @Override
    public @Nullable T getNullableResult(@NotNull ResultSet resultSet, @NotNull String columnName)
            throws SQLException {
        String value = resultSet.getString(columnName);
        return value == null ? null : factory.apply(value);
    }

    @Override
    public @Nullable T getNullableResult(@NotNull ResultSet resultSet, int columnIndex) throws SQLException {
        String value = resultSet.getString(columnIndex);
        return value == null ? null : factory.apply(value);
    }

    @Override
    public @Nullable T getNullableResult(@NotNull CallableStatement statement, int columnIndex)
            throws SQLException {
        String value = statement.getString(columnIndex);
        return value == null ? null : factory.apply(value);
    }
}
