package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.YhtMockServerApplication;
import cn.capinfo.gjj.yhtmock.migration.DatabaseMigrationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class DatabaseStartupTest {

    @Test
    void migrationContextHasNoGatewayCallbacksOrDefaultSettings() {
        var source = DatabaseTestSupport.newDatabase();
        SpringApplication application = new SpringApplication(DatabaseMigrationConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run(arguments(source.getUrl()))) {
            assertThat(context.getBeansOfType(MockGatewayService.class)).isEmpty();
            assertThat(context.getBeansOfType(MockCallbackService.class)).isEmpty();
            assertThat(context.getBeansOfType(ZaykSvsSocketMockServer.class)).isEmpty();
            assertThat(context.getBean(MockStoreService.class).count("YHT_MOCK_SETTINGS")).isZero();
        }
    }

    @Test
    void normalContextInitializesDatabaseSettingsWithoutDuplicateStoreBeans() {
        var source = DatabaseTestSupport.newDatabase();
        SpringApplication application = new SpringApplication(YhtMockServerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run(arguments(source.getUrl()))) {
            assertThat(context.getBeansOfType(MockStoreService.class)).hasSize(1);
            assertThat(context.getBean(MockStoreService.class).count("YHT_MOCK_SETTINGS")).isEqualTo(1);
        }
    }

    @Test
    void deliveredSqlMatchesApplicationSchema() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/db/schema-dm.sql")))
                .isEqualTo(Files.readString(Path.of("../doc/数据库脚本/一户通挡板达梦/01-schema.sql")));
    }

    private String[] arguments(String url) {
        return new String[]{"--spring.datasource.url=" + url, "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.main.web-application-type=none", "--yht-mock.svs-socket.enabled=false",
                "--spring.main.banner-mode=off"};
    }
}
