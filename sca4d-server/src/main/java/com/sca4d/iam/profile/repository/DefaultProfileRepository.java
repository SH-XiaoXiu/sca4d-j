package com.sca4d.iam.profile.repository;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.core.TimeSupport;
import com.sca4d.common.web.ClientStateTarget;
import com.sca4d.entity.IamDefaultProfileEntity;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.profile.DefaultProfile;
import com.sca4d.iam.resource.IamResourceTypes;
import com.sca4d.mapper.IamDefaultProfileMapper;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 读写 DEFAULT Principal 的默认资料。 */
@Repository
@RequiredArgsConstructor
public class DefaultProfileRepository {

    private final IamDefaultProfileMapper mapper;

    /** 为已创建的 DEFAULT Principal 写入默认资料。 */
    public void create(@NotNull Long principalId, @Nullable Long avatarFileId) {
        OffsetDateTime now = TimeSupport.now();
        if (mapper.insert(new IamDefaultProfileEntity()
                .setPrincipalId(principalId)
                .setAvatarFileId(avatarFileId)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setVersion(0L)
                .setDeleted(0)) != 1) {
            throw new IllegalStateException("DEFAULT Profile 写入行数异常");
        }
    }

    /** 按 Subject 读取默认资料；不存在时返回空值。 */
    public @Nullable DefaultProfile findBySubjectId(@NotNull Long subjectId) {
        DefaultProfileRow row = mapper.selectBySubjectId(subjectId);
        if (row == null) {
            return null;
        }
        return new DefaultProfile()
                .setSubjectId(row.getSubjectId())
                .setPrincipalId(row.getPrincipalId())
                .setAvatarFileId(row.getAvatarFileId())
                .setVersion(row.getVersion());
    }

    /** 批量读取 Subject 默认资料，结果按 Subject 主键索引。 */
    public @NotNull Map<Long, DefaultProfile> findBySubjectIds(@NotNull Collection<Long> subjectIds) {
        List<Long> distinctSubjectIds = subjectIds.stream().distinct().toList();
        if (distinctSubjectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, DefaultProfile> profiles = new LinkedHashMap<>();
        for (DefaultProfileRow row : mapper.selectBySubjectIds(distinctSubjectIds)) {
            DefaultProfile profile = new DefaultProfile()
                    .setSubjectId(row.getSubjectId())
                    .setPrincipalId(row.getPrincipalId())
                    .setAvatarFileId(row.getAvatarFileId())
                    .setVersion(row.getVersion());
            if (profiles.putIfAbsent(profile.getSubjectId(), profile) != null) {
                throw new IllegalStateException("Subject 存在重复 DEFAULT Profile: " + profile.getSubjectId());
            }
        }
        return Map.copyOf(profiles);
    }

    /** 按当前版本替换 Subject 默认头像。 */
    public void updateAvatar(
            @NotNull Long subjectId, @Nullable Long avatarFileId, @NotNull Long version) {
        if (mapper.updateAvatar(subjectId, avatarFileId, version) == 0) {
            throw new ConcurrencyConflictException(
                    IamErrorCode.DEFAULT_PROFILE_CHANGED,
                    ClientStateTarget.resource(IamResourceTypes.SUBJECT, subjectId));
        }
    }

    /** 按当前版本逻辑删除 Subject 默认资料。 */
    public void isolate(@NotNull Long subjectId, @NotNull Long version) {
        if (mapper.isolateBySubjectId(subjectId, version) == 0) {
            throw new ConcurrencyConflictException(
                    IamErrorCode.DEFAULT_PROFILE_CHANGED,
                    ClientStateTarget.resource(IamResourceTypes.SUBJECT, subjectId));
        }
    }

    /** 硬删除已隔离 Subject 的默认资料。 */
    public void purge(@NotNull Long subjectId) {
        mapper.purgeBySubjectId(subjectId);
    }
}
