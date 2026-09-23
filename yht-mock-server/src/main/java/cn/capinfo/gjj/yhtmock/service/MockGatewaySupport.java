package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.MockScenarioContext;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MockGatewaySupport {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter TS_MS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CapsCodecService codecService;
    private final MockStoreService storeService;

    public MockGatewaySupport(CapsCodecService codecService, MockStoreService storeService) {
        this.codecService = codecService;
        this.storeService = storeService;
    }

    public MockScenarioContext buildScenarioContext(String requestMesgType, String acctNo,
                                                    String protocolNo, String reqId, String batchNo, String sysSeqNo) {
        MockScenarioContext context = new MockScenarioContext();
        context.requestMesgType = requestMesgType;
        context.acctNo = acctNo;
        context.protocolNo = protocolNo;
        context.reqId = reqId;
        context.batchNo = batchNo;
        context.sysSeqNo = sysSeqNo;
        return context;
    }

    public ProtocolState buildProtocolState(Document document, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
        ProtocolState protocolState = new ProtocolState();
        protocolState.protocolNo = firstNonBlank(codecService.text(document, "DbtrProtocol"),
                codecService.text(document, "OrgnlDbtrProtocol"),
                codecService.text(document, "OrgnlId"));
        if (protocolState.protocolNo.isBlank() || "0".equals(protocolState.protocolNo)) {
            protocolState.protocolNo = "MOCK-PROT-" + timestamp();
        }
        protocolState.acctNo = firstNonBlank(codecService.text(document, "DbtrActId"),
                codecService.text(document, "AcctNo"));
        protocolState.acctName = firstNonBlank(codecService.text(document, "DbtrActName"),
                codecService.text(document, "AcctName"));
        protocolState.corpNo = successCorp(requestHeader);
        protocolState.customerId = safe(codecService.text(document, "CstmrId"));
        protocolState.customerName = firstNonBlank(codecService.text(document, "CstmrNm"), protocolState.acctName);
        protocolState.feeNoList = normalizeFeeNoList(codecService.text(document, "FeeNoList"));
        protocolState.bankId = safe(codecService.text(document, "DbtrBankId"));
        protocolState.phone = firstNonBlank(codecService.text(document, "DbtrPhone"),
                codecService.text(document, "Phone"), codecService.text(document, "Mobile"));
        protocolState.signReqId = safe(codecService.text(document, "ReqId"));
        protocolState.authCode = safe(codecService.text(document, "AuthCd"));
        protocolState.changeType = safe(codecService.text(document, "ChngTp"));
        protocolState.sendType = safe(codecService.text(document, "SndTp"));
        protocolState.origMsgId = safe(codecService.text(document, "OrigMsgId"));
        protocolState.pyerBgNum = safe(codecService.text(document, "PyerBgNum"));
        protocolState.queryTime = safe(codecService.text(document, "QueryTime"));
        protocolState.protocolProcessCode = resolveProtocolProcessCode(protocolState, scenarioRule);
        protocolState.remark = resolveMsg(scenarioRule, safe(codecService.text(document, "Remark"), "mock protocol accepted"));
        protocolState.status = resolveStatus(scenarioRule, "SUCC");
        protocolState.resFlag = resolveResFlag(scenarioRule, "SUCC");
        protocolState.errorCode = resolveErrorCode(scenarioRule, "");
        protocolState.errorMsg = resolveErrorMsg(scenarioRule, "");
        protocolState.callbackEnabled = !isAutoCallbackDisabled(scenarioRule);
        protocolState.callbackMesgType = defaultString(resolveCallbackType(scenarioRule), "caps.306.001.01");
        protocolState.scenarioName = scenarioRule == null ? "" : safe(scenarioRule.name, String.valueOf(scenarioRule.id));
        return protocolState;
    }

    public TradeState buildTradeState(Document document, MockSettings settings) {
        TradeState tradeState = new TradeState();
        tradeState.sysSeqNo = safe(codecService.text(document, "SysSeqNo"));
        if (tradeState.sysSeqNo.isBlank()) {
            // 兜底流水号统一不超过 32 位：MOCK-SEQ- 前缀 9 位 + 23 位十六进制（9 + 23 = 32）。
            tradeState.sysSeqNo = "MOCK-SEQ-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 23);
        }
        tradeState.reqId = safe(codecService.text(document, "ReqId"));
        tradeState.serialNum = firstNonBlank(codecService.text(document, "SerialNum"),
                tradeState.reqId, tradeState.sysSeqNo);
        tradeState.tranCode = safe(codecService.text(document, "TranCode"), "201");
        tradeState.acctNo = firstNonBlank(codecService.text(document, "DbtrActId"),
                codecService.text(document, "AcctNo"));
        tradeState.acctName = safe(codecService.text(document, "DbtrActName"));
        tradeState.amount = resolveTradeAmount(document);
        tradeState.movementEligible = !firstNonBlank(codecService.text(document, "PayAmt"),
                codecService.text(document, "TxAmt")).isBlank()
                && !firstNonBlank(codecService.text(document, "SerialNum"),
                codecService.text(document, "ReqId"), codecService.text(document, "SysSeqNo")).isBlank();
        tradeState.bankId = safe(codecService.text(document, "DbtrBankId"));
        tradeState.creditorAcctNo = safe(codecService.text(document, "CdtrActId"));
        tradeState.creditorAcctName = safe(codecService.text(document, "CdtrActName"));
        tradeState.creditorBankId = safe(codecService.text(document, "CdtrBankId"));
        tradeState.billNo = firstNonBlank(codecService.text(document, "BllNb"),
                codecService.text(document, "BillNumber"));
        tradeState.btchNb = safe(codecService.text(document, "BtchNb"));
        tradeState.checkDate = safe(codecService.text(document, "CheckDate"), currentDate());
        tradeState.callbackEnabled = true;
        tradeState.callbackMesgType = "caps.205.001.01";
        tradeState.scenarioName = "";
        // 对手账号校验 + 随机失败：规则命中「无效」→失败；有效+一类卡→成功；有效+二类卡→日限额1万；
        // randomFail 开关开启时成功结果再按概率随机失败。
        MockStoreService.AccountRuleDecision decision = storeService.evaluateCounterparty(
                tradeState.creditorAcctNo, toBigDecimal(tradeState.amount), tradeState.checkDate,
                settings.randomFail, settings.randomFailRatio, new HashMap<>());
        if (decision.success()) {
            tradeState.status = "SUCC";
            tradeState.resFlag = "SUCC";
            tradeState.retCode = "000000";
            tradeState.retMsg = "交易成功";
        } else {
            tradeState.status = "FAIL";
            tradeState.resFlag = "FAIL";
            tradeState.retCode = decision.failRetCode();
            tradeState.retMsg = decision.failRetMsg();
            tradeState.scenarioName = decision.ruleName();
        }
        return tradeState;
    }

    private String normalizeFeeNoList(String feeNoList) {
        String value = safe(feeNoList).trim();
        if (value.isBlank()) {
            return "";
        }
        return value.replace('?', ',').replace(',', '|');
    }

    private String resolveProtocolProcessCode(ProtocolState protocolState, MockScenarioRule scenarioRule) {
        if ("DELE".equalsIgnoreCase(safe(protocolState.changeType))) {
            return "CS20";
        }
        if ("SD01".equalsIgnoreCase(safe(protocolState.sendType))) {
            return "CS00";
        }
        if (!safe(protocolState.origMsgId).isBlank()) {
            return "CS00";
        }
        return "CS00";
    }

    public BatchState buildBatchState(Document document, MockSettings settings) {
        BatchState batchState = new BatchState();
        batchState.corpAcctNo = safe(codecService.text(document, "CorpAcctNo"));
        batchState.requestFileName = safe(codecService.text(document, "FileName"));
        batchState.batchNo = safe(codecService.text(document, "BatchNo"));
        if (batchState.batchNo.isBlank()) {
            // BatchNo 由银行/挡板侧生成，busi 请求固定留空。兜底号必须全局唯一：
            // 秒级时间戳在同秒并发批量下会撞号（2026-09-21 嘉兴 26+ 组重复，且连带结果文件名重复引发 BATCH_FILE_PARSE 误判），
            // 故改用「毫秒时间戳 + ReqId 短哈希」，总长 11+17+4=32，卡 busi 建表 PLATFORM_BATCH_NO varchar2(32) 上限。
            batchState.batchNo = "MOCK-BATCH-" + LocalDateTime.now().format(TS_MS_FORMATTER)
                    + shortHash4(safe(codecService.text(document, "ReqId")));
        }
        batchState.reqId = safe(codecService.text(document, "ReqId"));
        batchState.tranCode = safe(codecService.text(document, "TranCode"), "101");
        batchState.resFlag = "SUCC";
        batchState.errorCode = "";
        batchState.errorMsg = "";
        batchState.totalCount = safe(codecService.text(document, "TotalCount"), "1");
        batchState.totalAmount = safe(codecService.text(document, "TotalAmt"), "100.00");
        applyBatchSummaryFromRequestFile(document, batchState);
        batchState.checkDate = safe(codecService.text(document, "CheckDate"), currentDate());
        batchState.fileName = batchState.batchNo + ".txt";
        // 按明细中的「对手账号」逐笔校验（无效→失败；有效+一类卡→成功；有效+二类卡→日限额1万）；
        // randomFail 开启时成功明细再按概率随机失败。runningUsed 用于同一批次内跨明细的日限额累计。
        Map<String, BigDecimal> runningUsed = new HashMap<>();
        batchState.fileData = buildBatchResultFileData(document, batchState, settings, runningUsed);
        BatchResultSummary summary = summarizeBatchResultFile(batchState.fileData);
        boolean detailSuccess = summary != null && "SUCC".equals(summary.status());
        batchState.status = detailSuccess ? "SUCC" : "FAIL";
        batchState.resFlag = detailSuccess ? "SUCC" : "FAIL";
        if (!detailSuccess && summary != null) {
            batchState.errorCode = summary.firstFailedCode();
            batchState.errorMsg = summary.firstFailedMsg();
        }
        batchState.movementEligible = detailSuccess;
        batchState.callbackEnabled = true;
        batchState.callbackMesgType = "caps.107.001.01";
        batchState.scenarioName = "";
        return batchState;
    }

    private void applyBatchSummaryFromRequestFile(Document document, BatchState batchState) {
        String requestFileData = decodeBase64(safe(codecService.text(document, "FileData")));
        if (requestFileData.isBlank()) {
            return;
        }
        String[] lines = requestFileData.split("\\r?\\n");
        if (lines.length == 0) {
            return;
        }
        String[] summaryFields = safe(lines[0]).split("\\|", -1);
        if ("40501".equals(batchState.tranCode) || "40502".equals(batchState.tranCode)) {
            batchState.centerBankId = field(summaryFields, 5, "").trim();
        }
        String requestTotalCount = field(summaryFields, 3, "");
        String requestTotalAmount = field(summaryFields, 4, "");
        if (!requestTotalCount.isBlank()) {
            batchState.totalCount = requestTotalCount;
        }
        if (!requestTotalAmount.isBlank()) {
            batchState.totalAmount = requestTotalAmount;
        }
    }

    private String buildBatchResultFileData(Document document, BatchState batchState, MockSettings settings,
                                            Map<String, BigDecimal> runningUsed) {
        String requestFileData = decodeBase64(safe(codecService.text(document, "FileData")));
        List<String[]> requestDetails = new ArrayList<>();
        if (!requestFileData.isBlank()) {
            String[] lines = requestFileData.split("\\r?\\n");
            for (int i = 1; i < lines.length; i++) {
                String line = safe(lines[i]);
                if (!line.isBlank()) {
                    requestDetails.add(line.split("\\|", -1));
                }
            }
        }
        if (requestDetails.isEmpty()) {
            batchState.movementEligible = false;
            requestDetails.add(new String[]{"1", "", "", "", "", safe(batchState.totalAmount, "0.00"), "", batchState.batchNo + "-D1"});
        }

        String hostSerialNum = resolveBatchHostSerialNum(batchState);
        int successCount = 0;
        int failCount = 0;
        List<String> resultLines = new ArrayList<>();
        for (int i = 0; i < requestDetails.size(); i++) {
            String[] fields = requestDetails.get(i);
            if (fields.length < 6 || safe(fields[5]).isBlank()) {
                batchState.movementEligible = false;
            }
            String detailSeq = field(fields, 0, String.valueOf(i + 1));
            String bankId = field(fields, 1, "");
            String acctNo = field(fields, 3, "");
            String acctName = field(fields, 4, "");
            String amount = field(fields, 5, "0.00");
            MockStoreService.AccountRuleDecision decision = storeService.evaluateCounterparty(
                    acctNo, toBigDecimal(amount), safe(batchState.checkDate, currentDate()),
                    settings.randomFail, settings.randomFailRatio, runningUsed);
            String retCode;
            String retMsg;
            if (decision.success()) {
                retCode = "00";
                retMsg = "交易成功";
                successCount++;
            } else {
                retCode = decision.failRetCode();
                retMsg = decision.failRetMsg();
                failCount++;
            }
            resultLines.add(String.join("|", detailSeq, bankId, acctNo, amount, acctName, retCode, retMsg, hostSerialNum));
        }

        String summary = String.join("|",
                safe(batchState.tranCode),
                safe(codecService.text(document, "CorpNo")),
                safe(codecService.text(document, "FeeNo")),
                safe(batchState.totalCount, String.valueOf(requestDetails.size())),
                safe(batchState.totalAmount, "0.00"),
                String.valueOf(successCount),
                String.valueOf(failCount),
                "0",
                safe(batchState.batchNo),
                safe(batchState.checkDate, currentDate()));

        StringBuilder fileBuilder = new StringBuilder(summary);
        for (String line : resultLines) {
            fileBuilder.append("\n").append(line);
        }
        return codecService.base64(fileBuilder.toString());
    }

    private String resolveBatchHostSerialNum(BatchState batchState) {
        String batchNo = batchState == null ? "" : safe(batchState.batchNo);
        if (!batchNo.isBlank()) {
            return batchNo;
        }
        return "MOCK-HOST-" + timestamp();
    }

    private String decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    private String field(String[] fields, int index, String defaultValue) {
        if (fields == null || index < 0 || index >= fields.length) {
            return safe(defaultValue);
        }
        return safe(fields[index], defaultValue);
    }

    public String buildCaps900(String corpNo, String resFlag, String procCode, String procMsg) {
        return buildCaps900(corpNo, resFlag, procCode, procMsg, procMsg);
    }

    public String buildCaps900(String corpNo, String resFlag, String procCode,
                               String procMsg, String remark) {
        String normalizedCode = "SUCC".equals(resFlag) ? "I000"
                : Caps900CodeCatalog.normalizeFailureCode(procCode);
        String normalizedMsg = Caps900CodeCatalog.description(normalizedCode);
        return codecService.buildXml("caps.900.001.01",
                "<CorpNo>" + codecService.escape(corpNo) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(resFlag) + "</ResFlag>"
                        + "<ProcCode>" + codecService.escape(normalizedCode) + "</ProcCode>"
                        + "<ProcMsg>" + codecService.escape(normalizedMsg) + "</ProcMsg>"
                        + "<Remark>" + codecService.escape(remark) + "</Remark>",
                null);
    }

    public String buildCaps302(ProtocolState protocolState) {
        return codecService.buildXml("caps.302.001.01",
                "<CorpNo>" + codecService.escape(protocolState.corpNo) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(protocolState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(safe(protocolState.errorCode)) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(safe(protocolState.errorMsg)) + "</ErrorMsg>",
                "<FeeNo>" + codecService.escape(safe(protocolState.feeNoList)) + "</FeeNo>"
                        + "<OperType>ADDD</OperType>"
                        + "<BatchNo>" + codecService.escape(protocolState.protocolNo) + "</BatchNo>"
                        + "<Remark>" + codecService.escape(safe(protocolState.remark, "mock upload accepted")) + "</Remark>");
    }

    public String buildCaps304(ProtocolState protocolState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
        MockSettings settings = storeService.getSettings();
        if (protocolState == null) {
            return codecService.buildXml("caps.304.001.01",
                    "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                            + "<ResFlag>" + codecService.escape(resolveResFlag(scenarioRule, "FAIL")) + "</ResFlag>"
                            + "<ErrorCode>" + codecService.escape(resolveErrorCode(scenarioRule, settings.protocolNotFoundCode)) + "</ErrorCode>"
                            + "<ErrorMsg>" + codecService.escape(resolveErrorMsg(scenarioRule, settings.protocolNotFoundMsg)) + "</ErrorMsg>",
                    "<FileData></FileData>");
        }
        String fileDataXml = "<Protocol><DbtrProtocol>" + codecService.escape(protocolState.protocolNo)
                + "</DbtrProtocol><AcctNo>" + codecService.escape(protocolState.acctNo)
                + "</AcctNo><DbtrActId>" + codecService.escape(protocolState.acctNo)
                + "</DbtrActId><DbtrActName>" + codecService.escape(safe(protocolState.acctName, protocolState.customerName))
                + "</DbtrActName><DbtrBankId>" + codecService.escape(safe(protocolState.bankId))
                + "</DbtrBankId><FeeNoList>" + codecService.escape(safe(protocolState.feeNoList))
                + "</FeeNoList><ProcessCode>" + codecService.escape(safe(protocolState.protocolProcessCode, "CS00"))
                + "</ProcessCode><ProtocolProcessCode>" + codecService.escape(safe(protocolState.protocolProcessCode, "CS00"))
                + "</ProtocolProcessCode><Status>" + codecService.escape(protocolState.status)
                + "</Status><Scenario>" + codecService.escape(safe(protocolState.scenarioName)) + "</Scenario></Protocol>";
        return codecService.buildXml("caps.304.001.01",
                "<CorpNo>" + codecService.escape(protocolState.corpNo) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(protocolState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(safe(protocolState.errorCode)) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(safe(protocolState.errorMsg)) + "</ErrorMsg>",
                "<FileData>" + codecService.escape(codecService.base64(fileDataXml)) + "</FileData>");
    }

    public String buildCaps102(BatchState batchState, CapsHeader requestHeader) {
        return codecService.buildXml("caps.102.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(batchState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(safe(batchState.errorCode)) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(safe(batchState.errorMsg)) + "</ErrorMsg>",
                "<BatchNo>" + codecService.escape(batchState.batchNo) + "</BatchNo><Remark>"
                        + codecService.escape(firstNonBlank(batchState.errorMsg, "accepted")) + "</Remark>");
    }

    public String buildCaps104(BatchState batchState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
        if (batchState == null) {
            return buildCaps900(successCorp(requestHeader), resolveResFlag(scenarioRule, "FAIL"),
                    resolveCode(scenarioRule, "BATCH404"), resolveMsg(scenarioRule, "未找到批次"));
        }
        return codecService.buildXml("caps.104.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(batchState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(safe(batchState.errorCode)) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(safe(batchState.errorMsg)) + "</ErrorMsg>",
                "<BatchNo>" + codecService.escape(batchState.batchNo) + "</BatchNo>"
                        + "<BatchStatus>" + codecService.escape(batchState.status) + "</BatchStatus>"
                        + "<FileName>" + codecService.escape(safe(batchState.fileName, batchState.batchNo + ".txt")) + "</FileName><Error>"
                        + codecService.escape(safe(batchState.errorMsg)) + "</Error>");
    }

    public String buildCaps106(BatchState batchState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
        if (batchState == null) {
            return buildCaps900(successCorp(requestHeader), resolveResFlag(scenarioRule, "FAIL"),
                    resolveCode(scenarioRule, "BATCH404"), resolveMsg(scenarioRule, "未找到批次"));
        }
        // 105 是企业查询批次结果的请求：106 只如实回显批次状态，不修改状态、不写库。
        return codecService.buildXml("caps.106.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(batchState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(safe(batchState.errorCode)) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(safe(batchState.errorMsg)) + "</ErrorMsg>",
                "<BatchNo>" + codecService.escape(batchState.batchNo) + "</BatchNo>"
                        + "<BatchStatus>" + codecService.escape(batchState.status) + "</BatchStatus>"
                        + "<CheckDate>" + codecService.escape(safe(batchState.checkDate, currentDate())) + "</CheckDate>"
                        + "<FileData>" + codecService.escape(batchState.fileData) + "</FileData>");
    }

    public String buildCaps202(TradeState tradeState, CapsHeader requestHeader) {
        return codecService.buildXml("caps.202.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(tradeState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                "<ReturnTime>" + timestamp() + "</ReturnTime>"
                        + "<SysSeqNo>" + codecService.escape(tradeState.sysSeqNo) + "</SysSeqNo>"
                        + "<SerialNum>" + codecService.escape(safe(tradeState.serialNum, tradeState.reqId)) + "</SerialNum>"
                        + "<RetCode>" + codecService.escape(safe(tradeState.retCode, "000000")) + "</RetCode><RetMsg>"
                        + codecService.escape(safe(tradeState.retMsg, "accepted")) + "</RetMsg>"
                        + "<Remark>" + codecService.escape(safe(tradeState.scenarioName)) + "</Remark>"
                        + "<Use>MOCK</Use>"
                        + "<BtchNb>" + codecService.escape(safe(tradeState.btchNb)) + "</BtchNb>");
    }

    public String buildCaps204(TradeState tradeState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
        if (tradeState == null) {
            return buildCaps900(successCorp(requestHeader), resolveResFlag(scenarioRule, "FAIL"),
                    resolveCode(scenarioRule, "TRADE404"), resolveMsg(scenarioRule, "trade not found"));
        }
        return codecService.buildXml("caps.204.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(tradeState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                "<ReturnTime>" + timestamp() + "</ReturnTime>"
                        + "<SysSeqNo>" + codecService.escape(tradeState.sysSeqNo) + "</SysSeqNo>"
                        + "<SerialNum>" + codecService.escape(safe(tradeState.serialNum, tradeState.reqId)) + "</SerialNum>"
                        + "<RetCode>" + codecService.escape(tradeState.retCode) + "</RetCode>"
                        + "<RetMsgId>" + codecService.escape(tradeState.retCode) + "</RetMsgId>"
                        + "<CheckDate>" + codecService.escape(safe(tradeState.checkDate, currentDate())) + "</CheckDate>"
                        + "<RetMsg>" + codecService.escape(tradeState.retMsg) + "</RetMsg>"
                        + "<BizStatus>" + codecService.escape(tradeState.status) + "</BizStatus>"
                        + "<Remark>" + codecService.escape(safe(tradeState.scenarioName)) + "</Remark>"
                        + "<Use>MOCK</Use>"
                        + "<BtchNb>" + codecService.escape(safe(tradeState.btchNb)) + "</BtchNb>");
    }

    public String buildCaps602(CapsHeader requestHeader, String checkDate, String tranCode, MockScenarioRule scenarioRule) {
        String resolvedDate = safe(checkDate, currentDate());
        String resolvedTranCode = safe(tranCode, "00000");
        String reconXml = "<ReconDetail><CheckDate>" + codecService.escape(resolvedDate)
                + "</CheckDate><TranCode>" + codecService.escape(resolvedTranCode)
                + "</TranCode><Count>1</Count><Amount>100.00</Amount><Result>"
                + codecService.escape(resolveMsg(scenarioRule, "对账文件已生成")) + "</Result></ReconDetail>";
        return codecService.buildXml("caps.602.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<CheckDate>" + codecService.escape(resolvedDate) + "</CheckDate>"
                        + "<TranCode>" + codecService.escape(resolvedTranCode) + "</TranCode>"
                        + "<ResFlag>" + codecService.escape(resolveResFlag(scenarioRule, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(resolveErrorCode(scenarioRule, "")) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(resolveErrorMsg(scenarioRule, "")) + "</ErrorMsg>",
                "<FileData>" + codecService.escape(codecService.base64(reconXml)) + "</FileData>");
    }

    public String resolveResFlag(MockScenarioRule scenarioRule, String defaultValue) {
        return defaultValue;
    }

    /**
     * 批次结果文件推导结果：全部明细结果码为 00 → SUCC；存在非 00 → FAIL 并带首个失败明细的结果码与描述。
     * 传入为空、非 Base64 或没有明细行时返回 null，由调用方决定兜底策略（不猜测、不伪造终态）。
     */
    public static BatchResultSummary summarizeBatchResultFile(String fileData) {
        if (fileData == null || fileData.isBlank()) {
            return null;
        }
        String text;
        try {
            text = new String(Base64.getDecoder().decode(fileData.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String[] lines = text.split("\\r?\\n");
        int detailCount = 0;
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            String[] fields = lines[index].split("\\|", -1);
            if (fields.length < 6) {
                continue;
            }
            detailCount++;
            if (!"00".equals(fields[5].trim())) {
                return new BatchResultSummary("FAIL", fields[5].trim(),
                        fields.length > 6 ? fields[6].trim() : "", detailCount);
            }
        }
        return detailCount == 0 ? null : new BatchResultSummary("SUCC", "", "", detailCount);
    }

    public record BatchResultSummary(String status, String firstFailedCode, String firstFailedMsg, int detailCount) { }

    public String resolveStatus(MockScenarioRule scenarioRule, String defaultValue) {
        return defaultValue;
    }

    public String resolveCode(MockScenarioRule scenarioRule, String defaultValue) {
        return defaultValue;
    }

    public String resolveMsg(MockScenarioRule scenarioRule, String defaultValue) {
        return defaultValue;
    }

    public String resolveErrorCode(MockScenarioRule scenarioRule, String defaultValue) {
        return defaultValue;
    }

    public String resolveErrorMsg(MockScenarioRule scenarioRule, String defaultValue) {
        return defaultValue;
    }

    public boolean isAutoCallbackDisabled(MockScenarioRule scenarioRule) {
        return false;
    }

    public String resolveCallbackType(MockScenarioRule scenarioRule) {
        return "";
    }

    public String text(Document document, String tagName) {
        return codecService.text(document, tagName);
    }

    public String successCorp(CapsHeader requestHeader) {
        return safe(requestHeader.origSender, "33503C5801");
    }

    public String currentDate() {
        return LocalDateTime.now().format(DATE_FORMATTER);
    }

    public String timestamp() {
        return LocalDateTime.now().format(TS_FORMATTER);
    }

    /**
     * 取输入的 4 位十六进制短哈希（MD5 前 2 字节）。同输入同输出（幂等重发同号）；
     * 输入为空时用随机 UUID，保证兜底号仍唯一。
     */
    public String shortHash4(String input) {
        String seed = input == null || input.isBlank() ? UUID.randomUUID().toString() : input;
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(seed.getBytes(StandardCharsets.UTF_8));
            return String.format("%02x%02x", digest[0], digest[1]);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().substring(0, 4);
        }
    }

    public String resolveTradeAmount(Document document) {
        String amount = firstNonBlank(codecService.text(document, "PayAmt"), codecService.text(document, "TxAmt"));
        amount = safe(amount).trim();
        if (amount.length() > 3 && Character.isLetter(amount.charAt(0))
                && Character.isLetter(amount.charAt(1))
                && Character.isLetter(amount.charAt(2))) {
            amount = amount.substring(3);
        }
        return amount.isBlank() ? "100.00" : amount;
    }

    private BigDecimal toBigDecimal(String value) {
        try {
            return new BigDecimal(safe(value).trim().replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    public String safe(String value) {
        return value == null ? "" : value;
    }

    public String safe(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
