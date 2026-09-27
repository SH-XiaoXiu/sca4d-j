package com.sca4d.core.spring.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.dictionary.DictionaryOption;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.spring.dictionary.DictionaryMapper;
import com.sca4d.core.spring.dictionary.DictionaryRepository;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.LocalizedFieldsTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

/**
 * 在隔离 PostgreSQL 中验证设置值、字典聚合删除与版本冲突。
 */
@Testcontainers
class ConfigurationPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static DictionaryRepository dictionaries;

    private static SettingRepository settings;

    private static JdbcTemplate jdbc;

    /**
     * 字典完整删除使用真实事务代理。
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
    }

    /**
     * 加载正式表结构和 Mapper，设置保留行内的版本字段为 long。
     */
    @BeforeAll
    static void initialize() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry()
                .register(LocalizedFields.class, JdbcType.OTHER, new LocalizedFieldsTypeHandler(new ObjectMapper()));
        factory.setConfiguration(configuration);
        factory.setMapperLocations(
                new ClassPathResource("mapper/core/DictionaryMapper.xml"),
                new ClassPathResource("mapper/core/SettingMapper.xml"));
        SqlSessionFactory sessions = factory.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sessions);
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(
                DictionaryRepository.class,
                () -> new DictionaryRepository(template.getMapper(DictionaryMapper.class)));
        context.registerBean(
                SettingRepository.class,
                () -> new SettingRepository(
                        template.getMapper(SettingMapper.class),
                        new SqlBatches(new SqlBatchProperties())));
        context.refresh();
        dictionaries = context.getBean(DictionaryRepository.class);
        settings = context.getBean(SettingRepository.class);
    }

    /**
     * 关闭隔离测试上下文。
     */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * 每例使用独立配置记录。
     */
    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE sys_dictionary_option, sys_dictionary, sys_setting");
    }

    @Test
    @DisplayName("字典版本冲突保留全部选项，合法删除完整清理，选项删除失败恢复字典")
    void dictionaryDeletionIsAnAtomicAggregateOperation() {
        assertThat(dictionaries.createDictionary("test", "测试", true, 5)).isTrue();
        DictionaryOption option = new DictionaryOption(
                "first",
                null,
                "首项",
                LocalizedFields.of(Map.of()),
                false,
                true,
                5);
        assertThat(dictionaries.createOption("test", option)).isTrue();
        assertThat(dictionaries.updateDictionary("test", "修改", false, 0, 0)).isTrue();
        assertThat(dictionaries.deleteDictionary("test", 0)).isFalse();
        assertThat(dictionaries.findOptions("test")).hasSize(1);
        assertThat(dictionaries.findDictionary("test").orElseThrow().enabled()).isFalse();
        jdbc.execute(
                "CREATE OR REPLACE FUNCTION reject_option_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute(
                "CREATE TRIGGER reject_option_delete BEFORE DELETE ON sys_dictionary_option FOR EACH ROW EXECUTE FUNCTION reject_option_delete()");
        try {
            assertThatThrownBy(() -> dictionaries.deleteDictionary("test", 1)).isInstanceOf(RuntimeException.class);
            assertThat(dictionaries.findDictionary("test")).isPresent();
            assertThat(dictionaries.findOptions("test")).hasSize(1);
        } finally {
            jdbc.execute("DROP TRIGGER reject_option_delete ON sys_dictionary_option");
        }
        assertThat(dictionaries.deleteDictionary("test", 1)).isTrue();
        assertThat(dictionaries.findOptions("test")).isEmpty();
    }

    @Test
    @DisplayName("设置从明文切换密文、恢复默认和再次覆盖时保持版本与 ENV 写保护")
    void settingReplacementPreservesOwnerAndTombstoneSemantics() {
        SettingOwner owner = new SettingOwner(TreeCode.ofSegment("TEST"), "one");
        settings.replace(owner, "value", 1, "plain", null, null, null, VersionCondition.optimistic(new Version(0)));
        settings.replace(
                owner,
                "value",
                1,
                null,
                "test-key",
                new byte[] {1},
                1,
                VersionCondition.optimistic(new Version(1)));
        assertThat(settings.find(owner, "value").orElseThrow().valueText()).isNull();
        assertThat(settings.find(owner, "value").orElseThrow().valueCiphertext()).containsExactly((byte) 1);
        assertThatThrownBy(() -> settings.reset(owner, "value", 1, VersionCondition.optimistic(new Version(1))))
                .isInstanceOf(OptimisticLockException.class);
        settings.reset(owner, "value", 1, VersionCondition.optimistic(new Version(2)));
        StoredSetting tombstone = settings.find(owner, "value").orElseThrow();
        assertThat(tombstone.hasValue()).isFalse();
        assertThat(tombstone.valueCiphertext()).isNull();
        settings.replace(owner, "value", 1, "", null, null, null, VersionCondition.optimistic(new Version(3)));
        assertThat(settings.find(owner, "value").orElseThrow().valueText()).isEmpty();
        settings.initializeSystem("env", 1, "initial", null, null, null);
        settings.synchronizeSystem("env", 1, "environment", null, null, null);
        assertThatThrownBy(
                () -> settings.reset(SettingOwner.system(), "env", 1, VersionCondition.optimistic(new Version(2))))
                .isInstanceOf(OptimisticLockException.class);
        settings.releaseSystemSynchronization("env");
        settings.replace(
                SettingOwner.system(),
                "env",
                1,
                "editable",
                null,
                null,
                null,
                VersionCondition.optimistic(new Version(3)));
        assertThat(settings.findAllSystem(List.of("env", "env", "missing"))).hasSize(1);
        assertThat(settings.find(new SettingOwner(TreeCode.ofSegment("TEST"), "other"), "value")).isEmpty();
    }
}
