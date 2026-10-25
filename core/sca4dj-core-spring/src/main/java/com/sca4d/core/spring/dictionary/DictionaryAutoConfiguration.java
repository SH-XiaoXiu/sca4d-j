package com.sca4d.core.spring.dictionary;

import com.sca4d.core.api.dictionary.Dictionaries;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashSet;
import java.util.Set;

/** 自动发现 Dictionary 枚举并装配数据库选项聚合查询。 */
@AutoConfiguration(afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass(name = "org.mybatis.spring.SqlSessionFactoryBean")
@MapperScan(basePackageClasses = DictionaryMapper.class)
public class DictionaryAutoConfiguration {

    /** 创建 Dictionary 自动配置。 */
    public DictionaryAutoConfiguration() {
    }

    /** @return 扫描应用 ClassLoader 的 Dictionary 枚举查找器 */
    @Bean
    DictionaryScanner dictionaryScanner() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return new DictionaryScanner(
                classLoader == null ? DictionaryAutoConfiguration.class.getClassLoader() : classLoader);
    }

    /**
     * 扫描 `com.sca4d` 包和自动配置包下的 Dictionary 枚举，并构建代码字典目录。
     *
     * @return 代码字典目录；任一枚举定义不合法时 `DictionaryRegistry` 抛出异常，应用启动失败
     */
    @Bean
    DictionaryRegistry dictionaryRegistry(
            DictionaryScanner scanner,
            ConfigurableListableBeanFactory beanFactory) {
        Set<String> packages = new LinkedHashSet<>();
        packages.add("com.sca4d");
        if (AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        return new DictionaryRegistry(scanner.scan(packages));
    }

    /** @return 动态字典和扩展项的 Repository */
    @Bean
    DictionaryRepository dictionaryRepository(DictionaryMapper mapper) {
        return new DictionaryRepository(mapper);
    }

    /** @return 聚合 Java 枚举与数据库选项的字典查询入口；应用已声明 `Dictionaries` Bean 时不注册本实现 */
    @Bean
    @ConditionalOnMissingBean(Dictionaries.class)
    DatabaseDictionaries dictionaries(DictionaryRegistry registry, DictionaryRepository repository) {
        return new DatabaseDictionaries(registry, repository);
    }
}
