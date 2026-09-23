package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.CapsHeader;
import cn.capinfo.gjj.yhtmock.model.MockRecord;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import cn.capinfo.gjj.yhtmock.model.MockSettings;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MockGatewayService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MockGatewayService.class);

    private final CapsCodecService codecService;
    private final MockStoreService storeService;
    private final MockCallbackService callbackService;
    private final MockGatewaySupport support;
    private final SvsMockService svsMockService;
    private final List<CapsMessageHandler> handlers;

    private final Map<String, CapsMessageHandler> handlerRegistry = new HashMap<>();

    @Autowired
    public MockGatewayService(CapsCodecService codecService,
                              MockStoreService storeService,
                              MockCallbackService callbackService,
                              MockGatewaySupport support,
                              SvsMockService svsMockService,
                              List<CapsMessageHandler> handlers) {
        this.codecService = codecService;
        this.storeService = storeService;
        this.callbackService = callbackService;
        this.support = support;
        this.svsMockService = svsMockService;
        this.handlers = handlers;
    }

    MockGatewayService(CapsCodecService codecService,
                       MockStoreService storeService,
                       MockCallbackService callbackService) {
        this.codecService = codecService;
        this.storeService = storeService;
        this.callbackService = callbackService;
        this.support = new MockGatewaySupport(codecService, storeService);
        this.svsMockService = new SvsMockService(storeService);
        this.handlers = List.of(
                new ProtocolUploadHandler(this.support, storeService),
                new ProtocolSignHandler(this.support, storeService, callbackService),
                new ProtocolCallbackHandler(this.support, storeService),
                new ProtocolQueryHandler(this.support, storeService),
                new ProtocolCancelHandler(this.support, callbackService),
                new BatchApplyHandler(this.support, storeService, callbackService),
                new BatchQueryHandler(this.support, storeService),
                new BatchConfirmHandler(this.support, storeService, callbackService),
                new TradeApplyHandler(this.support, storeService, callbackService),
                new TradeQueryHandler(this.support, storeService),
                new ProbeHandler(this.support),
                new ReconHandler(this.support));
        initRegistry();
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
        boolean requestSigned = hasSignBlock(frame.headerText());
        boolean requestEncrypted = false;
        String dispatchXmlBody = frame.xmlBody();

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
            Document outerDocument = codecService.parseXml(dispatchXmlBody);
            if (outerDocument == null) {
                throw new IllegalArgumentException("\u62a5\u6587XML\u89e3\u6790\u5931\u8d25");
            }
            if (codecService.isDocumentEnvelope(outerDocument)) {
                String encryptedMessageText = codecService.documentMessageText(outerDocument);
                String decryptedBody = tryDecryptBusinessXml(requestMesgType, encryptedMessageText);
                if (!decryptedBody.isBlank()) {
                    requestEncrypted = true;
                    dispatchXmlBody = decryptedBody;
                }
            }
            Document document = codecService.parseXml(dispatchXmlBody);
            if (document == null) {
                throw new IllegalArgumentException("\u62a5\u6587XML\u89e3\u6790\u5931\u8d25");
            }

            reqId = firstNonBlank(codecService.text(document, "ReqId"),
                    codecService.text(document, "OrgnlReqId"));
            protocolNo = firstNonBlank(codecService.text(document, "DbtrProtocol"),
                    codecService.text(document, "OrgnlDbtrProtocol"),
                    codecService.text(document, "OrgnlId"),
                    codecService.text(document, "OrigMsgId"),
                    codecService.text(document, "PyerBgNum"));
            batchNo = firstNonBlank(codecService.text(document, "BatchNo"),
                    codecService.text(document, "BtchNb"),
                    codecService.text(document, "OrgnlBatchNo"));
            sysSeqNo = firstNonBlank(codecService.text(document, "SysSeqNo"),
                    codecService.text(document, "OrgnlSysSeqNo"),
                    codecService.text(document, "SerialNum"));
            acctNo = firstNonBlank(codecService.text(document, "DbtrActId"),
                    codecService.text(document, "AcctNo"));

            // 账号校验规则不再按中心账户匹配（matchScenario 已废弃），对手账号校验在交易/批次构建时逐笔完成；
            // 此处仅读取全局配置（含 randomFail 随机失败开关）供后续构建使用。
            MockSettings settings = storeService.getSettings();

            CapsMessageHandler handler = handlerRegistry.get(requestMesgType);
            if (handler == null) {
                responseMesgType = "caps.900.001.01";
                responseXml = buildCaps900(successCorp(requestHeader),
                        "FAIL", "E012", "业务类型非法");
                status = "FAIL";
            } else {
                GatewayRequestContext ctx = new GatewayRequestContext(
                        requestHeader, document, requestMesgType,
                        reqId, protocolNo, batchNo, sysSeqNo, acctNo, scenarioRule, settings);
                GatewayDispatchResult result = handler.handle(ctx);
                responseMesgType = result.responseMesgType();
                responseXml = result.responseXml();
                status = result.status();
                protocolNo = firstNonBlank(result.protocolNo(), protocolNo);
                batchNo = firstNonBlank(result.batchNo(), batchNo);
                sysSeqNo = firstNonBlank(result.sysSeqNo(), sysSeqNo);
            }
        } catch (Exception e) {
            responseMesgType = "caps.900.001.01";
            responseXml = buildCaps900(successCorp(requestHeader),
                    "FAIL", e instanceof IllegalArgumentException ? "E401" : "S999", null);
            status = "FAIL";
            log.warn("网关处理失败 mesgType={} reqId={} batchNo={} 企业号={} 原因={}",
                    requestMesgType, reqId, batchNo, safe(requestHeader.origSender), e.getMessage(), e);
        }

        String responseHeader = codecService.buildHeader(responseMesgType, "D",
                requestHeader.mesgId,
                safe(requestHeader.userName, "CAPS"),
                safe(requestHeader.password, "CAPS"),
                safe(requestHeader.origReceiver, "904290099992"),
                safe(requestHeader.origSender, "33503C5801"));
        boolean plainAck = "caps.900.001.01".equals(responseMesgType);
        String responseSignBlock = requestSigned && !plainAck ? buildSignBlock(responseXml) : "";
        String responseBody = requestEncrypted && !plainAck ? encryptBusinessXml(responseMesgType, responseXml) : responseXml;
        String responseMessage = responseHeader + responseSignBlock + responseBody;

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
        if (requestEncrypted) {
            // 请求/响应均走 CAPS 信封加密：保留解密后的明文业务报文，供链路视图对比。
            record.decryptedRequestBody = dispatchXmlBody;
            record.decryptedResponseBody = responseXml;
        }
        record.remark = scenarioRule == null
                ? "gateway dispatch"
                : "gateway dispatch by scenario: " + safe(scenarioRule.name, String.valueOf(scenarioRule.id));
        storeService.addRecord(record);
        log.info("网关请求 {} reqId={} batchNo={} sysSeqNo={} 企业号={} → 响应 {} status={}{}",
                requestMesgType, reqId, batchNo, sysSeqNo, safe(requestHeader.origSender),
                responseMesgType, status,
                scenarioRule == null ? "" : " 场景=" + safe(scenarioRule.name, String.valueOf(scenarioRule.id)));
        return responseMessage;
    }


    private String tryDecryptBusinessXml(String mesgType, String encryptedMessageText) {
        try {
            byte[] cipherBytes = Base64.getMimeDecoder().decode(encryptedMessageText.trim());
            byte[] plainBytes = svsMockService.decryptBytes("gateway-request", cipherBytes);
            String innerXml = new String(plainBytes, StandardCharsets.UTF_8).trim();
            if (innerXml.startsWith("<")) {
                return codecService.buildXmlWithInnerXml(mesgType, innerXml);
            }
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String encryptBusinessXml(String mesgType, String responseXml) {
        String innerXml = codecService.extractMessageInnerXml(responseXml);
        byte[] cipherBytes = svsMockService.encryptBytes("gateway-response", innerXml.getBytes(StandardCharsets.UTF_8));
        return codecService.buildDocumentWithText(mesgType, Base64.getEncoder().encodeToString(cipherBytes));
    }

    private String buildSignBlock(String responseXml) {
        byte[] signature = svsMockService.signBytes("gateway-response", responseXml.getBytes(StandardCharsets.UTF_8));
        return "{S:" + Base64.getEncoder().encodeToString(signature) + "}\r\n";
    }

    private boolean hasSignBlock(String headerText) {
        return headerText != null && headerText.contains("{S:");
    }

    private String buildCaps900(String corpNo, String resFlag, String procCode, String procMsg) {
        String normalizedCode = "SUCC".equals(resFlag) ? "I000"
                : Caps900CodeCatalog.normalizeFailureCode(procCode);
        return codecService.buildXml("caps.900.001.01",
                "<CorpNo>" + codecService.escape(corpNo) + "</CorpNo>"
                        + "<ResFlag>" + codecService.escape(resFlag) + "</ResFlag>"
                        + "<ProcCode>" + codecService.escape(normalizedCode) + "</ProcCode>"
                        + "<ProcMsg>" + codecService.escape(Caps900CodeCatalog.description(normalizedCode)) + "</ProcMsg>"
                        + "<Remark>" + codecService.escape(procMsg) + "</Remark>",
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

    private String firstNonBlank(String... values) {
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
