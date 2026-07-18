package com.sca4d.identity.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 验证 OAuth2 对象、数组、辅助 Profile 回退和缺失字段预览。 */
class OAuth2ProfileMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OAuth2ProfileMapper mapper = new OAuth2ProfileMapper();

    @Test
    void shouldMapArrayProfileAndFallbackToAuxiliaryProfile() throws Exception {
        OAuth2ProfilePreview preview = mapper.map(
                objectMapper.readTree("[{\"id\":\"external-1\",\"name\":\"Alice\"}]"),
                objectMapper.readTree("{\"email\":\"alice@example.com\",\"verified\":true}"),
                new OAuth2ProfileMapping(
                        "$[0].id",
                        "$[0].name",
                        "$.email",
                        "$.verified",
                        "$[0].avatar"));

        assertThat(preview.getProfile().getExternalSubject()).isEqualTo("external-1");
        assertThat(preview.getProfile().getDisplayName()).isEqualTo("Alice");
        assertThat(preview.getProfile().getEmail()).isEqualTo("alice@example.com");
        assertThat(preview.getProfile().getEmailVerified()).isTrue();
        assertThat(preview.getMissingFields()).containsExactly(OAuth2ProfileField.AVATAR);
    }

    @Test
    void shouldReportRequiredSubjectAndConfiguredOptionalFieldsWhenMissing() throws Exception {
        OAuth2ProfilePreview preview = mapper.map(
                objectMapper.readTree("{\"name\":{\"nested\":true}}"),
                null,
                new OAuth2ProfileMapping(
                        "$.id", "$.name", "$.email", "$.verified", null));

        assertThat(preview.getProfile().getExternalSubject()).isNull();
        assertThat(preview.getProfile().getDisplayName()).isNull();
        assertThat(preview.getProfile().getEmailVerified()).isFalse();
        assertThat(preview.getMissingFields())
                .containsExactly(
                        OAuth2ProfileField.SUBJECT,
                        OAuth2ProfileField.DISPLAY_NAME,
                        OAuth2ProfileField.EMAIL,
                        OAuth2ProfileField.EMAIL_VERIFIED);
    }
}
