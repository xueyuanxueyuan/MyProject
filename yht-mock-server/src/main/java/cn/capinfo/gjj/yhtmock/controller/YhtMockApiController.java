package cn.capinfo.gjj.yhtmock.controller;

import cn.capinfo.gjj.yhtmock.model.MockSettings;
import cn.capinfo.gjj.yhtmock.model.BankCounterparty;
import cn.capinfo.gjj.yhtmock.model.MockScenarioRule;
import cn.capinfo.gjj.yhtmock.service.MockCallbackService;
import cn.capinfo.gjj.yhtmock.service.MockGatewayService;
import cn.capinfo.gjj.yhtmock.service.MockStoreService;
import cn.capinfo.gjj.yhtmock.service.MockStoreService.ClearHistoryResult;
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

    /**
     * 业务查询列表：type=trade（单笔交易）/ batch（批量交易）/ protocol（签约类交易），服务端分页。
     */
    @GetMapping("/business/{type}")
    public Map<String, Object> business(@PathVariable String type,
                                        @RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) int size) {
        try {
            return storeService.businessList(type, page, size);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    /**
     * 修复仍停留在处理中的历史批次：按各自结果文件推导终态并落库（幂等，可重复执行）。
     */
    @PostMapping("/batches/repair-processing")
    public Map<String, Object> repairProcessingBatches() {
        return storeService.repairProcessingBatches();
    }

    /**
     * 批量明细列表：逐笔明细的状态与失败原因，并关联资金流水状态。
     */
    @GetMapping("/batches/{batchNo}/details")
    public Map<String, Object> batchDetails(@PathVariable String batchNo) {
        try {
            return storeService.batchDetails(batchNo);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/callback-config")
    public MockSettings callbackConfig() {
        return storeService.getSettings();
    }

    @PostMapping("/callback-config")
    public MockSettings updateCallbackConfig(@RequestBody MockSettings settings) {
        try {
            MockSettings saved = storeService.updateSettings(settings);
            org.slf4j.LoggerFactory.getLogger(YhtMockApiController.class).info(
                    "保存回调/动账配置成功：自动回调={} 动账通知={} 对手账户兜底={} 通知地址={}",
                    saved.autoPushEnabled, saved.movement != null && saved.movement.enabled,
                    saved.movement != null && saved.movement.fallbackEnabled,
                    saved.movement == null ? "" : saved.movement.targetUrl);
            return saved;
        } catch (IllegalArgumentException exception) {
            org.slf4j.LoggerFactory.getLogger(YhtMockApiController.class)
                    .warn("保存回调/动账配置被拒绝: {}", exception.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/bank-counterparties")
    public List<BankCounterparty> bankCounterparties() {
        return storeService.listBankCounterparties();
    }

    @PostMapping("/bank-counterparties")
    public BankCounterparty saveBankCounterparty(@RequestBody BankCounterparty account) {
        try {
            return storeService.saveBankCounterparty(account);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/bank-counterparties/{bankId}")
    public Map<String, Object> deleteBankCounterparty(@PathVariable String bankId) {
        return Map.of("success", storeService.deleteBankCounterparty(bankId));
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

    @PostMapping("/store/clear-history")
    public Map<String, Object> clearHistory() {
        ClearHistoryResult result = storeService.clearHistoryFiles();
        return Map.of(
                "success", true,
                "deletedFiles", result.deletedFiles(),
                "missingFiles", result.missingFiles(),
                "failedFiles", result.failedFiles(),
                "clearedTables", result.clearedTables(),
                "storage", "database",
                "message", "历史数据已清除"
        );
    }

    @PostMapping("/trigger-callback")
    public Map<String, Object> triggerCallback(@RequestBody TriggerCallbackRequest request) {
        String body = callbackService.triggerManualCallback(request.callbackMesgType, request.targetUrl,
                request.reqId, request.protocolNo, request.batchNo, request.sysSeqNo,
                request.acctNo, request.customerName, request.customerId,
                request.feeNoList, request.bankId, request.remark);
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
        public String acctNo;
        public String customerName;
        public String customerId;
        public String feeNoList;
        public String bankId;
        public String remark;
    }
}

