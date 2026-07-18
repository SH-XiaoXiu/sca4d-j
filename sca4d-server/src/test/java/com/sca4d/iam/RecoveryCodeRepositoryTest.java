package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.id.IdGenerator;
import com.sca4d.entity.IamRecoveryCodeEntity;
import com.sca4d.entity.IamRecoveryCodeSetEntity;
import com.sca4d.iam.recovery.repository.RecoveryCodeCandidateRow;
import com.sca4d.iam.recovery.repository.RecoveryCodeRepository;
import com.sca4d.iam.recovery.repository.RecoveryCodeSummaryRow;
import com.sca4d.mapper.RecoveryCodeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.List;

/** 验证恢复码集合替换顺序、批量写入和空查询行为。 */
class RecoveryCodeRepositoryTest {

    @Test
    void shouldRejectEmptyOrBlankHashCollectionsBeforeMapperAccess() {
        RecoveryCodeMapper mapper = mock(RecoveryCodeMapper.class);
        RecoveryCodeRepository repository = new RecoveryCodeRepository(mapper, mock(IdGenerator.class));

        assertThatThrownBy(() -> repository.replace(20L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.replace(20L, List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReplaceCurrentSetDeleteOldHashesAndInsertNewBatch() {
        RecoveryCodeMapper mapper = mock(RecoveryCodeMapper.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        IamRecoveryCodeSetEntity current = new IamRecoveryCodeSetEntity()
                .setId(90L)
                .setAccountId(20L)
                .setIssuedAt(OffsetDateTime.parse("2026-07-16T10:00:00+08:00"))
                .setVersion(2L);
        when(mapper.selectCurrentSetForUpdate(20L)).thenReturn(current);
        when(mapper.replaceSet(any(), any(), any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(100L, 101L, 102L);
        RecoveryCodeRepository repository = new RecoveryCodeRepository(mapper, idGenerator);

        var summary = repository.replace(20L, List.of("hash-1", "hash-2"));

        assertThat(summary.getSetId()).isEqualTo(100L);
        assertThat(summary.getRemainingCount()).isEqualTo(2);
        InOrder operations = inOrder(mapper);
        operations.verify(mapper).selectCurrentSetForUpdate(20L);
        operations.verify(mapper).replaceSet(any(), any(), any());
        operations.verify(mapper).deleteCodes(90L);
        operations.verify(mapper).insertSet(any());
        operations.verify(mapper).insertCodes(any());
        ArgumentCaptor<List<IamRecoveryCodeEntity>> codes = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertCodes(codes.capture());
        assertThat(codes.getValue())
                .extracting(IamRecoveryCodeEntity::getId)
                .containsExactly(101L, 102L);
        assertThat(codes.getValue())
                .extracting(IamRecoveryCodeEntity::getSetId)
                .containsOnly(100L);
        assertThat(codes.getValue())
                .extracting(IamRecoveryCodeEntity::getCodeHash)
                .containsExactly("hash-1", "hash-2");
        assertThat(codes.getValue()).allSatisfy(code -> assertThat(code.toString()).doesNotContain("hash-"));
    }

    @Test
    void shouldRevokeIdempotentlyWhenNoCurrentSetExists() {
        RecoveryCodeMapper mapper = mock(RecoveryCodeMapper.class);
        RecoveryCodeRepository repository = new RecoveryCodeRepository(mapper, mock(IdGenerator.class));

        repository.revoke(20L);

        verify(mapper).selectCurrentSetForUpdate(20L);
        verify(mapper, never()).replaceSet(any(), any(), any());
        verify(mapper, never()).deleteCodes(any());
    }

    @Test
    void shouldReturnSummariesWithOneBatchQueryAndSkipEmptyInput() {
        RecoveryCodeMapper mapper = mock(RecoveryCodeMapper.class);
        RecoveryCodeSummaryRow row = new RecoveryCodeSummaryRow();
        row.setSetId(100L);
        row.setAccountId(20L);
        row.setIssuedAt(OffsetDateTime.parse("2026-07-17T10:00:00+08:00"));
        row.setVersion(3L);
        row.setRemainingCount(7);
        when(mapper.selectCurrentSummaries(List.of(20L))).thenReturn(List.of(row));
        RecoveryCodeRepository repository = new RecoveryCodeRepository(mapper, mock(IdGenerator.class));

        assertThat(repository.findCurrent(List.of(20L, 20L)).get(20L))
                .satisfies(summary -> {
                    assertThat(summary.getSetId()).isEqualTo(100L);
                    assertThat(summary.getRemainingCount()).isEqualTo(7);
                    assertThat(summary.getVersion()).isEqualTo(3L);
                });
        verify(mapper).selectCurrentSummaries(List.of(20L));

        RecoveryCodeMapper emptyMapper = mock(RecoveryCodeMapper.class);
        assertThat(new RecoveryCodeRepository(emptyMapper, mock(IdGenerator.class))
                .findCurrent(List.of()))
                .isEmpty();
        verifyNoInteractions(emptyMapper);
    }

    @Test
    void shouldMapCandidatesAndUseOnlyCurrentUnusedCode() {
        RecoveryCodeMapper mapper = mock(RecoveryCodeMapper.class);
        RecoveryCodeCandidateRow row = new RecoveryCodeCandidateRow();
        row.setCodeId(101L);
        row.setSetId(100L);
        row.setAccountId(20L);
        row.setCodeHash("$argon2id$hash");
        row.setSetVersion(3L);
        when(mapper.selectUnusedCodes(20L)).thenReturn(List.of(row));
        when(mapper.useCode(any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.countCurrentUsedCode(20L, 101L, 3L)).thenReturn(1);
        RecoveryCodeRepository repository = new RecoveryCodeRepository(mapper, mock(IdGenerator.class));

        var candidate = repository.findUnused(20L).getFirst();

        assertThat(candidate.getCodeId()).isEqualTo(101L);
        assertThat(candidate.getSetId()).isEqualTo(100L);
        assertThat(candidate.getCodeHash()).isEqualTo("$argon2id$hash");
        assertThat(candidate.toString()).doesNotContain("$argon2id$hash");
        assertThat(repository.use(candidate)).isTrue();
        assertThat(repository.isCurrentUsed(20L, 101L, 3L)).isTrue();
        verify(mapper).useCode(eq(20L), eq(100L), eq(101L), eq(3L), any());
    }
}
