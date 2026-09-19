package com.sca4d.app.server.service;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.repository.PlatformUserProfileRepository;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.LockKey;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.resource.ResourceReference;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileService;
import com.sca4d.core.api.storage.FileStatus;
import com.sca4d.core.api.storage.FileUse;
import com.sca4d.core.api.storage.StoredFile;
import com.sca4d.iam.base.PlatformPrincipal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 管理示例 App 自己拥有的 Platform 用户资料。 */
@Service
public class PlatformUserProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformUserProfileService.class);

    private static final String DELETED_DISPLAY_NAME = "已删除账号";

    private static final Duration PROFILE_LOCK_WAIT = Duration.ofSeconds(10);

    /** Platform Account 头像文件使用的上传场景。 */
    public static final String PROFILE_AVATAR = "platform:user:profile:avatar";

    private final @NotNull PlatformUserProfileRepository repository;

    private final @NotNull FileService files;

    private final @NotNull DistributedLock distributedLock;

    private final @NotNull TransactionTemplate transactions;

    /**
     * 创建 Platform 用户资料服务。
     *
     * @param repository App 用户资料 Repository
     * @param files 文件服务
     * @param distributedLock 多实例资料操作锁
     * @param transactionManager 应用事务管理器
     */
    PlatformUserProfileService(
            @NotNull PlatformUserProfileRepository repository,
            @NotNull FileService files,
            @NotNull DistributedLock distributedLock,
            @NotNull PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "Platform User Profile Repository 不能为空");
        this.files = Objects.requireNonNull(files, "文件服务不能为空");
        this.distributedLock = Objects.requireNonNull(distributedLock, "分布式锁不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    /**
     * 为新建 Principal 创建资料。
     *
     * @param principalId IAM Principal 标识
     * @param displayName 展示名称
     * @param email 可选邮箱
     * @param phoneNumber 可选规范 E.164 手机号
     * @return 新建资料
     */
    @Transactional
    @CheckReturnValue
    public @NotNull PlatformUserProfile create(
            @NotNull Long principalId,
            @NotNull String displayName,
            @Nullable String email,
            @Nullable String phoneNumber) {
        PlatformUserProfile created = repository.insert(
                Objects.requireNonNull(principalId, "Profile Principal 标识不能为空"),
                requiredDisplayName(displayName),
                optional(email),
                optional(phoneNumber));
        LOG.info("Platform 用户资料已创建 | principalId={}", principalId);
        return created;
    }

    /**
     * 为 bootstrap Principal 补齐默认资料；重复执行不修改已有资料。
     *
     * @param principalId bootstrap Principal 标识
     * @param displayName 默认展示名称
     */
    @Transactional
    public void initialize(@NotNull Long principalId, @NotNull String displayName) {
        repository.insertIfAbsent(
                Objects.requireNonNull(principalId, "Profile Principal 标识不能为空"),
                requiredDisplayName(displayName));
    }

    /**
     * 读取必须存在的用户资料。
     *
     * @param principalId IAM Principal 标识
     * @return 用户资料
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull PlatformUserProfile loadByPrincipalId(@NotNull Long principalId) {
        return repository.find(Objects.requireNonNull(principalId, "Profile Principal 标识不能为空"))
                .orElseThrow(() -> new IllegalStateException("Platform Account 缺少 App 用户资料"));
    }

    /**
     * 按 Principal 标识读取用户资料。
     *
     * @param principalId IAM Principal 标识
     * @return 当前有效资料；不存在或已删除时为空
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull Optional<PlatformUserProfile> find(@NotNull Long principalId) {
        return repository.find(Objects.requireNonNull(principalId, "Profile Principal 标识不能为空"));
    }

    /**
     * 批量读取用户资料。
     *
     * @param principalIds IAM Principal 标识
     * @return 已存在的不可修改资料列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PlatformUserProfile> findAll(
            @NotNull Collection<@NotNull Long> principalIds) {
        return repository.findAll(
                Objects.requireNonNull(
                        principalIds,
                        "Profile Principal 标识不能为空"));
    }

    /**
     * 读取全部仍有效且配置邮箱的用户资料，供启动时复核 IAM Email 标识。
     *
     * @return 不可修改的资料列表
     */
    @Transactional(readOnly = true)
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull PlatformUserProfile> findAllWithEmail() {
        return repository.findAllWithEmail();
    }

    /**
     * 替换用户资料。
     *
     * @param principalId IAM Principal 标识
     * @param displayName 新展示名称
     * @param email 新的可选邮箱
     * @param phoneNumber 新的可选规范 E.164 手机号
     * @return 修改后的资料
     */
    @Transactional
    @CheckReturnValue
    public @NotNull PlatformUserProfile replace(
            @NotNull Long principalId,
            @NotNull String displayName,
            @Nullable String email,
            @Nullable String phoneNumber) {
        PlatformUserProfile updated = repository.update(
                Objects.requireNonNull(principalId, "Profile Principal 标识不能为空"),
                requiredDisplayName(displayName),
                optional(email),
                optional(phoneNumber));
        LOG.info("Platform 用户资料已修改 | principalId={}", principalId);
        return updated;
    }

    /**
     * 为 Platform Account 绑定新头像并释放旧头像。
     *
     * <p>方法取得资料分布式锁，并在同一数据库事务内修改文件状态和头像引用。重复绑定当前头像不修改数据。
     *
     * @param principalId 目标 Platform Principal 标识
     * @param uploader 当前上传并绑定头像的 Platform Principal
     * @param fileId 已完成上传的头像文件标识
     * @return 修改后的用户资料
     */
    @CheckReturnValue
    public @NotNull PlatformUserProfile replaceAvatar(
            @NotNull Long principalId,
            @NotNull PlatformPrincipal uploader,
            long fileId) {
        Long targetPrincipal = Objects.requireNonNull(principalId, "Profile Principal 标识不能为空");
        PlatformPrincipal checkedUploader = Objects.requireNonNull(uploader, "上传 Principal 不能为空");
        return distributedLock.execute(
                profileLock(targetPrincipal),
                PROFILE_LOCK_WAIT,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    // 在资料锁保护的事务中读取当前头像和新文件，防止并发替换遗留多个 IN_USE 文件。
                    FileUse use = avatarUse(targetPrincipal);
                    PlatformUserProfile current = repository.loadByPrincipalId(targetPrincipal);
                    StoredFile file = files.loadById(fileId);

                    // 新文件必须属于目标资料；重复绑定当前头像时只接受已经处于 IN_USE 的文件。
                    if (!file.use().scene().equals(use.scene())
                            || !file.use().target().type().equals(use.target().type())
                            || !file.use().target().id().equals(use.target().id())) {
                        throw new BusinessException(FileErrorCode.NOT_FOUND);
                    }
                    Long currentAvatarFileId = current.avatarFileId();
                    if (currentAvatarFileId != null && currentAvatarFileId == fileId) {
                        if (file.status() != FileStatus.IN_USE) {
                            throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                        }
                        return current;
                    }

                    // 首次绑定要求当前 Principal 是文件创建者，并且文件已经完成上传。
                    if (file.uploaderPrincipalId() != checkedUploader.principalId()) {
                        throw new BusinessException(FileErrorCode.NOT_FOUND);
                    }
                    if (file.status() != FileStatus.READY) {
                        throw new BusinessException(FileErrorCode.STATE_CONFLICT);
                    }

                    // 先绑定新文件，再修改资料引用并释放旧文件；任一步失败都会回滚数据库状态。
                    files.bind(fileId, use);
                    PlatformUserProfile updated = repository.updateAvatar(targetPrincipal, fileId);
                    if (currentAvatarFileId != null) {
                        files.release(currentAvatarFileId, use);
                    }
                    LOG.debug(
                            "Platform 用户头像替换已写入事务 | principalId={} uploaderPrincipalId={} fileId={} previousFileId={}",
                            targetPrincipal,
                            checkedUploader.principalId(),
                            fileId,
                            currentAvatarFileId);
                    return updated;
                }), "头像替换事务没有返回结果"));
    }

    /**
     * 删除 Platform Account 当前头像并释放对应文件。
     *
     * <p>方法取得资料分布式锁并在同一事务中清除文件引用。资料没有头像时不修改数据。
     *
     * @param principalId 目标 Platform Principal 标识
     * @return 删除头像后的用户资料
     */
    @CheckReturnValue
    public @NotNull PlatformUserProfile deleteAvatar(@NotNull Long principalId) {
        Long targetPrincipal = Objects.requireNonNull(principalId, "Profile Principal 标识不能为空");

        return distributedLock.execute(
                profileLock(targetPrincipal),
                PROFILE_LOCK_WAIT,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    // 锁内读取头像引用；没有头像时保持幂等，不写数据库。
                    PlatformUserProfile current = repository.loadByPrincipalId(targetPrincipal);
                    Long avatarFileId = current.avatarFileId();
                    if (avatarFileId == null) {
                        return current;
                    }

                    // 先清除资料引用，再把原头像登记为等待删除；失败时事务整体回滚。
                    FileUse use = avatarUse(targetPrincipal);
                    PlatformUserProfile updated = repository.updateAvatar(targetPrincipal, null);
                    files.release(avatarFileId, use);
                    LOG.debug(
                            "Platform 用户头像删除已写入事务 | principalId={} fileId={}",
                            targetPrincipal,
                            avatarFileId);
                    return updated;
                }), "头像删除事务没有返回结果"));
    }

    /**
     * 逻辑删除 Principal 的资料，并清空邮箱和手机号。
     *
     * @param principalId IAM Principal 标识
     */
    void deleteWithinTransaction(@NotNull Long principalId) {
        Long checkedId = Objects.requireNonNull(principalId, "Profile Principal 标识不能为空");

        PlatformUserProfile current = repository.loadByPrincipalId(checkedId);
        Long avatarFileId = current.avatarFileId();
        if (avatarFileId != null) {
            files.release(
                    avatarFileId,
                    avatarUse(checkedId));
        }
        repository.isolate(checkedId, DELETED_DISPLAY_NAME);
        LOG.debug(
                "Platform 用户资料删除状态已写入 | principalId={} avatarFileId={}",
                checkedId,
                avatarFileId);
    }

    /** 返回指定 Principal 的资料变更分布式锁键。 */
    static @NotNull LockKey profileLock(@NotNull Long principalId) {
        return new LockKey(
                "platform",
                "profile",
                Objects.requireNonNull(principalId, "Principal 标识不能为空").toString());
    }

    /** 返回绑定到指定 Platform 用户资料的头像文件用途。 */
    private static @NotNull FileUse avatarUse(@NotNull Long principalId) {
        return new FileUse(
                PROFILE_AVATAR,
                new ResourceReference(PlatformUserProfile.RESOURCE_TYPE, principalId.toString()));
    }

    /** 校验并清理展示名称。 */
    private static @NotNull String requiredDisplayName(@NotNull String displayName) {
        String checked = Objects.requireNonNull(displayName, "展示名称不能为空").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("展示名称不能为空");
        }
        return checked;
    }

    /** 把空白可选字段统一保存为空值。 */
    private static @Nullable String optional(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String checked = value.trim();
        return checked.isEmpty() ? null : checked;
    }

}
