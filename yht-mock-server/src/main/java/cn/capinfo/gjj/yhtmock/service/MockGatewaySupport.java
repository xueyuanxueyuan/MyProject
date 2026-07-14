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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        protocolState.protocolNo = safe(codecService.text(document, "DbtrProtocol"));
        if (protocolState.protocolNo.isBlank() || "0".equals(protocolState.protocolNo)) {
            protocolState.protocolNo = "MOCK-PROT-" + timestamp();
        }
        protocolState.acctNo = safe(codecService.text(document, "DbtrActId"));
        protocolState.corpNo = successCorp(requestHeader);
        protocolState.customerId = safe(codecService.text(document, "CstmrId"));
        protocolState.customerName = safe(codecService.text(document, "CstmrNm"));
        protocolState.feeNoList = safe(codecService.text(document, "FeeNoList"));
        protocolState.bankId = safe(codecService.text(document, "DbtrBankId"));
        protocolState.authCode = safe(codecService.text(document, "AuthCd"));
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
        tradeState.tranCode = safe(codecService.text(document, "TranCode"), "201");
        tradeState.acctNo = safe(codecService.text(document, "DbtrActId"));
        tradeState.amount = safe(codecService.text(document, "TxAmt"), "100.00");
        tradeState.bankId = safe(codecService.text(document, "DbtrBankId"));
        tradeState.status = resolveStatus(scenarioRule, "SUCC");
        tradeState.resFlag = resolveResFlag(scenarioRule, "SUCC");
        tradeState.retCode = resolveCode(scenarioRule, "0000");
        tradeState.retMsg = resolveMsg(scenarioRule, "交易成功");
        tradeState.callbackEnabled = !isAutoCallbackDisabled(scenarioRule);
        tradeState.callbackMesgType = defaultString(resolveCallbackType(scenarioRule), "caps.205.001.01");
        tradeState.scenarioName = scenarioRule == null ? "" : safe(scenarioRule.name, String.valueOf(scenarioRule.id));
        return tradeState;
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
        batchState.checkDate = safe(codecService.text(document, "CheckDate"), currentDate());
        batchState.fileName = batchState.batchNo + ".txt";
        batchState.fileData = codecService.base64("<BatchResult><BatchNo>" + batchState.batchNo + "</BatchNo><Status>"
                + safe(batchState.status, "SUCC") + "</Status><RetCode>" + resolveCode(scenarioRule, "0000")
                + "</RetCode></BatchResult>");
        batchState.callbackEnabled = !isAutoCallbackDisabled(scenarioRule);
        batchState.callbackMesgType = defaultString(resolveCallbackType(scenarioRule), "caps.107.001.01");
        batchState.scenarioName = scenarioRule == null ? "" : safe(scenarioRule.name, String.valueOf(scenarioRule.id));
        return batchState;
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
                + "</AcctNo><ProcessCode>CS00</ProcessCode><Status>" + codecService.escape(protocolState.status)
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
                        + "<SerialNum>" + codecService.escape(tradeState.reqId) + "</SerialNum>"
                        + "<RetCode>" + codecService.escape(safe(tradeState.retCode, "0000")) + "</RetCode><RetMsg>"
                        + codecService.escape(safe(tradeState.retMsg, "受理成功")) + "</RetMsg>");
    }

    public String buildCaps204(TradeState tradeState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
        if (tradeState == null) {
            return buildCaps900(successCorp(requestHeader), resolveResFlag(scenarioRule, "FAIL"),
                    resolveCode(scenarioRule, "TRADE404"), resolveMsg(scenarioRule, "未找到交易"));
        }
        return codecService.buildXml("caps.204.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(tradeState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                "<ReturnTime>" + timestamp() + "</ReturnTime>"
                        + "<SysSeqNo>" + codecService.escape(tradeState.sysSeqNo) + "</SysSeqNo>"
                        + "<RetCode>" + codecService.escape(tradeState.retCode) + "</RetCode>"
                        + "<RetMsg>" + codecService.escape(tradeState.retMsg) + "</RetMsg>"
                        + "<BizStatus>" + codecService.escape(tradeState.status) + "</BizStatus>");
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
                        + "<ResFlag>" + codecService.escape(resolveResFlag(scenarioRule, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(resolveErrorCode(scenarioRule, "")) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(resolveErrorMsg(scenarioRule, "")) + "</ErrorMsg>",
                "<CheckDate>" + codecService.escape(resolvedDate) + "</CheckDate>"
                        + "<TranCode>" + codecService.escape(resolvedTranCode) + "</TranCode>"
                        + "<FileData>" + codecService.escape(codecService.base64(reconXml)) + "</FileData>");
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

    public String successCorp(CapsHeader requestHeader) {
        return safe(requestHeader.origSender, "33503C5801");
    }

    public String currentDate() {
        return LocalDateTime.now().format(DATE_FORMATTER);
    }

    public String timestamp() {
        return LocalDateTime.now().format(TS_FORMATTER);
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

    public String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : safe(second);
    }
}
