package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.util.UUID;

final class DatabaseTestSupport {

    private DatabaseTestSupport() {
    }

    static MockStoreService create(Path ignored) {
        return create(newDatabase(), new YhtMockProperties());
    }

    static DriverManagerDataSource newDatabase() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-dm.sql")).execute(source);
        return source;
    }

    static MockStoreService create(DriverManagerDataSource source, YhtMockProperties properties) {
        MockStoreService store = new MockStoreService(new JdbcTemplate(source),
                new DataSourceTransactionManager(source), properties);
        store.init();
        return store;
    }
}
