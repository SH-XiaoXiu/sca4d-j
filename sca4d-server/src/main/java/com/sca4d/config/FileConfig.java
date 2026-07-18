package com.sca4d.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.common.boot.BootCheck;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.config.properties.Sca4djMinioProperties;
import com.sca4d.file.FileService;
import com.sca4d.file.repository.FileRepository;
import com.sca4d.file.service.DefaultFileService;
import com.sca4d.file.signature.DefaultFileSignatureService;
import com.sca4d.file.signature.FileGrantSigner;
import com.sca4d.file.signature.FileSignaturePolicy;
import com.sca4d.file.signature.FileSignatureRegistry;
import com.sca4d.file.signature.FileSignatureService;
import com.sca4d.file.storage.FileStorage;
import com.sca4d.file.storage.LocalFileStorage;
import com.sca4d.file.storage.MinioBootCheck;
import com.sca4d.file.storage.MinioFileStorage;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 注册文件上传、访问、占用和删除所需的 Spring Bean。 */
@Configuration
@EnableConfigurationProperties({Sca4djFileProperties.class, Sca4djMinioProperties.class})
@ConditionalOnProperty(prefix = "sca4dj.file", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileConfig {

    /**
     * 创建签发并校验文件上传授权的签名器。
     *
     * @param objectMapper 全局 JSON 契约
     * @param properties 文件授权密钥和有效期配置
     * @return 文件短期授权签发器
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull FileGrantSigner fileGrantSigner(
            @NotNull ObjectMapper objectMapper, @NotNull Sca4djFileProperties properties) {
        return new FileGrantSigner(objectMapper, properties);
    }

    /**
     * 提供本地文件系统存储实现。
     *
     * @param properties 本地存储目录与访问配置
     * @param grantSigner 本地上传接口使用的短期授权签名器
     * @return 本地文件存储
     */
    @Bean
    public @NotNull LocalFileStorage localFileStorage(
            @NotNull Sca4djFileProperties properties, @NotNull FileGrantSigner grantSigner) {
        return new LocalFileStorage(properties, grantSigner);
    }

    /**
     * 提供 MinIO 对象存储实现。
     *
     * @param properties MinIO 连接与 bucket 配置
     * @return MinIO 文件存储
     */
    @Bean
    public @NotNull MinioFileStorage minioFileStorage(@NotNull Sca4djMinioProperties properties) {
        return new MinioFileStorage(properties);
    }

    /**
     * 提供 MinIO 启动依赖检查。
     *
     * @param fileProperties 当前选择的文件存储提供方
     * @param minioProperties MinIO 连接配置
     * @param minioFileStorage 被检查的 MinIO 存储实例
     * @return 仅在选择 MinIO 时执行远端检查的启动探针
     */
    @Bean
    public @NotNull BootCheck minioBootCheck(
            @NotNull Sca4djFileProperties fileProperties,
            @NotNull Sca4djMinioProperties minioProperties,
            @NotNull MinioFileStorage minioFileStorage) {
        return new MinioBootCheck(fileProperties, minioProperties, minioFileStorage);
    }

    /**
     * 创建文件上传、访问、占用和删除服务。
     *
     * @param repository 读写文件元数据的仓储
     * @param idGenerator 文件主键生成器
     * @param properties 文件提供方和授权配置
     * @param grantSigner 签发并校验文件传输授权的签名器
     * @param storages 当前应用提供的全部存储实现
     * @return 文件上传、访问、占用和删除服务
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull FileService fileService(
            @NotNull FileRepository repository,
            @NotNull IdGenerator idGenerator,
            @NotNull Sca4djFileProperties properties,
            @NotNull FileGrantSigner grantSigner,
            @NotNull List<FileStorage> storages) {
        return new DefaultFileService(repository, idGenerator, properties, grantSigner, storages);
    }

    /**
     * 汇总并校验业务文件签名场景。
     *
     * @param policies 当前业务模块提供的全部签名判定策略
     * @return 按场景编码索引的签名策略注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull FileSignatureRegistry fileSignatureRegistry(@NotNull List<FileSignaturePolicy> policies) {
        return new FileSignatureRegistry(policies);
    }

    /**
     * 创建批量校验文件访问请求并签发授权的服务。
     *
     * @param registry 业务签名场景注册表
     * @param fileService 读取文件状态和归属的服务
     * @param grantSigner 文件短期授权签发器
     * @param properties 文件授权有效期配置
     * @return 批量文件访问与上传授权签发服务
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull FileSignatureService fileSignatureService(
            @NotNull FileSignatureRegistry registry,
            @NotNull FileService fileService,
            @NotNull FileGrantSigner grantSigner,
            @NotNull Sca4djFileProperties properties) {
        return new DefaultFileSignatureService(registry, fileService, grantSigner, properties);
    }
}
