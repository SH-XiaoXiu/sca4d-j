package com.sca4d.auth.x509;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** 验证按账号清理多张证书和重复清理都属于正常结果。 */
class X509CredentialRepositoryTest {

    @Test
    void erasesZeroOneOrMultipleCertificates() {
        X509CredentialMapper mapper = mock(X509CredentialMapper.class);
        X509CredentialRepository repository = new X509CredentialRepository(mapper);
        for (int count : new int[] {0, 1, 3}) {
            when(mapper.deleteByAccountId(11L)).thenReturn(count);
            assertThatCode(() -> repository.erase(11L)).doesNotThrowAnyException();
        }
    }
}
