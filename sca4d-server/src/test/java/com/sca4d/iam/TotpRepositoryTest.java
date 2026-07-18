package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.totp.repository.TotpRepository;
import com.sca4d.mapper.TotpMapper;
import org.junit.jupiter.api.Test;


/** 验证 TOTP Repository 转换 Mapper 结果，并由原子 SQL 限定类型、Account 和版本。 */
class TotpRepositoryTest {

    @Test
    void shouldConvertMissingReplaceAndDeleteRowsToPreconditionFailure() {
        TotpMapper mapper = mock(TotpMapper.class);
        TotpRepository repository = new TotpRepository(mapper);

        assertThatThrownBy(() -> repository.replace(
                40L,
                30L,
                new EncryptedSecret("test", new byte[12], new byte[32]),
                100L,
                3L))
                .isInstanceOfSatisfying(
                        ConcurrencyConflictException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.TOTP_CREDENTIAL_CHANGED));
        assertThatThrownBy(() -> repository.delete(40L, 30L, 3L))
                .isInstanceOfSatisfying(
                        ConcurrencyConflictException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.TOTP_CREDENTIAL_CHANGED));
    }

    @Test
    void shouldCheckAvailabilityWithoutReadingEncryptedSecret() {
        TotpMapper mapper = mock(TotpMapper.class);
        when(mapper.countActiveTotp(30L, 40L)).thenReturn(1);

        assertThat(new TotpRepository(mapper).hasActiveCredential(30L, 40L)).isTrue();

        verify(mapper).countActiveTotp(30L, 40L);
    }

}
