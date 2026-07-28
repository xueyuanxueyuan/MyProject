package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.TradeState;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class TradeApplyHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;
    private final MockCallbackService callbackService;

    TradeApplyHandler(MockGatewaySupport support, MockStoreService storeService,
                      MockCallbackService callbackService) {
        this.support = support;
        this.storeService = storeService;
        this.callbackService = callbackService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.201.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        TradeState tradeState = support.buildTradeState(ctx.document(), ctx.scenarioRule());
        storeService.saveTrade(tradeState);
        String responseXml = support.buildCaps202(tradeState, ctx.requestHeader());
        callbackService.scheduleCaps205(ctx.requestHeader(), tradeState);
        return GatewayDispatchResult.of("caps.202.001.01", responseXml,
                support.safe(tradeState.status, "SUCC"),
                "", "", tradeState.sysSeqNo);
    }
}

@Component
class TradeQueryHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;

    TradeQueryHandler(MockGatewaySupport support, MockStoreService storeService) {
        this.support = support;
        this.storeService = storeService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.203.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        TradeState tradeState = storeService.findTrade(ctx.sysSeqNo(), ctx.reqId());
        String responseXml = support.buildCaps204(tradeState, ctx.requestHeader(), ctx.scenarioRule());
        String status = tradeState == null
                ? support.resolveStatus(ctx.scenarioRule(), "FAIL")
                : support.safe(tradeState.status, "SUCC");
        return GatewayDispatchResult.of("caps.204.001.01", responseXml, status,
                "", "", tradeState == null ? ctx.sysSeqNo() : tradeState.sysSeqNo);
    }
}

@Component
class ProbeHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;

    ProbeHandler(MockGatewaySupport support) {
        this.support = support;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.999.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        String responseXml = support.buildCaps900(support.successCorp(ctx.requestHeader()),
                support.resolveResFlag(ctx.scenarioRule(), "SUCC"),
                support.resolveCode(ctx.scenarioRule(), "00000000"),
                support.resolveMsg(ctx.scenarioRule(), "探测成功"));
        return GatewayDispatchResult.of("caps.900.001.01", responseXml,
                support.resolveStatus(ctx.scenarioRule(), "SUCC"),
                "", "", "");
    }
}

@Component
class ReconHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;

    ReconHandler(MockGatewaySupport support) {
        this.support = support;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.601.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        String checkDate = ctx.document() == null ? support.currentDate() : support.text(ctx.document(), "CheckDate");
        String tranCode = ctx.document() == null ? "" : support.text(ctx.document(), "TranCode");
        String responseXml = support.buildCaps602(ctx.requestHeader(), checkDate, tranCode, ctx.scenarioRule());
        return GatewayDispatchResult.of("caps.602.001.01", responseXml,
                support.resolveStatus(ctx.scenarioRule(), "SUCC"),
                "", "", "");
    }
}