package com.sca4d.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.stp.StpLogic;
import com.sca4d.auth.session.SaTokenRedisSerialization;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.spring.config.AppIsolationAutoConfiguration;
import com.sca4d.core.spring.config.CoreRedisAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

class AuthAutoConfigurationTest {
    static {
        AppName.publish("sca4d-test");
    }

    @Test
    @DisplayName("Uses The Application Runtime Namespace As Sa-Token Login Type")
    void usesTheApplicationNameAsSaTokenLoginType() {
        StpLogic stpLogic = new AuthAutoConfiguration()
                .sca4djStpLogic();

        assertThat(stpLogic.getLoginType()).isEqualTo("SCA4D_TEST:AUTH");
    }

    @Test
    @DisplayName("Uses Sa-Token Redis Template DAO With The Default Redis Factory")
    void usesSaTokenRedisTemplateDaoWithDefaultRedisFactory() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        new ApplicationContextRunner().withPropertyValues("spring.application.name=sca4d-test")
                .withConfiguration(
                        AutoConfigurations.of(CoreRedisAutoConfiguration.class, AppIsolationAutoConfiguration.class))
                .withUserConfiguration(SaTokenDaoForRedisTemplate.class, SaTokenRedisSerialization.class)
                .withBean("redisConnectionFactory", RedisConnectionFactory.class, () -> connectionFactory)
                .run(context -> {
                    SaTokenDaoForRedisTemplate saTokenDao = context.getBean(SaTokenDaoForRedisTemplate.class);
                    RedisTemplate<?, ?> sca4djRedis = context.getBean(
                            "sca4djRedisTemplate",
                            RedisTemplate.class);

                    assertThat(context).hasSingleBean(SaTokenDao.class);
                    assertThat(saTokenDao.stringRedisTemplate.getConnectionFactory())
                            .isSameAs(connectionFactory);
                    assertThat(sca4djRedis.getConnectionFactory()).isSameAs(connectionFactory);
                    assertThat(saTokenDao.stringRedisTemplate).isNotSameAs(sca4djRedis);
                });
    }
}
