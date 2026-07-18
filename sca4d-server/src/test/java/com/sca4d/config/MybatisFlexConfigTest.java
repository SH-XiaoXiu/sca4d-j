package com.sca4d.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.FlexGlobalConfig;
import com.sca4d.common.id.IdGenerator;
import org.junit.jupiter.api.Test;

/** 验证 MyBatis-Flex 类型处理器和审计字段填充配置。 */
class MybatisFlexConfigTest {

    @Test
    void shouldConfigureLogicDeleteAndVersionColumns() {
        FlexGlobalConfig globalConfig = new FlexGlobalConfig();

        new MybatisFlexConfig().sca4dMyBatisFlexCustomizer(idGenerator()).customize(globalConfig);

        assertThat(globalConfig.getKeyConfig().getKeyType()).isEqualTo(KeyType.Generator);
        assertThat(globalConfig.getKeyConfig().getValue()).isEqualTo("clusterId");
        assertThat(globalConfig.getLogicDeleteColumn()).isEqualTo("deleted");
        assertThat(globalConfig.getNormalValueOfLogicDelete()).isEqualTo(0);
        assertThat(globalConfig.getDeletedValueOfLogicDelete()).isEqualTo(1);
        assertThat(globalConfig.getVersionColumn()).isEqualTo("version");
    }

    /** @return 始终生成 1 的 MyBatis-Flex 主键生成器 */
    private static IdGenerator idGenerator() {
        return () -> 1L;
    }
}
