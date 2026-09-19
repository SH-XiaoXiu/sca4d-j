package com.sca4d.iam.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.persistence.AffectedRowsMismatchException;
import com.sca4d.core.spring.persistence.batch.SqlBatchProperties;
import com.sca4d.core.spring.persistence.batch.SqlBatches;
import com.sca4d.iam.identity.mapper.IdentityMapper;
import org.junit.jupiter.api.Test;

/** 验证 Account 与 Principal 关系允许重复建立，但拒绝异常批量写入结果。 */
class IdentityRepositoryTest {

    @Test
    void acceptsAnExistingAccountPrincipalLink() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityRepository repository = new IdentityRepository(mapper, new SqlBatches(new SqlBatchProperties()));
        when(mapper.insertLink(11L, 21L)).thenReturn(1, 0);
        assertThatCode(() -> {
            repository.link(11L, 21L);
            repository.link(11L, 21L);
        }).doesNotThrowAnyException();
        when(mapper.insertLink(11L, 21L)).thenReturn(2);
        assertThatThrownBy(() -> repository.link(11L, 21L)).isInstanceOf(AffectedRowsMismatchException.class);
    }
}
