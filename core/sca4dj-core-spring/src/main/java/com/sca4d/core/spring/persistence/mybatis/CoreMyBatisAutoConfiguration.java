package com.sca4d.core.spring.persistence.mybatis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.spring.config.CoreJacksonAutoConfiguration;
import com.sca4d.core.spring.json.Sca4djJson;
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
 * 注册 Core 标识、版本、认证域和枚举在 MyBatis 中使用的类型处理器。
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
     * 在应用没有自行指定 XML 位置时加载所有模块的标准 Mapper 目录。
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
     * 注册 Core 标识、版本、认证域和枚举处理器。
     *
     * @return MyBatis 配置定制器
     */
    @Bean
    ConfigurationCustomizer coreTypeHandlerCustomizer(@Sca4djJson ObjectMapper objectMapper) {
        return configuration -> {
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setArgNameBasedConstructorAutoMapping(true);
            configuration.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);
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
                            new StringValueTypeHandler<>(Action::new, action -> action.code().value()));
            configuration.getTypeHandlerRegistry()
                    .register(
                            TreeCode.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(
                                    value -> value.indexOf('/') < 0 && value.indexOf(':') < 0
                                            ? TreeCode.ofSegment(value)
                                            : TreeCode.of(value),
                                    TreeCode::value));
            configuration.getTypeHandlerRegistry()
                    .register(
                            LocalizedFields.class,
                            JdbcType.OTHER,
                            new LocalizedFieldsTypeHandler(objectMapper));
        };
    }
}
