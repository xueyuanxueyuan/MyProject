package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.*;
import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class MovementFlowTest {
    @Test
    void manualGenerationDoesNotSendAndRetryRequiresConfirmationAndFreshVersion() throws Exception {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/notify", exchange -> {
            int attempt = requests.incrementAndGet();
            byte[] body = (attempt == 1 ? "{\"code\":1}" : "{\"code\":0,\"data\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (var service = new MovementNotificationService(store)) {
            var settings = store.getSettings();
            settings.movement.enabled = false;
            settings.movement.targetUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/notify";
            store.updateSettings(settings);
            var bank = new BankCounterparty();
            bank.bankId = "CENTER";
            bank.bankName = "中心银行";
            bank.accountNo = "99990001";
            bank.accountName = "模拟对手";
            bank.accountBankId = "999999999999";
            store.saveBankCounterparty(bank);
            var header = new CapsHeader(); header.origSender = "CORP";
            var trade = trade();
            service.notifyTrade(header, trade);
            var flow = store.movementFlows().query("", "", "", "", "", "", "", 1, 20).items().get(0);
            assertThat(flow.status).isEqualTo("MISSING");
            assertThat(requests.get()).isZero();
            flow = service.generateMovement(flow.id, flow.version, "补缺失通知");
            assertThat(flow.status).isEqualTo("PENDING");
            assertThat(requests.get()).isZero();
            var pending = flow;
            settings.movement.enabled = true;
            store.updateSettings(settings);
            service.notifyTrade(header, trade);
            assertThat(requests.get()).isZero();
            assertThat(store.movementFlows().find(flow.id).status).isEqualTo("PENDING");
            flow = service.pushMovement(flow.id, flow.version, false, "首次手动推送");
            assertThat(flow.status).isEqualTo("FAIL");
            var failed = flow;
            assertThatThrownBy(() -> service.pushMovement(failed.id, failed.version, false, "重试"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.pushMovement(pending.id, pending.version, true, "重复点击"))
                    .isInstanceOf(IllegalStateException.class);
            var snapshot = flow.payload;
            flow = service.pushMovement(flow.id, flow.version, true, "已核对结算未收到");
            assertThat(flow.status).isEqualTo("SUCC");
            assertThat(flow.payload).isEqualTo(snapshot);
            assertThat(requests.get()).isEqualTo(2);
            var success = flow;
            assertThatThrownBy(() -> service.pushMovement(success.id, success.version, true, "成功不能重发"))
                    .isInstanceOf(IllegalStateException.class);
            store.clearHistoryFiles();
            assertThat(store.movementFlows().find(flow.id).status).isEqualTo("SUCC");
            assertThat(store.movementFlows().attempts(flow.id)).hasSize(3);
            var restored = DatabaseTestSupport.create(source, new YhtMockProperties());
            assertThat(restored.movementFlows().find(flow.id).payload).isEqualTo(snapshot);
        } finally { server.stop(0); }
    }

    @Test
    void queryFiltersPaginationAndLiteralWildcardsDoNotBroadenResults() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        for (int index = 0; index < 5; index++) {
            var flow = candidate("FLOW-" + index);
            flow.centerBankId = index < 3 ? "BANK-A" : "BANK-B";
            flow.reqId = index == 0 ? "LITERAL_%" : "OTHER";
            store.movementFlows().register(flow);
        }
        assertThat(store.movementFlows().query("2026-09-21", "2026-09-21", "BANK-A", "ACCOUNT", "", "IN", "MISSING", 1, 2).items()).hasSize(2);
        assertThat(store.movementFlows().query("", "", "BANK-A", "", "", "", "", 2, 2).items()).hasSize(1);
        assertThat(store.movementFlows().query("", "", "", "", "_%", "", "", 1, 20).total()).isEqualTo(1);
        assertThatThrownBy(() -> store.movementFlows().query("20260922", "20260921", "", "", "", "", "", 1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.movementFlows().query("", "", "", "", "", "", "", 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentClaimsOnlyAllowOneSenderAndStaleUnknownRetriesAreRejected() throws Exception {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var other = DatabaseTestSupport.create(source, new YhtMockProperties());
        var flow = store.movementFlows().register(candidate("CONCURRENT"));
        var pending = store.movementFlows().generated(flow.id, flow.version, "{}", "生成");
        var wins = new AtomicInteger();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int index = 0; index < 12; index++) {
            var target = index % 2 == 0 ? store : other;
            futures.add(pool.submit(() -> {
                try {
                    target.movementFlows().claim(pending.id, pending.version, false, "推送", "http://localhost/notify");
                    wins.incrementAndGet();
                } catch (IllegalStateException expected) { }
            }));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        for (var future : futures) future.get();
        assertThat(wins.get()).isEqualTo(1);
        var sending = store.movementFlows().find(flow.id);
        assertThat(sending.status).isEqualTo("SENDING");
        assertThatThrownBy(() -> store.movementFlows().claim(sending.id, sending.version, true, "在途不重发", "http://localhost/notify"))
                .isInstanceOf(IllegalStateException.class);
        var audit = store.movementFlows().attempts(flow.id).stream().filter(item -> "PUSH".equals(item.action)).findFirst().orElseThrow();
        var record = new MockRecord(); record.status = "UNKNOWN"; record.remark = "模拟超时";
        var unknown = store.movementFlows().finished(audit, record);
        assertThatThrownBy(() -> store.movementFlows().claim(unknown.id, unknown.version, false, "未确认", "http://localhost/notify"))
                .isInstanceOf(IllegalArgumentException.class);
        store.movementFlows().claim(unknown.id, unknown.version, true, "已核对未收到", "http://localhost/notify");
        assertThat(store.movementFlows().find(flow.id).attemptCount).isEqualTo(2);
    }

    @Test
    void oldDedupWithoutPayloadIsProtectedAndApiRejectsInvalidRequests() throws Exception {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var candidate = candidate("OLD");
        store.claimMovement(candidate.movementKey);
        var flow = store.movementFlows().register(candidate);
        assertThat(flow.status).isEqualTo("LEGACY");
        assertThatThrownBy(() -> store.movementFlows().generated(flow.id, flow.version, "{}", "禁止重建"))
                .isInstanceOf(IllegalStateException.class);
        try (var service = new MovementNotificationService(store)) {
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                    new cn.capinfo.gjj.yhtmock.controller.MovementFlowController(service)).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/yht-mock/api/movements").param("page", "0"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/yht-mock/api/movements").param("status", "LEGACY"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/yht-mock/api/movements/OLD/push")
                    .contentType("application/json").content("{\"version\":1,\"reason\":\"重推\",\"confirmedNotReceived\":true}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        }
    }

    @Test
    void syncHistoryDoesNotSendAndReportsMissingOriginalSource() throws Exception {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var trade = trade(); store.saveTrade(trade);
        try (var service = new MovementNotificationService(store)) {
            assertThat(service.syncHistory().get("skipped")).isEqualTo(1);
            var codec = new CapsCodecService();
            var source = new MockRecord(); source.recordType = "GATEWAY"; source.mesgType = "caps.201.001.01";
            source.sysSeqNo = trade.sysSeqNo;
            source.requestBody = codec.buildHeader("caps.201.001.01", "R", "REF", "CAPS", "CAPS", "CORP", "BANK")
                    + codec.buildXml("caps.201.001.01", "<TranCode>20602</TranCode>", "");
            store.addRecord(source);
            assertThat(service.syncHistory().get("inspected")).isEqualTo(1);
            service.syncHistory();
            var rows = store.movementFlows().query("", "", "", "", "", "", "", 1, 20);
            assertThat(rows.total()).isEqualTo(1);
            assertThat(rows.items().get(0).status).isEqualTo("MISSING");
            assertThat(store.movementFlows().attempts(rows.items().get(0).id)).isEmpty();
        }
    }

    @Test
    void versionTwoUpgradeCreatesLedgerAndPreservesExistingBankConfiguration() throws Exception {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var bank = new BankCounterparty(); bank.bankId = "BANK"; bank.bankName = "银行";
        bank.accountNo = "ACCOUNT"; bank.accountName = "户名"; bank.accountBankId = "999";
        store.saveBankCounterparty(bank);
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("DROP TABLE YHT_MOCK_FLOW_ATTEMPT"); jdbc.execute("DROP TABLE YHT_MOCK_FLOW");
        jdbc.execute("ALTER TABLE YHT_MOCK_MOVEMENT_CFG DROP COLUMN C_FALLBACK_ENABLED");
        jdbc.execute("ALTER TABLE YHT_MOCK_RECORD DROP COLUMN C_DECRYPTED_REQUEST_BODY");
        jdbc.execute("ALTER TABLE YHT_MOCK_RECORD DROP COLUMN C_DECRYPTED_RESPONSE_BODY");
        // 库是按 v6 全量建的，模拟 v2 前把 v6 新增结构回退干净：
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
        jdbc.update("UPDATE YHT_MOCK_CONTROL SET SCHEMA_VERSION = 2 WHERE ID = 1");
        assertThatThrownBy(() -> DatabaseTestSupport.create(source, new YhtMockProperties())).isInstanceOf(IllegalStateException.class);
        var script = java.nio.file.Files.readString(java.nio.file.Path.of("../doc/数据库脚本/一户通挡板达梦/04-upgrade-v2-to-v3.sql"));
        for (String statement : script.substring(script.indexOf("CREATE TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        assertThatThrownBy(() -> DatabaseTestSupport.create(source, new YhtMockProperties())).isInstanceOf(IllegalStateException.class);
        var fallbackScript = java.nio.file.Files.readString(java.nio.file.Path.of("../doc/数据库脚本/一户通挡板达梦/05-upgrade-v3-to-v4.sql"));
        for (String statement : fallbackScript.substring(fallbackScript.indexOf("ALTER TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        assertThatThrownBy(() -> DatabaseTestSupport.create(source, new YhtMockProperties())).isInstanceOf(IllegalStateException.class);
        var recordScript = java.nio.file.Files.readString(java.nio.file.Path.of("../doc/数据库脚本/一户通挡板达梦/06-upgrade-v4-to-v5.sql"));
        for (String statement : recordScript.substring(recordScript.indexOf("ALTER TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        // v5 -> v6：场景规则精简为账号校验规则 + 随机失败开关列。
        var scenarioScript = java.nio.file.Files.readString(java.nio.file.Path.of("../doc/数据库脚本/一户通挡板达梦/07-upgrade-v5-to-v6.sql"));
        for (String statement : scenarioScript.substring(scenarioScript.indexOf("ALTER TABLE")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
        var upgraded = DatabaseTestSupport.create(source, new YhtMockProperties());
        assertThat(upgraded.findBankCounterparty("BANK").accountNo).isEqualTo("ACCOUNT");
        assertThat(upgraded.movementFlows().query("", "", "", "", "", "", "", 1, 20).total()).isZero();
        assertThat(upgraded.getSettings().movement.fallbackEnabled).isFalse();
    }

    @Test
    void batchWithMissingBankConfigurationRegistersOnePackageFlowForLaterGeneration() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var settings = store.getSettings(); settings.movement.enabled = true;
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected"; store.updateSettings(settings);
        var batch = new BatchState(); batch.batchNo = "BATCH-MISSING"; batch.corpAcctNo = "CENTER";
        batch.centerBankId = "UNCONFIGURED"; batch.tranCode = "40502"; batch.checkDate = "20260921";
        batch.status = "SUCC"; batch.resFlag = "SUCC";
        batch.fileData = java.util.Base64.getEncoder().encodeToString(("summary\n"
                + "1|PERSON-BANK|PERSON1|10.00|NAME|00|OK|HOST\n"
                + "2|PERSON-BANK|PERSON2|20.00|NAME|00|OK|HOST").getBytes(StandardCharsets.UTF_8));
        var header = new CapsHeader(); header.origSender = "CORP";
        try (var service = new MovementNotificationService(store)) {
            service.notifyBatch(header, batch);
            var rows = store.movementFlows().query("", "", "", "", "BATCH-MISSING", "", "MISSING", 1, 20);
            // 按包生成：2 条成功明细只登记 1 条包级流水，金额为成功明细合计。
            assertThat(rows.items()).hasSize(1);
            assertThat(rows.items().get(0).amount).isEqualByComparingTo("30.00");
            assertThat(rows.items().get(0).direction).isEqualTo("IN");
            assertThat(rows.items().get(0).centerBankId).isEqualTo("UNCONFIGURED");
            assertThat(rows.items().get(0).lastError).contains("未配置");
            // 同一批次重复触发不产生第二条包级流水（去重键为批次维度）。
            service.notifyBatch(header, batch);
            assertThat(store.movementFlows().query("", "", "", "", "BATCH-MISSING", "", "", 1, 20).items()).hasSize(1);
        }
    }

    @Test
    void batchPackageFlowUsesSuccessfulDetailSumAndNegatesForPayment() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var batch = new BatchState(); batch.batchNo = "BATCH-PAY"; batch.corpAcctNo = "CENTER";
        batch.centerBankId = "CENTER"; batch.tranCode = "40501"; batch.checkDate = "20260921";
        batch.status = "SUCC"; batch.resFlag = "SUCC";
        batch.fileData = java.util.Base64.getEncoder().encodeToString(("summary\n"
                + "1|BANK|ACCT-1|10.50|NAME|00|OK|HOST\n"
                + "2|BANK|ACCT-2|20.25|NAME|00|OK|HOST\n"
                + "3|BANK|ACCT-3|30.00|NAME|01|余额不足|HOST").getBytes(StandardCharsets.UTF_8));
        var header = new CapsHeader(); header.origSender = "CORP";
        try (var service = new MovementNotificationService(store)) {
            service.notifyBatch(header, batch);
            var rows = store.movementFlows().query("", "", "", "", "BATCH-PAY", "", "", 1, 20);
            assertThat(rows.items()).hasSize(1);
            // 付款方向取负；失败明细（30.00）不计入合计。
            assertThat(rows.items().get(0).amount).isEqualByComparingTo("-30.75");
            assertThat(rows.items().get(0).direction).isEqualTo("OUT");
        }
    }

    @Test
    void batchWithNoSuccessfulDetailRecordsSkipInsteadOfPackageFlow() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var batch = new BatchState(); batch.batchNo = "BATCH-ALLFAIL-PKG"; batch.corpAcctNo = "CENTER";
        batch.centerBankId = "CENTER"; batch.tranCode = "40502"; batch.checkDate = "20260921";
        batch.status = "SUCC"; batch.resFlag = "SUCC";
        batch.fileData = java.util.Base64.getEncoder().encodeToString(("summary\n"
                + "1|BANK|ACCT-1|10.00|NAME|01|余额不足|HOST").getBytes(StandardCharsets.UTF_8));
        var header = new CapsHeader(); header.origSender = "CORP";
        try (var service = new MovementNotificationService(store)) {
            service.notifyBatch(header, batch);
            assertThat(store.movementFlows().query("", "", "", "", "BATCH-ALLFAIL-PKG", "", "", 1, 20).items()).isEmpty();
            assertThat(store.listRecords(5)).anyMatch(item -> "MOVEMENT".equals(item.recordType)
                    && "SKIP".equals(item.status) && item.remark.contains("无成功明细"));
        }
    }

    @Test
    void nonSuccessBatchRecordsSkipReasonInsteadOfSilentNoFlow() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected";
        store.updateSettings(settings);
        var batch = new BatchState(); batch.batchNo = "BATCH-PROC"; batch.corpAcctNo = "CENTER";
        batch.centerBankId = "CENTER"; batch.tranCode = "40502"; batch.checkDate = "20260921";
        batch.status = "PROC"; batch.resFlag = "SUCC";
        batch.fileData = java.util.Base64.getEncoder().encodeToString(
                "summary\n1|BANK|PERSON1|10.00|NAME|00|OK|HOST".getBytes(StandardCharsets.UTF_8));
        var header = new CapsHeader(); header.origSender = "CORP";
        try (var service = new MovementNotificationService(store)) {
            service.notifyBatch(header, batch);
            assertThat(store.movementFlows().query("", "", "", "", "BATCH-PROC", "", "", 1, 20).total()).isZero();
            var record = store.listRecords(10).stream()
                    .filter(item -> "MOVEMENT".equals(item.recordType) && "BATCH-PROC".equals(item.batchNo))
                    .findFirst().orElseThrow();
            assertThat(record.status).isEqualTo("SKIP");
            assertThat(record.remark).contains("非成功").contains("PROC");
        }
    }

    @Test
    void nonSuccessTradeRecordsSkipReason() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var trade = trade();
        trade.status = "FAIL";
        var header = new CapsHeader(); header.origSender = "CORP";
        try (var service = new MovementNotificationService(store)) {
            service.notifyTrade(header, trade);
            assertThat(store.movementFlows().query("", "", "", "", "", "", "", 1, 20).total()).isZero();
            assertThat(store.listRecords(10)).anyMatch(item -> "MOVEMENT".equals(item.recordType)
                    && "SKIP".equals(item.status) && item.remark.contains("非成功"));
        }
    }

    @Test
    void successfulBatchWithoutSuccessfulDetailsRecordsSkipReason() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://127.0.0.1:1/no-network-expected";
        store.updateSettings(settings);
        var batch = new BatchState(); batch.batchNo = "BATCH-ALLFAIL"; batch.corpAcctNo = "CENTER";
        batch.centerBankId = "CENTER"; batch.tranCode = "40502"; batch.checkDate = "20260921";
        batch.status = "SUCC"; batch.resFlag = "SUCC";
        batch.fileData = java.util.Base64.getEncoder().encodeToString(
                "summary\n1|BANK|PERSON1|10.00|NAME|01|失败|HOST".getBytes(StandardCharsets.UTF_8));
        var header = new CapsHeader(); header.origSender = "CORP";
        try (var service = new MovementNotificationService(store)) {
            service.notifyBatch(header, batch);
            assertThat(store.movementFlows().query("", "", "", "", "BATCH-ALLFAIL", "", "", 1, 20).total()).isZero();
            var record = store.listRecords(10).stream()
                    .filter(item -> "MOVEMENT".equals(item.recordType) && "BATCH-ALLFAIL".equals(item.batchNo))
                    .findFirst().orElseThrow();
            assertThat(record.status).isEqualTo("SKIP");
            assertThat(record.remark).contains("无成功明细");
        }
    }

    @Test
    void pushing107FinalizesLegacyProcessingBatchAndRegistersPackageFlow() {
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        store.getSettings();
        var batch = new BatchState();
        batch.batchNo = "LEGACY-PROC"; batch.reqId = "REQ-L"; batch.tranCode = "40502";
        batch.status = "PROC"; batch.resFlag = "SUCC"; batch.centerBankId = "CENTER";
        batch.corpAcctNo = "CENTER-ACCOUNT"; batch.checkDate = "20260715";
        batch.fileData = java.util.Base64.getEncoder().encodeToString(
                "summary\n1|BANK|A1|10.00|甲|00|交易成功|H1".getBytes(StandardCharsets.UTF_8));
        store.saveBatch(batch);
        var header = new CapsHeader(); header.origSender = "CORP";

        var callback = new MockCallbackService(new CapsCodecService(), store);
        callback.scheduleCaps107(header, batch);

        // 107 推送即终态：历史处理中批次被按结果文件纠正为 SUCC。
        assertThat(store.findBatch("LEGACY-PROC").status).isEqualTo("SUCC");
        // 动账调度是异步且带延迟的，等待按包流水登记完成（1 条，金额为成功明细合计）。
        org.awaitility.Awaitility.await().atMost(5, java.util.concurrent.TimeUnit.SECONDS).untilAsserted(() -> {
            var flows = store.movementFlows().query("", "", "", "", "LEGACY-PROC", "", "", 1, 20);
            assertThat(flows.items()).hasSize(1);
            assertThat(flows.items().get(0).amount).isEqualByComparingTo("10.00");
            assertThat(flows.items().get(0).status).isEqualTo("MISSING");
        });
    }

    @Test
    void interruptedSenderRecoveryNeedsStoppedOwnerConfirmationAndRejectsLateCompletion() {
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        var flow = store.movementFlows().register(candidate("INTERRUPTED"));
        var pending = store.movementFlows().generated(flow.id, flow.version, "{}", "生成");
        var audit = store.movementFlows().claim(pending.id, pending.version, false, "发送", "http://localhost/notify");
        var sending = store.movementFlows().find(flow.id);
        assertThatThrownBy(() -> store.movementFlows().recover(sending.id, sending.version, true, "同进程不得释放"))
                .isInstanceOf(IllegalStateException.class);
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.update("UPDATE YHT_MOCK_FLOW_ATTEMPT SET C_OWNER_ID = ? WHERE C_ID = ?", "STOPPED-PROCESS", audit.id);
        assertThatThrownBy(() -> store.movementFlows().recover(sending.id, sending.version, false, "未确认"))
                .isInstanceOf(IllegalArgumentException.class);
        var recovered = store.movementFlows().recover(sending.id, sending.version, true, "已停止旧发送进程并核对结算");
        assertThat(recovered.status).isEqualTo("UNKNOWN");
        var stale = new MockRecord(); stale.status = "SUCC";
        assertThatThrownBy(() -> store.movementFlows().finished(audit, stale)).isInstanceOf(IllegalStateException.class);
        var retry = store.movementFlows().claim(recovered.id, recovered.version, true, "确认未收到后重推", "http://localhost/notify");
        assertThatThrownBy(() -> store.movementFlows().finished(audit, stale)).isInstanceOf(IllegalStateException.class);
        store.movementFlows().finished(retry, stale);
        assertThat(store.movementFlows().find(flow.id).status).isEqualTo("SUCC");
    }

    @Test
    void ledgerAmountUsesExactTextInApiResponses() throws Exception {
        var flow = candidate("EXACT-MONEY");
        flow.amount = new java.math.BigDecimal("999999999999999999.99");
        var source = DatabaseTestSupport.newDatabase();
        var store = DatabaseTestSupport.create(source, new YhtMockProperties());
        store.movementFlows().register(flow);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var result = mapper.readTree(mapper.writeValueAsString(store.movementFlows().find(flow.id)));
        assertThat(result.path("amount").isTextual()).isTrue();
        assertThat(result.path("amount").asText()).isEqualTo("999999999999999999.99");
    }

    @Test
    void normalizeSerialsCapsLengthRewritesPayloadAndIsIdempotent() throws Exception {
        // 归一化规则：≤32 位原样保留；>32 位收敛为恰好 32 位，且同一原值结果确定可重复
        assertThat(MovementFlowStore.normalizeSerial("SHORT-1")).isEqualTo("SHORT-1");
        String oversized = "HOST-BANK-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        assertThat(oversized.length()).isGreaterThan(MovementFlowStore.SERIAL_MAX);
        String normalized = MovementFlowStore.normalizeSerial(oversized);
        assertThat(normalized.length()).isEqualTo(MovementFlowStore.SERIAL_MAX);
        assertThat(normalized).isEqualTo(MovementFlowStore.normalizeSerial(oversized));
        assertThat(MovementFlowStore.normalizeSerial(oversized + "-X")).isNotEqualTo(normalized);

        // 历史数据统一更新：超长流水号收敛、已生成报文 yhlsh 同步改写、合规流水不动、可重复执行（幂等）
        var store = DatabaseTestSupport.create(DatabaseTestSupport.newDatabase(), new YhtMockProperties());
        store.movementFlows().register(candidate("SHORT-FLOW"));
        var longFlow = candidate("LONG-FLOW");
        longFlow.sysSeqNo = oversized;
        longFlow.payload = "{\"yhzhhm\":\"ACCOUNT\",\"yhlsh\":\"" + oversized + "\",\"je\":12.34}";
        store.movementFlows().register(longFlow);

        var first = store.movementFlows().normalizeHistoricalSerials();
        assertThat(first.get("inspected")).isEqualTo(1);
        assertThat(first.get("normalized")).isEqualTo(1);
        for (var flow : store.movementFlows().query("", "", "", "", "", "", "", 1, 20).items()) {
            assertThat(flow.sysSeqNo.length()).isLessThanOrEqualTo(MovementFlowStore.SERIAL_MAX);
        }
        var updated = store.movementFlows().find("LONG-FLOW");
        assertThat(updated.sysSeqNo).isEqualTo(normalized);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(updated.payload).path("yhlsh").asText())
                .isEqualTo(normalized);
        assertThat(store.movementFlows().find("SHORT-FLOW").sysSeqNo).isEqualTo("SHORT-FLOW");

        var second = store.movementFlows().normalizeHistoricalSerials();
        assertThat(second.get("inspected")).isEqualTo(0);
        assertThat(second.get("normalized")).isEqualTo(0);
    }

    static MovementFlow candidate(String id) {
        var flow = new MovementFlow(); flow.id = id; flow.movementKey = "CORP|20260921|20602|" + id;
        flow.businessDate = "20260921"; flow.centerBankId = "BANK"; flow.acctNo = "ACCOUNT";
        flow.sysSeqNo = id; flow.direction = "IN"; flow.amount = new java.math.BigDecimal("12.34");
        return flow;
    }

    static TradeState trade() {
        var trade = new TradeState();
        trade.sysSeqNo = "BANK-SERIAL-1";
        trade.serialNum = "REQUEST-1";
        trade.tranCode = "20602";
        trade.creditorAcctNo = "CENTER-ACCOUNT";
        trade.creditorBankId = "CENTER";
        trade.amount = "12.34";
        trade.checkDate = "20260921";
        trade.status = "SUCC";
        trade.resFlag = "SUCC";
        trade.retCode = "000000";
        return trade;
    }
}
