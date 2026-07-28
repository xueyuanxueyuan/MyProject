package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MockGatewayService {

    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CapsCodecService codecService;
    private final MockStoreService storeService;
    private final MockCallbackService callbackService;
    private final MockGatewaySupport support;
    private final List<CapsMessageHandler> handlers;

    private final Map<String, CapsMessageHandler> handlerRegistry = new HashMap<>();

    public MockGatewayService(CapsCodecService codecService,
                              MockStoreService storeService,
                              MockCallbackService callbackService,
                              MockGatewaySupport support,
                              List<CapsMessageHandler> handlers) {
        this.codecService = codecService;
        this.storeService = storeService;
        this.callbackService = callbackService;
        this.support = support;
        this.handlers = handlers;
    }

    @PostConstruct
    void initRegistry() {
        for (CapsMessageHandler handler : handlers) {
            for (String mesgType : handler.supportedMesgTypes()) {
                handlerRegistry.put(mesgType, handler);
            }
        }
    }

    public String dispatch(String rawMessage) {
        CapsCodecService.ParsedFrame frame = codecService.parseFrame(rawMessage);
        var requestHeader = frame.header();
        String requestMesgType = safe(requestHeader.mesgType);

        String reqId = "";
        String protocolNo = "";
        String batchNo = "";
        String sysSeqNo = "";
        String acctNo = "";
        String status = "SUCC";
        MockScenarioRule scenarioRule = null;
        String responseMesgType;
        String responseXml;

        try {
            Document document = codecService.parseXml(frame.xmlBody());
            if (document == null) {
                throw new IllegalArgumentException("报文XML解析失败");
            }

            reqId = codecService.text(document, "ReqId");
            protocolNo = firstNonBlank(codecService.text(document, "DbtrProtocol"),
                    codecService.text(document, "OrgnlId"));
            batchNo = codecService.text(document, "BatchNo");
            sysSeqNo = codecService.text(document, "SysSeqNo");
            acctNo = codecService.text(document, "DbtrActId");

            scenarioRule = storeService.matchScenario(support.buildScenarioContext(
                    requestMesgType, acctNo, protocolNo, reqId, batchNo, sysSeqNo));

            CapsMessageHandler handler = handlerRegistry.get(requestMesgType);
            if (handler == null) {
                responseMesgType = "caps.900.001.01";
                responseXml = buildCaps900(successCorp(requestHeader),
                        "FAIL", "UNSPRT", "暂不支持该报文");
                status = "FAIL";
            } else {
                GatewayRequestContext ctx = new GatewayRequestContext(
                        requestHeader, document, requestMesgType,
                        reqId, protocolNo, batchNo, sysSeqNo, acctNo, scenarioRule);
                GatewayDispatchResult result = handler.handle(ctx);
                responseMesgType = result.responseMesgType();
                responseXml = result.responseXml();
                status = result.status();
                protocolNo = result.protocolNo();
                batchNo = result.batchNo();
                sysSeqNo = result.sysSeqNo();
            }
        } catch (Exception e) {
            responseMesgType = "caps.900.001.01";
            responseXml = buildCaps900(successCorp(requestHeader),
                    "FAIL", "EXCEPT", e.getMessage());
            status = "FAIL";
        }

        String responseMessage = codecService.buildMessage(responseMesgType, "D",
                requestHeader.mesgId,
                safe(requestHeader.userName, "CAPS"),
                safe(requestHeader.password, "CAPS"),
                safe(requestHeader.origReceiver, "904290099992"),
                safe(requestHeader.origSender, "33503C5801"),
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
        record.remark = scenarioRule == null
                ? "gateway dispatch"
                : "gateway dispatch by scenario: " + safe(scenarioRule.name, String.valueOf(scenarioRule.id));
        storeService.addRecord(record);
        return responseMessage;
    }

    private String buildCaps900(String corpNo, String resFlag, String procCode, String procMsg) {
        return codecService.buildXml("caps.900.001.01",
                "<CorpNo>" + codecService.escape(corpNo) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(resFlag) + "</ResFlag>"
                        + "<ProcCode>" + codecService.escape(procCode) + "</ProcCode>"
                        + "<ProcMsg>" + codecService.escape(procMsg) + "</ProcMsg>",
                null);
    }

    private String successCorp(CapsHeader requestHeader) {
        return safe(requestHeader.origSender, "33503C5801");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String firstNonBlank(String first, String second) {
        return (first != null && !first.isBlank()) ? first : safe(second);
    }
}