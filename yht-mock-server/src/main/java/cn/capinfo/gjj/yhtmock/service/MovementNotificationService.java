package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.BankCounterparty;
import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MovementSettings;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import cn.capinfo.gjj.yhtmock.model.MovementFlow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class MovementNotificationService implements AutoCloseable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MovementNotificationService.class);
    private final MockStoreService store;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000), runnable -> {
                Thread thread = new Thread(runnable, "mock-movement-notification");
                thread.setDaemon(true);
                return thread;
            });

    public MovementNotificationService(MockStoreService store) {
        this.store = store;
    }

    public void scheduleTrade(CapsHeader header, TradeState trade) {
        if (trade != null) {
            TradeState copy = mapper.convertValue(trade, TradeState.class);
            submit(() -> notifyTrade(header, copy));
        }
    }

    public void scheduleBatch(CapsHeader header, BatchState batch) {
        if (batch != null) {
            BatchState copy = mapper.convertValue(batch, BatchState.class);
            submit(() -> notifyBatch(header, copy));
        }
    }

    private void submit(Runnable action) {
        try {
            long delay = Math.max(0L, store.getSettings().delayMs);
            executor.execute(() -> {
                try {
                    Thread.sleep(delay);
                    action.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failure("", "", "服务关闭导致通知任务取消");
                }
            });
        } catch (RejectedExecutionException exception) {
            failure("", "", "通知队列已满或服务正在关闭，未发送");
        }
    }

    void notifyTrade(CapsHeader header, TradeState trade) {
        processTrade(header, trade, true);
    }

    private void processTrade(CapsHeader header, TradeState trade, boolean automatic) {
        MovementSettings settings = store.getSettings().movement;
        if (trade == null) {
            return;
        }
        if (!"SUCC".equals(trade.status) || !"SUCC".equals(trade.resFlag) || !"000000".equals(trade.retCode)) {
            skip(trade.reqId, "", "原交易非成功（状态 " + safe(trade.status) + "/结果标志 " + safe(trade.resFlag)
                    + "/结果码 " + safe(trade.retCode) + "），仅 SUCC+SUCC+000000 生成动账流水");
            return;
        }
        try {
            if (!trade.movementEligible) {
                throw new IllegalArgumentException("原交易缺少金额或稳定业务标识，未生成通知");
            }
            boolean payment = payment(trade.tranCode, "20601", "20602");
            String serial = required(first(trade.serialNum, trade.reqId, trade.sysSeqNo), 120, "交易流水");
            String key = key(header, trade.checkDate, trade.tranCode, serial);
            // 银行流水号统一不超过 32 位：原值超长时按「前缀+确定性哈希」收敛后再登记，空值仍拒绝。
            String bankSerial = MovementFlowStore.normalizeSerial(trade.sysSeqNo);
            required(bankSerial, MovementFlowStore.SERIAL_MAX, "银行流水");
            MovementFlow flow =             registerFlow(key, payment ? trade.acctNo : trade.creditorAcctNo,
                    payment ? trade.bankId : trade.creditorBankId, payment, trade.amount,
                    bankSerial, first(trade.reqId, trade.serialNum), "", trade.tranCode, trade.checkDate);
            log.info("登记单笔动账流水 交易码={} 流水={} 方向={} 金额={} 中心银行={}",
                    trade.tranCode, serial, payment ? "付款" : "收款", trade.amount,
                    payment ? safe(trade.bankId) : safe(trade.creditorBankId));
            if (automatic && settings.enabled && trade.callbackEnabled) autoPush(flow);
        } catch (Exception exception) {
            failure(trade.reqId, "", exception.getMessage());
        }
    }

    void notifyBatch(CapsHeader header, BatchState batch) {
        processBatch(header, batch, true);
    }

    private void processBatch(CapsHeader header, BatchState batch, boolean automatic) {
        MovementSettings settings = store.getSettings().movement;
        if (batch == null) {
            return;
        }
        if (!"SUCC".equals(batch.status) || !"SUCC".equals(batch.resFlag)
                || !(blank(batch.errorCode) || "00".equals(batch.errorCode))) {
            skip(batch.reqId, batch.batchNo, "原批次非成功（状态 " + safe(batch.status) + "/结果标志 "
                    + safe(batch.resFlag) + "/错误码 " + safe(batch.errorCode) + "），仅 SUCC+SUCC+00 生成动账流水");
            return;
        }
        try {
            if (!batch.movementEligible) {
                throw new IllegalArgumentException("原批量请求缺少有效明细，未生成通知");
            }
            boolean payment = payment(batch.tranCode, "40501", "40502");
            required(batch.batchNo, 120, "批次号");
            String text = new String(Base64.getDecoder().decode(required(batch.fileData, Integer.MAX_VALUE,
                    "批量结果文件")), StandardCharsets.UTF_8);
            String[] lines = text.split("\\r?\\n");
            if (lines.length < 2) {
                throw new IllegalArgumentException("批量结果文件无明细");
            }
            int successCount = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (int index = 1; index < lines.length; index++) {
                if (lines[index].isBlank()) {
                    continue;
                }
                String[] fields = lines[index].split("\\|", -1);
                if (fields.length < 8) {
                    throw new IllegalArgumentException("批量结果文件第" + (index + 1) + "行字段不足");
                }
                if (!"00".equals(fields[5])) {
                    continue;
                }
                total = total.add(new BigDecimal(required(fields[3], 40, "明细金额")));
                successCount++;
            }
            if (successCount == 0) {
                skip(batch.reqId, batch.batchNo, "批次结果文件无成功明细（明细结果码非 00），未生成动账流水");
                return;
            }
            // 按包生成：整个批次只登记一条动账流水，金额取成功明细合计，去重键使用批次维度而非明细维度。
            String key = key(header, batch.checkDate, batch.tranCode, first(batch.requestFileName, batch.batchNo));
            // 银行流水号统一不超过 32 位：MOCK 前缀 + 确定性 UUID 十六进制前 28 位（4 + 28 = 32）。
            String bankSerial = "MOCK" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))
                    .toString().replace("-", "").substring(0, 28);
            MovementFlow flow = registerFlow(key, batch.corpAcctNo, batch.centerBankId, payment,
                    total.toPlainString(), bankSerial, batch.reqId, batch.batchNo, batch.tranCode, batch.checkDate);
            log.info("按包登记批量动账流水 批次={} 交易码={} 成功明细数={} 合计金额={} 中心银行={}",
                    batch.batchNo, batch.tranCode, successCount, total.toPlainString(), safe(batch.centerBankId));
            if (automatic && settings.enabled && batch.callbackEnabled) {
                try {
                    autoPush(flow);
                } catch (Exception exception) {
                    failure(batch.reqId, batch.batchNo, exception.getMessage());
                }
            }
        } catch (Exception exception) {
            failure(batch.reqId, batch.batchNo, exception.getMessage());
        }
    }

    private BankCounterparty counterparty(String centerBankId, MovementSettings settings) {
        if (settings.fallbackEnabled) {
            // 兜底开关开启：不再按中心账户所属银行判断是否配置对手账户，统一使用默认兜底账户。
            var fallback = new BankCounterparty();
            fallback.bankId = "FALLBACK";
            fallback.bankName = "默认兜底账户";
            fallback.accountNo = settings.counterpartyAccount;
            fallback.accountName = settings.counterpartyName;
            fallback.accountBankId = settings.counterpartyBank;
            fallback.enabled = true;
            fallback.validate();
            return fallback;
        }
        String bankId = required(centerBankId, 40, "中心账户所属银行标识");
        BankCounterparty account = store.findBankCounterparty(bankId);
        if (account == null || !account.enabled) {
            throw new IllegalArgumentException("中心账户所属银行 " + bankId + " 未配置或已停用对手账户");
        }
        account.validate();
        return account;
    }

    private MovementFlow registerFlow(String key, String account, String bank, boolean payment,
                                      String amount, String bankSerial, String reqId, String batchNo,
                                      String tranCode, String businessDate) {
        BigDecimal money = new BigDecimal(required(amount, 40, "交易金额")).setScale(2, RoundingMode.UNNECESSARY);
        if (money.signum() <= 0 || money.precision() > 20) throw new IllegalArgumentException("交易金额必须为有效正数，最多18位整数及2位小数");
        var flow = new MovementFlow();
        flow.id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        flow.movementKey = key;
        flow.sourceType = blank(batchNo) ? "TRADE" : "BATCH";
        flow.businessDate = businessDate;
        flow.acctNo = required(account, 40, "中心账号");
        flow.centerBankId = bank;
        flow.direction = payment ? "OUT" : "IN";
        flow.amount = payment ? money.negate() : money;
        flow.sysSeqNo = bankSerial;
        flow.reqId = reqId;
        flow.batchNo = batchNo;
        flow.tranCode = tranCode;
        return store.movementFlows().register(flow);
    }

    private void autoPush(MovementFlow flow) throws Exception {
        if (!"MISSING".equals(flow.status)) return;
        try {
            flow = generateMovement(flow.id, flow.version, "自动生成通知");
            pushMovement(flow.id, flow.version, false, "自动推送");
        } catch (IllegalStateException exception) {
            if (!"SENDING".equals(store.movementFlows().find(flow.id).status)
                    && !"SUCC".equals(store.movementFlows().find(flow.id).status)) throw exception;
        }
    }

    public MovementFlow generateMovement(String id, long version, String reason) {
        reason = required(reason, 200, "操作原因");
        var flow = store.movementFlows().find(id);
        try {
            var settings = store.getSettings().movement;
            settings.applyDefaults();
            settings.validateNotificationFields();
            var counterparty = counterparty(flow.centerBankId, settings);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("yhzhhm", flow.acctNo);
            payload.put("jydm", "OUT".equals(flow.direction) ? settings.payCode : settings.receiveCode);
            payload.put("jydszh", counterparty.accountNo);
            payload.put("jydshm", counterparty.accountName);
            payload.put("jydshh", counterparty.accountBankId);
            payload.put("je", flow.amount);
            payload.put("yue", settings.balance);
            payload.put("zhaiyao", "一户通挡板模拟" + ("OUT".equals(flow.direction) ? "付款" : "收款"));
            payload.put("beizhu", "挡板模拟动账，原交易标识：" + flow.movementKey);
            payload.put("yhlsh", flow.sysSeqNo);
            // 交易日期必须是交易处理完成的日期（即原交易 checkDate），不随推送时间变化；
            // 结算端按原接口接收并落 zjZhbdtz.jyrq，不再自行按推送日生成日期。
            if (!blank(flow.businessDate)) {
                payload.put("jyrq", flow.businessDate);
            }
            MovementFlow generated = store.movementFlows().generated(id, version, mapper.writeValueAsString(payload), reason);
            log.info("生成动账通知流水 流水={} 方向={} 金额={} 对手账号={} 兜底={} 原因={}",
                    id, flow.direction, flow.amount, counterparty.accountNo, settings.fallbackEnabled, reason);
            return generated;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            store.movementFlows().generationFailed(id, exception.getMessage());
            log.warn("生成动账通知失败 流水={} 原因={}", id, exception.getMessage());
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    /**
     * 批量补生成并推送：只处理「未生成 / 待推送 / 失败」的流水（即未推送与推送失败两类）。
     * 未生成的先补生成通知再推送；失败属于补推，必须已确认结算端未收到。
     * 已受理、发送中、历史保护一律拒绝；结果不明（可能已推送）在批量入口也拒绝，
     * 只能回到单笔详情里核对结算结果后补推，避免无差别重复动账。逐条处理并返回每个流水的处理结果。
     */
    public Map<String, Object> batchPush(List<String> ids, String reason, boolean confirmedNotReceived) {
        String operationReason = required(reason, 200, "操作原因");
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请至少勾选一条资金流水");
        }
        List<String> targets = new ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(item -> item != null && !item.isBlank()).map(String::trim).toList()));
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("请至少勾选一条资金流水");
        }
        if (targets.size() > 50) {
            throw new IllegalArgumentException("单次批量推送最多 50 条，当前勾选 " + targets.size() + " 条，请分批处理");
        }
        List<Map<String, Object>> pushed = new ArrayList<>();
        List<Map<String, Object>> generated = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        for (String id : targets) {
            MovementFlow flow;
            try {
                flow = store.movementFlows().find(id);
            } catch (RuntimeException exception) {
                rejected.add(rejection(id, "", "", "流水不存在或已清理"));
                continue;
            }
            String status = safe(flow.status);
            if (!Set.of("MISSING", "PENDING", "FAIL").contains(status)) {
                rejected.add(rejection(flow.id, flow.sysSeqNo, status, statusRule(status)));
                continue;
            }
            try {
                if ("MISSING".equals(status)) {
                    flow = generateMovement(flow.id, flow.version, operationReason);
                    generated.add(entry(flow.id, flow.sysSeqNo, flow.status, flow.amount == null ? "" : flow.amount.toPlainString()));
                }
                if (!"PENDING".equals(flow.status) && !confirmedNotReceived) {
                    rejected.add(rejection(flow.id, flow.sysSeqNo, flow.status,
                            "补推失败 / 结果不明流水前必须勾选确认结算未收到"));
                    continue;
                }
                MovementFlow result = pushMovement(flow.id, flow.version, confirmedNotReceived, operationReason);
                pushed.add(entry(result.id, result.sysSeqNo, result.status,
                        result.amount == null ? "" : result.amount.toPlainString()));
            } catch (RuntimeException exception) {
                rejected.add(rejection(flow.id, flow.sysSeqNo, status, exception.getMessage()));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requested", targets.size());
        summary.put("generatedCount", generated.size());
        summary.put("pushedCount", pushed.size());
        summary.put("rejectedCount", rejected.size());
        summary.put("generated", generated);
        summary.put("pushed", pushed);
        summary.put("rejected", rejected);
        summary.put("message", "勾选 " + targets.size() + " 条：补生成 " + generated.size() + " 条，推送 "
                + pushed.size() + " 条，拒绝 " + rejected.size()
                + " 条（只有未推送（未生成 / 待推送）与推送失败的流水可以推送，已受理 / 发送中 / 历史保护 / 结果不明不重复推送）。");
        log.info("批量补生成并推送：勾选={} 补生成={} 推送={} 拒绝={} 原因={}",
                targets.size(), generated.size(), pushed.size(), rejected.size(), operationReason);
        return summary;
    }

    /**
     * 不允许在批量入口处理的状态说明：口径与页面列表展示、单笔推送校验一致。
     */
    private String statusRule(String status) {
        return switch (status) {
            case "UNKNOWN" -> "结果不明可能已经推送，批量推送已拒绝；请在流水详情中核对结算接收结果后单笔补推";
            case "SUCC" -> "已受理的流水不允许重复推送，重复推送可能重复动账";
            case "SENDING" -> "发送中的流水不允许重复推送";
            case "LEGACY" -> "历史保护流水缺少通知快照，禁止推送";
            default -> "当前状态（" + (status.isEmpty() ? "未知" : status) + "）不允许推送";
        };
    }

    private Map<String, Object> entry(String id, String sysSeqNo, String status, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", safe(id));
        row.put("sysSeqNo", safe(sysSeqNo));
        row.put("status", safe(status));
        row.put("amount", safe(amount));
        return row;
    }

    private Map<String, Object> rejection(String id, String sysSeqNo, String status, String reason) {
        Map<String, Object> row = entry(id, sysSeqNo, status, "");
        row.put("reason", safe(reason));
        return row;
    }

    public MovementFlow pushMovement(String id, long version, boolean confirmedNotReceived, String reason) {
        reason = required(reason, 200, "操作原因");
        var flow = store.movementFlows().find(id);
        if (flow.version != version || !java.util.Set.of("PENDING", "FAIL", "UNKNOWN").contains(flow.status)
                || blank(flow.payload)) throw new IllegalStateException("流水状态已变化或不允许推送，请刷新详情");
        var settings = mapper.convertValue(store.getSettings().movement, MovementSettings.class);
        settings.enabled = true;
        settings.validate();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(settings.targetUrl))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(flow.payload));
        String authorization = "";
        if (!blank(settings.authorizationEnv)) {
            authorization = System.getenv(settings.authorizationEnv);
            if (blank(authorization)) throw new IllegalArgumentException("指定的鉴权环境变量为空，未发送通知");
            builder.header("Authorization", authorization);
        }
        HttpRequest request = builder.build();
        var attempt = store.movementFlows().claim(id, version, confirmedNotReceived, reason, settings.targetUrl);
        MockRecord record = record(flow.reqId, flow.batchNo);
        record.sysSeqNo = flow.sysSeqNo;
        record.target = settings.targetUrl;
        record.requestBody = flow.payload;
        record.status = "UNKNOWN";
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            record.responseBody = blank(authorization) ? response.body() : response.body().replace(authorization, "[REDACTED]");
            record.remark = "HTTP " + response.statusCode() + "；补推前请核对结算是否已接收";
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                record.status = "FAIL";
            } else {
                JsonNode result = mapper.readTree(response.body());
                if (result != null && result.hasNonNull("code")) {
                    boolean rejected = result.path("data").isBoolean() && !result.path("data").asBoolean();
                    record.status = "0".equals(result.get("code").asText()) && !rejected ? "SUCC" : "FAIL";
                }
            }
            log.info("推送动账通知 流水={} → {} HTTP {} 结果={}", id, settings.targetUrl, response.statusCode(), record.status);
        } catch (Exception exception) {
            record.remark = "通知结果不明（" + exception.getClass().getSimpleName() + "），请核对结算后再补推";
            log.warn("推送动账通知结果不明 流水={} → {} 原因={}", id, settings.targetUrl, exception.getMessage());
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return store.movementFlows().finished(attempt, record);
    }

    public MovementFlowStore flowStore() { return store.movementFlows(); }

    public Map<String, Object> syncHistory() {
        int inspected = 0;
        int skipped = 0;
        var codec = new CapsCodecService();
        for (var trade : store.listTrades()) {
            if (!"SUCC".equals(trade.status) || !"SUCC".equals(trade.resFlag)
                    || !"000000".equals(trade.retCode) || !trade.movementEligible
                    || !("20601".equals(trade.tranCode) || "20602".equals(trade.tranCode))) continue;
            var source = store.findMovementSource(trade.sysSeqNo, "", "caps.201.001.01");
            var header = source == null ? null : codec.parseFrame(source.requestBody).header();
            if (header == null || blank(header.origSender)) { skipped++; continue; }
            processTrade(header, trade, false);
            inspected++;
        }
        for (var batch : store.listBatches()) {
            if (!"SUCC".equals(batch.status) || !"SUCC".equals(batch.resFlag) || !batch.movementEligible
                    || !(blank(batch.errorCode) || "00".equals(batch.errorCode))
                    || !("40501".equals(batch.tranCode) || "40502".equals(batch.tranCode))) continue;
            var source = store.findMovementSource("", batch.batchNo, "caps.101.001.01");
            var header = source == null ? null : codec.parseFrame(source.requestBody).header();
            if (header == null || blank(header.origSender)) { skipped++; continue; }
            processBatch(header, batch, false);
            inspected++;
        }
        return Map.of("inspected", inspected, "skipped", skipped, "message", "仅同步可核验的成功交易到流水，未发送通知；缺失原始请求的交易已跳过");
    }

    private void failure(String reqId, String batchNo, String reason) {
        MockRecord record = record(reqId, batchNo);
        record.status = "FAIL";
        record.remark = "未发送动账通知：" + reason;
        store.addRecord(record);
        log.warn("动账通知失败 reqId={} 批次={} 原因={}", reqId, batchNo, reason);
    }

    /**
     * 记录“未生成动账流水”的跳过原因：原交易/批次非成功时不再静默返回，避免页面上只剩 107/205 成功而查不到任何动账痕迹。
     */
    private void skip(String reqId, String batchNo, String reason) {
        MockRecord record = record(reqId, batchNo);
        record.status = "SKIP";
        record.remark = "未生成动账流水：" + reason;
        store.addRecord(record);
        log.warn("未生成动账流水 reqId={} 批次={} 原因={}", reqId, batchNo, reason);
    }

    private MockRecord record(String reqId, String batchNo) {
        MockRecord record = new MockRecord();
        record.recordType = "MOVEMENT";
        record.source = "yht-mock";
        record.mesgType = "saveZhbdzt";
        record.reqId = reqId;
        record.batchNo = batchNo;
        return record;
    }

    private String key(CapsHeader header, String date, String code, String serial) {
        return String.join("|", required(header == null ? null : header.origSender, 40, "企业号"),
                required(date, 8, "业务日期"), code, serial);
    }

    private boolean payment(String code, String payCode, String receiveCode) {
        if (!payCode.equals(code) && !receiveCode.equals(code)) {
            throw new IllegalArgumentException("不支持的动账交易类型：" + code);
        }
        return payCode.equals(code);
    }

    private String required(String value, int max, String label) {
        return MovementSettings.required(value, max, label);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String first(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return "";
    }

    @Override
    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
