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
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

/**
 * 把由单一 {@code long} 值构造的类型映射为 SQL BIGINT。
 *
 * @param <T> 需要读写的 Java 类型
 */
public final class LongValueTypeHandler<T> extends BaseTypeHandler<T> {

    private final @NotNull LongFunction<T> factory;

    private final @NotNull ToLongFunction<T> valueReader;

    /**
     * 创建 BIGINT 与目标类型之间的 MyBatis 转换器。
     *
     * @param factory 根据数据库中的 long 值创建目标对象
     * @param valueReader 从目标对象读取 long 值
     * @throws NullPointerException 任一转换函数为空时抛出
     */
    public LongValueTypeHandler(@NotNull LongFunction<T> factory, @NotNull ToLongFunction<T> valueReader) {
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
        statement.setLong(index, valueReader.applyAsLong(parameter));
    }

    @Override
    public @Nullable T getNullableResult(@NotNull ResultSet resultSet, @NotNull String columnName)
            throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : factory.apply(value);
    }

    @Override
    public @Nullable T getNullableResult(@NotNull ResultSet resultSet, int columnIndex) throws SQLException {
        long value = resultSet.getLong(columnIndex);
        return resultSet.wasNull() ? null : factory.apply(value);
    }

    @Override
    public @Nullable T getNullableResult(@NotNull CallableStatement statement, int columnIndex)
            throws SQLException {
        long value = statement.getLong(columnIndex);
        return statement.wasNull() ? null : factory.apply(value);
    }
}
