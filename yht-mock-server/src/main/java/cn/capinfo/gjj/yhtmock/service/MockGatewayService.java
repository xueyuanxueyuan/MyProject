package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockScenarioContext;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MockGatewayService {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CapsCodecService codecService;
    private final MockStoreService storeService;
    private final MockCallbackService callbackService;

    public MockGatewayService(CapsCodecService codecService,
                              MockStoreService storeService,
                              MockCallbackService callbackService) {
        this.codecService = codecService;
        this.storeService = storeService;
        this.callbackService = callbackService;
    }

    public String dispatch(String rawMessage) {
        CapsCodecService.ParsedFrame frame = codecService.parseFrame(rawMessage);
        CapsHeader requestHeader = frame.header();
        String requestMesgType = safe(requestHeader.mesgType);
        String responseMesgType;
        String responseXml;
        String reqId = "";
        String protocolNo = "";
        String batchNo = "";
        String sysSeqNo = "";
        String acctNo = "";
        String status = "SUCC";
        MockScenarioRule scenarioRule = null;
        try {
            Document document = codecService.parseXml(frame.xmlBody());
            if (document == null) {
                throw new IllegalArgumentException("报文XML解析失败");
            }
            reqId = codecService.text(document, "ReqId");
            protocolNo = firstNonBlank(codecService.text(document, "DbtrProtocol"), codecService.text(document, "OrgnlId"));
            batchNo = codecService.text(document, "BatchNo");
            sysSeqNo = codecService.text(document, "SysSeqNo");
            acctNo = codecService.text(document, "DbtrActId");
            scenarioRule = storeService.matchScenario(buildScenarioContext(
                    requestMesgType, acctNo, protocolNo, reqId, batchNo, sysSeqNo));
            switch (requestMesgType) {
                case "caps.999.001.01" -> {
                    responseMesgType = "caps.900.001.01";
                    responseXml = buildCaps900(successCorp(requestHeader),
                            resolveResFlag(scenarioRule, "SUCC"),
                            resolveCode(scenarioRule, "00000000"),
                            resolveMsg(scenarioRule, "探测成功"));
                    status = resolveStatus(scenarioRule, "SUCC");
                }
                case "caps.301.001.01" -> {
                    ProtocolState protocolState = buildProtocolState(document, requestHeader, scenarioRule);
                    storeService.saveProtocol(protocolState);
                    responseMesgType = "caps.302.001.01";
                    responseXml = buildCaps302(protocolState);
                    protocolNo = protocolState.protocolNo;
                    status = safe(protocolState.status, "SUCC");
                }
                case "caps.303.001.01" -> {
                    ProtocolState protocolState = storeService.findProtocol(codecService.text(document, "DbtrProtocol"), acctNo);
                    responseMesgType = "caps.304.001.01";
                    responseXml = buildCaps304(protocolState, requestHeader, scenarioRule);
                    protocolNo = protocolState == null ? protocolNo : protocolState.protocolNo;
                    status = protocolState == null ? resolveStatus(scenarioRule, "FAIL") : safe(protocolState.status, "SUCC");
                }
                case "caps.305.001.01" -> {
                    ProtocolState protocolState = buildProtocolState(document, requestHeader, scenarioRule);
                    protocolState.signReqId = safe(reqId);
                    protocolState.status = resolveStatus(scenarioRule, "ACCEPTED");
                    protocolState.callbackMesgType = defaultString(resolveCallbackType(scenarioRule), "caps.306.001.01");
                    protocolState.callbackEnabled = !isAutoCallbackDisabled(scenarioRule);
                    storeService.saveProtocol(protocolState);
                    responseMesgType = "caps.900.001.01";
                    responseXml = buildCaps900(successCorp(requestHeader),
                            safe(protocolState.resFlag, "SUCC"),
                            resolveCode(scenarioRule, "00000000"),
                            resolveMsg(scenarioRule, "受理成功"));
                    protocolNo = protocolState.protocolNo;
                    status = safe(protocolState.status, "SUCC");
                    callbackService.scheduleCaps306(requestHeader, protocolState);
                }
                case "caps.307.001.01" -> {
                    responseMesgType = "caps.900.001.01";
                    responseXml = buildCaps900(successCorp(requestHeader),
                            resolveResFlag(scenarioRule, "SUCC"),
                            resolveCode(scenarioRule, "00000000"),
                            resolveMsg(scenarioRule, "撤销受理成功"));
                    status = resolveStatus(scenarioRule, "SUCC");
                    if (!isAutoCallbackDisabled(scenarioRule)) {
                        callbackService.scheduleCaps308(requestHeader,
                                codecService.text(document, "OrgnlId"),
                                "CANCEL-" + timestamp());
                    }
                }
                case "caps.101.001.01" -> {
                    BatchState batchState = buildBatchState(document, scenarioRule);
                    storeService.saveBatch(batchState);
                    responseMesgType = "caps.102.001.01";
                    responseXml = buildCaps102(batchState, requestHeader);
                    batchNo = batchState.batchNo;
                    status = safe(batchState.status, "SUCC");
                    callbackService.scheduleCaps107(requestHeader, batchState);
                }
                case "caps.103.001.01" -> {
                    BatchState batchState = storeService.findBatch(codecService.text(document, "BatchNo"));
                    responseMesgType = "caps.104.001.01";
                    responseXml = buildCaps104(batchState, requestHeader, scenarioRule);
                    status = batchState == null ? resolveStatus(scenarioRule, "FAIL") : safe(batchState.status, "SUCC");
                }
                case "caps.105.001.01" -> {
                    BatchState batchState = storeService.findBatch(codecService.text(document, "BatchNo"));
                    responseMesgType = "caps.106.001.01";
                    responseXml = buildCaps106(batchState, requestHeader, scenarioRule);
                    status = batchState == null ? resolveStatus(scenarioRule, "FAIL") : safe(batchState.status, "SUCC");
                }
                case "caps.201.001.01" -> {
                    TradeState tradeState = buildTradeState(document, scenarioRule);
                    storeService.saveTrade(tradeState);
                    responseMesgType = "caps.202.001.01";
                    responseXml = buildCaps202(tradeState, requestHeader);
                    sysSeqNo = tradeState.sysSeqNo;
                    status = safe(tradeState.status, "SUCC");
                    callbackService.scheduleCaps205(requestHeader, tradeState);
                }
                case "caps.203.001.01" -> {
                    TradeState tradeState = storeService.findTrade(codecService.text(document, "SysSeqNo"), reqId);
                    responseMesgType = "caps.204.001.01";
                    responseXml = buildCaps204(tradeState, requestHeader, scenarioRule);
                    status = tradeState == null ? resolveStatus(scenarioRule, "FAIL") : safe(tradeState.status, "SUCC");
                }
                case "caps.601.001.01" -> {
                    responseMesgType = "caps.602.001.01";
                    responseXml = buildCaps602(requestHeader, codecService.text(document, "CheckDate"),
                            codecService.text(document, "TranCode"), scenarioRule);
                    status = resolveStatus(scenarioRule, "SUCC");
                }
                default -> {
                    responseMesgType = "caps.900.001.01";
                    responseXml = buildCaps900(successCorp(requestHeader), "FAIL", "UNSPRT", "暂不支持该报文");
                    status = "FAIL";
                }
            }
        } catch (Exception e) {
            responseMesgType = "caps.900.001.01";
            responseXml = buildCaps900(successCorp(requestHeader), "FAIL", "EXCEPT", e.getMessage());
            status = "FAIL";
        }
        String responseMessage = codecService.buildMessage(responseMesgType, "D",
                requestHeader.mesgId, safe(requestHeader.userName, "CAPS"), safe(requestHeader.password, "CAPS"),
                safe(requestHeader.origReceiver, "904290099992"), safe(requestHeader.origSender, "33503C5801"),
                responseXml);
        MockRecord record = new MockRecord();
        record.recordType = "GATEWAY";
        record.source = safe(requestHeader.origSender, "client");
        record.target = safe(requestHeader.origReceiver, "caps");
        record.mesgType = requestMesgType;
        record.mesgId = requestHeader.mesgId;
        record.reqId = reqId;
        record.protocolNo = protocolNo;
        record.batchNo = batchNo;
        record.sysSeqNo = sysSeqNo;
        record.status = status;
        record.requestBody = rawMessage;
        record.responseBody = responseMessage;
        record.remark = scenarioRule == null ? "gateway dispatch" : "gateway dispatch by scenario: " + safe(scenarioRule.name, String.valueOf(scenarioRule.id));
        storeService.addRecord(record);
        return responseMessage;
    }

    private ProtocolState buildProtocolState(Document document, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
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

    private TradeState buildTradeState(Document document, MockScenarioRule scenarioRule) {
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

    private BatchState buildBatchState(Document document, MockScenarioRule scenarioRule) {
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

    private String buildCaps900(String corpNo, String resFlag, String procCode, String procMsg) {
        return codecService.buildXml("caps.900.001.01",
                "<CorpNo>" + codecService.escape(corpNo) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(resFlag) + "</ResFlag>"
                        + "<ProcCode>" + codecService.escape(procCode) + "</ProcCode>"
                        + "<ProcMsg>" + codecService.escape(procMsg) + "</ProcMsg>",
                null);
    }

    private String buildCaps302(ProtocolState protocolState) {
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

    private String buildCaps304(ProtocolState protocolState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
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

    private String buildCaps102(BatchState batchState, CapsHeader requestHeader) {
        return codecService.buildXml("caps.102.001.01",
                "<CorpNo>" + codecService.escape(successCorp(requestHeader)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(safe(batchState.resFlag, "SUCC")) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(safe(batchState.errorCode)) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(safe(batchState.errorMsg)) + "</ErrorMsg>",
                "<BatchNo>" + codecService.escape(batchState.batchNo) + "</BatchNo><Remark>"
                        + codecService.escape(firstNonBlank(batchState.errorMsg, "accepted")) + "</Remark>");
    }

    private String buildCaps104(BatchState batchState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
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

    private String buildCaps106(BatchState batchState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
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

    private String buildCaps202(TradeState tradeState, CapsHeader requestHeader) {
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

    private String buildCaps204(TradeState tradeState, CapsHeader requestHeader, MockScenarioRule scenarioRule) {
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

    private String buildCaps602(CapsHeader requestHeader, String checkDate, String tranCode, MockScenarioRule scenarioRule) {
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

    private MockScenarioContext buildScenarioContext(String requestMesgType, String acctNo,
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

    private String resolveResFlag(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceResFlag, defaultValue);
    }

    private String resolveStatus(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceStatus, defaultValue);
    }

    private String resolveCode(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceRetCode, defaultValue);
    }

    private String resolveMsg(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceRetMsg, defaultValue);
    }

    private String resolveErrorCode(MockScenarioRule scenarioRule, String defaultValue) {
        if (scenarioRule == null) {
            return defaultValue;
        }
        if ("FAIL".equalsIgnoreCase(scenarioRule.forceResFlag)) {
            return safe(scenarioRule.forceRetCode, defaultValue);
        }
        return safe(scenarioRule.forceRetCode, defaultValue);
    }

    private String resolveErrorMsg(MockScenarioRule scenarioRule, String defaultValue) {
        return scenarioRule == null ? defaultValue : safe(scenarioRule.forceRetMsg, defaultValue);
    }

    private boolean isAutoCallbackDisabled(MockScenarioRule scenarioRule) {
        return scenarioRule != null && scenarioRule.disableAutoCallback;
    }

    private String resolveCallbackType(MockScenarioRule scenarioRule) {
        return scenarioRule == null ? "" : safe(scenarioRule.callbackMesgType);
    }

    private String successCorp(CapsHeader requestHeader) {
        return safe(requestHeader.origSender, "33503C5801");
    }

    private String currentDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private String timestamp() {
        return LocalDateTime.now().format(TS_FORMATTER);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String firstNonBlank(String first, String second) {
        return (first != null && !first.isBlank()) ? first : safe(second);
    }
}
