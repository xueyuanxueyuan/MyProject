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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class MockGatewaySupport {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
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

    public TradeState buildTradeState(Document document, MockScenarioRule scenarioRule) {
        TradeState tradeState = new TradeState();
        tradeState.sysSeqNo = safe(codecService.text(document, "SysSeqNo"));
        if (tradeState.sysSeqNo.isBlank()) {
            tradeState.sysSeqNo = "MOCK-SEQ-" + timestamp();
        }
        tradeState.reqId = safe(codecService.text(document, "ReqId"));
        tradeState.serialNum = firstNonBlank(codecService.text(document, "SerialNum"),
                tradeState.reqId, tradeState.sysSeqNo);
        tradeState.tranCode = safe(codecService.text(document, "TranCode"), "201");
        tradeState.acctNo = firstNonBlank(codecService.text(document, "DbtrActId"),
                codecService.text(document, "AcctNo"));
        tradeState.acctName = safe(codecService.text(document, "DbtrActName"));
        tradeState.amount = resolveTradeAmount(document);
        tradeState.bankId = safe(codecService.text(document, "DbtrBankId"));
        tradeState.creditorAcctNo = safe(codecService.text(document, "CdtrActId"));
        tradeState.creditorAcctName = safe(codecService.text(document, "CdtrActName"));
        tradeState.creditorBankId = safe(codecService.text(document, "CdtrBankId"));
        tradeState.billNo = firstNonBlank(codecService.text(document, "BllNb"),
                codecService.text(document, "BillNumber"));
        tradeState.btchNb = safe(codecService.text(document, "BtchNb"));
        tradeState.checkDate = safe(codecService.text(document, "CheckDate"), currentDate());
        tradeState.status = resolveStatus(scenarioRule, "SUCC");
        tradeState.resFlag = resolveResFlag(scenarioRule, "SUCC");
        tradeState.retCode = resolveCode(scenarioRule, "000000");
        tradeState.retMsg = resolveMsg(scenarioRule, "trade success");
        tradeState.callbackEnabled = !isAutoCallbackDisabled(scenarioRule);
        tradeState.callbackMesgType = defaultString(resolveCallbackType(scenarioRule), "caps.205.001.01");
        tradeState.scenarioName = scenarioRule == null ? "" : safe(scenarioRule.name, String.valueOf(scenarioRule.id));
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
        String forcedCode = scenarioRule == null ? "" : safe(scenarioRule.forceRetCode);
        if (forcedCode.startsWith("CS")) {
            return forcedCode;
        }
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

    public BatchState buildBatchState(Document document, MockScenarioRule scenarioRule) {
        BatchState batchState = new BatchState();
        batchState.batchNo = safe(codecService.text(document, "BatchNo"));
        if (batchState.batchNo.isBlank()) {
            batchState.batchNo = "MOCK-BATCH-" + timestamp();
        }
        batchState.reqId = safe(codecService.text(document, "ReqId"));
        batchState.tranCode = safe(codecService.text(document, "TranCode"), "101");
        batchState.status = resolveStatus(scenarioRule, "PROC");
        batchState.resFlag = resolveResFlag(scenarioRule, "SUCC");
        batchState.errorCode = resolveErrorCode(scenarioRule, "");
        batchState.errorMsg = resolveMsg(scenarioRule, "");
        batchState.totalCount = safe(codecService.text(document, "TotalCount"), "1");
        batchState.totalAmount = safe(codecService.text(document, "TotalAmt"), "100.00");
        applyBatchSummaryFromRequestFile(document, batchState);
        batchState.checkDate = safe(codecService.text(document, "CheckDate"), currentDate());
        batchState.fileName = batchState.batchNo + ".txt";
        batchState.fileData = buildBatchResultFileData(document, batchState, scenarioRule);
        batchState.callbackEnabled = !isAutoCallbackDisabled(scenarioRule);
        batchState.callbackMesgType = defaultString(resolveCallbackType(scenarioRule), "caps.107.001.01");
        batchState.scenarioName = scenarioRule == null ? "" : safe(scenarioRule.name, String.valueOf(scenarioRule.id));
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
        String requestTotalCount = field(summaryFields, 3, "");
        String requestTotalAmount = field(summaryFields, 4, "");
        if (!requestTotalCount.isBlank()) {
            batchState.totalCount = requestTotalCount;
        }
        if (!requestTotalAmount.isBlank()) {
            batchState.totalAmount = requestTotalAmount;
        }
    }

    private String buildBatchResultFileData(Document document, BatchState batchState, MockScenarioRule scenarioRule) {
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
            requestDetails.add(new String[]{"1", "", "", "", "", safe(batchState.totalAmount, "0.00"), "", batchState.batchNo + "-D1"});
        }

        String retCode = resolveCode(scenarioRule, "00");
        String retMsg = resolveMsg(scenarioRule, "交易成功");
        boolean success = "00".equals(retCode);
        String successCount = success ? String.valueOf(requestDetails.size()) : "0";
        String failCount = success ? "0" : String.valueOf(requestDetails.size());
        String summary = String.join("|",
                safe(batchState.tranCode),
                safe(codecService.text(document, "CorpNo")),
                safe(codecService.text(document, "FeeNo")),
                safe(batchState.totalCount, String.valueOf(requestDetails.size())),
                safe(batchState.totalAmount, "0.00"),
                successCount,
                failCount,
                "0",
                safe(batchState.batchNo),
                safe(batchState.checkDate, currentDate()));

        StringBuilder fileBuilder = new StringBuilder(summary);
        String hostSerialNum = resolveBatchHostSerialNum(batchState);
        for (int i = 0; i < requestDetails.size(); i++) {
            String[] fields = requestDetails.get(i);
            String detailSeq = field(fields, 0, String.valueOf(i + 1));
            String bankId = field(fields, 1, "");
            String acctNo = field(fields, 3, "");
            String acctName = field(fields, 4, "");
            String amount = field(fields, 5, "0.00");
            fileBuilder.append("\n")
                    .append(String.join("|", detailSeq, bankId, acctNo, amount, acctName, retCode, retMsg, hostSerialNum));
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
        return codecService.buildXml("caps.900.001.01",
                "<CorpNo>" + codecService.escape(corpNo) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(resFlag) + "</ResFlag>"
                        + "<ProcCode>" + codecService.escape(procCode) + "</ProcCode>"
                        + "<ProcMsg>" + codecService.escape(procMsg) + "</ProcMsg>",
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
        if ("PROC".equalsIgnoreCase(batchState.status)) {
            batchState.status = "SUCC";
        }
        storeService.saveBatch(batchState);
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
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceResFlag, defaultValue);
    }

    public String resolveStatus(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceStatus, defaultValue);
    }

    public String resolveCode(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceRetCode, defaultValue);
    }

    public String resolveMsg(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceRetMsg, defaultValue);
    }

    public String resolveErrorCode(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceRetCode, defaultValue);
    }

    public String resolveErrorMsg(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceRetMsg, defaultValue);
    }

    public boolean isAutoCallbackDisabled(MockScenarioRule scenarioRule) {
        return scenarioRule != null && scenarioRule.disableAutoCallback;
    }

    public String resolveCallbackType(MockScenarioRule scenarioRule) {
        return scenarioRule == null ? "" : safe(scenarioRule.callbackMesgType);
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