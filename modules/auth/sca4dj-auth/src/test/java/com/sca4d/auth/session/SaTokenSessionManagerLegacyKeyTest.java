package com.sca4d.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.auth.testing.SessionTestFixture;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.permission.OperationCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

/** 验证 Redis 中保存的旧版小写 realmKey Session 按未登录处理，不向调用方抛出异常。 */
class SaTokenSessionManagerLegacyKeyTest {

    private static final String TOKEN = "legacy-token";

    private final SessionCodec codec = SessionTestFixture.codec();

    private final StpLogic stpLogic = mock(StpLogic.class);

    private final RedisSessionGeneration generations = mock(RedisSessionGeneration.class);

    private final OperationCatalog operations = new OperationCatalog(List.of(), List.of());

    private final SaTokenSessionManager sessions = new SaTokenSessionManager(
            codec,
            mock(SessionRefreshService.class),
            generations,
            operations,
            mock(RedisSessionTokenIndex.class),
            stpLogic,
            Clock.fixed(SessionTestFixture.NOW.plusSeconds(60), ZoneOffset.UTC));

    @Test
    @DisplayName("Treats A Stored Session With A Lowercase Realm Key As Not Logged In")
    void treatsAStoredSessionWithALowercaseRealmKeyAsNotLoggedIn() {
        Session session = SessionTestFixture.session(SessionTestFixture.NOW.plusSeconds(3600));
        String legacy = codec.encode(session).replace("\"realmKey\":\"TEST\"", "\"realmKey\":\"test\"");
        assertThat(legacy).contains("\"realmKey\":\"test\"");
        SaSession tokenSession = new SaSession("legacy")
                .set(SaTokenSessionManager.SESSION_ATTRIBUTE, legacy)
                .set(SaTokenSessionManager.INVALIDATION_GENERATION_ATTRIBUTE, 0L)
                .set(SaTokenSessionManager.LOADED_GENERATION_ATTRIBUTE, 0L)
                .set(SaTokenSessionManager.OPERATION_CATALOG_FINGERPRINT_ATTRIBUTE, operations.fingerprint());
        when(stpLogic.getLoginIdByToken(TOKEN)).thenReturn(session.account().accountId());
        when(stpLogic.getTokenSessionByToken(TOKEN, false)).thenReturn(tokenSession);

        assertThat(sessions.current(TOKEN)).isEmpty();

        // 残留登录状态被注销，后续请求不会反复解码同一条旧数据。
        verify(stpLogic).logoutByTokenValue(TOKEN);
        verify(generations, never()).current(session.account().accountId());
    }
}
