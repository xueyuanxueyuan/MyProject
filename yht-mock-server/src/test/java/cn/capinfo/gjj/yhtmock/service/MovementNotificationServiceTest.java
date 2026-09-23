package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class MovementNotificationServiceTest {
    @TempDir
    Path tempDir;
    private MockStoreService store;
    private org.springframework.jdbc.datasource.DriverManagerDataSource database;
    private MovementNotificationService service;
    private HttpServer server;
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private String response = "{\"code\":0,\"data\":true}";
    private int httpStatus = 200;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/saveZhbdzt", exchange -> {
            requests.add(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        database = DatabaseTestSupport.newDatabase();
        store = DatabaseTestSupport.create(database, new cn.capinfo.gjj.yhtmock.config.YhtMockProperties());
        assertThat(store.getSettings().movement.enabled).isFalse();
        var settings = store.getSettings();
        settings.delayMs = 0;
        settings.movement.enabled = true;
        settings.movement.targetUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/saveZhbdzt";
        settings.movement.receiveCode = "TEST-IN";
        settings.movement.payCode = "TEST-OUT";
        store.updateSettings(settings);
        for (String bankId : List.of("CENTER-IN-BANK", "CENTER-OUT-BANK", "CENTER-BATCH-BANK", "BANK")) {
            var account = new cn.capinfo.gjj.yhtmock.model.BankCounterparty();
            account.bankId = bankId;
            account.bankName = "测试中心银行";
            account.accountNo = "99993304000000000001";
            account.accountName = "一户通模拟对账专户";
            account.accountBankId = "999999999999";
            store.saveBankCounterparty(account);
        }
        service = new MovementNotificationService(store);
    }

    @AfterEach
    void tearDown() {
        service.close();
        server.stop(0);
    }

    @Test
    void sendsPageFieldsWithSignedAmountAndVirtualCounterparty() {
        service.notifyTrade(header(), trade("20602", "S1"));
        service.notifyTrade(header(), trade("20601", "S2"));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).get("yhzhhm").asText()).isEqualTo("CENTER-IN");
        assertThat(requests.get(0).get("je").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(requests.get(0).get("jydm").asText()).isEqualTo("TEST-IN");
        assertThat(requests.get(1).get("yhzhhm").asText()).isEqualTo("CENTER-OUT");
        assertThat(requests.get(1).get("je").decimalValue()).isEqualByComparingTo("-12.34");
        assertThat(requests.get(1).get("jydm").asText()).isEqualTo("TEST-OUT");
        assertThat(requests.get(0).get("jydszh").asText()).isEqualTo(store.getSettings().movement.counterpartyAccount);
        assertThat(requests.get(0).get("jydshm").asText()).isEqualTo(store.getSettings().movement.counterpartyName);
        assertThat(requests.get(0).get("jydshh").asText()).isEqualTo(store.getSettings().movement.counterpartyBank);
        assertThat(requests.get(0).get("yue").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(requests.get(0).get("yhlsh").asText()).isEqualTo("HOST-S1");
        assertThat(store.listRecords(10)).allMatch(record -> "SUCC".equals(record.status));
    }

    @Test
    void transactionDateIsCompletionDateNotPushDate() {
        // 单笔与批量按包都异步派发，需等到结算端收到请求再断言（动账通知走独立单线程队列）。
        service.notifyTrade(header(), trade("20602", "JYRQ-TRADE"));
        service.notifyBatch(header(), batch());
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(requests).hasSize(2);
            assertThat(requests).allSatisfy(payload -> {
                assertThat(payload.has("jyrq")).isTrue();
                assertThat(payload.get("jyrq").asText()).isEqualTo("20260920");
            });
        });
        // 明确排除“推送当日”：即便测试在某天运行，落库的仍应是交易完成日，而非推送当天。
        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        if (!"20260920".equals(today)) {
            assertThat(requests).noneSatisfy(payload -> assertThat(payload.get("jyrq").asText()).isEqualTo(today));
        }
    }

    @Test
    void defaultReconciliationIdentityUsesRequestAccountAmountAndSerial() {
        var settings = store.getSettings();
        settings.movement.receiveCode = "";
        settings.movement.payCode = null;
        store.updateSettings(settings);
        service.notifyTrade(header(), trade("20602", "DEFAULT-IN"));
        service.notifyTrade(header(), trade("20601", "DEFAULT-OUT"));
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(payload -> {
            assertThat(payload.get("jydm").asText()).isEqualTo("SBDC100");
            assertThat(payload.get("jydszh").asText()).isEqualTo("99993304000000000001");
            assertThat(payload.get("jydshm").asText()).isEqualTo("一户通模拟对账专户");
            assertThat(payload.get("jydshh").asText()).isEqualTo("999999999999");
        });
        assertThat(requests.get(0).get("yhzhhm").asText()).isEqualTo("CENTER-IN");
        assertThat(requests.get(0).get("je").decimalValue()).isEqualByComparingTo("12.34");
        assertThat(requests.get(0).get("yhlsh").asText()).isEqualTo("HOST-DEFAULT-IN");
        assertThat(requests.get(1).get("yhzhhm").asText()).isEqualTo("CENTER-OUT");
        assertThat(requests.get(1).get("je").decimalValue()).isEqualByComparingTo("-12.34");
        assertThat(requests.get(1).get("yhlsh").asText()).isEqualTo("HOST-DEFAULT-OUT");
    }

    @Test
    void disabledFailedPendingAndUnknownTradesDoNotSend() {
        var settings = store.getSettings();
        settings.movement.enabled = false;
        store.updateSettings(settings);
        service.notifyTrade(header(), trade("20602", "OFF"));
        settings.movement.enabled = true;
        store.updateSettings(settings);
        TradeState failed = trade("20602", "FAIL");
        failed.retCode = "999999";
        service.notifyTrade(header(), failed);
        failed.retCode = "000000";
        failed.status = "PROC";
        service.notifyTrade(header(), failed);
        failed.status = "SUCC";
        failed.resFlag = "FAIL";
        service.notifyTrade(header(), failed);
        service.notifyTrade(header(), trade("201", "UNKNOWN"));
        TradeState missing = trade("20602", "MISSING");
        missing.creditorAcctNo = "";
        service.notifyTrade(header(), missing);
        assertThat(requests).isEmpty();
    }

    @Test
    void concurrentDuplicatesAndRestartDoNotResend() throws Exception {
        var workers = Executors.newFixedThreadPool(4);
        for (int attempt = 0; attempt < 8; attempt++) {
            workers.submit(() -> service.notifyTrade(header(), trade("20602", "SAME")));
        }
        workers.shutdown();
        assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        store.clearRecords();
        MockStoreService restored = DatabaseTestSupport.create(database, new cn.capinfo.gjj.yhtmock.config.YhtMockProperties());
        restored.init();
        try (var restarted = new MovementNotificationService(restored)) {
            TradeState replay = trade("20602", "SAME");
            replay.sysSeqNo = "NEW-HOST";
            restarted.notifyTrade(header(), replay);
        }
        assertThat(requests).hasSize(1);
    }

    @Test
    void batchUsesCenterAccountAndRegistersSinglePackageNotification() {
        BatchState batch = batch();
        batch.status = "PROC";
        service.notifyBatch(header(), batch);
        assertThat(requests).isEmpty();
        batch.status = "SUCC";
        service.notifyBatch(header(), batch);
        service.notifyBatch(header(), batch);
        // 按包生成：同一批次无论多少条成功明细都只推送一条通知，重复触发去重。
        assertThat(requests).hasSize(1);
        assertThat(requests).allMatch(item -> "CENTER-BATCH".equals(item.get("yhzhhm").asText()));
        // 金额取成功明细合计并取负（付款方向），失败明细不计入。
        assertThat(requests.get(0).get("je").decimalValue()).isEqualByComparingTo("-40.00");
        assertThat(requests.get(0).get("yhlsh").asText()).startsWith("MOCK");
    }

    @Test
    void businessFailureAndInvalidResponseAreRecordedWithoutAutomaticRetry() {
        response = "{\"code\":500,\"msg\":\"rejected\"}";
        service.notifyTrade(header(), trade("20602", "BUSINESS-FAIL"));
        service.notifyTrade(header(), trade("20602", "BUSINESS-FAIL"));
        assertThat(store.listRecords(1).get(0).status).isEqualTo("FAIL");
        response = "<html>login</html>";
        service.notifyTrade(header(), trade("20602", "UNKNOWN-RESULT"));
        assertThat(store.listRecords(1).get(0).status).isEqualTo("UNKNOWN");
        httpStatus = 403;
        service.notifyTrade(header(), trade("20602", "HTTP-FAIL"));
        assertThat(store.listRecords(1).get(0).status).isEqualTo("FAIL");
        assertThat(requests).hasSize(3);
    }

    @Test
    void rejectsUnsafeConfigurationAndInvalidAmounts() {
        var settings = store.getSettings();
        settings.movement.targetUrl = "http://name:secret@localhost/saveZhbdzt";
        assertThatThrownBy(() -> store.updateSettings(settings)).isInstanceOf(IllegalArgumentException.class);
        settings.movement.targetUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/saveZhbdzt";
        for (String amount : List.of("0", "-1", "1.001", "invalid")) {
            TradeState invalid = trade("20602", amount);
            invalid.amount = amount;
            service.notifyTrade(header(), invalid);
        }
        settings.movement.authorizationEnv = "YHT_TEST_MISSING_AUTH_20260920";
        store.updateSettings(settings);
        service.notifyTrade(header(), trade("20602", "NO-AUTH"));
        assertThat(requests).isEmpty();
    }

    @Test
    void persistenceFailurePreventsDelivery() throws Exception {
        var blockedDatabase = DatabaseTestSupport.newDatabase();
        MockStoreService blockedStore = DatabaseTestSupport.create(blockedDatabase, new cn.capinfo.gjj.yhtmock.config.YhtMockProperties());
        blockedStore.updateSettings(store.getSettings());
        blockedStore.saveBankCounterparty(store.findBankCounterparty("CENTER-IN-BANK"));
        new org.springframework.jdbc.core.JdbcTemplate(blockedDatabase).execute("DROP TABLE YHT_MOCK_MOVEMENT");
        try (var blockedService = new MovementNotificationService(blockedStore)) {
            blockedService.notifyTrade(header(), trade("20602", "NO-DISK"));
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void standardGatewayTradeTriggersMovementEvenWhenCapsCallbacksAreDisabled() {
        var settings = store.getSettings();
        settings.autoPushEnabled = false;
        store.updateSettings(settings);
        CapsCodecService codec = new CapsCodecService();
        MockCallbackService callback = new MockCallbackService(codec, store, service);
        MockGatewayService gateway = new MockGatewayService(codec, store, callback);
        String xml = codec.buildXml("caps.201.001.01", "<TranCode>20602</TranCode>",
                "<SerialNum>GATEWAY-1</SerialNum><DbtrActId>PERSON</DbtrActId>"
                        + "<CdtrActId>CENTER</CdtrActId><CdtrBankId>CENTER-IN-BANK</CdtrBankId><PayAmt>CNY24.68</PayAmt>");
        gateway.dispatch(message(codec, "caps.201.001.01", xml));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(requests).hasSize(1));
        assertThat(requests.get(0).get("yhzhhm").asText()).isEqualTo("CENTER");
        assertThat(requests.get(0).get("je").decimalValue()).isEqualByComparingTo("24.68");
    }

    @Test
    void batchResultRetrievalTriggersMovementAndRepeatedUploadIsDeduplicated() {
        var settings = store.getSettings();
        settings.autoPushEnabled = false;
        store.updateSettings(settings);
        CapsCodecService codec = new CapsCodecService();
        MockCallbackService callback = new MockCallbackService(codec, store, service);
        MockGatewayService gateway = new MockGatewayService(codec, store, callback);
        String file = "40502|CORP|00000|1|10.00|BANK|CENTER|\n1|UNCONFIGURED-PERSON-BANK|1|PERSON|NAME|10.00|P1||SERIAL|remark|";
        String xml = codec.buildXml("caps.101.001.01",
                "<TranCode>40502</TranCode><CorpAcctNo>CENTER</CorpAcctNo><FileName>stable-batch.txt</FileName>",
                "<FileData>" + codec.base64(file) + "</FileData>");
        for (int attempt = 0; attempt < 2; attempt++) {
            gateway.dispatch(message(codec, "caps.101.001.01", xml));
            assertThat(store.listBatches().get(0).centerBankId).isEqualTo("BANK");
            String batchNo = store.listBatches().get(0).batchNo;
            String query = codec.buildXml("caps.105.001.01", null, "<BatchNo>" + batchNo + "</BatchNo>");
            gateway.dispatch(message(codec, "caps.105.001.01", query));
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(requests).hasSize(1));
        }
        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(requests).hasSize(1));
        assertThat(requests.get(0).get("je").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void selectsCenterBankForReceiptPaymentAndBatchInsteadOfPersonalBank() {
        for (String bankId : List.of("CENTER-IN-BANK", "CENTER-OUT-BANK", "CENTER-BATCH-BANK")) {
            var account = store.findBankCounterparty(bankId);
            account.accountNo = "ACCOUNT-" + bankId;
            account.accountName = "NAME-" + bankId;
            account.accountBankId = "ROUTE-" + bankId;
            store.saveBankCounterparty(account);
        }
        service.notifyTrade(header(), trade("20602", "BANK-IN"));
        service.notifyTrade(header(), trade("20601", "BANK-OUT"));
        service.notifyBatch(header(), batch());
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).path("jydszh").asText()).isEqualTo("ACCOUNT-CENTER-IN-BANK");
        assertThat(requests.get(1).path("jydszh").asText()).isEqualTo("ACCOUNT-CENTER-OUT-BANK");
        // 批量按包生成，只有一条通知，对手账户取批次中心银行（而非明细个人银行）。
        assertThat(requests.get(2).path("jydszh").asText()).isEqualTo("ACCOUNT-CENTER-BATCH-BANK");
        assertThat(requests.get(2).path("jydshm").asText()).isEqualTo("NAME-CENTER-BATCH-BANK");
        assertThat(requests.get(2).path("jydshh").asText()).isEqualTo("ROUTE-CENTER-BATCH-BANK");
    }

    @Test
    void missingUnknownOrDisabledCenterBankFailsWithoutClaimAndCanRetryAfterConfiguration() {
        for (String bankId : new String[]{null, "", "CENTER-IN-BANK-OTHER"}) {
            var trade = trade("20602", "NO-MAPPING");
            trade.creditorBankId = bankId;
            service.notifyTrade(header(), trade);
        }
        var account = store.findBankCounterparty("CENTER-IN-BANK");
        account.enabled = false;
        store.saveBankCounterparty(account);
        service.notifyTrade(header(), trade("20602", "NO-MAPPING"));
        var batch = batch();
        batch.centerBankId = null;
        service.notifyBatch(header(), batch);
        assertThat(requests).isEmpty();
        // 4 笔单笔失败记录 + 1 条按包生成的批量失败记录（此前按明细生成时是 2 条）。
        assertThat(store.listRecords(10)).hasSize(5).allMatch(record -> "FAIL".equals(record.status));
        account.enabled = true;
        store.saveBankCounterparty(account);
        service.notifyTrade(header(), trade("20602", "NO-MAPPING"));
        assertThat(requests).hasSize(1);
    }

    @Test
    void batchPushOnlyHandlesNotPushedAndFailedFlows() {
        var pending = customFlow("BP-PENDING", "PENDING", "{\"je\":12.34}");
        var failed = customFlow("BP-FAIL", "FAIL", "{\"je\":12.34}");
        var accepted = customFlow("BP-SUCC", "SUCC", "{\"je\":12.34}");
        var sending = customFlow("BP-SENDING", "SENDING", "{\"je\":12.34}");
        var legacy = customFlow("BP-LEGACY", "LEGACY", null);
        var unknown = customFlow("BP-UNKNOWN", "UNKNOWN", "{\"je\":12.34}");
        var missing = customFlow("BP-MISSING", "MISSING", null);

        assertThatThrownBy(() -> service.batchPush(List.of(), "批量补推", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("至少勾选一条");
        assertThatThrownBy(() -> service.batchPush(List.of(pending.id), "", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("操作原因");

        // 待推送（未推送）可直接推送；失败流水未勾选核对确认 → 拒绝，不发送。
        var first = service.batchPush(List.of(pending.id, failed.id), "批量补推未推送与失败", false);
        assertThat(first).containsEntry("requested", 2).containsEntry("generatedCount", 0)
                .containsEntry("pushedCount", 1).containsEntry("rejectedCount", 1);
        assertThat(requests).hasSize(1);
        assertThat(rejections(first)).hasSize(1);
        assertThat(rejections(first).get(0)).contains("必须勾选确认");

        // 已受理 / 发送中 / 历史保护 / 结果不明：一律拒绝，其中结果不明要引导单笔补推。
        var second = service.batchPush(List.of(accepted.id, sending.id, legacy.id, unknown.id), "批量补推", true);
        assertThat(second).containsEntry("pushedCount", 0).containsEntry("rejectedCount", 4);
        assertThat(requests).hasSize(1);
        assertThat(rejections(second)).anySatisfy(text -> assertThat(text).contains("单笔补推"))
                .anySatisfy(text -> assertThat(text).contains("重复动账"))
                .anySatisfy(text -> assertThat(text).contains("缺少通知快照"));

        // 未生成：批量入口先补生成通知再推送；失败流水在勾选确认后补推成功。
        var third = service.batchPush(List.of(missing.id, failed.id), "批量补生成并补推", true);
        assertThat(third).containsEntry("generatedCount", 1).containsEntry("pushedCount", 2)
                .containsEntry("rejectedCount", 0);
        assertThat(requests).hasSize(3);
        assertThat(store.movementFlows().find(missing.id).status).isEqualTo("SUCC");
        assertThat(store.movementFlows().find(failed.id).status).isEqualTo("SUCC");
        assertThat(store.movementFlows().find(unknown.id).status).isEqualTo("UNKNOWN");
        assertThat(store.movementFlows().find(accepted.id).attemptCount).isZero();
    }

    @Test
    void flowQueryAcceptsMultipleNotificationStatuses() {
        customFlow("Q-PENDING", "PENDING", "{}");
        customFlow("Q-FAIL", "FAIL", "{}");
        customFlow("Q-SUCC", "SUCC", "{}");
        var page = store.movementFlows().query("", "", "", "", "", "", "MISSING,PENDING,FAIL", 1, 20);
        assertThat(page.items()).extracting(item -> item.status).containsExactlyInAnyOrder("PENDING", "FAIL");
        assertThat(store.movementFlows().query("", "", "", "", "", "", " FAIL ", 1, 20).items())
                .extracting(item -> item.status).containsExactly("FAIL");
        assertThatThrownBy(() -> store.movementFlows().query("", "", "", "", "", "", "PENDING,BOGUS", 1, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("通知状态无效");
    }

    @SuppressWarnings("unchecked")
    private List<String> rejections(Map<String, Object> summary) {
        return ((List<Map<String, Object>>) summary.get("rejected")).stream()
                .map(row -> (String) row.get("id") + "：" + row.get("reason")).toList();
    }

    /**
     * 直接落库一条指定状态的流水，用于验证批量推送的状态口径（不经过受理流程，避免自动推送干扰）。
     */
    private cn.capinfo.gjj.yhtmock.model.MovementFlow customFlow(String serial, String status, String payload) {
        var flow = new cn.capinfo.gjj.yhtmock.model.MovementFlow();
        flow.id = java.util.UUID.nameUUIDFromBytes(("BATCH-PUSH|" + serial).getBytes(StandardCharsets.UTF_8)).toString();
        flow.movementKey = "CORP|20260920|20602|" + serial;
        flow.sourceType = "TRADE";
        flow.businessDate = "20260920";
        flow.acctNo = "CENTER-IN";
        flow.centerBankId = "CENTER-IN-BANK";
        flow.direction = "IN";
        flow.amount = new java.math.BigDecimal("12.34");
        flow.sysSeqNo = "HOST-" + serial;
        flow.tranCode = "20602";
        flow.status = status;
        flow.payload = payload;
        return store.movementFlows().register(flow);
    }

    private String message(CapsCodecService codec, String type, String xml) {
        return codec.buildHeader(type, "R", "REF-001", "CAPS", "CAPS", "CORP", "BANK") + xml;
    }

    private CapsHeader header() {
        CapsHeader header = new CapsHeader();
        header.origSender = "CORP";
        return header;
    }

    private TradeState trade(String code, String serial) {
        TradeState trade = new TradeState();
        trade.tranCode = code;
        trade.serialNum = serial;
        trade.sysSeqNo = "HOST-" + serial;
        trade.checkDate = "20260920";
        trade.bankId = "CENTER-OUT-BANK";
        trade.creditorBankId = "CENTER-IN-BANK";
        trade.acctNo = "CENTER-OUT";
        trade.creditorAcctNo = "CENTER-IN";
        trade.amount = "12.34";
        trade.status = "SUCC";
        trade.resFlag = "SUCC";
        trade.retCode = "000000";
        return trade;
    }

    private BatchState batch() {
        BatchState batch = new BatchState();
        batch.batchNo = "BATCH-1";
        batch.centerBankId = "CENTER-BATCH-BANK";
        batch.corpAcctNo = "CENTER-BATCH";
        batch.tranCode = "40501";
        batch.checkDate = "20260920";
        batch.status = "SUCC";
        batch.resFlag = "SUCC";
        batch.fileData = Base64.getEncoder().encodeToString(("summary\n"
                + "1|BANK|PERSON1|10.00|NAME1|00|OK|HOST\n"
                + "2|BANK|PERSON2|20.00|NAME2|99|FAIL|HOST\n"
                + "3|BANK|PERSON3|30.00|NAME3|00|OK|HOST").getBytes(StandardCharsets.UTF_8));
        return batch;
    }
}
