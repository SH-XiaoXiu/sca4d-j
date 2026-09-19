package com.sca4d.iam.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.authentication.principalsession.PrincipalCodec;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.persistence.AffectedRowsMismatchException;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.base.PlatformPrincipal;
import com.sca4d.iam.identity.mapper.IdentityMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class IdentityServiceTest {
    private static final AccountIdentifierType EMAIL = new AccountIdentifierType("EMAIL");

    private static final SqlBatches SQL_BATCHES = new SqlBatches(new SqlBatchProperties());

    @Test
    @DisplayName("Normalizer 返回非法值时不访问 Repository")
    void rejectsInvalidNormalizerOutputBeforeRepositoryAccess() {
        assertInvalidNormalizedValue(null, NullPointerException.class);
        assertInvalidNormalizedValue(" ", IllegalArgumentException.class);
        assertInvalidNormalizedValue("x".repeat(321), IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Subject 类型为空时不生成或写入身份数据")
    @SuppressWarnings("DataFlowIssue")
    void rejectsNullSubjectTypeBeforeGeneratingOrWritingIdentity() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdGenerator ids = mock(IdGenerator.class);
        IdentityService identities = new IdentityService(
                new IdentityRepository(mapper, SQL_BATCHES),
                ids,
                new PrincipalTypes(List.of()),
                List.of());

        assertThatThrownBy(() -> identities.createSubject(null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(ids, mapper);
    }

    @Test
    @DisplayName("批量删除和恢复 Principal 时严格匹配类型及全部目标")
    void deletesAndRestoresEveryPrincipalOfTheExactType() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        PrincipalCodec<PlatformPrincipal> codec = platformPrincipalCodec();
        IdentityService identities = new IdentityService(
                new IdentityRepository(mapper, SQL_BATCHES),
                () -> 1L,
                new PrincipalTypes(List.of(codec)),
                List.of());
        List<Long> principalIds = List.of(41L, 42L);
        PrincipalType principalType = new PrincipalType("platform");
        when(
                mapper.updatePrincipals(
                        principalIds,
                        principalType,
                        false,
                        Patch.set(IdentityStatus.DISABLED),
                        Patch.set(true)))
                .thenReturn(2);
        when(
                mapper.updatePrincipals(
                        principalIds,
                        principalType,
                        true,
                        Patch.set(IdentityStatus.ACTIVE),
                        Patch.set(false)))
                .thenReturn(2);

        identities.deletePrincipals(principalIds, PlatformPrincipal.class);
        identities.restorePrincipals(principalIds, PlatformPrincipal.class);

        verify(mapper).updatePrincipals(
                principalIds,
                principalType,
                false,
                Patch.set(IdentityStatus.DISABLED),
                Patch.set(true));
        verify(mapper).updatePrincipals(
                principalIds,
                principalType,
                true,
                Patch.set(IdentityStatus.ACTIVE),
                Patch.set(false));
    }

    @Test
    @DisplayName("批量 Principal 删除缺少任一目标时整体失败")
    void rejectsPartialPrincipalDeletion() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService identities = new IdentityService(
                new IdentityRepository(mapper, SQL_BATCHES),
                () -> 1L,
                new PrincipalTypes(List.of(platformPrincipalCodec())),
                List.of());
        List<Long> principalIds = List.of(41L, 42L);
        when(
                mapper.updatePrincipals(
                        principalIds,
                        new PrincipalType("platform"),
                        false,
                        Patch.set(IdentityStatus.DISABLED),
                        Patch.set(true)))
                .thenReturn(1);

        assertThatThrownBy(() -> identities.deletePrincipals(principalIds, PlatformPrincipal.class))
                .isInstanceOf(AffectedRowsMismatchException.class);
    }

    @Test
    @DisplayName("批量读取空 Principal 集合时不访问 Mapper")
    void returnsEmptyAccountLinksWithoutMapperAccess() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService identities = new IdentityService(
                new IdentityRepository(mapper, SQL_BATCHES),
                () -> 1L,
                new PrincipalTypes(List.of()),
                List.of());

        assertThat(identities.findLinkedAccountIds(List.of())).isEmpty();

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("Principal Query 组合 IAM 条件并保持输入顺序")
    void filtersPrincipalsByDeclaredIamConditionsInInputOrder() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService identities = new IdentityService(
                new IdentityRepository(mapper, SQL_BATCHES),
                () -> 1L,
                new PrincipalTypes(List.of(platformPrincipalCodec())),
                List.of());
        RealmKey realm = new RealmKey("platform");
        PrincipalType principalType = new PrincipalType("platform");
        when(
                mapper.selectPrincipalIds(
                        List.of(42L, 41L, 43L),
                        principalType,
                        IdentityStatus.ACTIVE,
                        realm))
                .thenReturn(List.of(41L, 42L));

        assertThat(
                identities.findPrincipalIds(
                        PrincipalQuery.forIds(List.of(42L, 41L, 42L, 43L))
                                .type(PlatformPrincipal.class)
                                .status(IdentityStatus.ACTIVE)
                                .linkedAccount(LinkedAccountCondition.activeIn(realm))
                                .build()))
                .containsExactly(42L, 41L);
    }

    private static void assertInvalidNormalizedValue(String normalized, Class<? extends Throwable> failureType) {
        IdentityMapper mapper = mock(IdentityMapper.class);
        AccountIdentifierNormalizer normalizer = new AccountIdentifierNormalizer() {
            @Override
            public @NotNull AccountIdentifierType type() {
                return EMAIL;
            }

            @Override
            public @NotNull String normalize(@NotNull String rawValue) {
                return normalized;
            }
        };
        IdentityService identities = new IdentityService(
                new IdentityRepository(mapper, SQL_BATCHES),
                () -> 1L,
                new PrincipalTypes(List.of()),
                List.of(normalizer));

        assertThatThrownBy(() -> identities.resolveAccount(new RealmKey("customer"), EMAIL, "mail@example.com"))
                .isInstanceOf(failureType);
        verifyNoInteractions(mapper);
    }

    @SuppressWarnings("unchecked")
    private static PrincipalCodec<PlatformPrincipal> platformPrincipalCodec() {
        PrincipalCodec<PlatformPrincipal> codec = mock(PrincipalCodec.class);
        when(codec.type()).thenReturn("platform");
        when(codec.valueType()).thenReturn(PlatformPrincipal.class);
        return codec;
    }
}
