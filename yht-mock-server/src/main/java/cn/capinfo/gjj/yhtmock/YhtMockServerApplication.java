package cn.capinfo.gjj.yhtmock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import cn.capinfo.gjj.yhtmock.migration.DatabaseMigrationConfiguration;
import cn.capinfo.gjj.yhtmock.service.LegacySnapshotMigration;
import cn.capinfo.gjj.yhtmock.service.MockStoreService;
import java.nio.file.Path;
import java.util.Arrays;

@SpringBootApplication
public class YhtMockServerApplication {

    public static void main(String[] args) throws Exception {
        if (Arrays.stream(args).anyMatch(argument -> argument.equals("--yht-mock.migrate-file"))) {
            throw new IllegalArgumentException("Use --yht-mock.migrate-file=/absolute/path/to/snapshot.json");
        }
        var files = Arrays.stream(args).filter(argument -> argument.startsWith("--yht-mock.migrate-file=")).toList();
        if (!files.isEmpty()) {
            if (files.size() != 1 || files.get(0).substring("--yht-mock.migrate-file=".length()).isBlank()) {
                throw new IllegalArgumentException("Exactly one non-empty migration file is required");
            }
            SpringApplication migration = new SpringApplication(DatabaseMigrationConfiguration.class);
            migration.setWebApplicationType(WebApplicationType.NONE);
            try (var context = migration.run(args)) {
                var result = new LegacySnapshotMigration(context.getBean(MockStoreService.class))
                        .migrate(Path.of(files.get(0).substring("--yht-mock.migrate-file=".length())));
                org.slf4j.LoggerFactory.getLogger(YhtMockServerApplication.class)
                        .info("Snapshot migration: alreadyImported={}, sha256={}, counts={}",
                                result.alreadyImported(), result.sha256(), result.counts());
            }
            return;
        }
        SpringApplication.run(YhtMockServerApplication.class, args);
    }
}
