package com.sca4d.core.spring.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.enums.BaseEnum;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.config.CoreJacksonAutoConfiguration;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class CoreMyBatisAutoConfigurationTest {

    @Test
    @DisplayName("Reads Page Request Through JavaBean Properties")
    void readsPageRequestThroughJavaBeanProperties() {
        var page = SystemMetaObject.forObject(new PageRequest(3, 20));

        assertThat(page.getValue("page")).isEqualTo(3);
        assertThat(page.getValue("size")).isEqualTo(20);
        assertThat(page.getValue("offset")).isEqualTo(40L);
    }

    @Test
    @DisplayName("Registers Jsonb Handler Only For Localized Fields")
    void registersJsonbHandlerOnlyForLocalizedFields() {
        Configuration configuration = new Configuration();

        new CoreMyBatisAutoConfiguration()
                .coreTypeHandlerCustomizer(new ObjectMapper())
                .customize(configuration);

        assertThat(configuration.isMapUnderscoreToCamelCase()).isTrue();
        assertThat(configuration.isArgNameBasedConstructorAutoMapping()).isTrue();
        assertThat(configuration.getTypeHandlerRegistry().getTypeHandler(PersistentState.class))
                .isInstanceOf(CoreEnumTypeHandler.class);
        assertThat(
                configuration.getTypeHandlerRegistry()
                        .getTypeHandler(LocalizedFields.class, JdbcType.OTHER))
                .isInstanceOf(LocalizedFieldsTypeHandler.class);
        assertThat(
                configuration.getTypeHandlerRegistry()
                        .getTypeHandler(Map.class, JdbcType.OTHER))
                .isNull();
    }

    @Test
    @DisplayName("Builds Additional Factory With Explicit Mapper Resources")
    void buildsAdditionalFactoryWithExplicitMapperResources() throws Exception {
        MybatisProperties properties = new MybatisProperties();
        properties.setMapperLocations(new String[] {"classpath:/missing-default-mapper.xml"});
        AffectedRowsInterceptor affectedRows = new AffectedRowsInterceptor();
        AdditionalMyBatisSessionFactoryBuilder builder = new AdditionalMyBatisSessionFactoryBuilder(
                properties,
                List.of(affectedRows),
                List.of(),
                List.of(),
                null,
                List.of(configuration -> configuration.setMapUnderscoreToCamelCase(true)));
        ByteArrayResource mapper = new ByteArrayResource(
                ("""
                        <?xml version="1.0" encoding="UTF-8" ?>
                        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                            "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                        <mapper namespace="com.sca4d.core.spring.persistence.mybatis.CoreMyBatisAutoConfigurationTest$AdditionalMapper">
                            <select id="selectValue" resultType="int">SELECT 1</select>
                        </mapper>
                        """)
                        .getBytes(StandardCharsets.UTF_8));

        SqlSessionFactory factory = builder.build(mock(DataSource.class), List.of(mapper));

        assertThat(factory.getConfiguration().getVfsImpl()).isEqualTo(SpringBootVFS.class);
        assertThat(factory.getConfiguration().isMapUnderscoreToCamelCase()).isTrue();
        assertThat(factory.getConfiguration().getInterceptors()).containsExactly(affectedRows);
        assertThat(factory.getConfiguration().hasMapper(AdditionalMapper.class)).isTrue();
        assertThat(factory.getConfiguration().hasStatement(AdditionalMapper.class.getName() + ".selectValue"))
                .isTrue();
    }

    @Test
    @DisplayName("Installs Affected Rows Interceptor In Default Non-Web MyBatis Factory")
    void installsAffectedRowsInterceptorInDefaultNonWebMyBatisFactory() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                CoreJacksonAutoConfiguration.class,
                                MybatisAutoConfiguration.class,
                                CoreMyBatisAutoConfiguration.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SqlSessionFactory.class);
                    assertThat(
                            context.getBean(SqlSessionFactory.class)
                                    .getConfiguration()
                                    .getInterceptors())
                            .anyMatch(AffectedRowsInterceptor.class::isInstance);
                });
    }

    @Test
    @DisplayName("Rejects Batch As Default Executor")
    void rejectsBatchAsDefaultExecutor() {
        Configuration configuration = new Configuration();
        configuration.setDefaultExecutorType(ExecutorType.BATCH);

        assertThatThrownBy(
                () -> new CoreMyBatisAutoConfiguration()
                        .affectedRowsExecutorTypeCustomizer()
                        .customize(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持 MyBatis 默认 BATCH");
    }

    @Test
    @DisplayName("Rejects MyBatis Enums Without BaseEnum")
    void rejectsMyBatisEnumsWithoutBaseEnum() {
        TypeHandlerRegistry handlers = new TypeHandlerRegistry();
        handlers.setDefaultEnumTypeHandler(CoreEnumTypeHandler.class);

        Assertions.assertThatThrownBy(() -> handlers.getTypeHandler(OrdinaryState.class))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BaseEnum");

        assertThat(handlers.getTypeHandler(PersistentState.class)).isInstanceOf(CoreEnumTypeHandler.class);
    }

    /** 只用于验证附加会话工厂加载明确指定的 Mapper XML。 */
    @SuppressWarnings("unused") // Mapper XML 通过方法名绑定该测试语句。
    interface AdditionalMapper {

        /** @return 固定测试值 */
        int selectValue();
    }

    /** 只用于验证普通枚举不能进入 MyBatis。 */
    private enum OrdinaryState {
        ENABLED
    }

    /** 只用于验证 `BaseEnum` 可以使用全局 MyBatis 枚举处理器。 */
    private enum PersistentState implements BaseEnum {
        ENABLED;

        @Override
        public String value() {
            return name();
        }

        @Override
        public String description() {
            return "启用";
        }
    }
}
