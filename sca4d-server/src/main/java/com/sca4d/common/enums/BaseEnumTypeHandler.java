package com.sca4d.common.enums;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 枚举字符串转换器。
 *
 * <p>该转换器通过 {@link EnumValueCodec} 写入枚举的固定字符串值，并按相同规则把数据库字段还原为枚举。
 * 它不判断任何业务状态。
 *
 * @param <E> 枚举类型
 */
public class BaseEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private final Class<E> enumType;

    /**
     * 创建枚举类型转换器。
     *
     * @param enumType 枚举类型
     */
    public BaseEnumTypeHandler(@NotNull Class<E> enumType) {
        this.enumType = enumType;
    }

    /**
     * 写入非空枚举参数。
     *
     * @param ps JDBC 预编译语句
     * @param i 参数位置
     * @param parameter 枚举参数
     * @param jdbcType JDBC 类型
     * @throws SQLException JDBC 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(
            @NotNull PreparedStatement ps, int i, @NotNull E parameter, @Nullable JdbcType jdbcType)
            throws SQLException {
        String value = EnumValueCodec.encode(parameter);
        // jdbcType 为空时交给驱动推断；不为空时按 MyBatis 提供的 JDBC 类型写入。
        if (jdbcType == null) {
            ps.setObject(i, value);
            return;
        }
        ps.setObject(i, value, jdbcType.TYPE_CODE);
    }

    /**
     * 按列名读取可空枚举结果。
     *
     * @param rs JDBC 结果集
     * @param columnName 列名
     * @return 枚举值；数据库值为 null 时返回 null
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public @Nullable E getNullableResult(@NotNull ResultSet rs, @NotNull String columnName) throws SQLException {
        return EnumValueCodec.decode(enumType, rs.getString(columnName));
    }

    /**
     * 按列下标读取可空枚举结果。
     *
     * @param rs JDBC 结果集
     * @param columnIndex 列下标
     * @return 枚举值；数据库值为 null 时返回 null
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public @Nullable E getNullableResult(@NotNull ResultSet rs, int columnIndex) throws SQLException {
        return EnumValueCodec.decode(enumType, rs.getString(columnIndex));
    }

    /**
     * 从存储过程结果读取可空枚举结果。
     *
     * @param cs JDBC 存储过程语句
     * @param columnIndex 列下标
     * @return 枚举值；数据库值为 null 时返回 null
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public @Nullable E getNullableResult(@NotNull CallableStatement cs, int columnIndex) throws SQLException {
        return EnumValueCodec.decode(enumType, cs.getString(columnIndex));
    }
}
