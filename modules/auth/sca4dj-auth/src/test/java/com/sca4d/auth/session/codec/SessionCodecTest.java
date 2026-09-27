package com.sca4d.auth.session.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.auth.testing.SessionTestFixture;
import com.sca4d.core.api.authentication.AuthenticationFactorKind;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class SessionCodecTest {

    private static final String LEGACY_SESSION_WIRE = """
            {"formatVersion":2,"id":"session-1","subject":{"subjectId":11,"type":"HUMAN"},"account":{"accountId":22,"subjectId":11,"realmKey":"test"},"principals":[{"type":"test.principal","payload":"eyJpZCI6MzMsImZpZWxkIjoicHJpbmNpcGFsLWZpZWxkIn0="}],"authentications":[{"accountId":22,"methodCode":"password","factorKinds":["KNOWLEDGE","POSSESSION"],"authenticatedAt":"2026-07-22T00:00:00Z"}],"resources":[{"principalId":33,"resource":{"type":"test.resource","payload":"eyJ0eXBlIjoib3BlcmF0aW9uIiwiaWQiOiJwbGF0Zm9ybS5hY2NvdW50LnJlYWQiLCJmaWVsZCI6InJlc291cmNlLWZpZWxkIn0="}}],"issuedAt":"2026-07-22T00:00:00Z","expiresAt":"2026-07-22T01:00:00Z"}
            """;

    @Test
    @DisplayName("Rejects Former Session Wire Version")
    void rejectsFormerSessionWireVersion() {
        assertThatThrownBy(() -> SessionTestFixture.codec().decode(LEGACY_SESSION_WIRE))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("未知 Session wire 版本: 2");
    }

    @Test
    @DisplayName("Round trips complete sessions without external loading")
    void roundTripsConcreteTypesFieldsResourcesAndAuthenticationFactsWithoutExternalLoading() throws Exception {
        AtomicInteger principalDecodes = new AtomicInteger();
        AtomicInteger resourceDecodes = new AtomicInteger();
        SessionCodec codec = SessionTestFixture.codec(principalDecodes, resourceDecodes);
        Session original = SessionTestFixture.session(SessionTestFixture.NOW.plusSeconds(3600));

        String encoded = codec.encode(original);
        Session restored = codec.decode(encoded);

        ObjectNode wire = (ObjectNode) SessionTestFixture.OBJECT_MAPPER.readTree(encoded);
        assertThat(wire.get("subject").fieldNames()).toIterable().containsExactlyInAnyOrder("subjectId", "type");
        assertThat(wire.withArray("principals").get(0).get("version").intValue()).isEqualTo(1);
        assertThat(wire.get("account").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("accountId", "subjectId", "realmKey");
        assertThat(restored.subject().subjectId()).isEqualTo(original.subject().subjectId());
        assertThat(restored.subject().type()).isEqualTo(original.subject().type());
        assertThat(restored.account().accountId()).isEqualTo(original.account().accountId());
        assertThat(restored.account().subjectId()).isEqualTo(original.account().subjectId());
        assertThat(restored.account().realmKey()).isEqualTo(original.account().realmKey());
        assertThat(restored.principals()).singleElement().isExactlyInstanceOf(SessionTestFixture.TestPrincipal.class);
        SessionTestFixture.TestPrincipal principal = (SessionTestFixture.TestPrincipal) restored.principals()
                .getFirst();
        assertThat(principal.field()).isEqualTo("principal-field");
        assertThat(restored.resources(principal.principalId())).singleElement()
                .isExactlyInstanceOf(SessionTestFixture.TestResource.class);
        assertThat(((SessionTestFixture.TestResource) restored.resources(principal.principalId()).getFirst()).field())
                .isEqualTo("resource-field");
        assertThat(restored.authentications()).singleElement().satisfies(authentication -> {
            assertThat(authentication.methodCode()).isEqualTo(TreeCode.of("AUTHENTICATION/PASSWORD"));
            assertThat(authentication.factorKinds())
                    .containsExactlyInAnyOrder(AuthenticationFactorKind.KNOWLEDGE, AuthenticationFactorKind.POSSESSION);
            assertThat(authentication.authenticatedAt()).isEqualTo(SessionTestFixture.NOW);
        });
        assertThat(principalDecodes).hasValue(1);
        assertThat(resourceDecodes).hasValue(1);
    }

    @Test
    @DisplayName("Round Trips Ten Thousand Compact Principal IDs As One Set")
    void roundTripsTenThousandCompactPrincipalIdsAsOneSet() throws Exception {
        AtomicInteger principalDecodes = new AtomicInteger();
        AtomicInteger resourceDecodes = new AtomicInteger();
        SessionCodec codec = SessionTestFixture.codec(principalDecodes, resourceDecodes);
        Session original = SessionTestFixture.sessionWithCompactIds(
                SessionTestFixture.NOW.plusSeconds(3600),
                10_000);

        String encoded = codec.encode(original);
        Session restored = codec.decode(encoded);

        ObjectNode wire = (ObjectNode) SessionTestFixture.OBJECT_MAPPER.readTree(encoded);
        assertThat(wire.withArray("principalIdSets")).hasSize(1);
        assertThat(wire.withArray("resourceIdSets")).hasSize(1);
        assertThat(wire.withArray("principalIdSets").get(0).get("version").intValue()).isEqualTo(1);
        assertThat(wire.withArray("resourceIdSets").get(0).get("version").intValue()).isEqualTo(1);
        assertThat(restored.principalIdSets()).singleElement().satisfies(principalIds -> {
            assertThat(principalIds.principalType()).isEqualTo(SessionTestFixture.TestPrincipal.class);
            assertThat(principalIds.ids().size()).isEqualTo(10_000);
            assertThat(principalIds.ids().contains(1_927_273_629L)).isTrue();
            assertThat(principalIds.ids().contains(1_927_283_628L)).isTrue();
        });
        Principal principal = restored.principals().getFirst();
        assertThat(restored.resourceIdSets(principal.principalId())).singleElement().satisfies(resourceIds -> {
            assertThat(resourceIds.resourceType()).isEqualTo(TreeCode.of("PROJECT"));
            assertThat(resourceIds.ids().toArray()).containsExactly(1_927_273_629L, 1_927_273_630L);
        });
        assertThat(
                restored.grants(
                        principal.principalId(),
                        new SessionTestFixture.TestResource("project", "1927273629", "ignored")))
                .isTrue();
        assertThat(principalDecodes).hasValue(1);
        assertThat(resourceDecodes).hasValue(1);
    }

    @Test
    @DisplayName("Rejects Missing Duplicate And Ambiguous Codecs")
    void rejectsMissingDuplicateAndAmbiguousCodecs() {
        Session session = SessionTestFixture.session(SessionTestFixture.NOW.plusSeconds(3600));
        SessionCodec missingResourceCodec = new SessionCodec(
                SessionTestFixture.OBJECT_MAPPER,
                List.of(new SessionTestFixture.TestPrincipalCodec(new AtomicInteger())),
                List.of());

        assertThatThrownBy(() -> missingResourceCodec.encode(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 Session Codec");
        assertThatThrownBy(
                () -> new SessionCodec(
                        SessionTestFixture.OBJECT_MAPPER,
                        List.of(
                                new SessionTestFixture.TestPrincipalCodec(new AtomicInteger()),
                                new SessionTestFixture.TestPrincipalCodec(new AtomicInteger())),
                        List.of(new SessionTestFixture.TestResourceCodec(new AtomicInteger()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复 Principal Codec 值类型");
        assertThatThrownBy(
                () -> new SessionCodec(
                        SessionTestFixture.OBJECT_MAPPER,
                        List.of(
                                new SessionTestFixture.TestPrincipalCodec(new AtomicInteger()),
                                new AlternatePrincipalCodec()),
                        List.of(new SessionTestFixture.TestResourceCodec(new AtomicInteger()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复 Principal Codec 类型");
    }

    @Test
    @DisplayName("Rejects Unknown Version Codec And Corrupt Payload")
    void rejectsUnknownVersionCodecAndCorruptPayload() throws Exception {
        SessionCodec codec = SessionTestFixture.codec();
        String encoded = codec.encode(
                SessionTestFixture.session(
                        SessionTestFixture.NOW.plusSeconds(3600)));

        ObjectNode version = (ObjectNode) SessionTestFixture.OBJECT_MAPPER.readTree(encoded);
        version.put("formatVersion", 999);
        assertDecodeFails(codec, version);

        ObjectNode unknown = (ObjectNode) SessionTestFixture.OBJECT_MAPPER.readTree(encoded);
        ((ObjectNode) unknown.get("subject")).put("type", "UNKNOWN");
        assertDecodeFails(codec, unknown);

        ObjectNode corrupt = (ObjectNode) SessionTestFixture.OBJECT_MAPPER.readTree(encoded);
        ((ObjectNode) corrupt.get("account")).put("accountId", 0);
        assertDecodeFails(codec, corrupt);

        assertThatThrownBy(() -> codec.decode("{broken")).isInstanceOf(IllegalStateException.class)
                .hasMessage("Session 解码失败");
    }

    @Test
    @DisplayName("Rejects Unknown Or Corrupt Compact ID Sets")
    void rejectsUnknownOrCorruptCompactIdSets() throws Exception {
        SessionCodec codec = SessionTestFixture.codec();
        String encoded = codec.encode(
                SessionTestFixture.sessionWithCompactIds(
                        SessionTestFixture.NOW.plusSeconds(3600),
                        10));

        ObjectNode unknownType = modifyCompactPrincipalSet(
                encoded,
                compactSet -> compactSet.put("type", "unknown.principal"));
        assertDecodeFails(codec, unknownType);

        ObjectNode unknownEncoding = modifyCompactPrincipalSet(
                encoded,
                compactSet -> compactSet.put("encoding", "unknown"));
        assertDecodeFails(codec, unknownEncoding);

        ObjectNode badBase64 = modifyCompactPrincipalSet(
                encoded,
                compactSet -> compactSet.put("payload", "***"));
        assertDecodeFails(codec, badBase64);

        ObjectNode trailingPayload = modifyCompactPrincipalSet(encoded, compactSet -> {
            byte[] payload = Base64.getDecoder().decode(compactSet.get("payload").asText());
            compactSet.put(
                    "payload",
                    Base64.getEncoder().encodeToString(Arrays.copyOf(payload, payload.length + 1)));
        });
        assertDecodeFails(codec, trailingPayload);
    }

    /** 返回修改第一组紧凑 Principal 标识后的完整 wire。 */
    private static ObjectNode modifyCompactPrincipalSet(
            String encoded,
            Consumer<ObjectNode> modification) throws Exception {
        ObjectNode wire = (ObjectNode) SessionTestFixture.OBJECT_MAPPER.readTree(encoded);
        modification.accept((ObjectNode) wire.withArray("principalIdSets").get(0));
        return wire;
    }

    private static void assertDecodeFails(SessionCodec codec, ObjectNode wire) throws Exception {
        String encoded = SessionTestFixture.OBJECT_MAPPER.writeValueAsString(wire);
        assertThatThrownBy(() -> codec.decode(encoded)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Session 解码失败");
    }

    private static final class AlternatePrincipal implements Principal {
        @Override
        public long principalId() {
            return 99;
        }
    }

    private static final class AlternatePrincipalCodec implements PrincipalCodec<AlternatePrincipal> {
        @Override
        public @NotNull TreeCode type() {
            return TreeCode.of("test.principal");
        }

        @Override
        public Class<AlternatePrincipal> valueType() {
            return AlternatePrincipal.class;
        }

        @Override
        public byte[] encode(AlternatePrincipal value) {
            return new byte[] {1};
        }

        @Override
        public AlternatePrincipal decode(byte[] payload) {
            return new AlternatePrincipal();
        }
    }
}
