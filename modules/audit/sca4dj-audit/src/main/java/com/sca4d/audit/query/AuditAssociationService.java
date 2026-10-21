package com.sca4d.audit.query;

import com.sca4d.audit.recording.AuditRepository;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Objects;

/** 为历史审计追加有证据的资源关联，不改写原记录或已有关系。 */
public class AuditAssociationService {
    private final AuditRepository repository;

    public AuditAssociationService(@NotNull AuditRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    /**
     * 为已经保存的审计记录追加资源快照关联。
     *
     * <p>调用方必须先完成资源授权并确认关联证据；本方法只校验记录存在、写入去重关联，
     * 不根据当前资源归属重新解释历史记录。
     *
     * @param id 已保存的审计记录标识
     * @param resources 能从历史证据确认的关联资源；重复资源不会重复写入
     * @throws IllegalArgumentException 标识不是正数或审计记录不存在时抛出
     */
    @Transactional
    public void associate(long id, @NotNull Collection<? extends Resource> resources) {
        Objects.requireNonNull(resources, "关联资源不能为空");
        if (id <= 0 || repository.find(id).isEmpty()) {
            throw new IllegalArgumentException("审计记录不存在");
        }
        repository.associate(id, resources);
    }
}
