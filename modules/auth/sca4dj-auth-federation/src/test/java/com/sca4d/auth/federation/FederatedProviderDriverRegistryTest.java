package com.sca4d.auth.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.code.TreeCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;

/** 验证 Driver 登记的编码规则、按编码查找和空目录装配。 */
class FederatedProviderDriverRegistryTest {

    @Test
    @DisplayName("同一编码登记两个 Driver 时启动失败")
    void rejectsDuplicateCodes() {
        FederatedProviderDriver first = driver("TEST");
        FederatedProviderDriver second = driver("test");

        assertThatThrownBy(() -> new FederatedProviderDriverRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("通配节点和超过列宽的编码不能登记")
    void rejectsWildcardAndOverlongCodes() {
        FederatedProviderDriver wildcard = driver("TEST:*");
        FederatedProviderDriver overlong = driver("A".repeat(65));

        assertThatThrownBy(() -> new FederatedProviderDriverRegistry(List.of(wildcard)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FederatedProviderDriverRegistry(List.of(overlong)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("按层级编码查找已登记的 Driver，未登记的编码返回配置无效")
    void findsRegisteredDriverByHierarchicalCode() {
        FederatedProviderDriver driver = driver("TEST:OPEN");
        FederatedProviderDriverRegistry registry = new FederatedProviderDriverRegistry(List.of(driver));

        assertThat(registry.contains(TreeCode.ofColon("TEST:OPEN"))).isTrue();
        assertThat(registry.require(TreeCode.ofColon("test:open"))).isSameAs(driver);
        assertThat(registry.contains(TreeCode.ofColon("TEST"))).isFalse();
        assertThatThrownBy(() -> registry.require(TreeCode.ofColon("TEST")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(FederatedErrorCode.CONFIGURATION_INVALID));
    }

    @Test
    @DisplayName("容器中没有 Driver 时自动配置仍创建空目录，有 Driver 时登记全部 Driver")
    void autoConfigurationBuildsRegistryFromContainerDrivers() {
        DefaultListableBeanFactory empty = new DefaultListableBeanFactory();
        DefaultListableBeanFactory single = new DefaultListableBeanFactory();
        single.registerSingleton("testDriver", driver("TEST"));
        FederatedAutoConfiguration configuration = new FederatedAutoConfiguration();

        FederatedProviderDriverRegistry none = configuration
                .federatedProviderDriverRegistry(empty.getBeanProvider(FederatedProviderDriver.class));
        FederatedProviderDriverRegistry one = configuration
                .federatedProviderDriverRegistry(single.getBeanProvider(FederatedProviderDriver.class));

        assertThat(none.contains(TreeCode.of("TEST"))).isFalse();
        assertThat(one.contains(TreeCode.of("TEST"))).isTrue();
    }

    private static FederatedProviderDriver driver(String code) {
        return new FederatedProviderDriver() {
            @Override
            public TreeCode code() {
                return TreeCode.of(code);
            }

            @Override
            public FederatedProfile profile(FederatedClaims claims, FederatedProviderConfiguration configuration) {
                return new FederatedProfile(null, "subject", null, null, false, null);
            }
        };
    }
}
