package com.sca4d.core.spring.persistence.mybatis;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.gateway.GatewayCallbackCode;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.Name;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.retry.RetryRule;
import com.sca4d.core.api.scheduling.ScheduleKey;
import com.sca4d.core.spring.config.CoreJacksonAutoConfiguration;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.retry.RetryRuleCodec;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

/**
 * 注册 MyBatis 的 Repository 影响行数切面与插件、禁止 BATCH 执行器、Core 类型处理器和默认 Mapper XML 位置。
 */
@AutoConfiguration(after = CoreJacksonAutoConfiguration.class, afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@EnableAspectJAutoProxy
public class CoreMyBatisAutoConfiguration {

    private static final String DEFAULT_MAPPER_LOCATIONS = "classpath*:/mapper/**/*.xml";

    /**
     * 创建 MyBatis 自动配置。
     */
    public CoreMyBatisAutoConfiguration() {
    }

    /**
     * @return 执行 Repository 影响行数声明的切面
     */
    @Bean
    AffectedRowsAspect affectedRowsAspect() {
        return new AffectedRowsAspect();
    }

    /**
     * @return 捕获 MyBatis DML 真实影响行数的插件
     */
    @Bean
    AffectedRowsInterceptor affectedRowsInterceptor() {
        return new AffectedRowsInterceptor();
    }

    /**
     * 拒绝把 MyBatis 默认执行器配置为 BATCH。
     *
     * <p>配置为 BATCH 时，定制器在 SqlSessionFactory 创建阶段抛出 IllegalStateException，应用无法启动。
     *
     * @return MyBatis 配置定制器
     */
    @Bean
    ConfigurationCustomizer affectedRowsExecutorTypeCustomizer() {
        return configuration -> {
            if (configuration.getDefaultExecutorType() == ExecutorType.BATCH) {
                throw new IllegalStateException("Sca4d-J 不支持 MyBatis 默认 BATCH 执行器；请使用集合 SQL 完成批量写入");
            }
        };
    }

    /**
     * 创建只继承公共 MyBatis 行为、不继承默认 Mapper 资源的附加会话工厂构建器。
     *
     * @param properties               MyBatis 公共配置
     * @param interceptors             MyBatis 插件
     * @param typeHandlers             容器注册的类型处理器
     * @param languageDrivers          脚本语言实现
     * @param databaseIdProvider       可选数据库标识提供器
     * @param configurationCustomizers MyBatis Configuration 定制器
     * @return 附加数据源使用的会话工厂构建器
     */
    @Bean
    AdditionalMyBatisSessionFactoryBuilder additionalMyBatisSessionFactoryBuilder(
            MybatisProperties properties,
            ObjectProvider<Interceptor> interceptors,
            ObjectProvider<TypeHandler<?>> typeHandlers,
            ObjectProvider<LanguageDriver> languageDrivers,
            ObjectProvider<DatabaseIdProvider> databaseIdProvider,
            ObjectProvider<ConfigurationCustomizer> configurationCustomizers) {
        return new AdditionalMyBatisSessionFactoryBuilder(
                properties,
                interceptors.orderedStream().toList(),
                typeHandlers.orderedStream().toList(),
                languageDrivers.orderedStream().toList(),
                databaseIdProvider.getIfAvailable(),
                configurationCustomizers.orderedStream().toList());
    }

    /**
     * 在应用没有设置 {@code mybatis.mapper-locations} 时，加载类路径中所有 {@code mapper/} 目录下的 XML。
     *
     * <p>应用已经设置 {@code mapper-locations} 时定制器不做任何修改，由应用配置决定 Mapper XML 位置。
     *
     * @param properties MyBatis 运行配置
     * @return SQL 会话工厂定制器
     * @throws IOException 无法读取类路径资源时抛出
     */
    @Bean
    SqlSessionFactoryBeanCustomizer coreMapperLocations(MybatisProperties properties) throws IOException {
        String[] configuredLocations = properties.getMapperLocations();
        if (configuredLocations != null && configuredLocations.length > 0) {
            return sqlSessionFactory -> {
            };
        }
        Resource[] mapperResources = new PathMatchingResourcePatternResolver()
                .getResources(DEFAULT_MAPPER_LOCATIONS);
        return sqlSessionFactory -> sqlSessionFactory.setMapperLocations(mapperResources);
    }

    /**
     * 开启下划线到驼峰的字段映射，设置枚举默认处理器，并登记 Core 类型与列值的转换规则。
     *
     * <p>{@code RetryRule} 使用 {@code RetryRuleCodec} 编解码；{@code LocalizedFields} 使用 JSONB 与严格 JSON 映射器转换。
     * 其余类型按各自的字符串或 BIGINT 格式读写。
     *
     * @param objectMapper 严格 JSON 映射器，用于 {@code LocalizedFields} 的 JSONB 转换
     * @param retryRules 重试规则编解码器
     * @return MyBatis 配置定制器
     */
    @Bean
    ConfigurationCustomizer coreTypeHandlerCustomizer(
            @Sca4djJson Sca4djMapper objectMapper,
            RetryRuleCodec retryRules) {
        return configuration -> {
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setArgNameBasedConstructorAutoMapping(true);
            configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
            configuration.getTypeHandlerRegistry()
                    .register(
                            RetryRule.class,
                            new StringValueTypeHandler<>(retryRules::decode, retryRules::encode));
            configuration.getTypeHandlerRegistry()
                    .register(
                            Version.class,
                            JdbcType.BIGINT,
                            new LongValueTypeHandler<>(Version::new, Version::value));
            configuration.getTypeHandlerRegistry()
                    .register(
                            RealmKey.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(RealmKey::new, RealmKey::value));
            configuration.getTypeHandlerRegistry()
                    .register(
                            Action.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(Action::new, action -> action.code().format(COLON)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            TreeCode.class,
                            JdbcType.VARCHAR,
                            new TreeCodeTypeHandler());
            configuration.getTypeHandlerRegistry()
                    .register(
                            Name.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(Name::new, value -> value.format(COLON)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            Key.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(text -> Key.parse(text, COLON), value -> value.format(COLON)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            JobKey.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(JobKey::new, value -> value.format(NameFormat.SLASH)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            ScheduleKey.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(ScheduleKey::new, value -> value.format(NameFormat.SLASH)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            GatewayCallbackCode.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(
                                    GatewayCallbackCode::new,
                                    value -> value.format(NameFormat.DOT)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            AuthenticationActionCode.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(
                                    AuthenticationActionCode::new,
                                    value -> value.format(NameFormat.DOT)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            LocalizedFields.class,
                            JdbcType.OTHER,
                            new LocalizedFieldsTypeHandler(objectMapper));
        };
    }
}
