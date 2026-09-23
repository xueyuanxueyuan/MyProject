package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

class DatabaseStoreTest {

    @Test
    void stateIsSharedAcrossInstancesAndReadsAreDetached() {
        var database = DatabaseTestSupport.newDatabase();
        var first = DatabaseTestSupport.create(database, new YhtMockProperties());
        var second = DatabaseTestSupport.create(database, new YhtMockProperties());
        BatchState batch = new BatchState();
        batch.batchNo = "BATCH-001";
        batch.status = "PROC";
        batch.fileData = "批次中文结果".repeat(20000);
        first.saveBatch(batch);
        assertThat(second.findBatch(batch.batchNo).fileData).isEqualTo(batch.fileData);
        second.findBatch(batch.batchNo).status = "SUCC";
        assertThat(first.findBatch(batch.batchNo).status).isEqualTo("PROC");
        batch.status = "SUCC";
        second.saveBatch(batch);
        assertThat(first.findBatch(batch.batchNo).status).isEqualTo("SUCC");
    }

    @Test
    void movementClaimIsUniqueAcrossConnections() throws Exception {
        var database = DatabaseTestSupport.newDatabase();
        var first = DatabaseTestSupport.create(database, new YhtMockProperties());
        var second = DatabaseTestSupport.create(database, new YhtMockProperties());
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                var store = index % 2 == 0 ? first : second;
                tasks.add(() -> store.claimMovement("SAME-MOVEMENT"));
            }
            int successes = 0;
            for (var result : executor.invokeAll(tasks)) {
                if (result.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cleanupPreservesSettingsRulesAndMovementDeduplication() throws Exception {
        var database = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(database, new YhtMockProperties());
        MockSettings settings = store.getSettings();
        settings.defaultTargetUrl = "http://example.invalid/receive";
        store.updateSettings(settings);
        MockScenarioRule rule = new MockScenarioRule();
        rule.name = "保留规则";
        store.saveScenario(rule);
        store.claimMovement("DO-NOT-REPLAY");
        MockRecord record = new MockRecord();
        record.requestBody = "<Message>中文</Message>";
        store.addRecord(record);
        store.clearHistoryFiles();
        assertThat(store.listRecords(100)).isEmpty();
        assertThat(store.listScenarios()).hasSize(1);
        assertThat(store.getSettings().defaultTargetUrl).isEqualTo(settings.defaultTargetUrl);
        assertThat(store.claimMovement("DO-NOT-REPLAY")).isFalse();
    }

    @Test
    void databaseErrorsAreNotSilentlyIgnored() {
        var database = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(database, new YhtMockProperties());
        new JdbcTemplate(database).execute("DROP TABLE YHT_MOCK_BATCH");
        BatchState batch = new BatchState();
        batch.batchNo = "FAIL";
        assertThatThrownBy(() -> store.saveBatch(batch)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void accountRuleSpecificityPrefersMostRecentlyUpdatedMatchingRule() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        MockScenarioRule generic = new MockScenarioRule();
        generic.name = "generic";
        generic.enabled = true;
        generic.accountRule = "";      // 空正则：匹配任意对手账号（兜底）
        generic.valid = true;
        generic.class1Card = true;
        store.saveScenario(generic);
        MockScenarioRule exact = new MockScenarioRule();
        exact.name = "exact";
        exact.enabled = true;
        exact.accountRule = "123%_";  // 仅匹配含 "123%_" 的对手账号
        exact.valid = false;          // 命中即失败
        exact.class1Card = true;
        store.saveScenario(exact);

        var running = new java.util.HashMap<String, java.math.BigDecimal>();
        // 精确规则后保存（updatedAt 更新）→ 优先匹配；"123%_" 命中 exact（无效）→ 判定失败
        assertThat(store.evaluateCounterparty("123%_", new java.math.BigDecimal("1"), "20260921",
                false, 0.0, running).success()).isFalse();
        // "12345" 不匹配精确规则，回退兜底规则 → 成功
        assertThat(store.evaluateCounterparty("12345", new java.math.BigDecimal("1"), "20260921",
                false, 0.0, running).success()).isTrue();
    }
}
