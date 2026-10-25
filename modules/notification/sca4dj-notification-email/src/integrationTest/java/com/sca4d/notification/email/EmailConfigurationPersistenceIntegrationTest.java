package com.sca4d.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.concurrent.exception.OptimisticLockException;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.security.EncryptedSecret;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsAspect;
import com.sca4d.core.spring.persistence.mybatis.AffectedRowsInterceptor;
import com.sca4d.core.spring.persistence.mybatis.CoreEnumTypeHandler;
import com.sca4d.core.spring.persistence.mybatis.LongValueTypeHandler;
import com.sca4d.notification.email.sender.EmailSenderRepository;
import com.sca4d.notification.email.sender.SmtpSecurity;
import com.sca4d.notification.email.sender.mapper.EmailSenderMapper;
import org.apache.ibatis.session.Configuration;
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
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 仅使用 PostgreSQL 验证邮件配置的凭据 Patch、唯一启用约束和版本保护，不连接 SMTP。 */
@Testcontainers
class EmailConfigurationPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static AnnotationConfigApplicationContext context;

    private static JdbcTemplate jdbc;

    private static EmailSenderRepository senders;

    /** 装配 Mapper 和实际影响行数检查。 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @MapperScan(basePackageClasses = EmailSenderMapper.class)
    static class PersistenceConfiguration {
    }

    /** 在隔离数据库加载正式 schema 和字段转换器。 */
    @BeforeAll
    static void initialize() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                new ClassPathResource("META-INF/sca4dj/schema/dictionary-settings.sql"),
                new ClassPathResource("META-INF/sca4dj/schema/notification-email.sql"));
        schema.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        schema.execute(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
        configuration.getTypeHandlerRegistry()
                .register(Version.class, JdbcType.BIGINT, new LongValueTypeHandler<>(Version::new, Version::value));
        factory.setConfiguration(configuration);
        factory.setPlugins(new AffectedRowsInterceptor());
        factory.setMapperLocations(new ClassPathResource("mapper/notification/email/EmailSenderMapper.xml"));
        SqlSessionFactory sessions = factory.getObject();
        context = new AnnotationConfigApplicationContext();
        context.register(PersistenceConfiguration.class);
        context.registerBean(SqlSessionFactory.class, () -> sessions);
        context.registerBean(AffectedRowsAspect.class);
        context.registerBean(VersionConditions.class, () -> VersionCondition::optimistic);
        context.registerBean(EmailSenderRepository.class);
        context.refresh();
        senders = context.getBean(EmailSenderRepository.class);
    }

    /** 释放测试上下文。 */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    /** 清空每例的独立邮件配置。 */
    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE notification_email_sender");
    }

    @Test
    @DisplayName("SMTP 密码可保留、替换、清空，唯一启用冲突和旧版本不破坏原配置")
    void senderCredentialIntentAndVersionArePreserved() {
        EncryptedSecret password = new EncryptedSecret("test-key", new byte[] {1});
        senders.add(
                1,
                "first",
                "smtp.example",
                25,
                SmtpSecurity.NONE,
                "user",
                password,
                "from@example.test",
                "name",
                "reply@example.test");
        Version initial = senders.find(1).orElseThrow().version();
        senders.replace(
                1,
                "first",
                "smtp.example",
                25,
                SmtpSecurity.NONE,
                "user",
                false,
                null,
                "from@example.test",
                null,
                null);
        assertThat(senders.findConfiguration(1).orElseThrow().password()).isEqualTo(password);
        EncryptedSecret replacement = new EncryptedSecret("test-key-2", new byte[] {2});
        senders.replace(
                1,
                "first",
                "smtp.example",
                25,
                SmtpSecurity.NONE,
                "user",
                true,
                replacement,
                "from@example.test",
                null,
                null);
        assertThat(senders.findConfiguration(1).orElseThrow().password()).isEqualTo(replacement);
        EmailSenderRepository stale = new EmailSenderRepository(
                context.getBean(EmailSenderMapper.class),
                ignored -> VersionCondition.optimistic(initial));
        assertThatThrownBy(() -> stale.setEnabled(1, true)).isInstanceOf(OptimisticLockException.class);
        senders.replace(
                1,
                "first",
                "smtp.example",
                25,
                SmtpSecurity.NONE,
                null,
                true,
                null,
                "from@example.test",
                null,
                null);
        assertThat(senders.findConfiguration(1).orElseThrow().password()).isNull();
        assertThat(senders.find(1).orElseThrow().username()).isNull();
        senders.setEnabled(1, true);
        senders.add(2, "second", "smtp.example", 25, SmtpSecurity.NONE, null, null, "from@example.test", null, null);
        assertThatThrownBy(() -> senders.setEnabled(2, true))
                .isInstanceOf(BusinessException.class);
        assertThat(senders.findEnabledConfiguration().orElseThrow().sender().senderId()).isEqualTo(1);
        assertThat(senders.find(2).orElseThrow().enabled()).isFalse();
    }

}
