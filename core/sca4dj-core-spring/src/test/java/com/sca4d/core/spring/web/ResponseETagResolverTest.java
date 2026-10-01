package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.spring.web.annotation.ETag;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证响应数据 ETag 声明的解析和失败边界。 */
class ResponseETagResolverTest {

    private final ResponseETagResolver resolver = new ResponseETagResolver();

    @Test
    @DisplayName("解析顶层响应数据的唯一版本声明")
    void resolvesOneTopLevelVersionDeclaration() {
        assertThat(resolver.resolve(new VersionedBody())).contains(new Version(3));
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("ordinary")).isEmpty();
        assertThat(resolver.resolve(List.of(new VersionedBody()))).isEmpty();
    }

    @Test
    @DisplayName("拒绝重复、非公开或签名错误的版本声明")
    void rejectsInvalidVersionDeclarations() {
        assertThatThrownBy(() -> resolver.validate(MultipleVersions.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能声明一个");
        assertThatThrownBy(() -> resolver.validate(NonPublicVersion.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须公开、无参数并返回 Version");
        assertThatThrownBy(() -> resolver.validate(InvalidVersion.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须公开、无参数并返回 Version");
    }

    @Test
    @DisplayName("当前实例没有稳定版本时不发布 ETag")
    void omitsEtagForDynamicInstance() {
        assertThat(resolver.resolve(new DynamicBody())).isEmpty();
    }

    /** 提供一个合法版本声明。 */
    static class VersionedBody {

        /** @return 固定测试版本 */
        @ETag
        public Version revision() {
            return new Version(3);
        }
    }

    /** 提供重复版本声明。 */
    static class MultipleVersions {

        /** @return 第一个测试版本 */
        @ETag
        public Version first() {
            return new Version(1);
        }

        /** @return 第二个测试版本 */
        @ETag
        public Version second() {
            return new Version(2);
        }
    }

    /** 提供非公开版本声明。 */
    static class NonPublicVersion {

        /** @return 固定测试版本 */
        @ETag
        private Version revision() {
            return new Version(3);
        }
    }

    /** 提供返回类型错误的版本声明。 */
    static class InvalidVersion {

        /** @return 非法字符串版本 */
        @ETag
        public String revision() {
            return "3";
        }
    }

    /** 提供没有稳定版本的动态响应实例。 */
    static class DynamicBody {

        /** @return 动态实例不发布 ETag */
        @ETag
        public @Nullable Version revision() {
            return null;
        }
    }
}
