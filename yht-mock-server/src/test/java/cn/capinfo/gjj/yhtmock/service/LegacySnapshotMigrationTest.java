package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class LegacySnapshotMigrationTest {

    @TempDir
    Path directory;

    @Test
    void migratesAllTypesAndRepeatedImportDoesNotReplayOrOverwrite() throws Exception {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var migration = new LegacySnapshotMigration(store);
        MockStateSnapshot snapshot = snapshot();
        Path file = directory.resolve("mock-state.json");
        new ObjectMapper().writeValue(file.toFile(), snapshot);
        byte[] original = Files.readAllBytes(file);
        assertThat(migration.migrate(file).alreadyImported()).isFalse();
        assertThat(store.getSettings().defaultTargetUrl).isEqualTo("http://saved.invalid/receive");
        assertThat(store.findProtocol("P1", "").updatedAt).isEqualTo(123L);
        assertThat(store.findTrade("T1", "").status).isEqualTo("SUCC");
        assertThat(store.findBatch("B1").status).isEqualTo("PROC");
        assertThat(store.listRecords(10).get(0).requestBody).isEqualTo("历史中文报文");
        assertThat(store.listScenarios()).hasSize(1);
        assertThat(store.claimMovement("UNKNOWN-KEY")).isFalse();
        assertThat(migration.migrate(file).alreadyImported()).isTrue();
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
        MockRecord next = store.addRecord(new MockRecord());
        assertThat(next.id).isEqualTo(101L);
        store.clearHistoryFiles();
        assertThat(migration.migrate(file).alreadyImported()).isTrue();
        assertThat(store.listRecords(10)).isEmpty();
    }

    @Test
    void importRejectsUsedDatabaseAndDifferentSnapshot() throws Exception {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var migration = new LegacySnapshotMigration(store);
        Path file = directory.resolve("snapshot.json");
        new ObjectMapper().writeValue(file.toFile(), snapshot());
        store.getSettings();
        assertThatThrownBy(() -> migration.migrate(file)).isInstanceOf(IllegalStateException.class);
        assertThat(store.listRecords(10)).isEmpty();
        var fresh = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var freshMigration = new LegacySnapshotMigration(fresh);
        freshMigration.migrate(file);
        Files.writeString(file, Files.readString(file) + " ");
        assertThatThrownBy(() -> freshMigration.migrate(file)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedInsertRollsBackEveryTableAndMigrationMarker() throws Exception {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var migration = new LegacySnapshotMigration(store);
        MockStateSnapshot snapshot = snapshot();
        snapshot.movementAttempts.put("INVALID", "X".repeat(100));
        Path file = directory.resolve("invalid.json");
        new ObjectMapper().writeValue(file.toFile(), snapshot);
        assertThatThrownBy(() -> migration.migrate(file)).isInstanceOf(RuntimeException.class);
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_SETTINGS", Long.class)).isZero();
        assertThat(store.listProtocols()).isEmpty();
        assertThat(store.listRecords(10)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT MIGRATION_HASH FROM YHT_MOCK_CONTROL WHERE ID=1", String.class)).isNull();
    }

    @Test
    void invalidJsonDuplicateFieldsAndMapKeyMismatchAreRejected() throws Exception {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var migration = new LegacySnapshotMigration(store);
        Path file = directory.resolve("invalid.json");
        Files.writeString(file, "{broken");
        assertThatThrownBy(() -> migration.migrate(file)).isInstanceOf(Exception.class);
        Files.writeString(file, "{\"records\":[],\"records\":[]}");
        assertThatThrownBy(() -> migration.migrate(file)).isInstanceOf(Exception.class);
        var snapshot = snapshot();
        snapshot.batches.get("B1").batchNo = "DIFFERENT";
        new ObjectMapper().writeValue(file.toFile(), snapshot);
        assertThatThrownBy(() -> migration.migrate(file)).isInstanceOf(IllegalArgumentException.class);
    }

    private MockStateSnapshot snapshot() {
        MockStateSnapshot snapshot = new MockStateSnapshot();
        snapshot.settings.defaultTargetUrl = "http://saved.invalid/receive";
        ProtocolState protocol = new ProtocolState();
        protocol.protocolNo = "P1";
        protocol.updatedAt = 123L;
        snapshot.protocols.put("P1", protocol);
        TradeState trade = new TradeState();
        trade.sysSeqNo = "T1";
        trade.status = "SUCC";
        snapshot.trades.put("T1", trade);
        BatchState batch = new BatchState();
        batch.batchNo = "B1";
        batch.status = "PROC";
        snapshot.batches.put("B1", batch);
        MockRecord record = new MockRecord();
        record.id = 100;
        record.requestBody = "历史中文报文";
        snapshot.records.add(record);
        MockScenarioRule scenario = new MockScenarioRule();
        scenario.id = 23;
        snapshot.scenarios.add(scenario);
        snapshot.movementAttempts.put("UNKNOWN-KEY", "UNKNOWN");
        return snapshot;
    }
}
