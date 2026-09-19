package com.sca4d.iam.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.core.spring.persistence.mybatis.StringValueTypeHandler;
import com.sca4d.iam.identity.PrincipalType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 IAM 自有值对象的 MyBatis 类型注册。 */
class IamTypeHandlerConfigurationTest {

    @Test
    @DisplayName("Registers Principal Type With Shared String Value Handler")
    void registersPrincipalTypeWithSharedStringValueHandler() {
        Configuration configuration = new Configuration();

        new IamAutoConfiguration().iamTypeHandlerCustomizer().customize(configuration);

        assertThat(configuration.getTypeHandlerRegistry().getTypeHandler(PrincipalType.class, JdbcType.VARCHAR))
                .isInstanceOf(StringValueTypeHandler.class);
    }
}
