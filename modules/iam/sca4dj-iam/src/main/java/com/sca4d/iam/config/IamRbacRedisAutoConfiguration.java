package com.sca4d.iam.config;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.spring.config.CoreRedisAutoConfiguration;
import com.sca4d.core.spring.json.Sca4djJson;
import com.sca4d.core.spring.json.Sca4djMapper;
import com.sca4d.core.spring.redis.RedisHashTag;
import com.sca4d.core.spring.redis.Sca4djRedis;
import com.sca4d.iam.rbac.PermissionSelectionRegistry;
import com.sca4d.iam.rbac.RbacPermissionCache;
import com.sca4d.iam.rbac.RedisRbacPermissionCache;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/** 在应用安装脚手架 Redis 模板时启用跨实例 RBAC 权限缓存。 */
@AutoConfiguration(after = CoreRedisAutoConfiguration.class, before = IamAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnBean(annotation = Sca4djRedis.class)
public class IamRbacRedisAutoConfiguration {

    /** 创建 IAM RBAC Redis 自动配置。 */
    public IamRbacRedisAutoConfiguration() {
    }

    /** RBAC 多 key Lua 和 GET 使用同一 Redis Cluster 槽。 */
    @Bean
    RedisHashTag rbacRedisHashTag() {
        return new RedisHashTag(TreeCode.ofColon("IAM:RBAC"));
    }

    /**
     * 创建使用 App 隔离名称和脚手架 JSON 规则的 RBAC 权限缓存。
     *
     * @param redis 脚手架 Redis 模板
     * @param objectMapper 脚手架 JSON 映射器
     * @param permissionSelections 应用安装的 RBAC 权限选择规则
     * @return 使用权限目录指纹隔离缓存坐标的 Redis 实现
     */
    @Bean
    @NotNull
    @ConditionalOnMissingBean(RbacPermissionCache.class)
    RbacPermissionCache redisRbacPermissionCache(
            @Sca4djRedis @NotNull RedisTemplate<Key, String> redis,
            @Sca4djJson @NotNull Sca4djMapper objectMapper,
            @NotNull PermissionSelectionRegistry permissionSelections) {
        return new RedisRbacPermissionCache(redis, objectMapper, permissionSelections);
    }
}
