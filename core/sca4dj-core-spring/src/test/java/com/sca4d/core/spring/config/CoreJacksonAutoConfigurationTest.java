package com.sca4d.core.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sca4d.core.spring.json.Sca4djJson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 验证通用 JSON 映射器在字符串与 Java 64 位整数之间双向转换。 */
class CoreJacksonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(
                            CoreJacksonAutoConfiguration.class,
                            JacksonAutoConfiguration.class));

    @Test
    @DisplayName("Serializes primitive and boxed longs as strings and reads them back")
    void serializesLongsAsStringsAndReadsThemBack() {
        contextRunner.run(context -> {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            String json = objectMapper.writeValueAsString(
                    new LongPayload(9_007_199_254_740_993L, 2_081_571_083_525_292_032L));
            LongPayload restored = objectMapper.readValue(
                    "{\"primitiveValue\":\"9007199254740993\",\"boxedValue\":\"2081571083525292032\"}",
                    LongPayload.class);

            assertThat(json).isEqualTo(
                    "{\"primitiveValue\":\"9007199254740993\",\"boxedValue\":\"2081571083525292032\"}");
            assertThat(restored.getPrimitiveValue()).isEqualTo(9_007_199_254_740_993L);
            assertThat(restored.getBoxedValue()).isEqualTo(2_081_571_083_525_292_032L);
        });
    }

    @Test
    @DisplayName("Installs one shared non-default mapper for scaffold data")
    void installsSharedNonDefaultScaffoldMapper() {
        contextRunner.run(context -> {
            ObjectMapper httpMapper = context.getBean(ObjectMapper.class);
            ObjectMapper sca4djMapper = context.getBean("sca4djObjectMapper", ObjectMapper.class);
            AbstractBeanDefinition definition = (AbstractBeanDefinition) context.getBeanFactory()
                    .getBeanDefinition("sca4djObjectMapper");

            assertThat(context.getBeansOfType(ObjectMapper.class)).hasSize(2);
            assertThat(sca4djMapper).isNotSameAs(httpMapper);
            assertThat(sca4djMapper.getRegisteredModuleIds()).contains("jackson-datatype-jsr310");
            assertThat(sca4djMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                    .isFalse();
            assertThat(definition.isDefaultCandidate()).isFalse();
        });
    }

    @Test
    @DisplayName("Uses application mapper qualified for scaffold data")
    void usesApplicationMapperQualifiedForScaffoldData() {
        contextRunner
                .withUserConfiguration(CustomSca4djJsonConfiguration.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean("sca4djObjectMapper");
                    assertThat(context.getBean("customSca4djObjectMapper", ObjectMapper.class))
                            .isSameAs(
                                    context.getBeansWithAnnotation(Sca4djJson.class)
                                            .get("customSca4djObjectMapper"));
                });
    }

    /** 声明替换脚手架内部 JSON 映射器的应用测试配置。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomSca4djJsonConfiguration {

        /** @return 应用显式声明的脚手架内部 JSON 映射器 */
        @Bean
        @Sca4djJson
        ObjectMapper customSca4djObjectMapper() {
            return new ObjectMapper();
        }
    }

    /** 保存 primitive 和 boxed 64 位整数的 JSON 测试对象。 */
    @SuppressWarnings("unused") // Jackson 反射调用空构造器和 setter 完成反序列化。
    public static class LongPayload {

        private long primitiveValue;

        private @Nullable Long boxedValue;

        /** 创建供 Jackson 反序列化使用的空对象。 */
        public LongPayload() {
        }

        /** @param primitiveValue primitive 64 位整数 @param boxedValue boxed 64 位整数 */
        LongPayload(long primitiveValue, @NotNull Long boxedValue) {
            this.primitiveValue = primitiveValue;
            this.boxedValue = boxedValue;
        }

        /** @return primitive 64 位整数 */
        public long getPrimitiveValue() {
            return primitiveValue;
        }

        /** @param primitiveValue primitive 64 位整数 */
        public void setPrimitiveValue(long primitiveValue) {
            this.primitiveValue = primitiveValue;
        }

        /** @return boxed 64 位整数 */
        public @Nullable Long getBoxedValue() {
            return boxedValue;
        }

        /** @param boxedValue boxed 64 位整数 */
        public void setBoxedValue(@Nullable Long boxedValue) {
            this.boxedValue = boxedValue;
        }
    }
}
