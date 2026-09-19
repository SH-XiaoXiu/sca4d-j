package com.sca4d.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.notification.NotificationCode;
import com.sca4d.core.api.query.ListRequest;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.api.query.Sort;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.LongValueTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import com.sca4d.notification.template.mapper.NotificationTemplateMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/** 使用 PostgreSQL 验证通知模板分页、统一条件查询、Patch 更新和乐观版本。 */
@Testcontainers
class NotificationTemplatePersistenceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static JdbcTemplate jdbc;

    private static NotificationTemplateRepository templates;

    /** 装配 Mapper 和实际影响行数检查。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @MapperScan(basePackageClasses = NotificationTemplateMapper.class)
    static class PersistenceConfiguration {
    }

    /** 在隔离数据库加载正式 schema、Mapper 和值类型处理器。 */
    @BeforeAll
    static void initialize() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/notification.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);

        org.apache.ibatis.session.Configuration mybatis = new org.apache.ibatis.session.Configuration();
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.getTypeHandlerRegistry()
                .register(Version.class, JdbcType.BIGINT, new LongValueTypeHandler<>(Version::new, Version::value));
        mybatis.getTypeHandlerRegistry()
                .register(
                        NotificationCode.class,
                        JdbcType.VARCHAR,
                        new StringValueTypeHandler<>(NotificationCode::new, NotificationCode::value));
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(mybatis);
        factory.setPlugins(new AffectedRowsInterceptor());
        factory.setMapperLocations(new ClassPathResource("mapper/notification/NotificationTemplateMapper.xml"));
        SqlSessionFactory sessions = factory.getObject();

        context = new AnnotationConfigApplicationContext();
        context.register(PersistenceConfiguration.class);
        context.registerBean(SqlSessionFactory.class, () -> sessions);
        context.registerBean(AffectedRowsAspect.class);
        context.registerBean(VersionConditions.class, () -> VersionCondition::optimistic);
        context.registerBean(ObjectMapper.class);
        context.registerBean(NotificationTemplateRepository.class);
        context.refresh();
        templates = context.getBean(NotificationTemplateRepository.class);
    }

    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE notification_template");
    }

    @Test
    @DisplayName("分页排序、点查询和 Patch 更新使用同一模板持久化语义")
    void pagesFindsAndUpdatesTemplates() throws Exception {
        ObjectMapper json = new ObjectMapper();
        templates.add(1, new NotificationCode("alpha.notice"), json.readTree("{\"zh-CN\":{}}"));
        templates.add(2, new NotificationCode("beta.notice"), json.readTree("{\"zh-CN\":{}}"));
        templates.add(3, new NotificationCode("gamma.notice"), json.readTree("{\"zh-CN\":{}}"));

        Page<NotificationTemplate> page = templates.page(
                new ListRequest(
                        new PageRequest(1, 2),
                        List.of(Sort.of("NOTIFICATION_CODE").desc())));

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getItems()).extracting(value -> value.code().value())
                .containsExactly("gamma.notice", "beta.notice");
        assertThat(templates.find(1).orElseThrow().code().value()).isEqualTo("alpha.notice");

        templates.replace(1, new NotificationCode("alpha.changed"), json.readTree("{\"en-US\":{}}"));
        templates.setEnabled(1, true);

        NotificationTemplate enabled = templates.findEnabled(new NotificationCode("alpha.changed")).orElseThrow();
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.version().value()).isEqualTo(2);
        assertThat(enabled.content().has("en-US")).isTrue();
    }
}
