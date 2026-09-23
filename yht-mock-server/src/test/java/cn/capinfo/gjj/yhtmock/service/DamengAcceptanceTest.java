package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "YHT_MOCK_DM_ACCEPTANCE", matches = "true")
class DamengAcceptanceTest {

    @Test
    void verifiesRealDamengSchemaClobTransactionsAndDedupWithoutCommitting() {
        String username = required("YHT_MOCK_DM_TEST_USERNAME");
        assertThat(username).isEqualTo("YHT_MOCK_TEST");
        var source = new DriverManagerDataSource(required("YHT_MOCK_DM_TEST_URL"), username,
                required("YHT_MOCK_DM_TEST_PASSWORD"));
        source.setDriverClassName("dm.jdbc.driver.DmDriver");
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT USER FROM DUAL", String.class)).isEqualTo("YHT_MOCK_TEST");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_SETTINGS", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_BANK_ACCOUNT", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_FLOW", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_FLOW_ATTEMPT", Long.class)).isZero();
        var manager = new DataSourceTransactionManager(source);
        var store = new MockStoreService(jdbc, manager, new YhtMockProperties());
        store.init();
        new TransactionTemplate(manager).execute(status -> {
            try {
                store.applyStartupSettings();
                BankCounterparty account = new BankCounterparty();
                account.bankId = "DM-TEST";
                account.bankName = "达梦验收中心银行";
                account.accountNo = "99993304000000000001";
                account.accountName = "达梦验收模拟对手";
                account.accountBankId = "999999999999";
                store.saveBankCounterparty(account);
                assertThat(store.findBankCounterparty(account.bankId).accountName).isEqualTo(account.accountName);
                var flow = new MovementFlow();
                flow.id = "DM-FLOW"; flow.movementKey = "DM-FLOW-KEY";
                flow.acctNo = "DM-ACCOUNT"; flow.centerBankId = "DM-TEST";
                flow.businessDate = "20260921"; flow.direction = "IN";
                flow.amount = new java.math.BigDecimal("999999999999999999.99");
                flow = store.movementFlows().register(flow);
                flow = store.movementFlows().generated(flow.id, flow.version, "达梦通知快照".repeat(20000), "验收生成");
                assertThat(store.movementFlows().find(flow.id).payload).hasSize(120000);
                assertThat(store.movementFlows().query("20260921", "20260921", "DM-TEST", "DM-ACCOUNT", "", "IN", "PENDING", 1, 20).total()).isEqualTo(1);
                assertThat(store.movementFlows().attempts(flow.id)).hasSize(1);
                MockRecord record = new MockRecord();
                record.requestBody = "达梦中文报文".repeat(20000);
                store.addRecord(record);
                assertThat(store.listRecords(1).get(0).requestBody).isEqualTo(record.requestBody);
                BatchState batch = new BatchState();
                batch.batchNo = "DM-ACCEPTANCE";
                batch.status = "PROC";
                batch.centerBankId = "DM-TEST";
                batch.fileData = record.requestBody;
                store.saveBatch(batch);
                assertThat(store.findBatch(batch.batchNo).fileData).isEqualTo(batch.fileData);
                assertThat(store.findBatch(batch.batchNo).centerBankId).isEqualTo("DM-TEST");
                MockScenarioRule rule = new MockScenarioRule();
                rule.name = "dm-rule";
                rule.enabled = true;
                rule.accountRule = "123%_";
                rule.valid = true;
                rule.class1Card = true;
                store.saveScenario(rule);
                assertThat(store.listScenarios().stream().anyMatch(r -> "dm-rule".equals(r.name))).isTrue();
                assertThat(store.claimMovement("DM-ACCEPTANCE")).isTrue();
                assertThat(store.claimMovement("DM-ACCEPTANCE")).isFalse();
                store.finishMovement("DM-ACCEPTANCE", "UNKNOWN");
                return null;
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            } finally {
                status.setRollbackOnly();
            }
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_SETTINGS", Long.class)).isZero();
        assertThat(store.listRecords(1)).isEmpty();
        assertThat(store.listBankCounterparties()).isEmpty();
        assertThat(store.movementFlows().query("", "", "", "", "", "", "", 1, 20).total()).isZero();
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required test environment variable: " + name);
        }
        return value;
    }
}
