package com.sca4d.common.id;

import com.mybatisflex.core.keygen.IKeyGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis-Flex 集群 ID 主键生成器。
 */
public class MybatisFlexClusterIdKeyGenerator implements IKeyGenerator {

    private final IdGenerator idGenerator;

    /**
     * 创建 MyBatis-Flex 集群 ID 主键生成器。
     *
     * @param idGenerator ID 生成器
     */
    public MybatisFlexClusterIdKeyGenerator(@NotNull IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public Object generate(@NotNull Object entity, String key) {
        return idGenerator.nextId();
    }
}
