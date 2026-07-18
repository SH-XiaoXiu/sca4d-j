package com.sca4d.config;

import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.FlexGlobalConfig.KeyConfig;
import com.mybatisflex.core.keygen.KeyGeneratorFactory;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import com.sca4d.common.id.IdGenerator;
import com.sca4d.common.id.MybatisFlexClusterIdKeyGenerator;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 通用配置。
 */
@Configuration
public class MybatisFlexConfig {

    private static final String CLUSTER_ID_GENERATOR = "clusterId";

    /**
     * 注册脚手架默认 ORM 字段约定。
     *
     * @param idGenerator ID 生成器
     * @return MyBatis-Flex 配置定制器
     */
    @Bean
    public @NotNull MyBatisFlexCustomizer sca4dMyBatisFlexCustomizer(@NotNull IdGenerator idGenerator) {
        return globalConfig -> {
            KeyGeneratorFactory.register(
                    CLUSTER_ID_GENERATOR, new MybatisFlexClusterIdKeyGenerator(idGenerator));
            KeyConfig keyConfig = new KeyConfig();
            keyConfig.setKeyType(KeyType.Generator);
            keyConfig.setValue(CLUSTER_ID_GENERATOR);
            keyConfig.setBefore(true);
            globalConfig.setKeyConfig(keyConfig);
            globalConfig.setLogicDeleteColumn("deleted");
            globalConfig.setNormalValueOfLogicDelete(0);
            globalConfig.setDeletedValueOfLogicDelete(1);
            globalConfig.setVersionColumn("version");
        };
    }
}
