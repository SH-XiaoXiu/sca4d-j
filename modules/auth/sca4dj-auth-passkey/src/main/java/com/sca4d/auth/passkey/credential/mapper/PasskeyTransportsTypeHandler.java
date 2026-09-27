package com.sca4d.auth.passkey.credential.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

/** 读写 Passkey 凭据的 transport 集合；仅在对应列显式使用，不注册为通用 Set 映射。 */
public class PasskeyTransportsTypeHandler extends BaseTypeHandler<Set<String>> {
    @Override
    public void setNonNullParameter(
            @NotNull PreparedStatement statement,
            int index,
            @NotNull Set<String> values,
            @Nullable JdbcType jdbcType) throws SQLException {
        statement.setString(index, values.stream().sorted().collect(Collectors.joining(",")));
    }

    @Override
    public @NotNull Set<String> getNullableResult(@NotNull ResultSet result, @NotNull String column)
            throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public @NotNull Set<String> getNullableResult(@NotNull ResultSet result, int column) throws SQLException {
        return decode(result.getString(column));
    }

    @Override
    public @NotNull Set<String> getNullableResult(@NotNull CallableStatement statement, int column)
            throws SQLException {
        return decode(statement.getString(column));
    }

    /** 将逗号分隔的 transport 值恢复为不可修改集合；空列表示未报告 transport。 */
    private static @NotNull Set<String> decode(@Nullable String value) {
        return value == null || value.isBlank() ? Set.of() : Set.of(value.split(","));
    }
}
