package cn.capinfo.gjj.yhtmock.migration;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.service.MockStoreService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@EnableAutoConfiguration
@EnableConfigurationProperties(YhtMockProperties.class)
public class DatabaseMigrationConfiguration {

    @Bean
    MockStoreService mockStoreService(JdbcTemplate jdbc, PlatformTransactionManager manager, YhtMockProperties properties) {
        return new MockStoreService(jdbc, manager, properties);
    }
}
