package com.sca4d.core.spring.persistence.mybatis;

import com.sca4d.core.api.enums.BaseEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 使用 Core 枚举的 `value()` 读写数据库字符串。
 *
 * @param <E> 需要映射的枚举类型
 */
public final class CoreEnumTypeHandler<E extends Enum<E> & BaseEnum> extends BaseTypeHandler<E> {

    private final Class<E> enumType;

    /**
     * 创建指定枚举类型的处理器。
     *
     * @param enumType 枚举类型
     */
    public CoreEnumTypeHandler(Class<E> enumType) {
        this.enumType = Objects.requireNonNull(enumType, "枚举类型不能为空");
        if (!BaseEnum.class.isAssignableFrom(enumType)) {
            throw new IllegalArgumentException("MyBatis 枚举必须实现 BaseEnum: " + enumType.getName());
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, E parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, parameter.value());
    }

    @Override
    public @Nullable E getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return decode(resultSet.getString(columnName));
    }

    @Override
    public @Nullable E getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return decode(resultSet.getString(columnIndex));
    }

    @Override
    public @Nullable E getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return decode(statement.getString(columnIndex));
    }

    /** 按 `BaseEnum.value()` 把数据库字符串恢复为枚举常量。 */
    private @Nullable E decode(@Nullable String value) {
        return value == null ? null : BaseEnum.fromValue(enumType, value);
    }
}
