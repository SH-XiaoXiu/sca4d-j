package com.sca4d.core.spring.setting;

import com.sca4d.core.api.concurrent.VersionConditions;
import com.sca4d.core.api.security.SecretKeyring;
import com.sca4d.core.api.setting.Setting;
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

/** 自动装配设置目录、数据库读写、密钥保护和指定初始化节点。 */
@AutoConfiguration(afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "org.mybatis.spring.SqlSessionFactoryBean")
@MapperScan(basePackageClasses = SettingMapper.class)
@EnableConfigurationProperties({SettingsBootstrapProperties.class, EnvironmentPrefixProperties.class})
public class SettingsAutoConfiguration {

    public SettingsAutoConfiguration() {
    }

    /** @return 按当前应用前缀把 ENV 后缀解析为环境变量名的解析器 */
    @Bean
    EnvironmentVariableResolver environmentVariableResolver(
            Environment environment,
            EnvironmentPrefixProperties properties) {
        return new EnvironmentVariableResolver(environment, properties);
    }

    /** @return 已校验重复键和 ENV 的设置目录 */
    @Bean
    SettingCatalog settingCatalog(List<Setting<?>> settings) {
        return new SettingCatalog(settings);
    }

    /** @return 封装 MyBatis 影响行数检查的设置 Repository */
    @Bean
    SettingRepository settingRepository(SettingMapper mapper, SqlBatches sqlBatches) {
        return new SettingRepository(mapper, sqlBatches);
    }

    /** @return 每次从数据库读取、不在进程内缓存的设置读写器 */
    @Bean
    @ConditionalOnMissingBean(DatabaseSettingStore.class)
    DatabaseSettingStore databaseSource(
            SettingRepository repository,
            ObjectProvider<SecretKeyring> keyring,
            VersionConditions versionConditions) {
        return new DatabaseSettingStore(repository, keyring.getIfAvailable(), versionConditions);
    }

    /** @return 只在指定初始化节点启用的 ENV 初始化器 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.bootstrap", name = "enabled", havingValue = "true")
    SettingBootstrap settingBootstrap(
            SettingCatalog catalog,
            SettingRepository repository,
            ObjectProvider<SecretKeyring> keyring,
            Environment environment,
            EnvironmentVariableResolver variables) {
        return new SettingBootstrap(catalog, repository, keyring.getIfAvailable(), environment, variables);
    }

    /** @return 容器启动后执行一次且失败会阻止节点就绪的 Settings 初始化调用 */
    @Bean
    @ConditionalOnProperty(prefix = "sca4dj.bootstrap", name = "enabled", havingValue = "true")
    ApplicationRunner settingBootstrapRunner(SettingBootstrap bootstrap) {
        return arguments -> bootstrap.initialize();
    }
}
