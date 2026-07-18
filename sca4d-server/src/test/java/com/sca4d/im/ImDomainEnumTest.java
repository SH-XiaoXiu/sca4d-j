package com.sca4d.im;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.common.enums.BaseEnum;
import org.junit.jupiter.api.Test;

/** 验证即时消息状态枚举的固定值和解析规则。 */
class ImDomainEnumTest {

    @Test
    void shouldResolveConversationTypeByRequestOrMemberSize() {
        assertThat(ImConversationType.resolve(null, 2)).isEqualTo(ImConversationType.SINGLE);
        assertThat(ImConversationType.resolve(null, 3)).isEqualTo(ImConversationType.GROUP);
        assertThat(ImConversationType.resolve(ImConversationType.SINGLE, 2)).isEqualTo(ImConversationType.SINGLE);
        assertThat(BaseEnum.fromValue(ImConversationType.class, "single"))
                .isEqualTo(ImConversationType.SINGLE);
        assertThat(ImConversationType.SINGLE.supportsMemberSize(3)).isFalse();
        assertThat(ImConversationType.GROUP.supportsMemberSize(3)).isTrue();
    }

    @Test
    void shouldResolveMessageContentType() {
        assertThat(ImMessageContentType.resolve(null)).isEqualTo(ImMessageContentType.TEXT);
        assertThat(ImMessageContentType.resolve(ImMessageContentType.TEXT).requiresTextContent()).isTrue();
        assertThat(BaseEnum.fromValue(ImMessageContentType.class, "image"))
                .isEqualTo(ImMessageContentType.IMAGE);
        assertThat(ImMessageContentType.IMAGE.requiresAttachmentFile()).isTrue();
        assertThat(ImMessageContentType.IMAGE.preview(null)).isEqualTo("图片消息");
    }

    @Test
    void shouldResolveInitialMemberRole() {
        assertThat(ImMemberRole.initialRole(ImConversationType.GROUP, true)).isEqualTo(ImMemberRole.OWNER);
        assertThat(ImMemberRole.initialRole(ImConversationType.GROUP, false)).isEqualTo(ImMemberRole.MEMBER);
        assertThat(ImMemberRole.initialRole(ImConversationType.SINGLE, true)).isEqualTo(ImMemberRole.MEMBER);
    }
}
