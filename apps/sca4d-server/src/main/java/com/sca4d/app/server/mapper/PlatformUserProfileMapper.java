package com.sca4d.app.server.mapper;

import com.sca4d.app.server.entity.PlatformUserProfile;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** 定义示例 App 用户资料的最小查询和写入 SQL。 */
public interface PlatformUserProfileMapper {

    /**
     * 新增一份 Platform 用户资料。
     *
     * @param principalId IAM Principal 标识
     * @param displayName 展示名称
     * @param email 可选邮箱
     * @param phoneNumber 可选规范 E.164 手机号
     * @return 新增行数
     */
    int insert(
            @Param("principalId") @NotNull Long principalId,
            @Param("displayName") @NotNull String displayName,
            @Param("email") @Nullable String email,
            @Param("phoneNumber") @Nullable String phoneNumber);

    /**
     * 只在 Principal 尚无资料时新增默认资料。
     *
     * @param principalId IAM Principal 标识
     * @param displayName 默认展示名称
     * @return 新增行数；资料已存在时为 {@code 0}
     */
    int insertIfAbsent(
            @Param("principalId") @NotNull Long principalId,
            @Param("displayName") @NotNull String displayName);

    /**
     * 按 Principal 标识查询资料。
     *
     * @param principalId IAM Principal 标识
     * @return 资料；不存在时为空
     */
    @CheckReturnValue
    @Nullable
    PlatformUserProfile select(@Param("principalId") @NotNull Long principalId);

    /**
     * 批量查询 Principal 的资料。
     *
     * @param principalIds IAM Principal 标识集合
     * @return 已存在的资料
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull PlatformUserProfile> selectAll(
            @Param("principalIds") @NotNull Collection<@NotNull Long> principalIds);

    /**
     * 查询全部仍有效且配置邮箱的资料。
     *
     * @return 按 Principal 标识排序的资料
     */
    @CheckReturnValue
    @NotNull
    List<@NotNull PlatformUserProfile> selectAllWithEmail();

    /**
     * 覆盖资料字段。
     *
     * @param principalId IAM Principal 标识
     * @param displayName 新展示名称
     * @param email 新的可选邮箱
     * @param phoneNumber 新的可选规范 E.164 手机号
     * @return 修改行数
     */
    int update(
            @Param("principalId") @NotNull Long principalId,
            @Param("displayName") @NotNull String displayName,
            @Param("email") @Nullable String email,
            @Param("phoneNumber") @Nullable String phoneNumber);

    /**
     * 修改资料引用的头像文件。
     *
     * @param principalId IAM Principal 标识
     * @param avatarFileId 新头像文件标识；删除头像时为空
     * @return 修改行数
     */
    int updateAvatar(
            @Param("principalId") @NotNull Long principalId,
            @Param("avatarFileId") @Nullable Long avatarFileId);

    /**
     * 清除个人信息并把资料标记为逻辑删除。
     *
     * @param principalId IAM Principal 标识
     * @param displayName 已删除账号的固定显示名称
     * @return 修改行数
     */
    int markDeleted(
            @Param("principalId") @NotNull Long principalId,
            @Param("displayName") @NotNull String displayName);
}
