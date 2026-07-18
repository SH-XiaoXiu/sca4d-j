package com.sca4d.file.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.exception.BizException;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdValidator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** 验证文件签名规则注册、重复场景拒绝和查找。 */
class FileSignatureRegistryTest {

    @Test
    void shouldUseSameSceneContractForRequestAndRegistry() throws NoSuchFieldException {
        BizId declaration = FileSignatureItem.class.getDeclaredField("scene").getAnnotation(BizId.class);
        BizIdValidator validator = new BizIdValidator();
        validator.initialize(declaration);
        String maxLengthScene = "a.b." + "c".repeat(124);
        String tooLongScene = "a.b." + "c".repeat(125);

        assertThat(validator.isValid("im.message.attachment", null)).isTrue();
        assertThat(validator.isValid("im-message-attachment", null)).isFalse();
        assertThat(validator.isValid(maxLengthScene, null)).isTrue();
        assertThat(validator.isValid(tooLongScene, null)).isFalse();
        new FileSignatureRegistry(List.of(policy("im.message.attachment")));
        new FileSignatureRegistry(List.of(policy(maxLengthScene)));
        assertThatThrownBy(() -> new FileSignatureRegistry(List.of(policy("im-message-attachment"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文件签名场景格式错误");
        assertThatThrownBy(() -> new FileSignatureRegistry(List.of(policy(tooLongScene))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文件签名场景格式错误");
    }

    @Test
    void shouldRejectDuplicateSceneAtStartup() {
        FileSignaturePolicy first = policy("iam.default-profile.avatar");
        FileSignaturePolicy second = policy("iam.default-profile.avatar");

        assertThatThrownBy(() -> new FileSignatureRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复文件签名场景");
    }

    @Test
    void shouldDenyUnknownScene() {
        FileSignatureRegistry registry = new FileSignatureRegistry(List.of());

        assertThatThrownBy(() -> registry.require("unknown.resource.file"))
                .isInstanceOf(BizException.class);
    }

    /**
     * 创建只声明单个场景的文件签名测试策略。
     *
     * @param scene 待注册的场景编码
     * @return 不执行额外授权逻辑的测试策略
     */
    private @NotNull FileSignaturePolicy policy(@NotNull String scene) {
        return new FileSignaturePolicy() {
            @Override
            public @NotNull Set<String> scenes() {
                return Set.of(scene);
            }

            @Override
            public void authorize(@NotNull CurrentRequest auth, @NotNull List<FileSignatureItem> items) {
            }
        };
    }
}
