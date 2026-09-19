package com.sca4d.iot.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.query.Sort;
import com.sca4d.iot.api.ProductSource;
import com.sca4d.iot.persistence.DeviceDetailsUpdate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** 验证 Device 部分更新 XML 按 Patch 状态生成明确列赋值。 */
class DeviceMapperPatchSqlTest {

    @Test
    @DisplayName("SET 展示名称并把 CLEAR 备注生成为 SQL NULL")
    void writesSetValueAndClearColumn() {
        BoundSql sql = boundSql(new DeviceDetailsUpdate(Patch.set("Gateway"), Patch.clear()));

        assertThat(normalize(sql.getSql()))
                .isEqualTo("UPDATE iot_device SET display_name = ?, remark = NULL WHERE id = ?");
        assertThat(sql.getParameterMappings())
                .extracting(mapping -> mapping.getProperty())
                .containsExactly("update.displayName.value", "deviceId");
    }

    @Test
    @DisplayName("IGNORE 展示名称并只生成 SET 备注列")
    void omitsIgnoredColumn() {
        DeviceDetailsUpdate update = new DeviceDetailsUpdate(Patch.ignore(), Patch.set(""));
        BoundSql sql = boundSql(update);

        assertThat(normalize(sql.getSql()))
                .isEqualTo("UPDATE iot_device SET remark = ? WHERE id = ?");
        assertThat(sql.getParameterMappings())
                .extracting(mapping -> mapping.getProperty())
                .containsExactly("update.remark.value", "deviceId");
        assertThat(new Configuration().newMetaObject(update).getValue("remark.value"))
                .isEqualTo("");
    }

    @Test
    @DisplayName("Device 条件查询只生成已声明的筛选和安全排序")
    void buildsDeclaredDeviceQuery() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("deviceIds", null);
        parameters.put("productSource", ProductSource.DATABASE);
        parameters.put("productCode", "gateway");
        parameters.put("productId", 31L);
        parameters.put("search", "edge");
        parameters.put("protocolCode", "mqtt-v1");
        parameters.put("directConnection", true);
        parameters.put("offset", 20L);
        parameters.put("size", 10);
        parameters.put("sort", java.util.List.of(Sort.CREATED_AT.desc(), Sort.NAME.asc()));

        MappedStatement statement = configuration().getMappedStatement(
                "com.sca4d.iot.persistence.mapper.DeviceMapper.selectDevices");
        String sql = normalize(statement.getBoundSql(parameters).getSql());

        assertThat(sql)
                .contains("product_source = ?")
                .contains("product_code = ?")
                .contains("product_id = ?")
                .contains("protocol_code = ?")
                .contains("connection_protocol IS NOT NULL")
                .contains("ORDER BY created_at DESC , code ASC , id ASC")
                .endsWith("LIMIT ? OFFSET ?");
    }

    /** 解析 Mapper XML，并为给定字段更新生成不连接数据库的 BoundSql。 */
    private static BoundSql boundSql(DeviceDetailsUpdate update) {
        MappedStatement statement = configuration().getMappedStatement(
                "com.sca4d.iot.persistence.mapper.DeviceMapper.updateDeviceDetails");
        return statement.getBoundSql(Map.of("deviceId", 21L, "update", update));
    }

    /** 解析 Device Mapper XML 并返回包含全部语句的 MyBatis 配置。 */
    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        String resource = "mapper/iot/DeviceMapper.xml";
        try (InputStream input = DeviceMapperPatchSqlTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取 Device Mapper XML", exception);
        }
        return configuration;
    }

    /** 把动态 SQL 中仅用于排版的连续空白压缩成单个空格。 */
    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
