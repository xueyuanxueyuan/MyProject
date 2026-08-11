package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class ProtocolUploadHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;

    ProtocolUploadHandler(MockGatewaySupport support, MockStoreService storeService) {
        this.support = support;
        this.storeService = storeService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.301.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        ProtocolState protocolState = support.buildProtocolState(ctx.document(), ctx.requestHeader(), ctx.scenarioRule());
        storeService.saveProtocol(protocolState);
        String responseXml = support.buildCaps302(protocolState);
        return GatewayDispatchResult.of("caps.302.001.01", responseXml,
                support.safe(protocolState.status, "SUCC"),
                protocolState.protocolNo, "", "");
    }
}

@Component
class ProtocolSignHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;
    private final MockCallbackService callbackService;

    ProtocolSignHandler(MockGatewaySupport support, MockStoreService storeService,
                        MockCallbackService callbackService) {
        this.support = support;
        this.storeService = storeService;
        this.callbackService = callbackService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.305.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        String changeType = ctx.document() == null ? "" : support.text(ctx.document(), "ChngTp");
        String sendType = ctx.document() == null ? "" : support.text(ctx.document(), "SndTp");
        String origMsgId = ctx.document() == null ? "" : support.text(ctx.document(), "OrigMsgId");
        String pyerBgNum = ctx.document() == null ? "" : support.text(ctx.document(), "PyerBgNum");

        if (!origMsgId.isBlank() && changeType.isBlank()) {
            String responseXml = support.buildCaps900(support.successCorp(ctx.requestHeader()),
                    support.resolveResFlag(ctx.scenarioRule(), "SUCC"),
                    support.resolveCode(ctx.scenarioRule(), "00000000"),
                    support.resolveMsg(ctx.scenarioRule(), "query accepted"));
            return GatewayDispatchResult.of("caps.900.001.01", responseXml,
                    support.resolveStatus(ctx.scenarioRule(), "SUCC"),
                    support.firstNonBlank(ctx.protocolNo(), origMsgId, pyerBgNum), "", "");
        }

        ProtocolState protocolState;
        if ("SD01".equalsIgnoreCase(sendType)) {
            protocolState = storeService.findProtocolByReqId(ctx.reqId());
            if (protocolState == null) {
                protocolState = support.buildProtocolState(ctx.document(), ctx.requestHeader(), ctx.scenarioRule());
            }
            protocolState.authCode = ctx.document() == null ? protocolState.authCode : support.text(ctx.document(), "AuthCd");
            protocolState.sendType = "SD01";
            protocolState.changeType = support.firstNonBlank(protocolState.changeType, "ADDD");
            protocolState.signReqId = support.firstNonBlank(protocolState.signReqId, ctx.reqId());
            protocolState.status = support.resolveStatus(ctx.scenarioRule(), "SUCC");
            protocolState.protocolProcessCode = "CS00";
        } else if ("DELE".equalsIgnoreCase(changeType)) {
            ProtocolState existing = storeService.findProtocol(ctx.protocolNo(), ctx.acctNo());
            protocolState = existing == null
                    ? support.buildProtocolState(ctx.document(), ctx.requestHeader(), ctx.scenarioRule())
                    : existing;
            ProtocolState requestState = support.buildProtocolState(ctx.document(), ctx.requestHeader(), ctx.scenarioRule());
            protocolState.signReqId = support.firstNonBlank(requestState.signReqId, ctx.reqId(), protocolState.signReqId);
            protocolState.changeType = "DELE";
            protocolState.sendType = support.firstNonBlank(requestState.sendType, "SD00");
            protocolState.feeNoList = support.firstNonBlank(requestState.feeNoList, protocolState.feeNoList);
            protocolState.acctNo = support.firstNonBlank(requestState.acctNo, protocolState.acctNo);
            protocolState.acctName = support.firstNonBlank(requestState.acctName, protocolState.acctName);
            protocolState.bankId = support.firstNonBlank(requestState.bankId, protocolState.bankId);
            protocolState.status = support.resolveStatus(ctx.scenarioRule(), "CANCELLED");
            protocolState.protocolProcessCode = "CS20";
            protocolState.remark = support.resolveMsg(ctx.scenarioRule(), "cancel accepted");
        } else {
            protocolState = support.buildProtocolState(ctx.document(), ctx.requestHeader(), ctx.scenarioRule());
            protocolState.signReqId = support.firstNonBlank(protocolState.signReqId, ctx.reqId());
            protocolState.changeType = support.firstNonBlank(protocolState.changeType, "ADDD");
            protocolState.sendType = support.firstNonBlank(protocolState.sendType, "SD00");
            protocolState.status = support.resolveStatus(ctx.scenarioRule(), "ACCEPTED");
            protocolState.protocolProcessCode = support.firstNonBlank(protocolState.protocolProcessCode, "CS00");
        }

        protocolState.callbackMesgType = support.defaultString(
                support.resolveCallbackType(ctx.scenarioRule()), "caps.306.001.01");
        protocolState.callbackEnabled = !support.isAutoCallbackDisabled(ctx.scenarioRule());
        storeService.saveProtocol(protocolState);

        String responseXml = support.buildCaps900(support.successCorp(ctx.requestHeader()),
                support.safe(protocolState.resFlag, "SUCC"),
                support.resolveCode(ctx.scenarioRule(), "00000000"),
                support.resolveMsg(ctx.scenarioRule(), "accepted"));

        callbackService.scheduleCaps306(ctx.requestHeader(), protocolState);
        return GatewayDispatchResult.of("caps.900.001.01", responseXml,
                support.safe(protocolState.status, "SUCC"),
                protocolState.protocolNo, "", "");
    }
}

@Component
class ProtocolQueryHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;

    ProtocolQueryHandler(MockGatewaySupport support, MockStoreService storeService) {
        this.support = support;
        this.storeService = storeService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.303.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        String dbtrProtocol = ctx.document() == null ? "" : support.text(ctx.document(), "DbtrProtocol");
        ProtocolState protocolState = storeService.findProtocol(dbtrProtocol, ctx.acctNo());
        String responseXml = support.buildCaps304(protocolState, ctx.requestHeader(), ctx.scenarioRule());
        String protocolNo = protocolState == null ? ctx.protocolNo() : protocolState.protocolNo;
        String status = protocolState == null
                ? support.resolveStatus(ctx.scenarioRule(), "FAIL")
                : support.safe(protocolState.status, "SUCC");
        return GatewayDispatchResult.of("caps.304.001.01", responseXml, status,
                protocolNo, "", "");
    }
}

@Component
class ProtocolCancelHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockCallbackService callbackService;

    ProtocolCancelHandler(MockGatewaySupport support, MockCallbackService callbackService) {
        this.support = support;
        this.callbackService = callbackService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.307.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        String responseXml = support.buildCaps900(support.successCorp(ctx.requestHeader()),
                support.resolveResFlag(ctx.scenarioRule(), "SUCC"),
                support.resolveCode(ctx.scenarioRule(), "00000000"),
                support.resolveMsg(ctx.scenarioRule(), "cancel accepted"));
        if (!support.isAutoCallbackDisabled(ctx.scenarioRule())) {
            String orgnlId = ctx.document() == null ? ctx.protocolNo() : support.text(ctx.document(), "OrgnlId");
            callbackService.scheduleCaps308(ctx.requestHeader(), orgnlId, "CANCEL-" + support.timestamp());
        }
        return GatewayDispatchResult.of("caps.900.001.01", responseXml,
                support.resolveStatus(ctx.scenarioRule(), "SUCC"),
                "", "", "");
    }
}
