package com.sca4d.core.spring.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sca4d.core.api.concurrent.VersionCondition;
import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 验证 Settings 必须显式安装版本条件，避免缺失装配被静默回退。 */
class SettingsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SettingsAutoConfiguration.class))
            .withBean("settingMapper", SettingMapper.class, () -> mock(SettingMapper.class))
            .withBean(SqlBatches.class, () -> new SqlBatches(new SqlBatchProperties()));

    @Test
    void rejectsMissingVersionConditions() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void installsSettingsWithExplicitVersionConditions() {
        runner.withBean(VersionConditions.class, () -> VersionCondition::optimistic)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DatabaseSettingStore.class));
    }
}
