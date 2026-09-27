package com.sca4d.web.realtime.ticket;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.SessionLifecycle;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.utils.TextUtils;
import com.sca4d.web.realtime.config.WebRealtimeInfrastructureNames;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 把短期一次性 WebSocket 票据保存到 Redis，并在握手时原子读取和删除。 */
@ApiStatus.Internal
public class RedisTicketService implements WebRealtimeTicketService {

    private static final DefaultRedisScript<String> CONSUME = consumeScript();

    private final @NotNull RedisTemplate<Key, String> redis;

    private final @NotNull SessionLifecycle sessions;

    private final @NotNull WebRealtimeProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull SecureRandom random;

    private final @NotNull WebRealtimeInfrastructureNames names;

    /** 创建一次性票据签发和消费服务。 */
    public RedisTicketService(
            @NotNull RedisTemplate<Key, String> redis,
            @NotNull SessionLifecycle sessions,
            @NotNull WebRealtimeInfrastructureNames names,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock) {
        this.redis = Objects.requireNonNull(redis, "Redis 操作不能为空");
        this.sessions = Objects.requireNonNull(sessions, "SessionLifecycle 不能为空");
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        this.clock = Objects.requireNonNull(clock, "Web Realtime 时钟不能为空");
        this.random = new SecureRandom();
        this.names = Objects.requireNonNull(names, "Web Realtime 基础设施名称不能为空");
    }

    /** 签发绑定 SessionId 和 WebSocket 入口路径的随机票据。 */
    @Override
    public @NotNull WebRealtimeTicket issue(
            @NotNull Session session,
            @NotNull String endpointPath) {
        Session checkedSession = Objects.requireNonNull(session, "Session 不能为空");
        String checkedPath = path(endpointPath);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.getTicketTtl());
        String value = checkedSession.id().value() + '\n' + checkedPath;
        redis.opsForValue().set(names.ticket(ticket), value, properties.getTicketTtl());
        return new WebRealtimeTicket(ticket, expiresAt);
    }

    /** 原子消费票据，并重新读取仍在线的权威 Session。 */
    @NotNull
    Optional<Session> consume(@NotNull String ticket, @NotNull String endpointPath) {
        String checkedTicket = TextUtils.requireText(ticket, "Web Realtime 票据");
        if (checkedTicket.length() > 128) {
            return Optional.empty();
        }
        String stored = Objects.toString(
                redis.execute(CONSUME, List.of(names.ticket(checkedTicket))),
                "");
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        String[] fields = stored.split("\\n", -1);
        if (fields.length != 2 || !path(endpointPath).equals(fields[1])) {
            return Optional.empty();
        }
        return sessions.resolve(new SessionId(fields[0]));
    }

    /** 校验 App 配置的 WebSocket 路径。 */
    private static @NotNull String path(@NotNull String value) {
        String checked = TextUtils.requireText(value, "WebSocket 入口路径");
        if (!checked.startsWith("/") || checked.contains("?") || checked.contains("#")) {
            throw new IllegalArgumentException("WebSocket 入口必须是以 / 开头且不含查询或片段的路径");
        }
        return checked;
    }

    /** 从 classpath 加载一次性票据原子消费脚本。 */
    private static @NotNull DefaultRedisScript<String> consumeScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/web-realtime/ticket-consume.lua"));
        script.setResultType(String.class);
        return script;
    }
}
