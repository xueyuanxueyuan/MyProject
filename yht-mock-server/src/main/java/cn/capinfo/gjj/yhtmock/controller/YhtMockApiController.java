package cn.capinfo.gjj.yhtmock.controller;

import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import cn.capinfo.gjj.yhtmock.service.MockCallbackService;
import cn.capinfo.gjj.yhtmock.service.MockGatewayService;
import cn.capinfo.gjj.yhtmock.service.MockStoreService;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/yht-mock/api")
public class YhtMockApiController {

    private final MockGatewayService gatewayService;
    private final MockStoreService storeService;
    private final MockCallbackService callbackService;

    public YhtMockApiController(MockGatewayService gatewayService,
                                MockStoreService storeService,
                                MockCallbackService callbackService) {
        this.gatewayService = gatewayService;
        this.storeService = storeService;
        this.callbackService = callbackService;
    }

    @PostMapping(value = "/gateway", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_XML_VALUE)
    public String gateway(@RequestBody String rawMessage) {
        return gatewayService.dispatch(rawMessage);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return storeService.buildStats();
    }

    @GetMapping("/logs")
    public List<?> logs(@RequestParam(defaultValue = "100") @Min(1) int limit) {
        return storeService.listRecords(limit);
    }

    @DeleteMapping("/logs")
    public String clearLogs() {
        storeService.clearRecords();
        return "OK";
    }

    @GetMapping("/protocols")
    public List<?> protocols() {
        return storeService.listProtocols();
    }

    @GetMapping("/trades")
    public List<?> trades() {
        return storeService.listTrades();
    }

    @GetMapping("/batches")
    public List<?> batches() {
        return storeService.listBatches();
    }

    @GetMapping("/callback-config")
    public MockSettings callbackConfig() {
        return storeService.getSettings();
    }

    @PostMapping("/callback-config")
    public MockSettings updateCallbackConfig(@RequestBody MockSettings settings) {
        return storeService.updateSettings(settings);
    }

    @GetMapping("/scenarios")
    public List<MockScenarioRule> scenarios() {
        return storeService.listScenarios();
    }

    @PostMapping("/scenarios")
    public MockScenarioRule saveScenario(@RequestBody MockScenarioRule scenarioRule) {
        return storeService.saveScenario(scenarioRule);
    }

    @DeleteMapping("/scenarios/{id}")
    public Map<String, Object> deleteScenario(@PathVariable long id) {
        return Map.of("success", storeService.deleteScenario(id), "id", id);
    }

    @PostMapping("/trigger-callback")
    public Map<String, Object> triggerCallback(@RequestBody TriggerCallbackRequest request) {
        String body = callbackService.triggerManualCallback(request.callbackMesgType, request.targetUrl,
                request.reqId, request.protocolNo, request.batchNo, request.sysSeqNo);
        return Map.of(
                "success", true,
                "callbackMesgType", request.callbackMesgType,
                "targetUrl", request.targetUrl == null || request.targetUrl.isBlank()
                        ? storeService.getSettings().defaultTargetUrl : request.targetUrl,
                "body", body
        );
    }

    @GetMapping("/open/{page}")
    public Map<String, String> openPage(@PathVariable String page) {
        return Map.of("url", "/yht-mock/" + page);
    }

    public static class TriggerCallbackRequest {
        public String callbackMesgType = "caps.306.001.01";
        public String targetUrl;
        public String reqId;
        public String protocolNo;
        public String batchNo;
        public String sysSeqNo;
    }
}
