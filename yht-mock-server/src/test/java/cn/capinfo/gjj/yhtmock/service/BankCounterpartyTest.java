package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.BankCounterparty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BankCounterpartyTest {
    @Test
    void storesDistinctBanksAndPreservesThemOnHistoryCleanup() {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var bank = bank("105000", "COUNTER-105");
        store.saveBankCounterparty(bank);
        store.saveBankCounterparty(bank("102000", "COUNTER-102"));
        var restarted = DatabaseTestSupport.create(source, new YhtMockProperties());
        assertThat(restarted.findBankCounterparty("105000").accountNo).isEqualTo("COUNTER-105");
        assertThat(restarted.findBankCounterparty("105000123456")).isNull();
        bank.enabled = false;
        store.saveBankCounterparty(bank);
        assertThat(restarted.findBankCounterparty("105000").enabled).isFalse();
        store.clearHistoryFiles();
        assertThat(store.listBankCounterparties()).hasSize(2);
        assertThat(store.deleteBankCounterparty("105000")).isTrue();
        assertThat(store.findBankCounterparty("105000")).isNull();
    }

    @Test
    void derivesDefaultEndpointButPreservesExplicitOverride() {
        var properties = new YhtMockProperties();
        properties.getSettlement().setReceiveUrl("http://settlement:8080/yht/yht/receive");
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), properties);
        assertThat(store.getSettings().movement.targetUrl).isEqualTo("http://settlement:8080/api/v1/ywgl/saveZhbdzt");
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://other:8080/custom";
        store.updateSettings(settings);
        store.applyStartupSettings();
        assertThat(store.getSettings().movement.targetUrl).isEqualTo("http://other:8080/custom");
    }

    @Test
    void invalidBankConfigurationIsRejected() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var bank = bank("105000", "ACCOUNT");
        bank.accountName = " ";
        assertThatThrownBy(() -> store.saveBankCounterparty(bank)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.listBankCounterparties()).isEmpty();
    }

    @Test
    void derivesGatewayPrefixAndDoesNotGuessUnknownReceivePaths() {
        for (String source : new String[]{"https://settlement/gateway/api/v1/ywgl/yht/yht/receive",
                "https://settlement/gateway/yht/yht/receive"}) {
            var properties = new YhtMockProperties();
            properties.getSettlement().setReceiveUrl(source);
            var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), properties);
            assertThat(store.getSettings().movement.targetUrl)
                    .isEqualTo("https://settlement/gateway/api/v1/ywgl/saveZhbdzt");
        }
        var properties = new YhtMockProperties();
        properties.getSettlement().setReceiveUrl("https://settlement/custom-receive");
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), properties);
        assertThat(store.getSettings().movement.targetUrl).isEmpty();
    }

    @Test
    void versionOneDatabaseIsRejectedUntilUpgradeDdlIsApplied() throws Exception {
        var source = DatabaseTestSupport.newDatabase();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("DROP TABLE YHT_MOCK_FLOW_ATTEMPT");
        jdbc.execute("DROP TABLE YHT_MOCK_FLOW");
        jdbc.execute("DROP TABLE YHT_MOCK_BANK_ACCOUNT");
        jdbc.execute("ALTER TABLE YHT_MOCK_BATCH DROP COLUMN C_CENTER_BANK_ID");
        jdbc.execute("ALTER TABLE YHT_MOCK_MOVEMENT_CFG DROP COLUMN C_FALLBACK_ENABLED");
        jdbc.execute("ALTER TABLE YHT_MOCK_RECORD DROP COLUMN C_DECRYPTED_REQUEST_BODY");
        jdbc.execute("ALTER TABLE YHT_MOCK_RECORD DROP COLUMN C_DECRYPTED_RESPONSE_BODY");
        // 库是按 v6 全量建的，模拟 v1 前必须把 v6 新增结构回退干净：
        // SETTINGS 两列待 07 重新 ADD；SCENARIO 需重建为含旧匹配/强制列的 v5 形态，07 才有列可 DROP。
        jdbc.execute("ALTER TABLE YHT_MOCK_SETTINGS DROP COLUMN C_RANDOM_FAIL");
        jdbc.execute("ALTER TABLE YHT_MOCK_SETTINGS DROP COLUMN C_RANDOM_FAIL_RATIO");
        jdbc.execute("DROP TABLE YHT_MOCK_SCENARIO");
        jdbc.execute("CREATE TABLE YHT_MOCK_SCENARIO ("
                + "C_ID BIGINT PRIMARY KEY, C_NAME CLOB, C_ENABLED SMALLINT NOT NULL, "
                + "C_REQUEST_MESG_TYPE CLOB, C_MATCH_ACCT_NO CLOB, C_MATCH_ACCT_SUFFIX CLOB, "
                + "C_MATCH_PROTOCOL_NO CLOB, C_MATCH_REQ_ID CLOB, C_MATCH_BATCH_NO CLOB, C_MATCH_SYS_SEQ_NO CLOB, "
                + "C_FORCE_RES_FLAG CLOB, C_FORCE_STATUS CLOB, C_FORCE_RET_CODE CLOB, C_FORCE_RET_MSG CLOB, "
                + "C_DISABLE_AUTO_CALLBACK CLOB, C_CALLBACK_MESG_TYPE CLOB, "
                + "C_REMARK CLOB, C_UPDATED_AT BIGINT NOT NULL)");
        jdbc.update("UPDATE YHT_MOCK_CONTROL SET SCHEMA_VERSION = 1 WHERE ID = 1");
        assertThatThrownBy(() -> DatabaseTestSupport.create(source, new YhtMockProperties()))
                .isInstanceOf(IllegalStateException.class);
        var script = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../doc/数据库脚本/一户通挡板达梦/03-upgrade-v1-to-v2.sql"));
        var ddl = script.substring(script.indexOf("CREATE TABLE"));
        for (String statement : ddl.split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        assertThat(jdbc.queryForObject("SELECT SCHEMA_VERSION FROM YHT_MOCK_CONTROL", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> DatabaseTestSupport.create(source, new YhtMockProperties()))
                .isInstanceOf(IllegalStateException.class);
        var flowScript = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../doc/数据库脚本/一户通挡板达梦/04-upgrade-v2-to-v3.sql"));
        for (String statement : flowScript.substring(flowScript.indexOf("CREATE TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        assertThatThrownBy(() -> DatabaseTestSupport.create(source, new YhtMockProperties()))
                .isInstanceOf(IllegalStateException.class);
        var fallbackScript = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../doc/数据库脚本/一户通挡板达梦/05-upgrade-v3-to-v4.sql"));
        for (String statement : fallbackScript.substring(fallbackScript.indexOf("ALTER TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        assertThat(jdbc.queryForObject("SELECT SCHEMA_VERSION FROM YHT_MOCK_CONTROL", Integer.class)).isEqualTo(4);
        assertThatThrownBy(() -> DatabaseTestSupport.create(source, new YhtMockProperties()))
                .isInstanceOf(IllegalStateException.class);
        var recordScript = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../doc/数据库脚本/一户通挡板达梦/06-upgrade-v4-to-v5.sql"));
        for (String statement : recordScript.substring(recordScript.indexOf("ALTER TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        assertThat(jdbc.queryForObject("SELECT SCHEMA_VERSION FROM YHT_MOCK_CONTROL", Integer.class)).isEqualTo(5);
        // v5 -> v6：场景规则精简为账号校验规则 + 随机失败开关列。
        var scenarioScript = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../doc/数据库脚本/一户通挡板达梦/07-upgrade-v5-to-v6.sql"));
        for (String statement : scenarioScript.substring(scenarioScript.indexOf("ALTER TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        assertThat(jdbc.queryForObject("SELECT SCHEMA_VERSION FROM YHT_MOCK_CONTROL", Integer.class)).isEqualTo(6);
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        assertThat(store.listBankCounterparties()).isEmpty();
        store.saveBankCounterparty(bank("105000", "UPGRADED"));
        assertThat(store.findBankCounterparty("105000").accountNo).isEqualTo("UPGRADED");
    }

    @Test
    void managementApiSupportsSaveListDeleteAndRejectsInvalidInput() throws Exception {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var controller = new cn.capinfo.gjj.yhtmock.controller.YhtMockApiController(null, store, null);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/yht-mock/api/bank-counterparties").contentType("application/json")
                .content(mapper.writeValueAsString(bank("105000", "API-ACCOUNT"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/yht-mock/api/bank-counterparties"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].accountNo").value("API-ACCOUNT"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/yht-mock/api/bank-counterparties").contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/yht-mock/api/bank-counterparties/105000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.success").value(true));
        assertThat(store.listBankCounterparties()).isEmpty();
    }

    private BankCounterparty bank(String bankId, String account) {
        var bank = new BankCounterparty();
        bank.bankId = bankId;
        bank.bankName = "测试银行";
        bank.accountNo = account;
        bank.accountName = "模拟对账户";
        bank.accountBankId = "999999999999";
        return bank;
    }
}
