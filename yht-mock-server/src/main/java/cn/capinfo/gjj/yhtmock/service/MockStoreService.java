package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.config.YhtMockProperties;
import cn.capinfo.gjj.yhtmock.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class MockStoreService {

    /** 二类卡日交易限额（元）。 */
    public static final BigDecimal CLASS2_DAILY_LIMIT = new BigDecimal("10000");
    /** 单笔交易随机失败返回码。 */
    public static final String RANDOM_FAIL_CODE = "RANDOM_FAIL";
    /** 对手账户无效返回码。 */
    public static final String ACCOUNT_INVALID_CODE = "ACCT_INVALID";
    /** 二类卡日限额超限返回码。 */
    public static final String LIMIT_EXCEED_CODE = "LIMIT_EXCEED";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final YhtMockProperties properties;
    private final JdbcModelTable<MockSettings> settings;
    private final JdbcModelTable<MovementSettings> movementSettings;
    private final JdbcModelTable<MockRecord> records;
    private final JdbcModelTable<MockScenarioRule> scenarios;
    private final JdbcModelTable<ProtocolState> protocols;
    private final JdbcModelTable<TradeState> trades;
    private final JdbcModelTable<BatchState> batches;
    private final JdbcModelTable<BankCounterparty> bankCounterparties;

    public MockStoreService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                            YhtMockProperties properties) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.properties = properties;
        settings = new JdbcModelTable<>(jdbc, "YHT_MOCK_SETTINGS", MockSettings.class, "singletonId");
        movementSettings = new JdbcModelTable<>(jdbc, "YHT_MOCK_MOVEMENT_CFG", MovementSettings.class, "singletonId");
        records = new JdbcModelTable<>(jdbc, "YHT_MOCK_RECORD", MockRecord.class, "id");
        scenarios = new JdbcModelTable<>(jdbc, "YHT_MOCK_SCENARIO", MockScenarioRule.class, "id");
        protocols = new JdbcModelTable<>(jdbc, "YHT_MOCK_PROTOCOL", ProtocolState.class, "protocolNo");
        trades = new JdbcModelTable<>(jdbc, "YHT_MOCK_TRADE", TradeState.class, "sysSeqNo");
        batches = new JdbcModelTable<>(jdbc, "YHT_MOCK_BATCH", BatchState.class, "batchNo");
        bankCounterparties = new JdbcModelTable<>(jdbc, "YHT_MOCK_BANK_ACCOUNT", BankCounterparty.class, "bankId");
    }

    @PostConstruct
    public void init() {
        Integer version = jdbc.queryForObject("SELECT SCHEMA_VERSION FROM YHT_MOCK_CONTROL WHERE ID = 1", Integer.class);
        if (!Integer.valueOf(6).equals(version)) {
            throw new IllegalStateException("Unsupported YHT mock database schema (expected 6); "
                    + "execute schema-dm.sql (fresh) or schema-dm-migration-v5-v6.sql (upgrade) first");
        }
        org.slf4j.LoggerFactory.getLogger(MockStoreService.class)
                .info("YHT 挡板数据库 Schema 版本={}，配置与业务表校验通过，持久化使用 JDBC", version);
        for (String table : List.of("YHT_MOCK_SETTINGS", "YHT_MOCK_MOVEMENT_CFG", "YHT_MOCK_RECORD",
                "YHT_MOCK_PROTOCOL", "YHT_MOCK_TRADE", "YHT_MOCK_BATCH", "YHT_MOCK_SCENARIO", "YHT_MOCK_MOVEMENT", "YHT_MOCK_BANK_ACCOUNT", "YHT_MOCK_FLOW", "YHT_MOCK_FLOW_ATTEMPT")) {
            jdbc.queryForList("SELECT * FROM " + table + " WHERE 1 = 0");
        }
    }

    <T> T write(Supplier<T> action) {
        return transactions.execute(status -> {
            jdbc.queryForObject("SELECT ID FROM YHT_MOCK_CONTROL WHERE ID = 1 FOR UPDATE", Integer.class);
            return action.get();
        });
    }

    public MockSettings getSettings() {
        return write(() -> {
            MockSettings current = settings.find(1);
            if (current == null) {
                current = new MockSettings();
                applyConfiguredDefaults(current, true);
                saveSettings(current);
            } else {
                MovementSettings movement = movementSettings.find(1);
                if (movement == null) {
                    throw new IllegalStateException("Missing movement settings row");
                }
                current.movement = movement;
                current.movement.applyDefaults();
                applyMovementTargetDefault(current);
            }
            return current;
        });
    }

    public void applyStartupSettings() {
        write(() -> {
            MockSettings current = getSettings();
            applyConfiguredDefaults(current, false);
            saveSettings(current);
            return null;
        });
    }

    private void applyConfiguredDefaults(MockSettings current, boolean firstLoad) {
        boolean override = firstLoad || properties.getCallback().isOverrideStoredSettings();
        String url = properties.getSettlement().getReceiveUrl();
        if (url != null && !url.isBlank() && (override || current.defaultTargetUrl == null || current.defaultTargetUrl.isBlank())) {
            current.defaultTargetUrl = url.trim();
        }
        if (override) {
            current.autoPushEnabled = properties.getCallback().isAutoPushEnabled();
            current.delayMs = properties.getCallback().getDelayMs();
            current.pushCaps107 = properties.getCallback().isPushCaps107();
            current.pushCaps205 = properties.getCallback().isPushCaps205();
            current.pushCaps306 = properties.getCallback().isPushCaps306();
            current.pushCaps308 = properties.getCallback().isPushCaps308();
        }
    }

    private void saveSettings(MockSettings current) {
        if (current.movement == null) {
            current.movement = new MovementSettings();
        }
        applyMovementTargetDefault(current);
        current.movement.validate();
        settings.save(current, 1);
        movementSettings.save(current.movement, 1);
    }

    private void applyMovementTargetDefault(MockSettings current) {
        if (current.movement.targetUrl != null && !current.movement.targetUrl.isBlank()) {
            return;
        }
        String source = current.defaultTargetUrl;
        if (source == null || source.isBlank()) {
            source = properties.getSettlement().getReceiveUrl();
        }
        if (source == null || source.isBlank()) {
            return;
        }
        try {
            java.net.URI uri = java.net.URI.create(source.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null) {
                return;
            }
            for (String suffix : List.of("/api/v1/ywgl/yht/yht/receive", "/yht/yht/receive")) {
                if (uri.getPath().endsWith(suffix)) {
                    String path = uri.getPath().substring(0, uri.getPath().length() - suffix.length());
                    current.movement.targetUrl = new java.net.URI(uri.getScheme(), uri.getAuthority(),
                            path + "/api/v1/ywgl/saveZhbdzt", null, null).toString();
                    return;
                }
            }
        } catch (IllegalArgumentException | java.net.URISyntaxException ignored) {
            return;
        }
    }

    public List<BankCounterparty> listBankCounterparties() {
        return bankCounterparties.select("1 = 1", " ORDER BY C_BANK_ID", 0);
    }

    public BankCounterparty findBankCounterparty(String bankId) {
        return bankId == null || bankId.isBlank() ? null : bankCounterparties.find(bankId.trim());
    }

    public BankCounterparty saveBankCounterparty(BankCounterparty account) {
        if (account == null) {
            throw new IllegalArgumentException("银行对手账户不能为空");
        }
        account.validate();
        return write(() -> {
            account.updatedAt = System.currentTimeMillis();
            bankCounterparties.save(account, account.bankId);
            return account;
        });
    }

    public boolean deleteBankCounterparty(String bankId) {
        return write(() -> jdbc.update("DELETE FROM YHT_MOCK_BANK_ACCOUNT WHERE C_BANK_ID = ?", bankId) > 0);
    }

    public MockSettings updateSettings(MockSettings current) {
        return write(() -> {
            if (current == null) {
                return getSettings();
            }
            saveSettings(current);
            return current;
        });
    }

    public List<MockRecord> listRecords(int limit) {
        return records.select("1 = 1", " ORDER BY C_CREATED_AT DESC, C_ID DESC", limit > 0 ? limit : 100);
    }

    public void clearRecords() {
        write(() -> jdbc.update("DELETE FROM YHT_MOCK_RECORD"));
    }

    public ClearHistoryResult clearHistoryFiles() {
        return write(() -> {
            List<String> tables = List.of("YHT_MOCK_RECORD", "YHT_MOCK_PROTOCOL", "YHT_MOCK_TRADE", "YHT_MOCK_BATCH");
            for (String table : tables) {
                jdbc.update("DELETE FROM " + table);
            }
            return new ClearHistoryResult(List.of(), List.of(), List.of(), tables);
        });
    }

    private long nextId(String column) {
        long value = jdbc.queryForObject("SELECT " + column + " FROM YHT_MOCK_CONTROL WHERE ID = 1", Long.class);
        jdbc.update("UPDATE YHT_MOCK_CONTROL SET " + column + " = ? WHERE ID = 1", Math.addExact(value, 1));
        return value;
    }

    public MockRecord addRecord(MockRecord record) {
        return write(() -> {
            record.id = nextId("RECORD_NEXT");
            record.createdAt = System.currentTimeMillis();
            records.insert(record, record.id);
            return record;
        });
    }

    public List<ProtocolState> listProtocols() {
        return protocols.select("1 = 1", " ORDER BY C_UPDATED_AT DESC, C_PROTOCOL_NO", 0);
    }

    public List<TradeState> listTrades() {
        return trades.select("1 = 1", " ORDER BY C_UPDATED_AT DESC, C_SYS_SEQ_NO", 0);
    }

    public List<BatchState> listBatches() {
        return batches.select("1 = 1", " ORDER BY C_UPDATED_AT DESC, C_BATCH_NO", 0);
    }

    public List<MockScenarioRule> listScenarios() {
        return scenarios.select("1 = 1", " ORDER BY C_UPDATED_AT DESC, C_ID DESC", 0);
    }

    /**
     * 业务查询列表：单笔交易 / 批量交易 / 签约类交易，按页返回摘要字段（不含批次结果文件等大字段）。
     */
    public Map<String, Object> businessList(String type, int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数超出范围");
        }
        String kind = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        int offset = (page - 1) * size;
        List<Map<String, Object>> items = new ArrayList<>();
        long total;
        switch (kind) {
            case "trade" -> {
                total = count("YHT_MOCK_TRADE");
                for (TradeState trade : trades.page("1 = 1", " ORDER BY C_UPDATED_AT DESC, C_SYS_SEQ_NO", offset, size)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", text(trade.sysSeqNo));
                    row.put("traceKeyword", firstNotBlank(trade.reqId, trade.sysSeqNo));
                    row.put("reqId", text(trade.reqId));
                    row.put("serialNum", text(trade.serialNum));
                    row.put("tranCode", text(trade.tranCode));
                    row.put("amount", text(trade.amount));
                    row.put("acctNo", text(trade.acctNo));
                    row.put("acctName", text(trade.acctName));
                    row.put("bankId", text(trade.bankId));
                    row.put("checkDate", text(trade.checkDate));
                    row.put("status", text(trade.status));
                    row.put("resFlag", text(trade.resFlag));
                    row.put("retCode", text(trade.retCode));
                    row.put("retMsg", text(trade.retMsg));
                    row.put("scenarioName", text(trade.scenarioName));
                    row.put("movementEligible", trade.movementEligible);
                    row.put("updatedAt", trade.updatedAt);
                    items.add(row);
                }
            }
            case "batch" -> {
                total = count("YHT_MOCK_BATCH");
                for (BatchState batch : batches.page("1 = 1", " ORDER BY C_UPDATED_AT DESC, C_BATCH_NO", offset, size)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", text(batch.batchNo));
                    row.put("traceKeyword", text(batch.batchNo));
                    row.put("batchNo", text(batch.batchNo));
                    row.put("reqId", text(batch.reqId));
                    row.put("tranCode", text(batch.tranCode));
                    row.put("totalCount", text(batch.totalCount));
                    row.put("totalAmount", text(batch.totalAmount));
                    row.put("centerBankId", text(batch.centerBankId));
                    row.put("corpAcctNo", text(batch.corpAcctNo));
                    row.put("fileName", text(batch.fileName));
                    row.put("checkDate", text(batch.checkDate));
                    row.put("status", text(batch.status));
                    row.put("resFlag", text(batch.resFlag));
                    row.put("errorCode", text(batch.errorCode));
                    row.put("errorMsg", text(batch.errorMsg));
                    row.put("scenarioName", text(batch.scenarioName));
                    row.put("movementEligible", batch.movementEligible);
                    row.put("updatedAt", batch.updatedAt);
                    items.add(row);
                }
            }
            case "protocol" -> {
                total = count("YHT_MOCK_PROTOCOL");
                for (ProtocolState protocol : protocols.page("1 = 1", " ORDER BY C_UPDATED_AT DESC, C_PROTOCOL_NO", offset, size)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", text(protocol.protocolNo));
                    row.put("traceKeyword", firstNotBlank(protocol.protocolNo, protocol.signReqId));
                    row.put("protocolNo", text(protocol.protocolNo));
                    row.put("signReqId", text(protocol.signReqId));
                    row.put("corpNo", text(protocol.corpNo));
                    row.put("customerName", text(protocol.customerName));
                    row.put("acctNo", text(protocol.acctNo));
                    row.put("acctName", text(protocol.acctName));
                    row.put("bankId", text(protocol.bankId));
                    row.put("phone", text(protocol.phone));
                    row.put("changeType", text(protocol.changeType));
                    row.put("sendType", text(protocol.sendType));
                    row.put("protocolProcessCode", text(protocol.protocolProcessCode));
                    row.put("status", text(protocol.status));
                    row.put("resFlag", text(protocol.resFlag));
                    row.put("errorCode", text(protocol.errorCode));
                    row.put("errorMsg", text(protocol.errorMsg));
                    row.put("scenarioName", text(protocol.scenarioName));
                    row.put("updatedAt", protocol.updatedAt);
                    items.add(row);
                }
            }
            default -> throw new IllegalArgumentException("不支持的业务类型：" + type);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", kind);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("totalPages", total == 0 ? 1 : (int) ((total + size - 1) / size));
        result.put("count", items.size());
        result.put("items", items);
        return result;
    }

    /**
     * 批量明细列表：解析批次结果文件逐笔明细，并关联该批次的资金流水状态与失败原因。
     */
    public Map<String, Object> batchDetails(String batchNo) {
        BatchState batch = findBatch(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("批次不存在：" + batchNo);
        }
        String text = decodeFileData(batch.fileData);
        List<Map<String, Object>> details = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            String[] fields = lines[index].split("\\|", -1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", field(fields, 0));
            row.put("bankId", field(fields, 1));
            row.put("acctNo", field(fields, 2));
            row.put("amount", field(fields, 3));
            row.put("acctName", field(fields, 4));
            row.put("retCode", field(fields, 5));
            row.put("retMsg", field(fields, 6));
            row.put("hostSerial", field(fields, 7));
            row.put("success", "00".equals(field(fields, 5)));
            row.put("fieldCount", fields.length);
            details.add(row);
        }
        Map<String, Long> statusCount = new LinkedHashMap<>();
        List<MovementFlow> batchFlows = movementFlows().byBatch(batchNo);
        MovementFlow packageFlow = null;
        for (MovementFlow flow : batchFlows) {
            statusCount.merge(blank(flow.status) ? "UNKNOWN" : flow.status, 1L, Long::sum);
            if (packageFlow == null || flow.createdAt >= packageFlow.createdAt) {
                packageFlow = flow;
            }
        }
        Map<String, Object> packageFlowView = null;
        if (packageFlow != null) {
            packageFlowView = new LinkedHashMap<>();
            packageFlowView.put("id", text(packageFlow.id));
            packageFlowView.put("status", text(packageFlow.status));
            packageFlowView.put("direction", text(packageFlow.direction));
            packageFlowView.put("amount", packageFlow.amount == null ? "" : packageFlow.amount.toPlainString());
            packageFlowView.put("lastError", text(packageFlow.lastError));
            packageFlowView.put("attemptCount", packageFlow.attemptCount);
            packageFlowView.put("sysSeqNo", text(packageFlow.sysSeqNo));
            packageFlowView.put("createdAt", packageFlow.createdAt);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", text(batch.batchNo));
        result.put("status", text(batch.status));
        result.put("resFlag", text(batch.resFlag));
        result.put("errorCode", text(batch.errorCode));
        result.put("errorMsg", text(batch.errorMsg));
        result.put("tranCode", text(batch.tranCode));
        result.put("centerBankId", text(batch.centerBankId));
        result.put("totalCount", text(batch.totalCount));
        result.put("totalAmount", text(batch.totalAmount));
        result.put("fileSummary", lines.length > 0 ? lines[0] : "");
        result.put("detailCount", details.size());
        result.put("successCount", details.stream().filter(row -> Boolean.TRUE.equals(row.get("success"))).count());
        result.put("successAmount", details.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("success")))
                .map(row -> String.valueOf(row.get("amount")))
                .filter(amount -> amount != null && !amount.isBlank())
                .map(amount -> {
                    try {
                        return new java.math.BigDecimal(amount);
                    } catch (NumberFormatException exception) {
                        return java.math.BigDecimal.ZERO;
                    }
                })
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .setScale(2, java.math.RoundingMode.UNNECESSARY)
                .toPlainString());
        result.put("flowGroupMode", "PACKAGE");
        result.put("flowStatusCount", statusCount);
        result.put("packageFlow", packageFlowView);
        result.put("details", details);
        return result;
    }

    /**
     * 修复仍停留在处理中的历史批次：按各批次自身结果文件推导终态并落库。
     * 结果文件缺失或不可解析时不猜测、不修改，单独列入 skipped 供人工核对。
     */
    public Map<String, Object> repairProcessingBatches() {
        return write(() -> {
            List<BatchState> processing = batches.select("C_STATUS = ?", " ORDER BY C_UPDATED_AT, C_BATCH_NO", 0, "PROC");
            List<Map<String, Object>> fixed = new ArrayList<>();
            List<Map<String, Object>> skipped = new ArrayList<>();
            for (BatchState batch : processing) {
                MockGatewaySupport.BatchResultSummary summary =
                        MockGatewaySupport.summarizeBatchResultFile(batch.fileData);
                if (summary == null) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("batchNo", text(batch.batchNo));
                    row.put("reason", batch.fileData == null || batch.fileData.isBlank()
                            ? "缺少批次结果文件，无法推导终态" : "结果文件无法解析，无法推导终态");
                    row.put("totalCount", text(batch.totalCount));
                    skipped.add(row);
                    continue;
                }
                String previous = text(batch.status);
                batch.status = summary.status();
                if ("FAIL".equals(summary.status())) {
                    if (blank(batch.errorCode)) {
                        batch.errorCode = summary.firstFailedCode();
                    }
                    if (blank(batch.errorMsg)) {
                        batch.errorMsg = summary.firstFailedMsg();
                    }
                }
                batch.updatedAt = System.currentTimeMillis();
                batches.save(batch, batch.batchNo);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("batchNo", text(batch.batchNo));
                row.put("before", previous);
                row.put("after", text(batch.status));
                row.put("detailCount", summary.detailCount());
                row.put("tranCode", text(batch.tranCode));
                row.put("updatedAt", batch.updatedAt);
                fixed.add(row);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("processingCount", processing.size());
            result.put("fixedCount", fixed.size());
            result.put("skippedCount", skipped.size());
            result.put("fixed", fixed);
            result.put("skipped", skipped);
            result.put("message", "处理中批次 " + processing.size() + " 个，已按结果文件落终态 " + fixed.size()
                    + " 个，无法推导跳过 " + skipped.size() + " 个；终态批次可到资金流水页执行「同步已有成功交易」补生成按包动账流水。");
            return result;
        });
    }

    private String decodeFileData(String fileData) {
        if (fileData == null || fileData.isBlank()) {
            return "";
        }
        try {
            return new String(java.util.Base64.getDecoder().decode(fileData.trim()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("批次结果文件不是合法的 Base64 内容", exception);
        }
    }

    private String field(String[] fields, int index) {
        return index >= 0 && index < fields.length ? fields[index].trim() : "";
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public MockScenarioRule saveScenario(MockScenarioRule rule) {
        if (rule == null) {
            return null;
        }
        return write(() -> {
            if (rule.id <= 0) {
                rule.id = nextId("SCENARIO_NEXT");
            } else {
                jdbc.update("UPDATE YHT_MOCK_CONTROL SET SCENARIO_NEXT = CASE WHEN SCENARIO_NEXT <= ? THEN ? ELSE SCENARIO_NEXT END WHERE ID = 1",
                        rule.id, Math.addExact(rule.id, 1));
            }
            rule.updatedAt = System.currentTimeMillis();
            scenarios.save(rule, rule.id);
            return rule;
        });
    }

    public boolean deleteScenario(long id) {
        return write(() -> jdbc.update("DELETE FROM YHT_MOCK_SCENARIO WHERE C_ID = ?", id) > 0);
    }

    /**
     * 账号校验规则判定结果。success=true 表示交易成功；失败时由 failRetCode/failRetMsg 描述原因。
     */
    public record AccountRuleDecision(boolean success, String failRetCode, String failRetMsg, String ruleName) {
        /** 成功判定。命名 ok：record 组件 success 的访问器已占用 success() 方法名，静态工厂不能同名。 */
        public static AccountRuleDecision ok() {
            return new AccountRuleDecision(true, "", "", "");
        }

        public static AccountRuleDecision fail(String code, String msg, String ruleName) {
            return new AccountRuleDecision(false, code, msg, ruleName == null ? "" : ruleName);
        }
    }

    /**
     * 按【对手账号】校验交易状态（不再按中心账户）。规则匹配优先级：后更新者优先。
     * - 命中规则且 valid=false：交易失败（对手账户无效）。
     * - 命中规则且 valid=true 且 class1Card=true：一类卡，全部成功。
     * - 命中规则且 valid=true 且 class1Card=false：二类卡，按「日交易限额 1 万元」累计判定（含 runningUsed 的本次请求内累计）。
     * - 未命中任何规则：不限制，默认成功。
     * - randomFail 开启时，在确定成功的基础上再按 randomFailRatio 概率随机失败（覆盖上述判定）。
     */
    public AccountRuleDecision evaluateCounterparty(String counterpartyAcctNo, BigDecimal amount,
                                                    String bizDate, boolean randomFail, double randomFailRatio,
                                                    Map<String, BigDecimal> runningUsed) {
        if (blank(counterpartyAcctNo)) {
            return AccountRuleDecision.ok();
        }
        BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
        List<MockScenarioRule> rules = scenarios.select("C_ENABLED = 1",
                " ORDER BY C_UPDATED_AT DESC, C_ID DESC", 0);
        MockScenarioRule matched = null;
        for (MockScenarioRule rule : rules) {
            if (ruleMatches(rule.accountRule, counterpartyAcctNo)) {
                matched = rule;
                break;
            }
        }
        if (matched == null) {
            return maybeRandomFail(AccountRuleDecision.ok(), randomFail, randomFailRatio);
        }
        if (!matched.valid) {
            return AccountRuleDecision.fail(ACCOUNT_INVALID_CODE, "对手账户无效，交易失败", matched.name);
        }
        if (matched.class1Card) {
            return maybeRandomFail(AccountRuleDecision.ok(), randomFail, randomFailRatio);
        }
        // 二类卡：日交易限额
        BigDecimal base = sumUsedToday(counterpartyAcctNo, bizDate);
        BigDecimal running = runningUsed.getOrDefault(counterpartyAcctNo, BigDecimal.ZERO);
        BigDecimal projected = base.add(running).add(amt);
        if (projected.compareTo(CLASS2_DAILY_LIMIT) > 0) {
            BigDecimal already = base.add(running);
            return AccountRuleDecision.fail(LIMIT_EXCEED_CODE,
                    "二类卡日交易限额(10000元)已超限，当前已用 " + already.toPlainString() + " 元", matched.name);
        }
        runningUsed.put(counterpartyAcctNo, running.add(amt));
        return maybeRandomFail(AccountRuleDecision.ok(), randomFail, randomFailRatio);
    }

    private AccountRuleDecision maybeRandomFail(AccountRuleDecision decision, boolean randomFail, double ratio) {
        if (decision.success() && randomFail && ThreadLocalRandom.current().nextDouble() < ratio) {
            return AccountRuleDecision.fail(RANDOM_FAIL_CODE, "随机模拟交易失败", decision.ruleName());
        }
        return decision;
    }

    private boolean ruleMatches(String accountRule, String acctNo) {
        if (blank(accountRule)) {
            return true; // 空正则视为匹配任意对手账号（兜底规则）
        }
        try {
            return java.util.regex.Pattern.compile(accountRule).matcher(acctNo == null ? "" : acctNo).find();
        } catch (Exception ignored) {
            return accountRule.equals(acctNo); // 非法正则退化为精确匹配
        }
    }

    /**
     * 统计指定对手账号在指定业务日期已成功发生的累计金额（用于二类卡日限额判定）。
     * 来源：YHT_MOCK_TRADE（对手账号+业务日期）与 YHT_MOCK_BATCH 结果文件中成功的明细。
     * 不新增计数表，复用既有记录；当前正在构建的交易/批次尚未落库，不计入。
     */
    public BigDecimal sumUsedToday(String acctNo, String bizDate) {
        if (blank(acctNo) || blank(bizDate)) {
            return BigDecimal.ZERO;
        }
        BigDecimal tradeSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CAST(C_AMOUNT AS DECIMAL(18,2))), 0) FROM YHT_MOCK_TRADE "
                        + "WHERE C_CREDITOR_ACCT_NO = ? AND C_CHECK_DATE = ?",
                BigDecimal.class, acctNo, bizDate);
        BigDecimal batchSum = BigDecimal.ZERO;
        List<BatchState> dayBatches = batches.select("C_CHECK_DATE = ?", "", 0, bizDate);
        for (BatchState batch : dayBatches) {
            String text = decodeFileData(batch.fileData);
            for (String line : text.split("\\r?\\n")) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\\|", -1);
                if (f.length < 6) {
                    continue;
                }
                if (!"00".equals(f[5].trim())) {
                    continue; // 仅统计成功明细
                }
                if (!acctNo.equals(f[2].trim())) {
                    continue;
                }
                try {
                    batchSum = batchSum.add(new BigDecimal(f[3].trim()));
                } catch (NumberFormatException ignore) {
                    // 金额不可解析时跳过该明细
                }
            }
        }
        return (tradeSum == null ? BigDecimal.ZERO : tradeSum).add(batchSum);
    }

    /** @deprecated 账号校验规则不再按中心账户匹配；对手账号判定在交易/批次构建时逐笔完成。 */
    @Deprecated
    public MockScenarioRule matchScenario(MockScenarioContext context) {
        return null;
    }

    public MovementFlowStore movementFlows() {
        return new MovementFlowStore(jdbc, this);
    }

    public MockRecord findMovementSource(String sysSeqNo, String batchNo, String type) {
        boolean batch = batchNo != null && !batchNo.isBlank();
        return first(records.select("C_RECORD_TYPE = ? AND C_MESG_TYPE = ? AND "
                + (batch ? "C_BATCH_NO" : "C_SYS_SEQ_NO") + " = ?", " ORDER BY C_ID ASC", 1,
                "GATEWAY", type, batch ? batchNo : sysSeqNo));
    }

    public boolean claimMovement(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Movement key is required");
        }
        return write(() -> {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM YHT_MOCK_MOVEMENT WHERE MOVEMENT_KEY = ?", Long.class, key);
            if (count != null && count > 0) {
                return false;
            }
            jdbc.update("INSERT INTO YHT_MOCK_MOVEMENT (MOVEMENT_KEY, STATUS) VALUES (?, ?)", key, "UNKNOWN");
            return true;
        });
    }

    public void finishMovement(String key, String status) {
        write(() -> {
            if (jdbc.update("UPDATE YHT_MOCK_MOVEMENT SET STATUS = ? WHERE MOVEMENT_KEY = ?", status, key) != 1) {
                throw new IllegalStateException("Movement has not been claimed");
            }
            return null;
        });
    }

    public void saveProtocol(ProtocolState protocol) {
        saveProtocol(protocol, null);
    }

    public void saveProtocol(ProtocolState protocol, String previousProtocolNo) {
        if (protocol == null || blank(protocol.protocolNo)) {
            return;
        }
        write(() -> {
            if (!blank(previousProtocolNo) && !previousProtocolNo.equals(protocol.protocolNo)) {
                jdbc.update("DELETE FROM YHT_MOCK_PROTOCOL WHERE C_PROTOCOL_NO = ?", previousProtocolNo);
            }
            protocol.updatedAt = System.currentTimeMillis();
            protocols.save(protocol, protocol.protocolNo);
            return null;
        });
    }

    public ProtocolState findProtocol(String protocolNo, String acctNo) {
        ProtocolState protocol = blank(protocolNo) ? null : protocols.find(protocolNo);
        return protocol != null || blank(acctNo) ? protocol : first(protocols.select("C_ACCT_NO = ?", " ORDER BY C_UPDATED_AT DESC, C_PROTOCOL_NO", 1, acctNo));
    }

    public ProtocolState findProtocolByReqId(String reqId) {
        return blank(reqId) ? null : first(protocols.select("C_SIGN_REQ_ID = ?", " ORDER BY C_UPDATED_AT DESC, C_PROTOCOL_NO", 1, reqId));
    }

    public void saveTrade(TradeState trade) {
        if (trade == null || blank(trade.sysSeqNo)) {
            return;
        }
        write(() -> {
            trade.updatedAt = System.currentTimeMillis();
            trades.save(trade, trade.sysSeqNo);
            return null;
        });
    }

    public TradeState findTrade(String sysSeqNo, String reqId) {
        TradeState trade = blank(sysSeqNo) ? null : trades.find(sysSeqNo);
        return trade != null || blank(reqId) ? trade : first(trades.select("C_REQ_ID = ?", " ORDER BY C_UPDATED_AT DESC, C_SYS_SEQ_NO", 1, reqId));
    }

    public void saveBatch(BatchState batch) {
        if (batch == null || blank(batch.batchNo)) {
            return;
        }
        write(() -> {
            batch.updatedAt = System.currentTimeMillis();
            batches.save(batch, batch.batchNo);
            return null;
        });
    }

    public BatchState findBatch(String batchNo) {
        return blank(batchNo) ? null : batches.find(batchNo);
    }

    public Map<String, Object> buildStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("recordCount", count("YHT_MOCK_RECORD"));
        stats.put("protocolCount", count("YHT_MOCK_PROTOCOL"));
        stats.put("tradeCount", count("YHT_MOCK_TRADE"));
        stats.put("batchCount", count("YHT_MOCK_BATCH"));
        stats.put("scenarioCount", count("YHT_MOCK_SCENARIO"));
        MockSettings current = getSettings();
        stats.put("callbackAutoPushEnabled", current.autoPushEnabled);
        stats.put("defaultTargetUrl", current.defaultTargetUrl);
        return stats;
    }

    LegacySnapshotMigration.Result importSnapshot(MockStateSnapshot snapshot, String hash) {
        return write(() -> {
            String existing = jdbc.queryForObject("SELECT MIGRATION_HASH FROM YHT_MOCK_CONTROL WHERE ID = 1", String.class);
            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("records", (long) snapshot.records.size());
            counts.put("protocols", (long) snapshot.protocols.size());
            counts.put("trades", (long) snapshot.trades.size());
            counts.put("batches", (long) snapshot.batches.size());
            counts.put("scenarios", (long) snapshot.scenarios.size());
            counts.put("movementAttempts", (long) snapshot.movementAttempts.size());
            if (hash.equals(existing)) {
                return new LegacySnapshotMigration.Result(true, hash, counts);
            }
            if (existing != null && !existing.isBlank()) {
                throw new IllegalStateException("A different snapshot has already been imported; refusing overwrite");
            }
            for (String table : List.of("YHT_MOCK_SETTINGS", "YHT_MOCK_MOVEMENT_CFG", "YHT_MOCK_RECORD",
                    "YHT_MOCK_PROTOCOL", "YHT_MOCK_TRADE", "YHT_MOCK_BATCH", "YHT_MOCK_SCENARIO", "YHT_MOCK_MOVEMENT", "YHT_MOCK_BANK_ACCOUNT", "YHT_MOCK_FLOW", "YHT_MOCK_FLOW_ATTEMPT")) {
                if (count(table) != 0) {
                    throw new IllegalStateException("Migration requires an unused database; table is not empty: " + table);
                }
            }
            Long recordNext = jdbc.queryForObject("SELECT RECORD_NEXT FROM YHT_MOCK_CONTROL WHERE ID = 1", Long.class);
            Long scenarioNext = jdbc.queryForObject("SELECT SCENARIO_NEXT FROM YHT_MOCK_CONTROL WHERE ID = 1", Long.class);
            if (recordNext != 1 || scenarioNext != 1) {
                throw new IllegalStateException("Migration requires an unused database; identifiers have already been allocated");
            }
            saveSettings(snapshot.settings);
            snapshot.records.forEach(item -> records.insert(item, item.id));
            snapshot.protocols.values().forEach(item -> protocols.insert(item, item.protocolNo));
            snapshot.trades.values().forEach(item -> trades.insert(item, item.sysSeqNo));
            snapshot.batches.values().forEach(item -> batches.insert(item, item.batchNo));
            snapshot.scenarios.forEach(item -> scenarios.insert(item, item.id));
            snapshot.movementAttempts.forEach((key, status) -> jdbc.update(
                    "INSERT INTO YHT_MOCK_MOVEMENT (MOVEMENT_KEY, STATUS) VALUES (?, ?)", key, status));
            long nextRecord = Math.max(snapshot.recordSequence, Math.addExact(snapshot.records.stream()
                    .mapToLong(item -> item.id).max().orElse(0), 1));
            long nextScenario = Math.max(snapshot.scenarioSequence, Math.addExact(snapshot.scenarios.stream()
                    .mapToLong(item -> item.id).max().orElse(0), 1));
            jdbc.update("UPDATE YHT_MOCK_CONTROL SET RECORD_NEXT = ?, SCENARIO_NEXT = ?, MIGRATION_HASH = ?, MIGRATION_COUNTS = ? WHERE ID = 1",
                    nextRecord, nextScenario, hash, counts.toString());
            return new LegacySnapshotMigration.Result(false, hash, counts);
        });
    }


    long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T first(List<T> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    public record ClearHistoryResult(List<String> deletedFiles, List<String> missingFiles,
                                     List<String> failedFiles, List<String> clearedTables) {
    }
}
