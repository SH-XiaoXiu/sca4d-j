package com.sca4d.auth.testing;

import static com.sca4d.core.api.name.NameFormat.SLASH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.session.codec.SessionCodec;
import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionId;
import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.authentication.principalsession.ResourceCodec;
import com.sca4d.core.api.authentication.principalsession.ResourceIdSet;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.identity.SubjectType;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

/**
 * 为 Auth 各功能包测试创建一致的 `Session` 和编解码器。
 */
@SuppressWarnings({"ClassCanBeRecord", "unused"})
public class SessionTestFixture {

    /**
     * 各 Session 测试共用的固定时间。
     */
    public static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    /**
     * 测试 Wire 数据使用的 JSON 映射器。
     */
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private SessionTestFixture() {
    }

    /**
     * @return 使用测试 Principal 与 Resource 类型的编解码器
     */
    public static SessionCodec codec() {
        return codec(new AtomicInteger(), new AtomicInteger());
    }

    /**
     * @return 记录解码次数的测试编解码器
     */
    public static SessionCodec codec(
            AtomicInteger principalDecodes,
            AtomicInteger resourceDecodes) {
        return new SessionCodec(
                OBJECT_MAPPER,
                List.of(new TestPrincipalCodec(principalDecodes)),
                List.of(new TestResourceCodec(resourceDecodes)));
    }

    /**
     * @return 在指定时间过期的测试 Session
     */
    public static Session session(Instant expiresAt) {
        Subject subject = new Subject(11, SubjectType.HUMAN);
        Account account = new Account(22, subject.subjectId(), new RealmKey("test"));
        TestPrincipal principal = new TestPrincipal(33, "principal-field");
        return new Session(
                new SessionId("session-1"),
                subject,
                account,
                List.of(principal),
                List.of(),
                List.of(
                        new Authentication(
                                account.accountId(),
                                TreeCode.ofSlash("AUTHENTICATION/PASSWORD"),
                                Set.of(AuthenticationFactorKind.KNOWLEDGE, AuthenticationFactorKind.POSSESSION),
                                NOW)),
                Map.of(
                        principal.principalId(),
                        List.of(new TestResource("operation", "platform:account:read", "resource-field"))),
                Map.of(),
                NOW,
                expiresAt);
    }

    /**
     * @param expiresAt        Session 过期时间
     * @param principalIdCount 从 1927273629 开始连续生成的紧凑 Principal 标识数量
     * @return 同时包含完整 Principal、紧凑 Principal 标识和数字 Resource 标识的测试 Session
     */
    public static Session sessionWithCompactIds(
            Instant expiresAt,
            int principalIdCount) {
        if (principalIdCount <= 0) {
            throw new IllegalArgumentException("紧凑 Principal 标识数量必须为正数");
        }
        Session base = session(expiresAt);
        List<Long> principalIds = LongStream.range(0, principalIdCount)
                .map(index -> 1_927_273_629L + index)
                .boxed()
                .toList();
        Principal principal = base.principals().getFirst();
        return new Session(
                base.id(),
                base.subject(),
                base.account(),
                base.principals(),
                List.of(new PrincipalIdSet<>(TestPrincipal.class, principalIds)),
                base.authentications(),
                Map.of(principal.principalId(), base.resources(principal.principalId())),
                Map.of(
                        principal.principalId(),
                        List.of(
                                new ResourceIdSet(
                                        TreeCode.ofSegment("PROJECT"),
                                        List.of(1_927_273_629L, 1_927_273_630L)))),
                base.issuedAt(),
                base.expiresAt());
    }

    private static byte[] write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(value);
        } catch (IOException exception) {
            throw new IllegalArgumentException("测试 payload 编码失败", exception);
        }
    }

    private static <T> T read(byte[] payload, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(payload, type);
        } catch (IOException exception) {
            throw new IllegalArgumentException("测试 payload 解码失败", exception);
        }
    }

    /**
     * 测试使用的 Principal。
     */
    public static class TestPrincipal implements Principal {
        private final long id;

        private final String field;

        /**
         * 使用指定标识和测试字段创建 Principal。
         */
        public TestPrincipal(long id, String field) {
            this.id = id;
            this.field = field;
        }

        @Override
        public long principalId() {
            return id;
        }

        /**
         * @return 编解码测试需要保留的字段
         */
        public String field() {
            return field;
        }
    }

    /**
     * 测试使用的 Resource。
     */
    public static class TestResource implements Resource {
        private final TreeCode type;

        private final String id;

        private final String field;

        /**
         * 使用指定类型、标识和测试字段创建 Resource。
         */
        public TestResource(String type, String id, String field) {
            this.type = TreeCode.ofSegment(type);
            this.id = id;
            this.field = field;
        }

        @Override
        public @NotNull TreeCode type() {
            return type;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        /**
         * @return 编解码测试需要保留的字段
         */
        public String field() {
            return field;
        }
    }

    /**
     * 测试 Principal 的计数编解码器。
     */
    public static class TestPrincipalCodec implements PrincipalCodec<TestPrincipal> {
        private final AtomicInteger decodes;

        /**
         * 使用指定计数器记录 Principal 解码次数。
         */
        public TestPrincipalCodec(AtomicInteger decodes) {
            this.decodes = decodes;
        }

        @Override
        public @NotNull TreeCode type() {
            return TreeCode.of("test.principal");
        }

        @Override
        public Class<TestPrincipal> valueType() {
            return TestPrincipal.class;
        }

        @Override
        public byte[] encode(TestPrincipal value) {
            return write(new PrincipalPayload(value.principalId(), value.field()));
        }

        @Override
        public TestPrincipal decode(byte[] payload) {
            decodes.incrementAndGet();
            PrincipalPayload value = read(payload, PrincipalPayload.class);
            return new TestPrincipal(value.id, value.field);
        }
    }

    /**
     * 测试 Resource 的计数编解码器。
     */
    public static class TestResourceCodec implements ResourceCodec<TestResource> {
        private final AtomicInteger decodes;

        /**
         * 使用指定计数器记录 Resource 解码次数。
         */
        public TestResourceCodec(AtomicInteger decodes) {
            this.decodes = decodes;
        }

        @Override
        public @NotNull TreeCode type() {
            return TreeCode.of("test.resource");
        }

        @Override
        public Class<TestResource> valueType() {
            return TestResource.class;
        }

        @Override
        public byte[] encode(TestResource value) {
            return write(new ResourcePayload(value.type().format(SLASH), value.id(), value.field()));
        }

        @Override
        public TestResource decode(byte[] payload) {
            decodes.incrementAndGet();
            ResourcePayload value = read(payload, ResourcePayload.class);
            return new TestResource(value.type, value.id, value.field);
        }
    }

    static class PrincipalPayload {
        public long id;

        public String field;

        public PrincipalPayload() {
        }

        PrincipalPayload(long id, String field) {
            this.id = id;
            this.field = field;
        }
    }

    static class ResourcePayload {
        public String type;

        public String id;

        public String field;

        public ResourcePayload() {
        }

        ResourcePayload(String type, String id, String field) {
            this.type = type;
            this.id = id;
            this.field = field;
        }
    }
}
