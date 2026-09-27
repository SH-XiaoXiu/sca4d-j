package com.sca4d.core.spring.persistence.mybatis;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 在 SQL VARCHAR 边界读写稳定 TreeCode，资源分类默认输出斜杠格式。 */
public class TreeCodeTypeHandler extends BaseTypeHandler<TreeCode> {

    private final NameFormat format;

    /** 创建资源分类使用的斜杠输出器。 */
    public TreeCodeTypeHandler() {
        this(NameFormat.SLASH);
    }

    /** 为明确的数据库列选择输出格式，读取时接受三种规范编码文本。 */
    protected TreeCodeTypeHandler(@NotNull NameFormat format) {
        this.format = format;
    }

    @Override
    public void setNonNullParameter(
            @NotNull PreparedStatement statement,
            int index,
            @NotNull TreeCode code,
            @Nullable JdbcType jdbcType) throws SQLException {
        statement.setString(index, code.format(format));
    }

    @Override
    public @Nullable TreeCode getNullableResult(@NotNull ResultSet result, @NotNull String column) throws SQLException {
        String text = result.getString(column);
        return text == null ? null : TreeCode.of(text);
    }

    @Override
    public @Nullable TreeCode getNullableResult(@NotNull ResultSet result, int column) throws SQLException {
        String text = result.getString(column);
        return text == null ? null : TreeCode.of(text);
    }

    @Override
    public @Nullable TreeCode getNullableResult(@NotNull CallableStatement result, int column) throws SQLException {
        String text = result.getString(column);
        return text == null ? null : TreeCode.of(text);
    }

    /** 为系统身份编码等明确声明的数据库列输出冒号格式。 */
    public static final class Colon extends TreeCodeTypeHandler {

        /** 创建冒号编码的 SQL 边界转换器。 */
        public Colon() {
            super(NameFormat.COLON);
        }
    }
}
