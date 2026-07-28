package cn.capinfo.gjj.yhtmock.service;

import cn.capinfo.gjj.yhtmock.model.BatchState;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class BatchApplyHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;
    private final MockCallbackService callbackService;

    BatchApplyHandler(MockGatewaySupport support, MockStoreService storeService,
                      MockCallbackService callbackService) {
        this.support = support;
        this.storeService = storeService;
        this.callbackService = callbackService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.101.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        BatchState batchState = support.buildBatchState(ctx.document(), ctx.scenarioRule());
        storeService.saveBatch(batchState);
        String responseXml = support.buildCaps102(batchState, ctx.requestHeader());
        callbackService.scheduleCaps107(ctx.requestHeader(), batchState);
        return GatewayDispatchResult.of("caps.102.001.01", responseXml,
                support.safe(batchState.status, "SUCC"),
                "", batchState.batchNo, "");
    }
}

@Component
class BatchQueryHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;

    BatchQueryHandler(MockGatewaySupport support, MockStoreService storeService) {
        this.support = support;
        this.storeService = storeService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.103.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        String batchNo = ctx.document() == null ? ctx.batchNo() : support.text(ctx.document(), "BatchNo");
        BatchState batchState = storeService.findBatch(batchNo);
        String responseXml = support.buildCaps104(batchState, ctx.requestHeader(), ctx.scenarioRule());
        String status = batchState == null
                ? support.resolveStatus(ctx.scenarioRule(), "FAIL")
                : support.safe(batchState.status, "SUCC");
        return GatewayDispatchResult.of("caps.104.001.01", responseXml, status,
                "", batchState == null ? ctx.batchNo() : batchState.batchNo, "");
    }
}

@Component
class BatchConfirmHandler implements CapsMessageHandler {

    private final MockGatewaySupport support;
    private final MockStoreService storeService;

    BatchConfirmHandler(MockGatewaySupport support, MockStoreService storeService) {
        this.support = support;
        this.storeService = storeService;
    }

    @Override
    public Set<String> supportedMesgTypes() {
        return Set.of("caps.105.001.01");
    }

    @Override
    public GatewayDispatchResult handle(GatewayRequestContext ctx) {
        String batchNo = ctx.document() == null ? ctx.batchNo() : support.text(ctx.document(), "BatchNo");
        BatchState batchState = storeService.findBatch(batchNo);
        String responseXml = support.buildCaps106(batchState, ctx.requestHeader(), ctx.scenarioRule());
        String status = batchState == null
                ? support.resolveStatus(ctx.scenarioRule(), "FAIL")
                : support.safe(batchState.status, "SUCC");
        return GatewayDispatchResult.of("caps.106.001.01", responseXml, status,
                "", batchState == null ? ctx.batchNo() : batchState.batchNo, "");
    }
}