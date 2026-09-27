package com.sca4d.app.server.plugin;

<<<<<<< HEAD
import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.plugin.mfa.MfaFacts;
import com.sca4d.app.server.plugin.mfa.MfaSettings;
import com.sca4d.app.server.plugin.mfa.MfaPolicy;
import com.sca4d.core.api.plugin.PluginBinding;
import com.sca4d.core.api.plugin.PluginMount;
import com.sca4d.core.api.plugin.PluginScope;
import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.plugin.spring.PluginAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
=======
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sca4d.app.server.authentication.AuthenticationActions;
import com.sca4d.app.server.plugin.mfa.MfaFacts;
import com.sca4d.app.server.plugin.mfa.MfaPolicy;
import com.sca4d.app.server.plugin.mfa.MfaSettings;
import com.sca4d.core.api.plugin.PluginBinding;
import com.sca4d.core.api.plugin.PluginMount;
import com.sca4d.core.api.plugin.PluginRuntime;
import com.sca4d.core.api.plugin.PluginScope;
import com.sca4d.plugin.spring.PluginAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
>>>>>>> dev
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.Map;
<<<<<<< HEAD
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
=======

import javax.sql.DataSource;
>>>>>>> dev

/** 用隔离 PostgreSQL 验证 App 到插件再到宿主的调用保留事务提交与回滚。 */
@Testcontainers
class MfaPluginTransactionIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
<<<<<<< HEAD
    @TempDir Path temporary;
=======

    @TempDir
    Path temporary;
>>>>>>> dev

    @Test
    @DisplayName("插件反向宿主调用参与同一事务，回滚不会留下写入且代次租用正常释放")
    void preservesTransactionAcrossPluginInvocation() {
        try (var context = new AnnotationConfigApplicationContext()) {
<<<<<<< HEAD
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("plugin-test", Map.of(
                    "sca4dj.plugin.enabled", "true", "sca4dj.plugin.api", "SCA4D:SERVER",
                    "sca4dj.plugin.artifacts[0]", System.getProperty("sca4dj.plugin.artifacts[0]"),
                    "sca4dj.plugin.cache-directory", temporary.resolve("cache").toString())));
=======
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "plugin-test",
                                    Map.of(
                                            "sca4dj.plugin.enabled",
                                            "true",
                                            "sca4dj.plugin.api",
                                            "SCA4D:SERVER",
                                            "sca4dj.plugin.artifacts[0]",
                                            System.getProperty("sca4dj.plugin.artifacts[0]"),
                                            "sca4dj.plugin.cache-directory",
                                            temporary.resolve("cache").toString())));
>>>>>>> dev
            context.register(PluginAutoConfiguration.class, Beans.class);
            context.refresh();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("CREATE TABLE plugin_transaction_probe (account_id BIGINT NOT NULL)");
            Probe probe = context.getBean(Probe.class);
            assertThrows(IllegalStateException.class, () -> probe.readPolicy(true));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM plugin_transaction_probe", Long.class));
            assertEquals(0, context.getBean(PluginRuntime.class).statuses().getFirst().uses());
            probe.readPolicy(false);
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM plugin_transaction_probe", Long.class));
            assertEquals(0, context.getBean(PluginRuntime.class).statuses().getFirst().uses());
        }
    }

    /** 只装配插件和真实 JDBC 事务，避免依赖 App 的其他基础设施。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement
    static class Beans {
        /** 使用容器产生的随机连接地址。 */
<<<<<<< HEAD
        @Bean DataSource source() {
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        }
        /** 装配真实 PostgreSQL 操作。 */
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        /** 装配宿主事务管理器。 */
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        /** 绑定实际事务代理后的事实服务。 */
        @Bean @PluginBinding(MfaFacts.class)
        MfaFacts facts(JdbcTemplate jdbc) { return new WritingFacts(jdbc); }
        /** 挂载插件扩展客户端。 */
        @Bean Probe probe(@PluginMount("AUTH:MFA:POLICY") MfaPolicy policy) { return new Probe(policy); }
=======
        @Bean
        DataSource source() {
            return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        }

        /** 装配真实 PostgreSQL 操作。 */
        @Bean
        JdbcTemplate jdbc(DataSource source) {
            return new JdbcTemplate(source);
        }

        /** 装配宿主事务管理器。 */
        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        /** 绑定实际事务代理后的事实服务。 */
        @Bean
        @PluginBinding(MfaFacts.class)
        MfaFacts facts(JdbcTemplate jdbc) {
            return new WritingFacts(jdbc);
        }

        /** 挂载插件扩展客户端。 */
        @Bean
        Probe probe(@PluginMount("AUTH:MFA:POLICY") MfaPolicy policy) {
            return new Probe(policy);
        }
>>>>>>> dev
    }

    /** 在宿主事务内调用插件，插件随后反向调用事实服务。 */
    public static class Probe {
        private final MfaPolicy policy;
<<<<<<< HEAD
        public Probe(MfaPolicy policy) { this.policy = policy; }
=======

        public Probe(MfaPolicy policy) {
            this.policy = policy;
        }

>>>>>>> dev
        /** 失败时外层事务必须回滚反向宿主调用产生的写入。 */
        @PluginScope
        @Transactional
        public void readPolicy(boolean fail) {
            policy.requirementFor(42, AuthenticationActions.LOGIN_ACTION);
<<<<<<< HEAD
            if (fail) throw new IllegalStateException("测试事务回滚");
=======
            if (fail)
                throw new IllegalStateException("测试事务回滚");
>>>>>>> dev
        }
    }

    /** 使用测试表写入证明事实分发器实际调用宿主事务代理。 */
    public static class WritingFacts extends MfaPluginSpringTest.Facts {
        private final JdbcTemplate jdbc;
<<<<<<< HEAD
        public WritingFacts(JdbcTemplate jdbc) { this.jdbc = jdbc; }
=======

        public WritingFacts(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

>>>>>>> dev
        @Override
        @Transactional
        public @NotNull MfaSettings settings(long accountId) {
            jdbc.update("INSERT INTO plugin_transaction_probe(account_id) VALUES (?)", accountId);
            return super.settings(accountId);
        }
    }
}
