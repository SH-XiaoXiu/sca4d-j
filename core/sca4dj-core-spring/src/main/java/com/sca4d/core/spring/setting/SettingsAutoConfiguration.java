package com.sca4d.core.spring.setting;

import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.Setting;
import com.sca4d.core.api.setting.Settings;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

/** 自动装配数据库 Settings、设置目录和指定初始化节点。 */
@AutoConfiguration(afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "org.mybatis.spring.SqlSessionFactoryBean")
@MapperScan(basePackageClasses = SettingMapper.class)
@EnableConfigurationProperties(SettingsBootstrapProperties.class)
public class SettingsAutoConfiguration {

    /** 创建 Settings 自动配置。 */
    public SettingsAutoConfiguration() {
    }

    /** @return 已校验重复键和 ENV 的设置目录 */
    @Bean
    SettingCatalog settingCatalog(List<Setting<?>> settings) {
        return new SettingCatalog(settings);
    }

    /** @return 隔离 MyBatis 影响行数的设置 Repository */
    @Bean
    SettingRepository settingRepository(SettingMapper mapper, SqlBatches sqlBatches) {
        return new SettingRepository(mapper, sqlBatches);
    }

    /** @return 每次读取数据库且不隐藏进程缓存的 Settings */
    @Bean
    @ConditionalOnMissingBean(Settings.class)
    DatabaseSettings databaseSettings(
            SettingCatalog catalog,
            SettingRepository repository,
            ObjectProvider<SecretKeyring> keyring,
            VersionConditions versionConditions) {
        return new DatabaseSettings(catalog, repository, keyring.getIfAvailable(), versionConditions);
    }

    /** @return 只在指定初始化节点启用的 ENV 初始化器 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.bootstrap", name = "enabled", havingValue = "true")
    SettingBootstrap settingBootstrap(
            SettingCatalog catalog,
            SettingRepository repository,
            ObjectProvider<SecretKeyring> keyring,
            Environment environment) {
        return new SettingBootstrap(catalog, repository, keyring.getIfAvailable(), environment);
    }

    /** @return 容器启动后执行一次且失败会阻止节点就绪的 Settings 初始化调用 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.bootstrap", name = "enabled", havingValue = "true")
    ApplicationRunner settingBootstrapRunner(SettingBootstrap bootstrap) {
        return arguments -> bootstrap.initialize();
    }
}
