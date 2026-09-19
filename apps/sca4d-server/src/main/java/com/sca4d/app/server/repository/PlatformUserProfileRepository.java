package com.sca4d.app.server.repository;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.mapper.PlatformUserProfileMapper;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 隔离 App 用户资料服务对 MyBatis、空结果和影响行数的依赖。 */
@Repository
public class PlatformUserProfileRepository {

    private final @NotNull PlatformUserProfileMapper mapper;

    /** 创建 App 用户资料 Repository。 */
    PlatformUserProfileRepository(@NotNull PlatformUserProfileMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Platform User Profile Mapper 不能为空");
    }

    /** 新增资料并返回数据库生成的时间和版本。 */
    @AffectedRows(target = "新增 Platform 用户资料")
    @CheckReturnValue
    @NotNull
    public PlatformUserProfile insert(
            @NotNull Long principalId,
            @NotNull String displayName,
            String email,
            String phoneNumber) {
        mapper.insert(principalId, displayName, email, phoneNumber);
        return find(principalId).orElseThrow(() -> new IllegalStateException("新增后的 Platform 用户资料不存在"));
    }

    /** 只在资料不存在时写入默认展示名称。 */
    @AffectedRows(target = "初始化 Platform 用户资料", whenZero = ZeroRowPolicy.ALLOW)
    public void insertIfAbsent(@NotNull Long principalId, @NotNull String displayName) {
        mapper.insertIfAbsent(principalId, displayName);
    }

    /** 按 Principal 标识查找资料。 */
    @CheckReturnValue
    @NotNull
    public Optional<PlatformUserProfile> find(@NotNull Long principalId) {
        return Optional.ofNullable(
                mapper.select(
                        Objects.requireNonNull(principalId, "Profile Principal 标识不能为空")));
    }

    /** 读取必须存在的资料。 */
    @CheckReturnValue
    @NotNull
    public PlatformUserProfile loadByPrincipalId(@NotNull Long principalId) {
        return find(principalId)
                .orElseThrow(() -> new IllegalStateException("Platform Account 缺少 App 用户资料"));
    }

    /** 批量读取已存在的资料。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    public List<@NotNull PlatformUserProfile> findAll(
            @NotNull Collection<@NotNull Long> principalIds) {
        if (principalIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectAll(principalIds));
    }

    /** 读取全部仍有效且配置邮箱的资料。 */
    @CheckReturnValue
    @NotNull
    @Unmodifiable
    public List<@NotNull PlatformUserProfile> findAllWithEmail() {
        return List.copyOf(mapper.selectAllWithEmail());
    }

    /** 覆盖资料字段。 */
    @AffectedRows(target = "Platform 用户资料")
    @CheckReturnValue
    @NotNull
    public PlatformUserProfile update(
            @NotNull Long principalId,
            @NotNull String displayName,
            String email,
            String phoneNumber) {
        mapper.update(principalId, displayName, email, phoneNumber);
        return find(principalId).orElseThrow(() -> new IllegalStateException("修改后的 Platform 用户资料不存在"));
    }

    /** 修改头像文件引用并返回增加版本后的资料。 */
    @AffectedRows(target = "Platform 用户头像")
    @CheckReturnValue
    @NotNull
    public PlatformUserProfile updateAvatar(@NotNull Long principalId, Long avatarFileId) {
        mapper.updateAvatar(principalId, avatarFileId);
        return find(principalId).orElseThrow(() -> new IllegalStateException("修改头像后的 Platform 用户资料不存在"));
    }

    /** 清除个人信息并逻辑隔离资料。 */
    @AffectedRows(target = "隔离 Platform 用户资料")
    public void isolate(@NotNull Long principalId, @NotNull String displayName) {
        mapper.markDeleted(
                Objects.requireNonNull(principalId, "Profile Principal 标识不能为空"),
                Objects.requireNonNull(displayName, "已删除账号显示名称不能为空"));
    }
}
