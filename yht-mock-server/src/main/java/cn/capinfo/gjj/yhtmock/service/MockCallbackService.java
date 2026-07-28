package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import cn.capinfo.gjj.yhtmock.model.TradeState;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class MockCallbackService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final CapsCodecService codecService;
    private final MockStoreService storeService;

    public MockCallbackService(CapsCodecService codecService, MockStoreService storeService) {
        this.codecService = codecService;
        this.storeService = storeService;
    }

    public void scheduleCaps306(CapsHeader requestHeader, ProtocolState protocolState) {
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps306 || protocolState == null || !protocolState.callbackEnabled) {
            return;
        }
        String callbackMesgType = defaultString(protocolState.callbackMesgType, "caps.306.001.01");
        String body = codecService.buildXml(callbackMesgType,
                "<CorpNo>" + codecService.escape(defaultString(protocolState.corpNo, requestHeader.origSender)) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(defaultString(protocolState.resFlag, settings.defaultProtocolResult)) + "</ResFlag>"
                        + "<ErrorCode>" + codecService.escape(defaultString(protocolState.errorCode, "")) + "</ErrorCode>"
                        + "<ErrorMsg>" + codecService.escape(defaultString(protocolState.errorMsg, "")) + "</ErrorMsg>",
                "<CtrctRtrFlg>1</CtrctRtrFlg>"
                        + "<OrgnlReqId>" + codecService.escape(protocolState.signReqId) + "</OrgnlReqId>"
                        + "<OrgnlDbtrProtocol>" + codecService.escape(protocolState.protocolNo) + "</OrgnlDbtrProtocol>"
                        + "<AuthChl>MOCK</AuthChl><Remark>" + codecService.escape(defaultString(protocolState.remark, "mock callback")) + "</Remark>");
        pushAsync(buildFullMessage(requestHeader, callbackMesgType, body), callbackMesgType,
                protocolState.signReqId, protocolState.protocolNo, null, null);
    }

    public void scheduleCaps308(CapsHeader requestHeader, String orgnlId, String cancleId) {
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps308) {
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
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps205 || tradeState == null || !tradeState.callbackEnabled) {
            return;
        }
        String callbackMesgType = defaultString(tradeState.callbackMesgType, "caps.205.001.01");
        String body = codecService.buildXml(callbackMesgType,
                "<CorpNo>" + codecService.escape(defaultString(requestHeader.origSender, "1111")) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(defaultString(tradeState.resFlag, settings.defaultTradeResult)) + "</ResFlag>"
                        + "<ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                "<TranCode>" + codecService.escape(tradeState.tranCode) + "</TranCode>"
                        + "<SysSeqNo>" + codecService.escape(tradeState.sysSeqNo) + "</SysSeqNo>"
                        + "<RetCode>" + codecService.escape(defaultString(tradeState.retCode, "000000")) + "</RetCode>"
                        + "<RetMsg>" + codecService.escape(defaultString(tradeState.retMsg, "交易成功")) + "</RetMsg>"
                        + "<PayAmt>" + codecService.escape(defaultString(tradeState.amount, "0")) + "</PayAmt>");
        pushAsync(buildFullMessage(requestHeader, callbackMesgType, body), callbackMesgType,
                tradeState.reqId, null, null, tradeState.sysSeqNo);
    }

    public void scheduleCaps107(CapsHeader requestHeader, BatchState batchState) {
        MockSettings settings = storeService.getSettings();
        if (!settings.autoPushEnabled || !settings.pushCaps107 || batchState == null || !batchState.callbackEnabled) {
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
        String resolvedUrl = targetUrl == null || targetUrl.isBlank() ? storeService.getSettings().defaultTargetUrl : targetUrl;
        String message = buildManualCallbackMessage(callbackMesgType, reqId, protocolNo, batchNo, sysSeqNo);
        push(message, callbackMesgType, resolvedUrl, reqId, protocolNo, batchNo, sysSeqNo);
        return message;
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
                    "<CorpNo>33503C5801</CorpNo><ResFlag>SUCC</ResFlag><ErrorCode></ErrorCode><ErrorMsg></ErrorMsg>",
                    "<CheckDate>20260705</CheckDate><ChannelCode>0103</ChannelCode><FileData>"
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
                            + codecService.escape(defaultString(protocolNo, "MOCK-PROT")) + "</OrgnlDbtrProtocol><AuthChl>MOCK</AuthChl><Remark>manual</Remark>");
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/xml;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(fullMessage))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            record.status = String.valueOf(response.statusCode());
            record.responseBody = response.body();
            record.remark = "callback pushed";
        } catch (IOException | InterruptedException e) {
            record.status = "FAIL";
            record.responseBody = e.getMessage();
            record.remark = "callback push failed";
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        storeService.addRecord(record);
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
