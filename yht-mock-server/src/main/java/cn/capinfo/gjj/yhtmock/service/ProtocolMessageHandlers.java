package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.ProtocolState;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

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
        ProtocolState protocolState = support.buildProtocolState(ctx.document(), ctx.requestHeader(), ctx.scenarioRule());
        protocolState.signReqId = support.safe(ctx.reqId());
        protocolState.status = support.resolveStatus(ctx.scenarioRule(), "ACCEPTED");
        protocolState.callbackMesgType = support.defaultString(
                support.resolveCallbackType(ctx.scenarioRule()), "caps.306.001.01");
        protocolState.callbackEnabled = !support.isAutoCallbackDisabled(ctx.scenarioRule());
        storeService.saveProtocol(protocolState);

        String responseXml = support.buildCaps900(support.successCorp(ctx.requestHeader()),
                support.safe(protocolState.resFlag, "SUCC"),
                support.resolveCode(ctx.scenarioRule(), "00000000"),
                support.resolveMsg(ctx.scenarioRule(), "受理成功"));

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
                support.resolveMsg(ctx.scenarioRule(), "撤销受理成功"));
        if (!support.isAutoCallbackDisabled(ctx.scenarioRule())) {
            String orgnlId = ctx.document() == null ? ctx.protocolNo() : support.text(ctx.document(), "OrgnlId");
            callbackService.scheduleCaps308(ctx.requestHeader(), orgnlId, "CANCEL-" + support.timestamp());
        }
        return GatewayDispatchResult.of("caps.900.001.01", responseXml,
                support.resolveStatus(ctx.scenarioRule(), "SUCC"),
                "", "", "");
    }
}