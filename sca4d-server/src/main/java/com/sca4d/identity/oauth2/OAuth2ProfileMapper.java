package com.sca4d.identity.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.sca4d.identity.external.ExternalIdentityProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 按配置的 JSONPath 从 OAuth2 主 Profile 和可选辅助 Profile 读取标准身份字段。 */
@Component
public class OAuth2ProfileMapper {

    /**
     * 主 Profile 优先，指定路径缺失时读取辅助 Profile；数组响应由配置中的 JSONPath 显式选择元素。
     *
     * @return 标准身份资料及所有配置但未读取到的字段
     */
    public @NotNull OAuth2ProfilePreview map(
            @NotNull JsonNode profile,
            @Nullable JsonNode auxiliaryProfile,
            @NotNull OAuth2ProfileMapping mapping) {
        DocumentContext primary = JsonPath.parse(profile.toString());
        DocumentContext auxiliary = auxiliaryProfile == null
                ? null
                : JsonPath.parse(auxiliaryProfile.toString());
        List<OAuth2ProfileField> missing = new ArrayList<>();

        String subject = text(primary, auxiliary, mapping.getSubjectPath());
        if (subject == null) {
            missing.add(OAuth2ProfileField.SUBJECT);
        }
        String displayName = text(primary, auxiliary, mapping.getDisplayNamePath());
        if (mapping.getDisplayNamePath() != null && displayName == null) {
            missing.add(OAuth2ProfileField.DISPLAY_NAME);
        }
        String email = text(primary, auxiliary, mapping.getEmailPath());
        if (mapping.getEmailPath() != null && email == null) {
            missing.add(OAuth2ProfileField.EMAIL);
        }
        Boolean emailVerified = bool(primary, auxiliary, mapping.getEmailVerifiedPath());
        if (mapping.getEmailVerifiedPath() != null && emailVerified == null) {
            missing.add(OAuth2ProfileField.EMAIL_VERIFIED);
        }
        String avatar = text(primary, auxiliary, mapping.getAvatarPath());
        if (mapping.getAvatarPath() != null && avatar == null) {
            missing.add(OAuth2ProfileField.AVATAR);
        }
        return new OAuth2ProfilePreview(
                new ExternalIdentityProfile(
                        subject,
                        displayName,
                        email,
                        Boolean.TRUE.equals(emailVerified),
                        avatar),
                List.copyOf(missing));
    }

    /** @return 主响应或辅助响应中路径指向的非空标量文本 */
    private @Nullable String text(
            @NotNull DocumentContext primary,
            @Nullable DocumentContext auxiliary,
            @Nullable String path) {
        Object value = read(primary, path);
        if (value == null && auxiliary != null) {
            value = read(auxiliary, path);
        }
        if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /** @return 主响应或辅助响应中路径指向的布尔值 */
    private @Nullable Boolean bool(
            @NotNull DocumentContext primary,
            @Nullable DocumentContext auxiliary,
            @Nullable String path) {
        Object value = read(primary, path);
        if (value == null && auxiliary != null) {
            value = read(auxiliary, path);
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text
                && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
            return Boolean.valueOf(text);
        }
        return null;
    }

    /** @return 路径读取结果；未配置或路径不存在时返回 null */
    private @Nullable Object read(@NotNull DocumentContext document, @Nullable String path) {
        if (path == null) {
            return null;
        }
        try {
            return document.read(path);
        } catch (PathNotFoundException exception) {
            return null;
        }
    }
}
