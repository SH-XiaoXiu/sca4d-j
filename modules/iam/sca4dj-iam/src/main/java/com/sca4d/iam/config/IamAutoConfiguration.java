package com.sca4d.iam.config;

import static com.sca4d.core.api.name.NameFormat.DOT;

import com.sca4d.core.api.authentication.SessionControl;
import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.permission.OperationCatalog;
import com.sca4d.core.api.runtime.InstanceCapability;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import com.sca4d.iam.acl.AclPermissionPolicy;
import com.sca4d.iam.acl.AclRepository;
import com.sca4d.iam.acl.AclRepositoryRegistry;
import com.sca4d.iam.acl.AclResourceTypes;
import com.sca4d.iam.acl.AclService;
import com.sca4d.iam.base.PlatformAccountDirectory;
import com.sca4d.iam.base.PlatformAccountDirectoryRepository;
import com.sca4d.iam.base.PlatformAccountLifecycleRepository;
import com.sca4d.iam.base.PlatformAccountLifecycleService;
import com.sca4d.iam.base.PlatformIdentityService;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.base.PlatformPrincipalCodec;
import com.sca4d.iam.base.PlatformPrincipalLoader;
import com.sca4d.iam.base.PlatformSessionIdentityLoader;
import com.sca4d.iam.base.acl.PlatformAclRepository;
import com.sca4d.iam.base.mapper.PlatformAccountMapper;
import com.sca4d.iam.base.mapper.PlatformAclMapper;
import com.sca4d.iam.base.mapper.PlatformRbacMapper;
import com.sca4d.iam.base.rbac.PlatformBootstrapService;
import com.sca4d.iam.base.rbac.PlatformOperations;
import com.sca4d.iam.base.rbac.PlatformRbacRepository;
import com.sca4d.iam.base.rbac.PlatformRbacResourceLoader;
import com.sca4d.iam.base.rbac.PlatformRbacService;
import com.sca4d.iam.base.rbac.PlatformRbacSessionInvalidation;
import com.sca4d.iam.identity.AccountIdentifierNormalizer;
import com.sca4d.iam.identity.IamAccountDirectory;
import com.sca4d.iam.identity.IdentityRepository;
import com.sca4d.iam.identity.IdentityService;
import com.sca4d.iam.identity.PrincipalType;
import com.sca4d.iam.identity.PrincipalTypes;
import com.sca4d.iam.identity.SystemIdentityRepository;
import com.sca4d.iam.identity.SystemPrincipalCodec;
import com.sca4d.iam.identity.UsernameAccountIdentifierNormalizer;
import com.sca4d.iam.identity.mapper.IdentityMapper;
import com.sca4d.iam.identity.mapper.SystemIdentityMapper;
import com.sca4d.iam.rbac.NoOpRbacPermissionCache;
import com.sca4d.iam.rbac.PermissionSelectionRegistry;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacOperationModule;
import com.sca4d.iam.rbac.RbacPermissionCache;
import com.sca4d.iam.rbac.RbacRepository;
import com.sca4d.iam.rbac.RbacRepositoryRegistry;
import com.sca4d.iam.rbac.RbacService;
import com.sca4d.iam.rbac.RbacSessionInvalidation;
import com.sca4d.iam.rbac.RbacSessionInvalidationRegistry;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 自动装配 IAM 身份管理、Platform 身份以及内置 RBAC 和 ACL 实现。
 */
@AutoConfiguration(before = MybatisAutoConfiguration.class)
@MapperScan(basePackageClasses = {IdentityMapper.class, PlatformRbacMapper.class})
@EnableTransactionManagement
@EnableConfigurationProperties({IamProperties.class, SystemIdentityProperties.class})
public class IamAutoConfiguration {

    /** 登记系统 Principal 的稳定持久化类型。 */
    @Bean
    SystemPrincipalCodec systemPrincipalCodec() {
        return new SystemPrincipalCodec();
    }

    /** 创建系统身份编码与 Principal 绑定的持久化边界。 */
    @Bean
    SystemIdentityRepository systemIdentityRepository(
            @NotNull SystemIdentityMapper mapper,
            @NotNull IdentityMapper identities,
            @NotNull SqlBatches batches) {
        return new SystemIdentityRepository(mapper, identities, batches);
    }

    /** 在数据库迁移后、工作线程启动前完成身份事务并发布常量。 */
    @Bean
    @DependsOnDatabaseInitialization
    SystemIdentityInitializer systemIdentityInitializer(
            @NotNull SystemIdentityRepository repository,
            @NotNull PlatformTransactionManager transactionManager,
            @NotNull SystemIdentityProperties properties,
            @NotNull List<InstanceCapability> capabilities) {
        return new SystemIdentityInitializer(
                repository,
                new TransactionTemplate(transactionManager),
                properties,
                capabilities);
    }

    /**
     * 创建 IAM 身份 Repository。
     *
     * @param mapper IAM 身份 Mapper
     * @param sqlBatches SQL 批处理器
     * @return 包内身份 Repository
     */
    @Bean
    @NotNull
    IdentityRepository identityRepository(@NotNull IdentityMapper mapper, @NotNull SqlBatches sqlBatches) {
        return new IdentityRepository(mapper, sqlBatches);
    }

    /**
     * 创建 IAM 内置的用户名规范化器。
     *
     * @return 用户名规范化器
     */
    @Bean
    @NotNull
    UsernameAccountIdentifierNormalizer usernameAccountIdentifierNormalizer() {
        return new UsernameAccountIdentifierNormalizer();
    }

    /**
     * 根据已安装的 Session Codec 建立 Principal 类型映射。
     *
     * @param codecs 应用安装的全部 Principal Session Codec
     * @return Principal 类型映射
     */
    @Bean
    @NotNull
    PrincipalTypes principalTypes(
            @NotNull List<? extends @NotNull PrincipalCodec<?>> codecs) {
        return new PrincipalTypes(codecs);
    }

    /**
     * 创建 IAM 身份服务。
     *
     * @param repository 身份 Repository
     * @param idGenerator 标识生成器
     * @param principalTypes Principal 类型映射
     * @param normalizers 登录标识规范化器
     * @return 身份服务
     */
    @Bean
    @NotNull
    IdentityService identityService(
            @NotNull IdentityRepository repository,
            @NotNull IdGenerator idGenerator,
            @NotNull PrincipalTypes principalTypes,
            @NotNull List<@NotNull AccountIdentifierNormalizer> normalizers) {
        return new IdentityService(repository, idGenerator, principalTypes, normalizers);
    }

    /**
     * 创建使用 IAM 身份数据查找账号的 Core 目录实现。
     *
     * @param identities IAM 身份管理服务
     * @return Core Account 目录
     */
    @Bean
    @NotNull
    AccountDirectory accountDirectory(@NotNull IdentityService identities) {
        return new IamAccountDirectory(identities);
    }

    /**
     * 创建 Platform ACL Repository。
     *
     * @param mapper Platform ACL Mapper
     * @return Platform ACL Repository
     */
    @Bean
    @NotNull
    PlatformAclRepository platformAclRepository(@NotNull PlatformAclMapper mapper) {
        return new PlatformAclRepository(mapper);
    }

    /**
     * 创建 ACL Repository 注册表。
     *
     * @param repositories 应用安装的全部 ACL Repository
     * @return 按领域范围路由的注册表
     */
    @Bean
    @NotNull
    AclRepositoryRegistry aclRepositoryRegistry(
            @NotNull List<@NotNull AclRepository> repositories) {
        return new AclRepositoryRegistry(repositories);
    }

    /**
     * 汇总并校验 ACL 资源类型归属。
     *
     * @param repositories ACL Repository 注册表
     * @return ACL 资源类型归属表
     */
    @Bean
    @NotNull
    AclResourceTypes aclResourceTypes(@NotNull AclRepositoryRegistry repositories) {
        return new AclResourceTypes(repositories);
    }

    /**
     * 创建统一 ACL 服务。
     *
     * @param repositories ACL Repository 注册表
     * @param types ACL 资源类型归属表
     * @param identities 身份管理服务
     * @return ACL 服务
     */
    @Bean
    @NotNull
    AclService aclService(
            @NotNull AclRepositoryRegistry repositories,
            @NotNull AclResourceTypes types,
            @NotNull IdentityService identities) {
        return new AclService(repositories, types, identities);
    }

    /**
     * 创建负责 ACL 资源类型的权限策略。
     *
     * @param types ACL 资源类型归属表
     * @param acl 统一 ACL 服务
     * @return ACL 权限策略
     */
    @Bean
    @NotNull
    AclPermissionPolicy aclPermissionPolicy(
            @NotNull AclResourceTypes types,
            @NotNull AclService acl) {
        return new AclPermissionPolicy(types, acl);
    }

    /**
     * 创建 MyBatis 类型配置。
     *
     * @return 为 IAM 自有值类型注册 JDBC 转换的配置
     */
    @Bean
    @NotNull
    ConfigurationCustomizer iamTypeHandlerCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry()
                    .register(
                            AccountIdentifierType.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(AccountIdentifierType::new, value -> value.format(DOT)));
            configuration.getTypeHandlerRegistry()
                    .register(
                            PrincipalType.class,
                            JdbcType.VARCHAR,
                            new StringValueTypeHandler<>(PrincipalType::new, value -> value.format(DOT)));
        };
    }

    /**
     * 创建访问 Platform 身份数据的服务。
     *
     * @param identities IAM 身份管理服务
     * @return Platform 身份服务
     */
    @Bean
    @NotNull
    PlatformIdentityService platformIdentityService(@NotNull IdentityService identities) {
        return new PlatformIdentityService(identities);
    }

    /**
     * 创建 Platform Account 目录持久化边界。
     *
     * @param mapper Platform Account Mapper
     * @param principalTypes Principal Java 类型与持久化值的映射
     * @param sqlBatches SQL 批处理器
     * @return 固定读取 PlatformPrincipal 的账号目录 Repository
     */
    @Bean
    @NotNull
    PlatformAccountDirectoryRepository platformAccountDirectoryRepository(
            @NotNull PlatformAccountMapper mapper,
            @NotNull PrincipalTypes principalTypes,
            @NotNull SqlBatches sqlBatches) {
        return new PlatformAccountDirectoryRepository(
                mapper,
                principalTypes.principalType(PlatformPrincipal.class),
                sqlBatches);
    }

    /**
     * 创建框架无关的 Platform Account 目录。
     *
     * @param repository Platform Account 持久化边界
     * @param sessions Auth 可选安装的 Account Session 控制器
     * @return Platform Account 目录
     */
    @Bean
    @NotNull
    PlatformAccountDirectory platformAccountDirectory(
            @NotNull PlatformAccountDirectoryRepository repository,
            @NotNull ObjectProvider<SessionControl> sessions) {
        return new PlatformAccountDirectory(repository, sessions.getIfAvailable());
    }

    /**
     * 创建 Platform Account 生命周期持久化边界。
     *
     * @param mapper Platform Account Mapper
     * @param principalTypes Principal Java 类型与持久化值的映射
     * @return 固定处理 PlatformPrincipal 的生命周期 Repository
     */
    @Bean
    @NotNull
    PlatformAccountLifecycleRepository platformAccountLifecycleRepository(
            @NotNull PlatformAccountMapper mapper,
            @NotNull PrincipalTypes principalTypes) {
        return new PlatformAccountLifecycleRepository(mapper, principalTypes.principalType(PlatformPrincipal.class));
    }

    /** 创建由各模块领域行为生成的 RBAC 权限选择规则注册表。 */
    @Bean
    @NotNull
    PermissionSelectionRegistry permissionSelectionRegistry(
            @NotNull OperationCatalog catalog,
            @NotNull List<@NotNull RbacOperationModule> modules) {
        return new PermissionSelectionRegistry(catalog, modules);
    }

    /** 应用没有安装 Redis 时使用不保存授权结果的实现。 */
    @Bean
    @NotNull
    @ConditionalOnMissingBean(RbacPermissionCache.class)
    RbacPermissionCache noOpRbacPermissionCache() {
        return new NoOpRbacPermissionCache();
    }

    /**
     * 创建 Platform 角色管理 Operation 模块。
     *
     * @return Platform Operation 和 RBAC 领域声明
     */
    @Bean
    @NotNull
    PlatformOperations platformOperations() {
        return new PlatformOperations();
    }

    /**
     * 创建 Platform RBAC 的 Session 失效适配器。
     *
     * @param identities 身份管理服务
     * @param sessions Auth 可选安装的 Account Session 控制器
     * @return 标记 PlatformAccount Session 的实现
     */
    @Bean
    @NotNull
    PlatformRbacSessionInvalidation platformRbacSessionInvalidation(
            @NotNull IdentityService identities,
            @NotNull ObjectProvider<SessionControl> sessions) {
        return new PlatformRbacSessionInvalidation(identities, sessions.getIfAvailable());
    }

    /**
     * 创建 Platform RBAC Repository。
     *
     * @param mapper Platform RBAC Mapper
     * @param versionConditions 根资源写入版本条件
     * @param locales 应用支持和默认 Locale
     * @param principalTypes Principal Java 类型与持久化值的映射
     * @return 使用 Platform 角色表的 Repository
     */
    @Bean
    @NotNull
    PlatformRbacRepository platformRbacRepository(
            @NotNull PlatformRbacMapper mapper,
            @NotNull VersionConditions versionConditions,
            @NotNull LocaleCatalog locales,
            @NotNull PrincipalTypes principalTypes,
            @NotNull SqlBatches sqlBatches) {
        return new PlatformRbacRepository(
                mapper,
                versionConditions,
                locales,
                principalTypes.principalType(PlatformPrincipal.class),
                sqlBatches);
    }

    /**
     * 创建 RBAC Repository 注册表。
     *
     * @param repositories 应用安装的业务角色 Repository
     * @return 按 {@link RbacDomain} 查找 Repository 的注册表
     */
    @Bean
    @NotNull
    RbacRepositoryRegistry rbacRepositoryRegistry(
            @NotNull List<@NotNull RbacRepository> repositories) {
        return new RbacRepositoryRegistry(repositories);
    }

    /**
     * 创建 RBAC Session 失效实现注册表。
     *
     * @param invalidations 应用安装的业务 Session 失效实现
     * @return 按 {@link RbacDomain} 查找 Session 失效实现的注册表
     */
    @Bean
    @NotNull
    RbacSessionInvalidationRegistry rbacSessionInvalidationRegistry(
            @NotNull List<@NotNull RbacSessionInvalidation> invalidations) {
        return new RbacSessionInvalidationRegistry(invalidations);
    }

    /**
     * 创建统一 RBAC 服务。
     *
     * @param repositories 业务角色 Repository 注册表
     * @param invalidations 业务 Session 失效实现注册表
     * @param selections 按 RBAC 领域登记的权限校验和展开规则
     * @param permissionCache Principal 有效权限选择符缓存
     * @param idGenerator 角色标识生成器
     * @return 通用 RBAC 服务
     */
    @Bean
    @NotNull
    RbacService rbacService(
            @NotNull RbacRepositoryRegistry repositories,
            @NotNull RbacSessionInvalidationRegistry invalidations,
            @NotNull PermissionSelectionRegistry selections,
            @NotNull RbacPermissionCache permissionCache,
            @NotNull IdGenerator idGenerator) {
        return new RbacService(repositories, invalidations, selections, permissionCache, idGenerator);
    }

    /**
     * 创建固定使用 Platform scope 的 RBAC 门面。
     *
     * @param rbac 通用 RBAC 服务
     * @param repository Platform RBAC Repository
     * @param selections 按 RBAC 领域查找 Operation 规则的注册表
     * @param idGenerator 角色标识生成器
     * @param locales 应用支持和默认 Locale
     * @param identities PlatformPrincipal 身份管理服务
     * @param accounts Platform Account 内部持久化边界
     * @param distributedLock Platform RBAC 跨实例写入锁
     * @param transactionManager Platform RBAC 事务管理器
     * @return 固定使用 Platform 角色 Repository 的服务
     */
    @Bean
    @NotNull
    PlatformRbacService platformRbacService(
            @NotNull RbacService rbac,
            @NotNull PlatformRbacRepository repository,
            @NotNull PermissionSelectionRegistry selections,
            @NotNull IdGenerator idGenerator,
            @NotNull LocaleCatalog locales,
            @NotNull IdentityService identities,
            @NotNull PlatformAccountDirectoryRepository accounts,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager) {
        return new PlatformRbacService(
                rbac,
                repository,
                selections.selection(PlatformRbacRepository.PLATFORM_DOMAIN),
                idGenerator,
                locales,
                identities,
                accounts,
                distributedLock,
                new TransactionTemplate(transactionManager));
    }

    /**
     * 创建同步删除 Platform Account 的生命周期服务。
     *
     * @param repository Platform Account 生命周期持久化边界
     * @param rbac Platform RBAC 服务
     * @param acl 统一 ACL 服务
     * @param sessions Auth 可选安装的 Account Session 终止实现
     * @return Platform Account 生命周期服务
     */
    @Bean
    @NotNull
    PlatformAccountLifecycleService platformAccountLifecycleService(
            @NotNull PlatformAccountLifecycleRepository repository,
            @NotNull PlatformRbacService rbac,
            @NotNull AclService acl,
            @NotNull ObjectProvider<SessionControl> sessions) {
        return new PlatformAccountLifecycleService(repository, rbac, acl, sessions.getIfAvailable());
    }

    /**
     * 创建 Platform RBAC Session 资源装载器。
     *
     * @param rbac Platform RBAC 服务
     * @return 为 PlatformPrincipal 向 Session 提供具体 Operation 的 Loader
     */
    @Bean
    @NotNull
    PlatformRbacResourceLoader platformRbacResourceLoader(@NotNull PlatformRbacService rbac) {
        return new PlatformRbacResourceLoader(rbac);
    }

    /**
     * 创建 Platform bootstrap 服务。
     *
     * @param identities Platform 身份服务
     * @param rbac Platform RBAC 服务
     * @return 初始化内置角色、bootstrap 账号和 ROOT 关系的服务
     */
    @Bean
    @NotNull
    PlatformBootstrapService platformBootstrapService(
            @NotNull PlatformIdentityService identities,
            @NotNull PlatformRbacService rbac,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager) {
        return new PlatformBootstrapService(
                identities,
                rbac,
                distributedLock,
                transactionManager);
    }

    /**
     * 创建 Platform Session 身份装载器。
     *
     * @param identities Platform 身份服务
     * @return Platform Session 身份装载器
     */
    @Bean
    @NotNull
    PlatformSessionIdentityLoader platformSessionIdentityLoader(@NotNull PlatformIdentityService identities) {
        return new PlatformSessionIdentityLoader(identities);
    }

    /**
     * 创建 platform realm 的 Principal 装载器。
     *
     * @param identities Platform 身份服务
     * @return 读取 Account 当前全部可用 `PlatformPrincipal` 的 Loader
     */
    @Bean
    @NotNull
    PlatformPrincipalLoader platformPrincipalLoader(@NotNull PlatformIdentityService identities) {
        return new PlatformPrincipalLoader(identities);
    }

    /**
     * 创建 PlatformPrincipal Session 编码器。
     *
     * @param objectMapper Platform 身份 Session payload 映射器
     * @return PlatformPrincipal Session 编码器
     */
    @Bean
    @NotNull
    PlatformPrincipalCodec platformSessionCodec(
            @NotNull @Sca4djJson Sca4djMapper objectMapper) {
        return new PlatformPrincipalCodec(objectMapper);
    }

    /**
     * 创建应用启动时执行的 IAM 初始化任务。
     *
     * @param bootstrap Platform 初始化服务
     * @param properties 初始化配置
     * @param distributedLock 多实例启动协调锁
     * @return IAM 初始化任务
     */
    @Bean
    @Order(0)
    @ConditionalOnProperty(prefix = "sca4dj.iam.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
    @NotNull
    IamBootstrap iamBootstrap(
            @NotNull PlatformBootstrapService bootstrap,
            @NotNull IamProperties properties,
            @NotNull DistributedLock distributedLock) {
        return new IamBootstrap(bootstrap, properties, distributedLock);
    }

}
