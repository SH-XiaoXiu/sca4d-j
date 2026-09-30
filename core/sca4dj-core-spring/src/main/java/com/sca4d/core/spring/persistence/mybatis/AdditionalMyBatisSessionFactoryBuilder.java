package com.sca4d.core.spring.persistence.mybatis;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

/** 使用公共 MyBatis 配置为一个明确的数据源创建资源隔离的附加 `SqlSessionFactory`，不负责运行时数据源路由。 */
public class AdditionalMyBatisSessionFactoryBuilder {

    private final @Nullable DatabaseIdProvider databaseIdProvider;

    private final @NotNull MybatisProperties properties;

    private final @NotNull List<@NotNull ConfigurationCustomizer> configurationCustomizers;

    private final @NotNull List<@NotNull Interceptor> interceptors;

    private final @NotNull List<@NotNull LanguageDriver> languageDrivers;

    private final @NotNull List<@NotNull TypeHandler<?>> typeHandlers;

    /**
     * 保存默认会话工厂与附加会话工厂可以共享的 MyBatis 配置组成。
     *
     * @param properties `mybatis` 公共配置
     * @param interceptors 容器注册的 MyBatis 插件；没有时允许为空
     * @param typeHandlers 容器注册的类型处理器；没有时允许为空
     * @param languageDrivers 容器注册的脚本语言实现；没有时允许为空
     * @param databaseIdProvider 可选数据库标识提供器
     * @param configurationCustomizers MyBatis Configuration 定制器
     */
    AdditionalMyBatisSessionFactoryBuilder(
            @NotNull MybatisProperties properties,
            @NotNull List<@NotNull Interceptor> interceptors,
            @NotNull List<@NotNull TypeHandler<?>> typeHandlers,
            @NotNull List<@NotNull LanguageDriver> languageDrivers,
            @Nullable DatabaseIdProvider databaseIdProvider,
            @NotNull List<@NotNull ConfigurationCustomizer> configurationCustomizers) {
        this.properties = Objects.requireNonNull(properties, "MyBatis 配置不能为空");
        this.interceptors = List.copyOf(Objects.requireNonNull(interceptors, "MyBatis 插件不能为空"));
        this.typeHandlers = List.copyOf(Objects.requireNonNull(typeHandlers, "MyBatis 类型处理器不能为空"));
        this.languageDrivers = List.copyOf(Objects.requireNonNull(languageDrivers, "MyBatis 脚本语言实现不能为空"));
        this.databaseIdProvider = databaseIdProvider;
        this.configurationCustomizers = List.copyOf(configurationCustomizers);
    }

    /**
     * 为附加数据源创建会话工厂，只加载调用方明确传入的 Mapper XML。
     *
     * <p>当前方法继承公共 MyBatis 配置、插件、类型处理器和 `ConfigurationCustomizer`，但不会读取
     * `mybatis.mapper-locations`，也不会执行 `SqlSessionFactoryBeanCustomizer`。调用方必须分别声明附加数据源的
     * `DataSource`、`SqlSessionTemplate` 和事务管理器。</p>
     *
     * @param dataSource 附加数据库连接池
     * @param mapperLocations 只属于当前数据库的 Mapper XML；允许为空以支持纯注解 Mapper
     * @return 只绑定指定数据源和 Mapper 资源的会话工厂
     * @throws Exception MyBatis 配置或 Mapper XML 无法解析时抛出
     */
    @CheckReturnValue
    public @NotNull SqlSessionFactory build(
            @NotNull DataSource dataSource,
            @NotNull List<@NotNull Resource> mapperLocations) throws Exception {
        List<Resource> checkedMapperLocations = List.copyOf(
                Objects.requireNonNull(mapperLocations, "Mapper XML 资源不能为空"));

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(Objects.requireNonNull(dataSource, "附加 DataSource 不能为空"));
        factory.setTransactionFactory(new SpringManagedTransactionFactory());

        Configuration configuration = new Configuration();
        MybatisProperties.CoreConfiguration coreConfiguration = properties.getConfiguration();
        if (coreConfiguration == null || coreConfiguration.getVfsImpl() == null) {
            factory.setVfs(SpringBootVFS.class);
        }
        if (coreConfiguration != null) {
            coreConfiguration.applyTo(configuration);
        }
        configurationCustomizers.forEach(customizer -> customizer.customize(configuration));
        factory.setConfiguration(configuration);

        if (properties.getConfigurationProperties() != null) {
            factory.setConfigurationProperties(properties.getConfigurationProperties());
        }
        if (!interceptors.isEmpty()) {
            factory.setPlugins(interceptors.toArray(Interceptor[]::new));
        }
        if (databaseIdProvider != null) {
            factory.setDatabaseIdProvider(databaseIdProvider);
        }
        if (StringUtils.hasLength(properties.getTypeAliasesPackage())) {
            factory.setTypeAliasesPackage(properties.getTypeAliasesPackage());
        }
        if (properties.getTypeAliasesSuperType() != null) {
            factory.setTypeAliasesSuperType(properties.getTypeAliasesSuperType());
        }
        if (StringUtils.hasLength(properties.getTypeHandlersPackage())) {
            factory.setTypeHandlersPackage(properties.getTypeHandlersPackage());
        }
        if (!typeHandlers.isEmpty()) {
            factory.setTypeHandlers(typeHandlers.toArray(TypeHandler<?>[]::new));
        }
        if (!checkedMapperLocations.isEmpty()) {
            factory.setMapperLocations(checkedMapperLocations.toArray(Resource[]::new));
        }

        Class<? extends LanguageDriver> defaultLanguageDriver = properties.getDefaultScriptingLanguageDriver();
        if (!languageDrivers.isEmpty()) {
            factory.setScriptingLanguageDrivers(languageDrivers.toArray(LanguageDriver[]::new));
            if (defaultLanguageDriver == null && languageDrivers.size() == 1) {
                defaultLanguageDriver = languageDrivers.getFirst().getClass();
            }
        }
        factory.setDefaultScriptingLanguageDriver(defaultLanguageDriver);
        return Objects.requireNonNull(factory.getObject(), "附加 SqlSessionFactory 创建失败");
    }
}
