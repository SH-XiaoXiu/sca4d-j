package com.sca4d.core.api.authentication;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次成功登录建立的 Session 和仅向调用方返回一次的 Token 集合。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class EstablishedSession {

    private final SessionTokens tokens;

    private final Session session;

    /**
     * 保存一次登录建立的 Session 及其访问 Token。
     *
     * @param tokens 向本次登录调用方交付的 Token 集合
     * @param session 已建立的认证 Session
     */
    public EstablishedSession(@NotNull SessionTokens tokens, @NotNull Session session) {
        this.tokens = Objects.requireNonNull(tokens, "Session Token 集合不能为空");
        this.session = Objects.requireNonNull(session, "Session 不能为空");
        if (!tokens.sessionId().equals(session.id().value())
                || !tokens.sessionExpiresAt().equals(session.expiresAt())) {
            throw new IllegalArgumentException("Token 集合与 Session 不匹配");
        }
    }

    /** @return 仅向本次登录调用方返回的 Token 集合 */
    public @NotNull SessionTokens tokens() {
        return tokens;
    }

    /**
     * 读取 Token 对应的服务端认证状态。
     *
     * @return 本次登录建立的认证 Session
     */
    public @NotNull Session session() {
        return session;
    }
}
