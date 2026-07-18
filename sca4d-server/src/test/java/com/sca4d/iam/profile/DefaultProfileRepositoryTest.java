package com.sca4d.iam.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.entity.IamDefaultProfileEntity;
import com.sca4d.iam.profile.repository.DefaultProfileRepository;
import com.sca4d.mapper.IamDefaultProfileMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证 DEFAULT Profile 初始持久化字段。 */
class DefaultProfileRepositoryTest {

    @Test
    void shouldInitializeRequiredPersistenceFieldsWhenCreatingProfile() {
        IamDefaultProfileMapper mapper = mock(IamDefaultProfileMapper.class);
        when(mapper.insert(any(IamDefaultProfileEntity.class))).thenReturn(1);
        DefaultProfileRepository repository = new DefaultProfileRepository(mapper);

        repository.create(10L, 20L);

        ArgumentCaptor<IamDefaultProfileEntity> profile = ArgumentCaptor.forClass(IamDefaultProfileEntity.class);
        verify(mapper).insert(profile.capture());
        assertThat(profile.getValue())
                .extracting(
                        IamDefaultProfileEntity::getPrincipalId,
                        IamDefaultProfileEntity::getAvatarFileId,
                        IamDefaultProfileEntity::getVersion,
                        IamDefaultProfileEntity::getDeleted)
                .containsExactly(10L, 20L, 0L, 0);
        assertThat(profile.getValue().getCreatedAt()).isNotNull();
        assertThat(profile.getValue().getUpdatedAt()).isEqualTo(profile.getValue().getCreatedAt());
    }
}
