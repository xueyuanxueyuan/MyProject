package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

@Service
public class MockCallbackService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockCallbackService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final CapsCodecService codecService;
    private final MockStoreService storeService;
    private final MovementNotificationService movementService;

    public MockCallbackService(CapsCodecService codecService, MockStoreService storeService) {
        this(codecService, storeService, new MovementNotificationService(storeService));
    }

    @Autowired
    public MockCallbackService(CapsCodecService codecService, MockStoreService storeService,
                               MovementNotificationService movementService) {
        this.codecService = codecService;
        this.storeService = storeService;
        this.movementService = movementService;
    }

    public void scheduleCaps306(CapsHeader requestHeader, ProtocolState protocolState) {
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps306 || protocolState == null || !protocolState.callbackEnabled) {
            log.info("跳过回调推送 caps.306：autoPushEnabled={} pushCaps306={} 协议={} callbackEnabled={}",
                    settings.autoPushEnabled, settings.pushCaps306,
                    protocolState == null ? "null" : defaultString(protocolState.protocolNo, "-"),
                    protocolState != null && protocolState.callbackEnabled);
            return;
        }
        String callbackMesgType = defaultString(protocolState.callbackMesgType, "caps.306.001.01");
        String processCode = defaultString(protocolState.protocolProcessCode, "CS00");
        String body = codecService.buildXml(callbackMesgType,
                "<CorpNo>" + codecService.escape(defaultString(protocolState.corpNo, requestHeader.origSender)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(defaultString(protocolState.resFlag, settings.defaultProtocolResult)) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(defaultString(protocolState.errorCode, "")) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(defaultString(protocolState.errorMsg, "")) + "</ErrorMsg>",
                "<CtrctRtrFlg>1</CtrctRtrFlg>"
                        + "<OrgnlReqId>" + codecService.escape(protocolState.signReqId) + "</OrgnlReqId>"
                        + "<OrgnlDbtrProtocol>" + codecService.escape(protocolState.protocolNo) + "</OrgnlDbtrProtocol>"
                        + "<AuthChl>MOCK</AuthChl>"
                        + "<ProtocolProcessCode>" + codecService.escape(processCode) + "</ProtocolProcessCode>"
                        + "<ProcessCode>" + codecService.escape(processCode) + "</ProcessCode>"
                        + "<PrtclPrcsCd>" + codecService.escape(processCode) + "</PrtclPrcsCd>"
                        + "<ChngTp>" + codecService.escape(defaultString(protocolState.changeType, "ADDD")) + "</ChngTp>"
                        + "<SndTp>" + codecService.escape(defaultString(protocolState.sendType, "SD00")) + "</SndTp>"
                        + "<ProtocolStatus>" + codecService.escape(defaultString(protocolState.status, "SUCC")) + "</ProtocolStatus>"
                        + "<FeeNoList>" + codecService.escape(defaultString(protocolState.feeNoList, "")) + "</FeeNoList>"
                        + "<DbtrActId>" + codecService.escape(defaultString(protocolState.acctNo, "")) + "</DbtrActId>"
                        + "<DbtrActName>" + codecService.escape(defaultString(protocolState.acctName, protocolState.customerName)) + "</DbtrActName>"
                        + "<DbtrBankId>" + codecService.escape(defaultString(protocolState.bankId, "")) + "</DbtrBankId>"
                        + "<DbtrPhone>" + codecService.escape(defaultString(protocolState.phone, "")) + "</DbtrPhone>"
                        + "<Remark>" + codecService.escape(defaultString(protocolState.remark, "mock callback")) + "</Remark>");
        pushAsync(buildFullMessage(requestHeader, callbackMesgType, body), callbackMesgType,
                protocolState.signReqId, protocolState.protocolNo, null, null);
    }

    public void scheduleCaps308(CapsHeader requestHeader, String orgnlId, String cancleId) {
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps308) {
            log.info("跳过回调推送 caps.308：autoPushEnabled={} pushCaps308={}", settings.autoPushEnabled, settings.pushCaps308);
            return;
        }
        String body = codecService.buildXml("caps.308.001.01",
                "<CorpNo>" + codecService.escape(defaultString(requestHeader.origSender, "1111")) + "</CorpNo>"
                        + "<ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                "<OrgnlId>" + codecService.escape(orgnlId) + "</OrgnlId>"
                        + "<CancleId>" + codecService.escape(cancleId) + "</CancleId>"
                        + "<RetCode>0000</RetCode><RetMsg>撤销成功</RetMsg>");
        pushAsync(buildFullMessage(requestHeader, "caps.308.001.01", body), "caps.308.001.01",
                null, orgnlId, null, cancleId);
    }

    public void scheduleCaps205(CapsHeader requestHeader, TradeState tradeState) {
        movementService.scheduleTrade(requestHeader, tradeState);
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps205 || tradeState == null || !tradeState.callbackEnabled) {
            log.info("跳过回调推送 caps.205：autoPushEnabled={} pushCaps205={} sysSeqNo={} callbackEnabled={}",
                    settings.autoPushEnabled, settings.pushCaps205,
                    tradeState == null ? "null" : defaultString(tradeState.sysSeqNo, "-"),
                    tradeState != null && tradeState.callbackEnabled);
            return;
        }
        String callbackMesgType = defaultString(tradeState.callbackMesgType, "caps.205.001.01");
        String body = codecService.buildXml(callbackMesgType,
                "<CorpNo>" + codecService.escape(defaultString(requestHeader.origSender, "1111")) + "</CorpNo>"
                        + "<TranCode>" + codecService.escape(defaultString(tradeState.tranCode, "201")) + "</TranCode>"
                        + "<SysSeqNo>" + codecService.escape(defaultString(tradeState.sysSeqNo, "")) + "</SysSeqNo>"
                        + "<ResFlag>" + codecService.escape(defaultString(tradeState.resFlag, settings.defaultTradeResult)) + "</ResFlag>"
                        + "<ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                "<SerialNum>" + codecService.escape(defaultString(tradeState.serialNum, tradeState.reqId)) + "</SerialNum>"
                        + "<PayAmt>" + codecService.escape(defaultString(tradeState.amount, "0")) + "</PayAmt>"
                        + "<BtchNb>" + codecService.escape(defaultString(tradeState.btchNb, "")) + "</BtchNb>"
                        + "<CheckDate>" + codecService.escape(defaultString(tradeState.checkDate, "")) + "</CheckDate>"
                        + "<RetCode>" + codecService.escape(defaultString(tradeState.retCode, "000000")) + "</RetCode>"
                        + "<RetMsgId>" + codecService.escape(defaultString(tradeState.retCode, "000000")) + "</RetMsgId>"
                        + "<RetMsg>" + codecService.escape(defaultString(tradeState.retMsg, "trade success")) + "</RetMsg>"
                        + "<DbtrActName>" + codecService.escape(defaultString(tradeState.acctName, "")) + "</DbtrActName>"
                        + "<DbtrActId>" + codecService.escape(defaultString(tradeState.acctNo, "")) + "</DbtrActId>"
                        + "<DbtrBankId>" + codecService.escape(defaultString(tradeState.bankId, "")) + "</DbtrBankId>"
                        + "<CdtrActName>" + codecService.escape(defaultString(tradeState.creditorAcctName, "")) + "</CdtrActName>"
                        + "<CdtrActId>" + codecService.escape(defaultString(tradeState.creditorAcctNo, "")) + "</CdtrActId>"
                        + "<CdtrBankId>" + codecService.escape(defaultString(tradeState.creditorBankId, "")) + "</CdtrBankId>"
                        + "<BllNb>" + codecService.escape(defaultString(tradeState.billNo, "")) + "</BllNb>");
        pushAsync(buildFullMessage(requestHeader, callbackMesgType, body), callbackMesgType,
                tradeState.reqId, null, null, tradeState.sysSeqNo);
    }

    public void scheduleCaps107(CapsHeader requestHeader, BatchState batchState) {
        finalizeBatchStatus(batchState);
        scheduleBatchMovement(requestHeader, batchState);
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps107 || batchState == null || !batchState.callbackEnabled) {
            log.info("跳过回调推送 caps.107：autoPushEnabled={} pushCaps107={} 批次={} callbackEnabled={}",
                    settings.autoPushEnabled, settings.pushCaps107,
                    batchState == null ? "null" : defaultString(batchState.batchNo, "-"),
                    batchState != null && batchState.callbackEnabled);
            return;
        }
        String callbackMesgType = defaultString(batchState.callbackMesgType, "caps.107.001.01");
        String body = codecService.buildXml(callbackMesgType,
                "<CorpNo>" + codecService.escape(defaultString(requestHeader.origSender, "1111")) + "</CorpNo>"
                        + "<BatchNo>" + codecService.escape(batchState.batchNo) + "</BatchNo>"
                        + "<CheckDate>" + codecService.escape(defaultString(batchState.checkDate, "")) + "</CheckDate>"
                        + "<BatchStatus>" + codecService.escape(defaultString(batchState.status, "SUCC")) + "</BatchStatus>"
                        + "<Remark>" + codecService.escape(defaultString(batchState.errorMsg, "mock callback")) + "</Remark>",
                "<FileData>" + codecService.escape(defaultString(batchState.fileData, "")) + "</FileData>");
        pushAsync(buildFullMessage(requestHeader, callbackMesgType, body), callbackMesgType,
                batchState.reqId, null, batchState.batchNo, null);
    }

    public String triggerManualCallback(String callbackMesgType, String targetUrl, String reqId,
                                        String protocolNo, String batchNo, String sysSeqNo) {
        return triggerManualCallback(callbackMesgType, targetUrl, reqId, protocolNo, batchNo, sysSeqNo,
                null, null, null, null, null, null);
    }

    public String triggerManualCallback(String callbackMesgType, String targetUrl, String reqId,
                                        String protocolNo, String batchNo, String sysSeqNo,
                                        String acctNo, String customerName, String customerId,
                                        String feeNoList, String bankId, String remark) {
        String resolvedUrl = targetUrl == null || targetUrl.isBlank()
                ? storeService.getSettings().defaultTargetUrl : targetUrl;
        boolean bankInitiated = isBankInitiatedCaps305(callbackMesgType);
        String resolvedReqId = bankInitiated
                ? defaultString(reqId, "MOCK-BANK-" + System.currentTimeMillis()) : reqId;
        String message = bankInitiated
                ? buildManualBankInitiatedMessage(callbackMesgType, resolvedReqId, protocolNo, acctNo,
                customerName, customerId, feeNoList, bankId, remark)
                : buildManualCallbackMessage(callbackMesgType, reqId, protocolNo, batchNo, sysSeqNo);
        String actualMesgType = bankInitiated ? "caps.305.001.01" : callbackMesgType;
        String traceProtocolNo = "caps.305.bank-sign".equals(callbackMesgType)
                ? "BANK-PENDING-" + resolvedReqId : protocolNo;
        push(message, actualMesgType, resolvedUrl, resolvedReqId, traceProtocolNo, batchNo, sysSeqNo);
        return message;
    }

    String buildManualBankInitiatedMessage(String callbackMesgType, String reqId, String protocolNo,
                                           String acctNo, String customerName, String customerId,
                                           String feeNoList, String bankId, String remark) {
        boolean sign = "caps.305.bank-sign".equals(callbackMesgType);
        boolean cancel = "caps.305.bank-cancel".equals(callbackMesgType);
        if (!sign && !cancel) {
            throw new IllegalArgumentException("不支持的银行主动业务类型: " + callbackMesgType);
        }
        if (cancel && (protocolNo == null || protocolNo.isBlank())) {
            throw new IllegalArgumentException("银行主动解约必须填写协议号");
        }
        String resolvedReqId = defaultString(reqId, "MOCK-BANK-" + System.currentTimeMillis());
        String resolvedProtocolNo = sign ? "BANK-PENDING-" + resolvedReqId : protocolNo.trim();
        String resolvedCustomerName = defaultString(customerName, "MOCK CUSTOMER");
        String resolvedAcctNo = defaultString(acctNo, "62220000000000000000");
        String resolvedFeeNoList = normalizeFeeNoList(feeNoList);
        String resolvedBankId = defaultString(bankId, "105000");
        String resolvedRemark = defaultString(remark, sign ? "manual bank sign" : "manual bank cancel");

        ProtocolState state = new ProtocolState();
        state.protocolNo = resolvedProtocolNo;
        state.acctNo = sign ? resolvedAcctNo : defaultString(acctNo, "");
        state.acctName = resolvedCustomerName;
        state.corpNo = "33503C5801";
        state.customerId = defaultString(customerId, "MOCK-CUSTOMER");
        state.customerName = resolvedCustomerName;
        state.feeNoList = resolvedFeeNoList;
        state.bankId = resolvedBankId;
        state.status = "PENDING";
        state.signReqId = resolvedReqId;
        state.changeType = sign ? "ADDD" : "DELE";
        state.sendType = "SD00";
        state.remark = resolvedRemark;
        state.callbackEnabled = false;
        state.callbackMesgType = "caps.306.001.01";
        storeService.saveProtocol(state);

        CapsHeader header = new CapsHeader();
        header.userName = "CAPS";
        header.password = "CAPS";
        header.origSender = "904290099992";
        header.origReceiver = "33503C5801";
        StringBuilder body = new StringBuilder();
        body.append("<ReqId>").append(codecService.escape(resolvedReqId)).append("</ReqId>")
                .append("<ChngTp>").append(sign ? "ADDD" : "DELE").append("</ChngTp>")
                .append("<SndrFlg>BKSD</SndrFlg>")
                .append("<CstmrId>").append(codecService.escape(state.customerId)).append("</CstmrId>")
                .append("<CstmrNm>").append(codecService.escape(resolvedCustomerName)).append("</CstmrNm>")
                .append("<FeeNoList>").append(codecService.escape(resolvedFeeNoList)).append("</FeeNoList>")
                .append("<DbtrProtocol>")
                .append(codecService.escape(sign ? "0" : resolvedProtocolNo))
                .append("</DbtrProtocol>");
        if (sign) {
            body.append("<DbtrActId>").append(codecService.escape(resolvedAcctNo)).append("</DbtrActId>")
                    .append("<DbtrActName>").append(codecService.escape(resolvedCustomerName)).append("</DbtrActName>")
                    .append("<DbtrCardType>03</DbtrCardType>");
        }
        body.append("<DbtrBankId>").append(codecService.escape(resolvedBankId)).append("</DbtrBankId>")
                .append("<SndTp>SD00</SndTp>")
                .append("<Remark>").append(codecService.escape(resolvedRemark)).append("</Remark>");
        String xml = codecService.buildXml("caps.305.001.01",
                "<CorpNo>33503C5801</CorpNo>", body.toString());
        return buildFullMessage(header, "caps.305.001.01", xml);
    }

    private boolean isBankInitiatedCaps305(String callbackMesgType) {
        return "caps.305.bank-sign".equals(callbackMesgType)
                || "caps.305.bank-cancel".equals(callbackMesgType);
    }

    private String normalizeFeeNoList(String feeNoList) {
        String value = defaultString(feeNoList, "00600|00601").trim();
        return value.replace('，', '|').replace(',', '|').replace(';', '|');
    }

    private String buildManualCallbackMessage(String callbackMesgType, String reqId,
                                              String protocolNo, String batchNo, String sysSeqNo) {
        CapsHeader header = new CapsHeader();
        header.userName = "CAPS";
        header.password = "CAPS";
        header.origSender = "904290099992";
        header.origReceiver = "33503C5801";
        String xml;
        switch (callbackMesgType) {
            case "caps.600.001.01" -> xml = codecService.buildXml("caps.600.001.01",
                    "<CorpNo>33503C5801</CorpNo><CheckDate>20260705</CheckDate><ChannelCode>0103</ChannelCode><ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                    "<FileData>"
                            + codecService.escape(codecService.base64("<Recon><Count>1</Count><Amt>100.00</Amt></Recon>"))
                            + "</FileData>");
            case "caps.916.001.01" -> xml = codecService.buildXml("caps.916.001.01",
                    "<CorpNo>33503C5801</CorpNo><ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                    "<SysSeqNo>" + codecService.escape(defaultString(sysSeqNo, "MOCK916")) + "</SysSeqNo><FileData>"
                            + codecService.escape(codecService.base64("<BankChange><BankNo>105000</BankNo></BankChange>"))
                            + "</FileData>");
            case "caps.308.001.01" -> xml = codecService.buildXml("caps.308.001.01",
                    "<CorpNo>33503C5801</CorpNo><ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                    "<OrgnlId>" + codecService.escape(defaultString(protocolNo, "MOCK-PROT")) + "</OrgnlId><CancleId>MOCK-CANCEL</CancleId><RetCode>0000</RetCode><RetMsg>撤销成功</RetMsg>");
            case "caps.205.001.01" -> xml = codecService.buildXml("caps.205.001.01",
                    "<CorpNo>33503C5801</CorpNo><ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                    "<TranCode>201</TranCode><SysSeqNo>" + codecService.escape(defaultString(sysSeqNo, "MOCK-SEQ")) + "</SysSeqNo><RetCode>000000</RetCode><RetMsg>交易成功</RetMsg><PayAmt>100.00</PayAmt>");
            case "caps.107.001.01" -> xml = codecService.buildXml("caps.107.001.01",
                    "<CorpNo>33503C5801</CorpNo><BatchNo>" + codecService.escape(defaultString(batchNo, "MOCK-BATCH")) + "</BatchNo><CheckDate>20260705</CheckDate><BatchStatus>SUCC</BatchStatus><Remark>manual</Remark>",
                    "<FileData>" + codecService.escape(codecService.base64("<Batch><Count>1</Count></Batch>")) + "</FileData>");
            default -> xml = codecService.buildXml("caps.306.001.01",
                    "<CorpNo>33503C5801</CorpNo><ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                    "<CtrctRtrFlg>1</CtrctRtrFlg><OrgnlReqId>" + codecService.escape(defaultString(reqId, "MOCK-REQ")) + "</OrgnlReqId><OrgnlDbtrProtocol>"
                            + codecService.escape(defaultString(protocolNo, "MOCK-PROT")) + "</OrgnlDbtrProtocol><AuthChl>MOCK</AuthChl><ProtocolProcessCode>CS00</ProtocolProcessCode><ProcessCode>CS00</ProcessCode><ChngTp>ADDD</ChngTp><ProtocolStatus>SUCC</ProtocolStatus><Remark>manual</Remark>");
        }
        return buildFullMessage(header, callbackMesgType, xml);
    }

    private String buildFullMessage(CapsHeader requestHeader, String callbackMesgType, String xmlBody) {
        return codecService.buildMessage(callbackMesgType, "D",
                requestHeader == null ? "" : requestHeader.mesgId,
                requestHeader == null ? "CAPS" : defaultString(requestHeader.userName, "CAPS"),
                requestHeader == null ? "CAPS" : defaultString(requestHeader.password, "CAPS"),
                requestHeader == null ? "904290099992" : defaultString(requestHeader.origReceiver, "904290099992"),
                requestHeader == null ? "33503C5801" : defaultString(requestHeader.origSender, "33503C5801"),
                xmlBody);
    }

    private void pushAsync(String fullMessage, String mesgType, String reqId,
                           String protocolNo, String batchNo, String sysSeqNo) {
        Thread thread = new Thread(() -> {
            MockSettings settings = storeService.getSettings();
            try {
                Thread.sleep(settings.delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            push(fullMessage, mesgType, settings.defaultTargetUrl, reqId, protocolNo, batchNo, sysSeqNo);
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void push(String fullMessage, String mesgType, String targetUrl,
                      String reqId, String protocolNo, String batchNo, String sysSeqNo) {
        MockRecord record = new MockRecord();
        record.recordType = "CALLBACK";
        record.source = "yht-mock-server";
        record.target = targetUrl;
        record.mesgType = mesgType;
        record.reqId = reqId;
        record.protocolNo = protocolNo;
        record.batchNo = batchNo;
        record.sysSeqNo = sysSeqNo;
        record.requestBody = fullMessage;
        try {
            if (targetUrl == null || targetUrl.isBlank()) {
                throw new IllegalArgumentException("callback target url is blank");
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/xml;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(fullMessage))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            record.status = String.valueOf(response.statusCode());
            record.responseBody = response.body();
            record.remark = "callback pushed";
            log.info("回调推送 {} → {} 批次={} reqId={} HTTP {}", mesgType, targetUrl, batchNo, reqId, response.statusCode());
        } catch (Exception e) {
            record.status = "FAIL";
            record.responseBody = e.getMessage();
            record.remark = "callback push failed";
            log.warn("回调推送失败 {} → {} 批次={} reqId={} 原因={}", mesgType, targetUrl, batchNo, reqId, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        storeService.addRecord(record);
    }

    public void scheduleBatchMovement(CapsHeader header, BatchState batch) {
        movementService.scheduleBatch(header, batch);
    }

    /**
     * 银行侧推送 107 即代表该批次交易处理完毕：推送前把仍停留在处理中（PROC/PEND/INIT 等）的批次
     * 按结果文件落终态并保存，保证 107 报文与页面状态都是终态。已有终态不覆盖；
     * 对手账号的校验与强制状态已统一在批次构建（buildBatchState）时按结果文件逐笔判定，此处不再二次干预。
     */
    private void finalizeBatchStatus(BatchState batchState) {
        if (batchState == null || isFinalBatchStatus(batchState.status)) {
            return;
        }
        var summary = MockGatewaySupport.summarizeBatchResultFile(batchState.fileData);
        if (summary == null) {
            log.warn("批次 {} 状态为 {} 但结果文件无法解析，保持原状态不做猜测", batchState.batchNo, batchState.status);
            return;
        }
        String previous = batchState.status;
        batchState.status = summary.status();
        if ("FAIL".equals(summary.status())) {
            if (batchState.errorCode == null || batchState.errorCode.isBlank()) {
                batchState.errorCode = summary.firstFailedCode();
            }
            if (batchState.errorMsg == null || batchState.errorMsg.isBlank()) {
                batchState.errorMsg = summary.firstFailedMsg();
            }
        }
        storeService.saveBatch(batchState);
        log.info("107 推送前落定批次终态：批次={} {} → {} 结果明细数={}",
                batchState.batchNo, defaultString(previous, "空"), batchState.status, summary.detailCount());
    }

    private boolean isFinalBatchStatus(String status) {
        return status != null && Set.of("SUCC", "FAIL", "CANC", "CANCEL", "REJ").contains(status.trim().toUpperCase());
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
